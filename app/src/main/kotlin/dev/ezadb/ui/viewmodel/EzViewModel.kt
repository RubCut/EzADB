package dev.ezadb.ui.viewmodel

import android.app.Application
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
import dev.ezadb.data.AppPreferences
import dev.ezadb.data.DemoBackend
import dev.ezadb.data.TerminalSession
import dev.ezadb.data.UsbHelper
import dev.ezadb.util.SafUtils
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
    private val app = application as EzAdbApp
    private val repo = app.deviceRepository
    private val connection = app.connection
    private val prefsRepo = app.preferences
    private val quickToggles = app.quickToggles
    private val demo: DemoBackend = app.demo

    private val _state = MutableStateFlow(EzUiState())
    val state: StateFlow<EzUiState> = _state.asStateFlow()
    val prefs: StateFlow<AppPreferences> = _state.map { it.prefs }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences())

    private var terminalSession: TerminalSession? = null
    private val history = dev.ezadb.core.TerminalHistory()
    private var demoInitialized = false

    init {
        viewModelScope.launch {
            prefsRepo.flow.collect { prefs ->
                val wasDemo = _state.value.prefs.demoMode
                _state.update { it.copy(prefs = prefs) }
                if (prefs.demoMode && !wasDemo) enableDemo()
                if (!prefs.demoMode && wasDemo) disableDemo()
            }
        }
        viewModelScope.launch {
            connection.state.collect { conn ->
                if (_state.value.prefs.demoMode) return@collect
                _state.update { it.copy(connection = conn, device = (conn as? ConnectionState.Connected)?.device ?: it.device) }
                if (conn is ConnectionState.Connected) {
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
    fun connectTcp(host: String, port: Int) = action("Подключение") {
        connection.connectTcp(host, port)
        prefsRepo.addRecent(Endpoint(host, port, "$host:$port", ConnectionKind.TCP))
        showMessage("Подключено к $host:$port")
    }

    fun pair(host: String, port: Int, code: String) = action("Сопряжение") {
        connection.pair(host, port, code)
        showMessage("Сопряжение выполнено. Подключитесь к порту из раздела «Беспроводная отладка».")
    }

    fun connectDiscovered(endpoint: DiscoveredEndpoint) {
        if (endpoint.type == dev.ezadb.core.DiscoveryType.PAIR) {
            showMessage("Для сопряжения введите код с экрана устройства")
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
            showMessage("Разрешите доступ к USB-устройству")
        }
    }

    fun onUsbPermission(device: UsbDevice, granted: Boolean) {
        if (granted) connectUsb(device) else showMessage("Доступ к USB не предоставлен")
    }

    fun onUsbDetached(device: UsbDevice) {
        connection.onUsbDetached(device)
    }

    fun connectUsb(device: UsbDevice) = action("USB-подключение") {
        connection.connectUsb(device)
        showMessage("USB-устройство подключено")
    }

    fun disconnect() = action("Отключение") {
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
        showMessage("Отключено")
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
                terminalLines = listOf(TerminalLine("EzADB demo shell. Введите help для списка команд.", TerminalKind.SYSTEM)),
                terminalRunning = true,
                terminalInteractive = false,
            )
        }
        showMessage("Демо-режим включён")
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
        showMessage("Демо-режим выключен")
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
            .onFailure { error -> showMessage("Не удалось получить сведения: ${error.message}") }
    }

    fun refreshToggles() = viewModelScope.launch {
        _state.update { it.copy(togglesLoading = true) }
        val result = runCatching {
            if (_state.value.prefs.demoMode) demo.toggles() else quickToggles.readAll()
        }
        _state.update { it.copy(toggles = result.getOrElse { emptyList() }, togglesLoading = false) }
        result.exceptionOrNull()?.let { showMessage("Переключатели: ${it.message}") }
    }

    fun setToggle(id: String, enabled: Boolean) = action("Переключатель") {
        _state.update { state -> state.copy(toggles = state.toggles.map { if (it.id == id) it.copy(busy = true) else it }) }
        runCatching {
            if (_state.value.prefs.demoMode) demo.setToggle(id, enabled) else quickToggles.set(id, enabled)
        }.onFailure { error ->
            _state.update { state -> state.copy(toggles = state.toggles.map { if (it.id == id) it.copy(busy = false, error = error.message) else it }) }
            showMessage("Не удалось переключить: ${error.message}")
            return@action
        }
        refreshToggles()
    }

    fun reboot(mode: String = "") = action("Перезагрузка") {
        if (_state.value.prefs.demoMode) demo.shell("reboot $mode") else repo.reboot(mode)
        showMessage(if (mode.isBlank()) "Устройство перезагружается" else "Перезагрузка в $mode")
        if (!_state.value.prefs.demoMode) disconnect()
    }

    fun setScreen(on: Boolean) = action("Экран") {
        if (_state.value.prefs.demoMode) demo.setToggle("screen", on) else repo.setScreenOn(on)
        refreshToggles()
    }

    fun enableWifiAdb() = action("Wi-Fi ADB") {
        val output = if (_state.value.prefs.demoMode) "Демо: ADB по Wi-Fi включён на порту 5555" else repo.enableWifiAdb()
        showMessage(output.ifBlank { "ADB по Wi-Fi включён. Подключитесь к IP устройства, порт 5555." })
    }

    fun screenshot(target: Uri) = action("Скриншот") {
        if (_state.value.prefs.demoMode) {
            getApplication<EzAdbApp>().contentResolver.openOutputStream(target)?.use { it.write(ByteArray(1024)) }
            showMessage("Демо-скриншот сохранён")
            return@action
        }
        val remote = "/data/local/tmp/ezadb-screen-${System.currentTimeMillis()}.png"
        try {
            repo.screenshot(remote)
            repo.pullToUri(remote, target)
            showMessage("Скриншот сохранён")
        } finally {
            runCatching { repo.delete(remote, recursive = false) }
        }
    }
    // endregion

    // region apps
    fun refreshApps() = viewModelScope.launch {
        _state.update { it.copy(appsLoading = true) }
        runCatching { if (_state.value.prefs.demoMode) demo.apps() else repo.listApps() }
            .onSuccess { apps -> _state.update { it.copy(apps = apps, appsLoading = false) } }
            .onFailure { error -> _state.update { it.copy(appsLoading = false) }; showMessage("Приложения: ${error.message}") }
    }

    fun setAppQuery(query: String) = _state.update { it.copy(appQuery = query) }
    fun setAppFilter(filter: AppFilter) = _state.update { it.copy(appFilter = filter) }
    fun setAppSort(sort: AppSort) = _state.update { it.copy(appSort = sort) }
    fun selectApp(app: AppInfo?) = _state.update { it.copy(selectedApp = app) }

    fun loadAppDetails(packageName: String) = action("Сведения о приложении") {
        val details = if (_state.value.prefs.demoMode) {
            demo.apps().firstOrNull { it.packageName == packageName } ?: throw IOException("Приложение не найдено")
        } else repo.appDetails(packageName)
        _state.update { state ->
            state.copy(
                selectedApp = details,
                apps = state.apps.map { if (it.packageName == packageName) details.copy(label = it.label) else it },
            )
        }
    }

    fun setAppEnabled(packageName: String, enabled: Boolean) = action("Состояние приложения") {
        if (_state.value.prefs.demoMode) demo.setAppEnabled(packageName, enabled) else repo.setAppEnabled(packageName, enabled)
        showMessage(if (enabled) "Приложение включено" else "Приложение отключено")
        refreshApps()
    }

    fun forceStop(packageName: String) = action("Остановка") {
        if (_state.value.prefs.demoMode) demo.forceStop(packageName) else repo.forceStop(packageName)
        showMessage("Приложение остановлено")
    }

    fun clearAppData(packageName: String) = action("Очистка данных") {
        if (_state.value.prefs.demoMode) demo.clearData(packageName) else repo.clearAppData(packageName)
        showMessage("Данные приложения очищены")
    }

    fun uninstallApp(packageName: String) = action("Удаление") {
        if (_state.value.prefs.demoMode) demo.uninstall(packageName) else repo.uninstallApp(packageName)
        showMessage("Приложение удалено")
        refreshApps()
    }

    fun launchApp(packageName: String) = action("Запуск") {
        if (_state.value.prefs.demoMode) showMessage("Демо: запуск $packageName") else repo.launchApp(packageName)
        showMessage("Приложение запущено")
    }

    fun openAppSettings(packageName: String) = action("Настройки приложения") {
        if (_state.value.prefs.demoMode) showMessage("Демо: настройки $packageName") else repo.openAppSettings(packageName)
    }

    fun installApk(uri: Uri) = action("Установка APK") {
        if (_state.value.prefs.demoMode) showMessage("Демо: APK установлен") else repo.installApk(uri)
        showMessage("APK установлен")
        refreshApps()
    }

    fun extractApk(packageName: String, target: Uri) = action("Извлечение APK") {
        if (_state.value.prefs.demoMode) {
            getApplication<EzAdbApp>().contentResolver.openOutputStream(target)?.use { it.write("demo apk".toByteArray()) }
        } else repo.extractApk(packageName, target)
        showMessage("APK сохранён")
    }
    // endregion

    // region files
    fun refreshFiles(path: String = _state.value.currentPath) = viewModelScope.launch {
        val normalized = PathUtils.normalize(path)
        _state.update { it.copy(filesLoading = true, currentPath = normalized) }
        runCatching { if (_state.value.prefs.demoMode) demo.listFiles(normalized) else repo.listFiles(normalized) }
            .onSuccess { files -> _state.update { it.copy(files = files, filesLoading = false) } }
            .onFailure { error -> _state.update { it.copy(filesLoading = false) }; showMessage("Файлы: ${error.message}") }
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
        if (!PathUtils.isTextFile(file.path)) {
            showMessage("Это не текстовый файл. Скачайте его для просмотра.")
            return
        }
        action("Открытие файла") {
            val bytes = if (_state.value.prefs.demoMode) demo.readFile(file.path) else repo.readFile(file.path)
            _state.update { it.copy(editorFile = file, editorText = String(bytes, Charsets.UTF_8), editorDirty = false) }
        }
    }

    fun updateEditor(text: String) = _state.update { it.copy(editorText = text, editorDirty = true) }
    fun closeEditor() = _state.update { it.copy(editorFile = null, editorText = "", editorDirty = false) }

    fun saveEditor() = action("Сохранение файла") {
        val file = _state.value.editorFile ?: return@action
        val bytes = _state.value.editorText.toByteArray(Charsets.UTF_8)
        if (_state.value.prefs.demoMode) demo.writeFile(file.path, bytes) else repo.writeFile(file.path, bytes)
        _state.update { it.copy(editorDirty = false) }
        showMessage("Файл сохранён")
        refreshFiles()
    }

    fun downloadFile(file: RemoteFile, target: Uri) = action("Скачивание") {
        if (_state.value.prefs.demoMode) {
            getApplication<EzAdbApp>().contentResolver.openOutputStream(target)?.use { it.write(demo.readFile(file.path)) }
        } else repo.pullToUri(file.path, target)
        showMessage("Файл скачан")
    }

    fun uploadFile(source: Uri) = action("Загрузка файла") {
        val name = PathUtils.sanitizeLocalName(SafUtils.displayName(getApplication(), source))
        val remote = PathUtils.join(_state.value.currentPath, name)
        if (_state.value.prefs.demoMode) {
            getApplication<EzAdbApp>().contentResolver.openInputStream(source)?.use { input -> demo.writeFile(remote, input.readBytes()) }
        } else repo.pushFromUri(source, remote)
        showMessage("Файл загружен: $remote")
        refreshFiles()
    }

    fun mkdir(name: String) = action("Создание папки") {
        val path = PathUtils.join(_state.value.currentPath, name)
        if (_state.value.prefs.demoMode) demo.mkdir(path) else repo.mkdir(path)
        refreshFiles()
    }

    fun renameFile(file: RemoteFile, newName: String) = action("Переименование") {
        val newPath = PathUtils.join(PathUtils.parent(file.path), newName)
        if (_state.value.prefs.demoMode) demo.rename(file.path, newPath) else repo.rename(file.path, newPath)
        refreshFiles()
    }

    fun deleteFile(file: RemoteFile) = action("Удаление") {
        if (_state.value.prefs.demoMode) demo.delete(file.path) else repo.delete(file.path, file.isDirectory)
        showMessage("Удалено: ${file.name}")
        refreshFiles()
    }

    fun chmodFile(file: RemoteFile, mode: String) = action("Права доступа") {
        if (_state.value.prefs.demoMode) showMessage("Демо: chmod $mode ${file.path}") else repo.chmod(file.path, mode)
        refreshFiles()
    }
    // endregion

    // region settings
    fun refreshSettings(namespace: SettingNamespace = _state.value.settingsNamespace) = viewModelScope.launch {
        _state.update { it.copy(settingsLoading = true, settingsNamespace = namespace) }
        runCatching { if (_state.value.prefs.demoMode) demo.listSettings(namespace) else repo.listSettings(namespace) }
            .onSuccess { entries -> _state.update { it.copy(settings = entries, settingsLoading = false) } }
            .onFailure { error -> _state.update { it.copy(settingsLoading = false) }; showMessage("Настройки: ${error.message}") }
    }

    fun setSettingsQuery(query: String) = _state.update { it.copy(settingsQuery = query) }

    fun putSetting(entry: SettingEntry, value: String) = action("Сохранение параметра") {
        val normalized = if (entry.type == dev.ezadb.core.SettingType.BOOL) dev.ezadb.core.SettingsCatalog.normalizeBool(value) else value
        if (!dev.ezadb.core.SettingsCatalog.validate(entry.type, normalized)) throw IOException("Некорректное значение для типа ${entry.type}")
        if (_state.value.prefs.demoMode) demo.putSetting(entry.namespace, entry.key, normalized)
        else repo.putSetting(entry.namespace, entry.key, normalized)
        showMessage("${entry.key} = $normalized")
        refreshSettings()
        refreshToggles()
    }

    fun deleteSetting(entry: SettingEntry) = action("Удаление параметра") {
        if (_state.value.prefs.demoMode) demo.deleteSetting(entry.namespace, entry.key) else repo.deleteSetting(entry.namespace, entry.key)
        refreshSettings()
    }

    fun addCustomSetting(namespace: SettingNamespace, key: String, value: String) = action("Добавление параметра") {
        if (key.isBlank()) throw IOException("Ключ не может быть пустым")
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
        appendTerminal(TerminalLine("Подключение к оболочке устройства…", TerminalKind.SYSTEM))
        val session = TerminalSession(
            kadb = kadb,
            scope = viewModelScope,
            onLine = { line -> appendTerminal(line) },
            onClosed = { reason ->
                terminalSession = null
                _state.update { it.copy(terminalRunning = false) }
                appendTerminal(TerminalLine(reason ?: "Сессия терминала завершена", TerminalKind.SYSTEM))
            },
        )
        runCatching {
            session.start()
            terminalSession = session
            _state.update { it.copy(terminalRunning = true, terminalInteractive = session.interactive) }
            if (session.interactive) session.sendLine("stty -echo")
        }.onFailure { error ->
            appendTerminal(TerminalLine("Не удалось открыть терминал: ${error.message}", TerminalKind.STDERR))
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
            if (!result.success) appendTerminal(TerminalLine("[код ${result.exitCode}]", TerminalKind.EXIT))
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
            .onFailure { error -> appendTerminal(TerminalLine("Ошибка отправки: ${error.message}", TerminalKind.STDERR)) }
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
    fun removeRecent(endpoint: Endpoint) = viewModelScope.launch { prefsRepo.removeRecent(endpoint) }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun action(label: String, block: suspend () -> Unit): Job = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        try {
            block()
        } catch (error: Throwable) {
            showMessage("$label: ${error.message ?: error.javaClass.simpleName}")
        } finally {
            _state.update { it.copy(busy = false) }
        }
    }
    // endregion
}
