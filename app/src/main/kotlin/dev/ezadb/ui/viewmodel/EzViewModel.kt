package dev.ezadb.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.usb.UsbDevice
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ezadb.EzAdbApp
import dev.ezadb.core.AppFilter
import dev.ezadb.core.AppInfo
import dev.ezadb.core.AppSort
import dev.ezadb.core.ConnectionKind
import dev.ezadb.core.ConnectionState
import dev.ezadb.core.DeviceInfo
import dev.ezadb.core.DiscoveredEndpoint
import dev.ezadb.core.Endpoint
import dev.ezadb.core.FileSort
import dev.ezadb.core.PathUtils
import dev.ezadb.core.QuickToggleState
import dev.ezadb.core.RemoteFile
import dev.ezadb.core.SettingEntry
import dev.ezadb.core.SettingNamespace
import dev.ezadb.core.TerminalKind
import dev.ezadb.core.TerminalLine
import dev.ezadb.R
import dev.ezadb.data.AdbErrors
import dev.ezadb.data.AppLanguage
import dev.ezadb.data.AppPreferences
import dev.ezadb.data.DemoBackend
import dev.ezadb.data.TerminalSession
import dev.ezadb.data.TransferHandle
import dev.ezadb.data.UsbHelper
import dev.ezadb.util.SafUtils
import dev.ezadb.util.ShareUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class EzUiState(
    val prefs: AppPreferences = AppPreferences(),
    val connection: ConnectionState = ConnectionState.Disconnected,
    val device: DeviceInfo? = null,
    val busy: Boolean = false,
    val progress: OperationProgress? = null,
    val imagePreview: ImagePreview? = null,
    val appIcons: Map<String, Bitmap> = emptyMap(),
    val message: String? = null,
    val discovered: List<DiscoveredEndpoint> = emptyList(),
    val apps: List<AppInfo> = emptyList(),
    val appsLoading: Boolean = false,
    val appQuery: String = "",
    val appFilter: AppFilter = AppFilter.ALL,
    val appSort: AppSort = AppSort.NAME,
    val selectedApp: AppInfo? = null,
    val files: List<RemoteFile> = emptyList(),
    val filesLoading: Boolean = false,
    val currentPath: String = "/sdcard",
    val fileQuery: String = "",
    val fileSort: FileSort = FileSort.NAME,
    val selectedFile: RemoteFile? = null,
    val editorFile: RemoteFile? = null,
    val editorText: String = "",
    val editorDirty: Boolean = false,
    val settings: List<SettingEntry> = emptyList(),
    val settingsLoading: Boolean = false,
    val settingsNamespace: SettingNamespace = SettingNamespace.GLOBAL,
    val settingsQuery: String = "",
    val toggles: List<QuickToggleState> = emptyList(),
    val togglesLoading: Boolean = false,
    val terminalLines: List<TerminalLine> = emptyList(),
    val terminalInput: String = "",
    val terminalRunning: Boolean = false,
    val terminalInteractive: Boolean = false,
) {
    val demo: Boolean get() = prefs.demoMode
    val connected: Boolean get() = connection is ConnectionState.Connected
}

class EzViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        const val MAX_IMAGE_BYTES = 32L * 1024 * 1024
    }

    private val app = application as EzAdbApp
    private val repo = app.deviceRepository
    private val connection = app.connection
    private val prefsRepo = app.preferences
    private val quickToggles = app.quickToggles
    private val demo: DemoBackend = app.demo

    private fun str(res: Int, vararg args: Any?): String = getApplication<Application>().getString(res, *args)

    private val _state = MutableStateFlow(EzUiState())
    val state: StateFlow<EzUiState> = _state.asStateFlow()
    val prefs: StateFlow<AppPreferences> = _state.map { it.prefs }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences())

    private var terminalSession: TerminalSession? = null
    private val history = dev.ezadb.core.TerminalHistory()
    private var demoInitialized = false
    private var lastLanguage: AppLanguage? = null
    private var currentAction: Job? = null
    private val iconRequests = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    init {
        viewModelScope.launch {
            prefsRepo.flow.collect { prefs ->
                val wasDemo = _state.value.prefs.demoMode
                val previousLanguage = lastLanguage
                lastLanguage = prefs.language
                _state.update { it.copy(prefs = prefs) }
                if (prefs.demoMode && !wasDemo) enableDemo()
                if (!prefs.demoMode && wasDemo) disableDemo()
                if (previousLanguage != null && previousLanguage != prefs.language) {
                    refreshDevice()
                    refreshToggles()
                    refreshApps()
                    refreshFiles()
                    refreshSettings()
                }
            }
        }
        viewModelScope.launch {
            connection.state.collect { conn ->
                if (_state.value.prefs.demoMode) return@collect
                _state.update { it.copy(connection = conn, device = (conn as? ConnectionState.Connected)?.device ?: it.device) }
                if (conn is ConnectionState.Connected) {
                    repo.onConnectionChanged()
                    refreshDevice()
                    refreshToggles()
                }
                if (conn is ConnectionState.Disconnected || conn is ConnectionState.Error) {
                    closeTerminal()
                }
            }
        }
        viewModelScope.launch {
            app.discovery.endpoints.collect { list -> _state.update { it.copy(discovered = list) } }
        }
    }

    // region connection
    fun connectTcp(host: String, port: Int) = action(str(R.string.connect)) {
        connection.connectTcp(host, port)
        prefsRepo.addRecent(Endpoint(host, port, "$host:$port", ConnectionKind.TCP))
        showMessage(str(R.string.connected_to, host, port))
    }

    fun pair(host: String, port: Int, code: String) = action(str(R.string.pair)) {
        connection.pair(host, port, code)
        showMessage(str(R.string.pairing_success))
    }

    fun connectDiscovered(endpoint: DiscoveredEndpoint) {
        if (endpoint.type == dev.ezadb.core.DiscoveryType.PAIR) {
            showMessage(str(R.string.pairing_code_hint))
        } else {
            connectTcp(endpoint.host, endpoint.port)
        }
    }

    fun onUsbAttached(device: UsbDevice) {
        if (_state.value.prefs.demoMode) return
        if (UsbHelper.hasPermission(getApplication(), device)) {
            connectUsb(device)
        } else {
            UsbHelper.requestPermission(getApplication(), device)
            showMessage(str(R.string.allow_usb_access))
        }
    }

    fun onUsbPermission(device: UsbDevice, granted: Boolean) {
        if (granted) connectUsb(device) else showMessage(str(R.string.usb_permission_denied))
    }

    fun onUsbDetached(device: UsbDevice) {
        connection.onUsbDetached(device)
    }

    fun connectUsb(device: UsbDevice) = action(str(R.string.action_usb)) {
        connection.connectUsb(device)
        showMessage(str(R.string.usb_connected))
    }

    fun disconnect() = action(str(R.string.disconnect)) {
        closeTerminal()
        if (_state.value.prefs.demoMode) {
            prefsRepo.setDemoMode(false)
            disableDemo()
            return@action
        }
        connection.disconnect()
        _state.update {
            it.copy(
                connection = ConnectionState.Disconnected, device = null, apps = emptyList(), files = emptyList(),
                settings = emptyList(), toggles = emptyList(), selectedApp = null, selectedFile = null,
                editorFile = null, terminalLines = emptyList(), terminalRunning = false,
            )
        }
        showMessage(str(R.string.disconnected_message))
    }

    fun refreshUsbDevices(): List<UsbDevice> = UsbHelper.adbDevices(getApplication())

    private fun enableDemo() {
        closeTerminal()
        viewModelScope.launch { runCatching { connection.disconnect() } }
        demoInitialized = true
        _state.update {
            it.copy(
                connection = ConnectionState.Connected(demo.deviceInfo()),
                device = demo.deviceInfo(),
                apps = demo.apps(),
                files = demo.listFiles("/sdcard"),
                currentPath = "/sdcard",
                settings = demo.listSettings(SettingNamespace.GLOBAL),
                toggles = demo.toggles(),
                terminalLines = listOf(TerminalLine(str(R.string.demo_shell_welcome), TerminalKind.SYSTEM)),
                terminalRunning = true,
                terminalInteractive = false,
            )
        }
        showMessage(str(R.string.demo_enabled))
    }

    private fun disableDemo() {
        demoInitialized = false
        closeTerminal()
        _state.update {
            it.copy(
                connection = ConnectionState.Disconnected, device = null, apps = emptyList(), files = emptyList(),
                settings = emptyList(), toggles = emptyList(), terminalLines = emptyList(), terminalRunning = false,
            )
        }
        showMessage(str(R.string.demo_disabled))
    }
    // endregion

    // region device
    fun refreshDevice() = viewModelScope.launch {
        if (_state.value.prefs.demoMode) {
            _state.update { it.copy(device = demo.deviceInfo(), connection = ConnectionState.Connected(demo.deviceInfo())) }
            return@launch
        }
        runCatching { repo.deviceInfo() }
            .onSuccess { info -> _state.update { it.copy(device = info, connection = ConnectionState.Connected(info)) } }
            .onFailure { error -> showMessage(str(R.string.device_info_error, err(error))) }
    }

    fun refreshToggles() = viewModelScope.launch {
        _state.update { it.copy(togglesLoading = true) }
        val result = runCatching {
            if (_state.value.prefs.demoMode) demo.toggles() else quickToggles.readAll()
        }
        _state.update { it.copy(toggles = result.getOrElse { emptyList() }, togglesLoading = false) }
        result.exceptionOrNull()?.let { showMessage(str(R.string.toggles_error, err(it))) }
    }

    fun setToggle(id: String, enabled: Boolean) = action(str(R.string.action_toggle)) {
        _state.update { state -> state.copy(toggles = state.toggles.map { if (it.id == id) it.copy(busy = true) else it }) }
        runCatching {
            if (_state.value.prefs.demoMode) demo.setToggle(id, enabled) else quickToggles.set(id, enabled)
        }.onFailure { error ->
            _state.update { state -> state.copy(toggles = state.toggles.map { if (it.id == id) it.copy(busy = false, error = err(error)) else it }) }
            showMessage(str(R.string.toggle_error, err(error)))
            return@action
        }
        refreshToggles()
    }

    fun reboot(mode: String = "") = action(str(R.string.reboot)) {
        if (_state.value.prefs.demoMode) demo.shell("reboot $mode") else repo.reboot(mode)
        showMessage(if (mode.isBlank()) str(R.string.rebooting) else str(R.string.rebooting_mode, mode))
        if (!_state.value.prefs.demoMode) disconnect()
    }

    fun setScreen(on: Boolean) = action(str(R.string.action_screen)) {
        if (_state.value.prefs.demoMode) demo.setToggle("screen", on) else repo.setScreenOn(on)
        refreshToggles()
    }

    fun enableWifiAdb() = action(str(R.string.action_wifi_adb)) {
        val output = if (_state.value.prefs.demoMode) str(R.string.demo_wifi_adb) else repo.enableWifiAdb()
        showMessage(output.ifBlank { str(R.string.wifi_adb_enabled) })
    }

    fun captureScreenshot() = transferAction(str(R.string.action_screenshot), null) { handle ->
        val bytes = if (_state.value.prefs.demoMode) {
            val png = withContext(Dispatchers.Default) { demo.screenshotPng() }
            simulateTransfer(png.size.toLong(), handle)
            png
        } else {
            repo.screenshotBytes(handle)
        }
        showImage(bytes, "image/png", "ezadb-screen-${System.currentTimeMillis()}.png")
        showMessage(str(R.string.screenshot_captured))
    }

    fun savePreview(target: Uri) {
        val preview = _state.value.imagePreview ?: return
        transferAction(str(R.string.save_to_device), preview.sizeBytes) { handle ->
            val output = getApplication<EzAdbApp>().contentResolver.openOutputStream(target)
                ?: throw IOException(str(R.string.file_write_open_failed))
            output.use { stream ->
                var written = 0
                while (written < preview.bytes.size) {
                    val chunk = minOf(64 * 1024, preview.bytes.size - written)
                    stream.write(preview.bytes, written, chunk)
                    written += chunk
                    handle.advance(chunk.toLong())
                }
                stream.flush()
            }
            handle.finish()
            showMessage(str(R.string.file_saved))
        }
    }

    fun sharePreview() {
        val preview = _state.value.imagePreview ?: return
        runCatching {
            val dir = File(getApplication<EzAdbApp>().cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, preview.name)
            file.writeBytes(preview.bytes)
            ShareUtils.shareFile(getApplication(), file, preview.mime)
        }.onFailure { showMessage(str(R.string.action_error, str(R.string.share), err(it))) }
    }

    fun closePreview() {
        _state.update { it.copy(imagePreview = null) }
    }

    fun openImage(file: RemoteFile) = transferAction(str(R.string.loading_image), file.sizeBytes) { handle ->
        if (file.sizeBytes > MAX_IMAGE_BYTES) {
            throw IOException(str(R.string.image_too_large, dev.ezadb.core.Formatters.bytes(file.sizeBytes)))
        }
        val bytes = if (_state.value.prefs.demoMode) {
            val data = demo.readFile(file.path)
            simulateTransfer(data.size.toLong(), handle)
            data
        } else repo.readFile(file.path, MAX_IMAGE_BYTES, handle)
        showImage(bytes, PathUtils.imageMime(file.path), file.name)
    }

    /** Loads a screenful of launcher icons in one device round trip. */
    fun prefetchIcons(packages: List<String>) {
        if (packages.isEmpty()) return
        viewModelScope.launch {
            val missing = withContext(Dispatchers.Default) {
                val known = _state.value.appIcons
                packages.distinct().filter { it !in known && iconRequests.add(it) }
            }
            if (missing.isEmpty()) return@launch
            val loaded = withContext(Dispatchers.Default) {
                val result = LinkedHashMap<String, Bitmap>()
                if (_state.value.prefs.demoMode) {
                    missing.forEach { pkg ->
                        runCatching { demo.appIcon(pkg) }.getOrNull()?.let { bytes ->
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { result[pkg] = it }
                        }
                    }
                } else {
                    runCatching { repo.appIcons(missing) }.getOrDefault(emptyMap()).forEach { (pkg, bytes) ->
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { result[pkg] = it }
                    }
                }
                result
            }
            iconRequests.removeAll(missing.toSet())
            if (loaded.isNotEmpty()) _state.update { it.copy(appIcons = it.appIcons + loaded) }
        }
    }

    suspend fun loadAppIcon(packageName: String): Bitmap? = runCatching {
        withContext(Dispatchers.Default) {
            val bytes = if (_state.value.prefs.demoMode) demo.appIcon(packageName) else repo.appIcon(packageName)
            bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
    }.getOrNull()

    private suspend fun showImage(bytes: ByteArray, mime: String, name: String) {
        val bitmap = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            ?: throw IOException(str(R.string.image_load_failed))
        _state.update { it.copy(imagePreview = ImagePreview(name, bitmap, bytes, mime)) }
    }
    // endregion

    // region apps
    fun refreshApps() = viewModelScope.launch {
        _state.update { it.copy(appsLoading = true) }
        runCatching { if (_state.value.prefs.demoMode) demo.apps() else repo.listApps() }
            .onSuccess { apps -> _state.update { it.copy(apps = apps, appsLoading = false) } }
            .onFailure { error -> _state.update { it.copy(appsLoading = false) }; showMessage(str(R.string.apps_error, err(error))) }
    }

    fun setAppQuery(query: String) = _state.update { it.copy(appQuery = query) }
    fun setAppFilter(filter: AppFilter) = _state.update { it.copy(appFilter = filter) }
    fun setAppSort(sort: AppSort) = _state.update { it.copy(appSort = sort) }
    fun selectApp(app: AppInfo?) = _state.update { it.copy(selectedApp = app) }

    fun loadAppDetails(packageName: String) = action(str(R.string.action_app_details)) {
        val details = if (_state.value.prefs.demoMode) {
            demo.apps().firstOrNull { it.packageName == packageName } ?: throw IOException(str(R.string.app_not_found))
        } else repo.appDetails(packageName)
        _state.update { state ->
            state.copy(
                selectedApp = details,
                apps = state.apps.map { if (it.packageName == packageName) details.copy(label = it.label) else it },
            )
        }
    }

    fun setAppEnabled(packageName: String, enabled: Boolean) = action(str(R.string.action_app_state)) {
        if (_state.value.prefs.demoMode) demo.setAppEnabled(packageName, enabled) else repo.setAppEnabled(packageName, enabled)
        showMessage(if (enabled) str(R.string.app_enabled) else str(R.string.app_disabled))
        refreshApps()
    }

    fun forceStop(packageName: String) = action(str(R.string.action_stop)) {
        if (_state.value.prefs.demoMode) demo.forceStop(packageName) else repo.forceStop(packageName)
        showMessage(str(R.string.app_stopped))
    }

    fun clearAppData(packageName: String) = action(str(R.string.action_clear)) {
        if (_state.value.prefs.demoMode) demo.clearData(packageName) else repo.clearAppData(packageName)
        showMessage(str(R.string.app_data_cleared))
    }

    fun uninstallApp(packageName: String) = action(str(R.string.action_delete)) {
        if (_state.value.prefs.demoMode) demo.uninstall(packageName) else repo.uninstallApp(packageName)
        showMessage(str(R.string.app_uninstalled))
        refreshApps()
    }

    fun launchApp(packageName: String) = action(str(R.string.action_launch)) {
        if (_state.value.prefs.demoMode) showMessage(str(R.string.demo_launch, packageName)) else repo.launchApp(packageName)
        showMessage(str(R.string.app_launched))
    }

    fun openAppSettings(packageName: String) = action(str(R.string.action_app_settings)) {
        if (_state.value.prefs.demoMode) showMessage(str(R.string.demo_app_settings, packageName)) else repo.openAppSettings(packageName)
    }

    fun installApk(uri: Uri) {
        val size = SafUtils.size(getApplication(), uri).takeIf { it > 0 }
        transferAction(str(R.string.action_install_apk), size) { handle ->
            if (_state.value.prefs.demoMode) {
                simulateTransfer(size ?: (24L * 1024 * 1024), handle)
                showMessage(str(R.string.demo_apk_installed))
            } else {
                repo.installApk(uri, handle)
            }
            showMessage(str(R.string.apk_installed))
            refreshApps()
        }
    }

    fun extractApk(packageName: String, target: Uri) {
        val size = _state.value.apps.firstOrNull { it.packageName == packageName }?.sizeBytes
            ?: _state.value.selectedApp?.sizeBytes
        transferAction(str(R.string.action_extract_apk), size) { handle ->
            if (_state.value.prefs.demoMode) {
                val payload = "demo apk".toByteArray()
                simulateTransfer(size ?: payload.size.toLong(), handle)
                getApplication<EzAdbApp>().contentResolver.openOutputStream(target)?.use { it.write(payload) }
            } else repo.extractApk(packageName, target, handle)
            showMessage(str(R.string.apk_saved))
        }
    }
    // endregion

    // region files
    fun refreshFiles(path: String = _state.value.currentPath) = viewModelScope.launch {
        val normalized = PathUtils.normalize(path)
        _state.update { it.copy(filesLoading = true, currentPath = normalized) }
        runCatching { if (_state.value.prefs.demoMode) demo.listFiles(normalized) else repo.listFiles(normalized) }
            .onSuccess { files -> _state.update { it.copy(files = files, filesLoading = false) } }
            .onFailure { error -> _state.update { it.copy(filesLoading = false) }; showMessage(str(R.string.files_error, err(error))) }
    }

    fun navigateUp() = refreshFiles(PathUtils.parent(_state.value.currentPath))
    fun setFileQuery(query: String) = _state.update { it.copy(fileQuery = query) }
    fun setFileSort(sort: FileSort) = _state.update { it.copy(fileSort = sort) }
    fun selectFile(file: RemoteFile?) = _state.update { it.copy(selectedFile = file) }

    fun openFile(file: RemoteFile) {
        if (file.isDirectory) {
            refreshFiles(file.path)
            return
        }
        if (PathUtils.isImageFile(file.path)) {
            openImage(file)
            return
        }
        if (!PathUtils.isTextFile(file.path)) {
            showMessage(str(R.string.file_binary_hint))
            return
        }
        transferAction(str(R.string.action_open_file), file.sizeBytes) { handle ->
            val bytes = if (_state.value.prefs.demoMode) {
                val data = demo.readFile(file.path)
                simulateTransfer(data.size.toLong(), handle)
                data
            } else repo.readFile(file.path, handle = handle)
            _state.update { it.copy(editorFile = file, editorText = String(bytes, Charsets.UTF_8), editorDirty = false) }
        }
    }

    fun updateEditor(text: String) = _state.update { it.copy(editorText = text, editorDirty = true) }
    fun closeEditor() = _state.update { it.copy(editorFile = null, editorText = "", editorDirty = false) }

    fun saveEditor() {
        val file = _state.value.editorFile ?: return
        val bytes = _state.value.editorText.toByteArray(Charsets.UTF_8)
        transferAction(str(R.string.action_save_file), bytes.size.toLong()) { handle ->
            if (_state.value.prefs.demoMode) {
                simulateTransfer(bytes.size.toLong(), handle)
                demo.writeFile(file.path, bytes)
            } else repo.writeFile(file.path, bytes, handle = handle)
            _state.update { it.copy(editorDirty = false) }
            showMessage(str(R.string.file_saved))
            refreshFiles()
        }
    }

    fun downloadFile(file: RemoteFile, target: Uri) =
        transferAction(str(R.string.action_download), file.sizeBytes) { handle ->
            if (_state.value.prefs.demoMode) {
                val data = demo.readFile(file.path)
                simulateTransfer(data.size.toLong(), handle)
                getApplication<EzAdbApp>().contentResolver.openOutputStream(target)?.use { it.write(data) }
            } else repo.pullToUri(file.path, target, handle)
            showMessage(str(R.string.file_downloaded))
        }

    fun uploadFile(source: Uri) {
        val name = PathUtils.sanitizeLocalName(SafUtils.displayName(getApplication(), source))
        val remote = PathUtils.join(_state.value.currentPath, name)
        val size = SafUtils.size(getApplication(), source).takeIf { it > 0 }
        transferAction(str(R.string.action_upload), size) { handle ->
            if (_state.value.prefs.demoMode) {
                val data = getApplication<EzAdbApp>().contentResolver.openInputStream(source)?.use { it.readBytes() } ?: ByteArray(0)
                simulateTransfer(data.size.toLong(), handle)
                demo.writeFile(remote, data)
            } else repo.pushFromUri(source, remote, handle = handle)
            showMessage(str(R.string.file_uploaded, remote))
            refreshFiles()
        }
    }

    fun mkdir(name: String) = action(str(R.string.action_mkdir)) {
        val path = PathUtils.join(_state.value.currentPath, name)
        if (_state.value.prefs.demoMode) demo.mkdir(path) else repo.mkdir(path)
        refreshFiles()
    }

    fun renameFile(file: RemoteFile, newName: String) = action(str(R.string.action_rename)) {
        val newPath = PathUtils.join(PathUtils.parent(file.path), newName)
        if (_state.value.prefs.demoMode) demo.rename(file.path, newPath) else repo.rename(file.path, newPath)
        refreshFiles()
    }

    fun deleteFile(file: RemoteFile) = action(str(R.string.action_delete)) {
        if (_state.value.prefs.demoMode) demo.delete(file.path) else repo.delete(file.path, file.isDirectory)
        showMessage(str(R.string.file_deleted, file.name))
        refreshFiles()
    }

    fun chmodFile(file: RemoteFile, mode: String) = action(str(R.string.action_chmod)) {
        if (_state.value.prefs.demoMode) showMessage(str(R.string.demo_chmod, mode, file.path)) else repo.chmod(file.path, mode)
        refreshFiles()
    }
    // endregion

    // region settings
    fun refreshSettings(namespace: SettingNamespace = _state.value.settingsNamespace) = viewModelScope.launch {
        _state.update { it.copy(settingsLoading = true, settingsNamespace = namespace) }
        runCatching { if (_state.value.prefs.demoMode) demo.listSettings(namespace) else repo.listSettings(namespace) }
            .onSuccess { entries -> _state.update { it.copy(settings = entries, settingsLoading = false) } }
            .onFailure { error -> _state.update { it.copy(settingsLoading = false) }; showMessage(str(R.string.settings_error, err(error))) }
    }

    fun setSettingsQuery(query: String) = _state.update { it.copy(settingsQuery = query) }

    fun putSetting(entry: SettingEntry, value: String) = action(str(R.string.action_save_setting)) {
        val normalized = if (entry.type == dev.ezadb.core.SettingType.BOOL) dev.ezadb.core.SettingsCatalog.normalizeBool(value) else value
        if (!dev.ezadb.core.SettingsCatalog.validate(entry.type, normalized)) throw IOException(str(R.string.setting_invalid_value, entry.type))
        if (_state.value.prefs.demoMode) demo.putSetting(entry.namespace, entry.key, normalized)
        else repo.putSetting(entry.namespace, entry.key, normalized)
        showMessage(str(R.string.setting_saved, entry.key, normalized))
        refreshSettings()
        refreshToggles()
    }

    fun deleteSetting(entry: SettingEntry) = action(str(R.string.action_delete_setting)) {
        if (_state.value.prefs.demoMode) demo.deleteSetting(entry.namespace, entry.key) else repo.deleteSetting(entry.namespace, entry.key)
        refreshSettings()
    }

    fun addCustomSetting(namespace: SettingNamespace, key: String, value: String) = action(str(R.string.action_add_setting)) {
        if (key.isBlank()) throw IOException(str(R.string.key_empty))
        if (_state.value.prefs.demoMode) demo.putSetting(namespace, key, value) else repo.putSetting(namespace, key, value)
        refreshSettings()
    }
    // endregion

    // region terminal
    fun ensureTerminal() {
        val state = _state.value
        if (!state.connected) return
        if (state.prefs.demoMode) {
            if (!demoInitialized) enableDemo()
            _state.update { it.copy(terminalRunning = true, terminalInteractive = false) }
            return
        }
        if (terminalSession != null) return
        val kadb = connection.current() ?: return
        appendTerminal(TerminalLine(str(R.string.terminal_connecting), TerminalKind.SYSTEM))
        val session = TerminalSession(
            kadb = kadb,
            context = getApplication(),
            scope = viewModelScope,
            onLine = { line -> appendTerminal(line) },
            onClosed = { reason ->
                terminalSession = null
                _state.update { it.copy(terminalRunning = false) }
                appendTerminal(TerminalLine(reason ?: str(R.string.terminal_closed), TerminalKind.SYSTEM))
            },
        )
        runCatching {
            session.start()
            terminalSession = session
            _state.update { it.copy(terminalRunning = true, terminalInteractive = session.interactive) }
            if (session.interactive) session.sendLine("stty -echo")
        }.onFailure { error ->
            appendTerminal(TerminalLine(str(R.string.terminal_open_error, err(error)), TerminalKind.STDERR))
        }
    }

    fun setTerminalInput(input: String) = _state.update { it.copy(terminalInput = input) }

    fun sendTerminal(command: String) {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return
        history.add(trimmed)
        appendTerminal(TerminalLine("$ $trimmed", TerminalKind.INPUT))
        _state.update { it.copy(terminalInput = "") }
        if (_state.value.prefs.demoMode) {
            val result = demo.shell(trimmed)
            if (result.output.isNotBlank()) appendTerminal(TerminalLine(result.output.trimEnd('\n'), TerminalKind.STDOUT))
            if (result.error.isNotBlank()) appendTerminal(TerminalLine(result.error.trimEnd('\n'), TerminalKind.STDERR))
            if (!result.success) appendTerminal(TerminalLine(str(R.string.terminal_exit_code, result.exitCode), TerminalKind.EXIT))
            return
        }
        val session = terminalSession
        if (session == null) {
            ensureTerminal()
            viewModelScope.launch {
                kotlinx.coroutines.delay(300)
                terminalSession?.sendLine(trimmed)
            }
            return
        }
        runCatching { session.sendLine(trimmed) }
            .onFailure { error -> appendTerminal(TerminalLine(str(R.string.terminal_send_error, err(error)), TerminalKind.STDERR)) }
    }

    fun terminalHistoryPrevious(): String? {
        history.begin(_state.value.terminalInput)
        return history.previous()
    }

    fun terminalHistoryNext(): String? = history.next()

    fun interruptTerminal() {
        terminalSession?.interrupt()
        appendTerminal(TerminalLine("^C", TerminalKind.SYSTEM))
    }

    fun clearTerminal() {
        _state.update { it.copy(terminalLines = emptyList()) }
    }

    fun closeTerminal() {
        terminalSession?.close()
        terminalSession = null
        _state.update { it.copy(terminalRunning = false) }
    }

    private fun appendTerminal(line: TerminalLine) {
        val clean = stripAnsi(line.text)
        val newLines = clean.split('\n').map { TerminalLine(it, line.kind) }
        _state.update { state ->
            val combined = (state.terminalLines + newLines).takeLast(5000)
            state.copy(terminalLines = combined)
        }
    }

    private fun stripAnsi(text: String): String =
        text.replace(Regex("\u001B\\[[;?0-9]*[a-zA-Z]"), "").replace("\r", "")
    // endregion

    // region prefs / messages
    fun setThemeMode(mode: dev.ezadb.data.ThemeMode) = viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    fun setDynamicColor(value: Boolean) = viewModelScope.launch { prefsRepo.setDynamicColor(value) }
    fun setAccent(accent: dev.ezadb.data.Accent) = viewModelScope.launch { prefsRepo.setAccent(accent) }
    fun setTerminalFontSize(size: Int) = viewModelScope.launch { prefsRepo.setTerminalFontSize(size) }
    fun setConfirmDestructive(value: Boolean) = viewModelScope.launch { prefsRepo.setConfirmDestructive(value) }
    fun setKeepScreenOn(value: Boolean) = viewModelScope.launch { prefsRepo.setKeepScreenOn(value) }
    fun setHaptics(value: Boolean) = viewModelScope.launch { prefsRepo.setHaptics(value) }
    fun setDemoMode(value: Boolean) = viewModelScope.launch { prefsRepo.setDemoMode(value) }
    fun setLanguage(language: AppLanguage) = viewModelScope.launch { prefsRepo.setLanguage(language) }
    fun setIntroSeen(value: Boolean) = viewModelScope.launch { prefsRepo.setIntroSeen(value) }
    fun removeRecent(endpoint: Endpoint) = viewModelScope.launch { prefsRepo.removeRecent(endpoint) }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun err(error: Throwable?): String = AdbErrors.message(getApplication(), error)

    fun cancelAction() {
        currentAction?.cancel()
    }

    private fun action(label: String, block: suspend () -> Unit): Job {
        val job = viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            // Quick actions must not flash a card: only show it when the device is slow.
            val reveal = launch {
                delay(450)
                _state.update { it.copy(progress = OperationProgress(label = label, phase = ProgressPhase.WORKING)) }
            }
            try {
                block()
            } catch (cancelled: CancellationException) {
                showMessage(str(R.string.progress_cancelled))
            } catch (error: Throwable) {
                showMessage(str(R.string.action_error, label, err(error)))
            } finally {
                reveal.cancel()
                _state.update { it.copy(busy = false, progress = null) }
            }
        }
        currentAction = job
        return job
    }

    /** Same as [action] but with a determinate byte counter and a cancel button. */
    private fun transferAction(label: String, total: Long?, block: suspend (TransferHandle) -> Unit): Job {
        val job = viewModelScope.launch {
            val self = coroutineContext[Job]
            val started = System.currentTimeMillis()
            _state.update {
                it.copy(busy = true, progress = OperationProgress(label, 0, total, ProgressPhase.TRANSFERRING, started))
            }
            val handle = TransferHandle(
                onProgress = { bytes ->
                    _state.update { state ->
                        val phase = if (total != null && bytes >= total) ProgressPhase.FINISHING else ProgressPhase.TRANSFERRING
                        state.copy(progress = state.progress?.copy(transferred = bytes, phase = phase))
                    }
                },
                ensureActive = { if (self?.isActive == false) throw CancellationException("cancelled") },
            )
            try {
                block(handle)
            } catch (cancelled: CancellationException) {
                showMessage(str(R.string.progress_cancelled))
            } catch (error: Throwable) {
                showMessage(str(R.string.action_error, label, err(error)))
            } finally {
                _state.update { it.copy(busy = false, progress = null) }
            }
        }
        currentAction = job
        return job
    }

    /** Demo transfers are instant; animate them so the progress UI can be evaluated. */
    private suspend fun simulateTransfer(total: Long, handle: TransferHandle) {
        val step = (total / 24).coerceAtLeast(1)
        var sent = 0L
        while (sent < total) {
            delay(45)
            sent = minOf(total, sent + step)
            handle.advance(sent - handle.transferred)
        }
        handle.finish()
    }
    // endregion
}
