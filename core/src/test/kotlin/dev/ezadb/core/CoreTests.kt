package dev.ezadb.core

fun testShellQuote() {
    expectEquals("'hello'", ShellQuote.quote("hello"))
    expectEquals("''", ShellQuote.quote(""))
    expectEquals("'a b'", ShellQuote.quote("a b"))
    expectEquals("'it'\\''s'", ShellQuote.quote("it's"))
    expectEquals("'$(rm -rf /)'", ShellQuote.quote("$(rm -rf /)"))
    expectEquals("'pm' 'list' 'packages'", ShellQuote.command("pm", "list", "packages"))
    expectEquals("'settings' 'put' 'global' 'adb_enabled' '1'", ShellQuote.settings("put", SettingNamespace.GLOBAL, "adb_enabled", "1"))
    expectEquals("'pm' 'disable-user' '--user' '0' 'com.example'", ShellQuote.pm("disable-user", "--user", "0", "com.example"))
}

fun testGetprop() {
    val props = Parsers.getprop(
        """
        [ro.product.model]: [Pixel 8]
        [ro.build.version.release]: [15]
        [ro.build.version.sdk]: [35]
        [ro.product.manufacturer]: [Google]
        [persist.sys.locale]: [ru-RU]
        [weird]: [a]b]
        """.trimIndent()
    )
    expectEquals("Pixel 8", props["ro.product.model"])
    expectEquals("15", props["ro.build.version.release"])
    expectEquals("35", props["ro.build.version.sdk"])
    expectEquals("Google", props["ro.product.manufacturer"])
    expectEquals("a]b", props["weird"])
}

fun testPackageList() {
    val output = """
        package:/data/app/~~abc/com.example-1/base.apk=com.example uid:10123 versionCode:42
        package:/system/priv-app/Settings/Settings.apk=com.android.settings uid:1000 versionCode:35
        package:/data/app/com.disabled-2/base.apk=com.disabled uid:10999
    """.trimIndent()
    val apps = Parsers.packageList(output, systemPackages = setOf("com.android.settings"), disabledPackages = setOf("com.disabled"))
    expectEquals(3, apps.size)
    val example = apps.first { it.packageName == "com.example" }
    expectEquals("/data/app/~~abc/com.example-1/base.apk", example.apkPath)
    expectEquals(10123, example.uid)
    expectEquals(42L, example.versionCode)
    expect(!example.system)
    expect(example.enabled)
    expect(apps.first { it.packageName == "com.android.settings" }.system)
    expect(!apps.first { it.packageName == "com.disabled" }.enabled)
    expectEquals(setOf("com.example", "com.disabled"), Parsers.packageNames("package:com.example\npackage:com.disabled\n"))
}

fun testSettingsParsers() {
    val list = Parsers.settingsList("adb_enabled=1\nwifi_on=0\nnull\nbroken\nlocation_mode=3")
    expectEquals(3, list.size)
    expectEquals("adb_enabled" to "1", list[0])
    expectEquals("location_mode" to "3", list[2])
    expectEquals(null, Parsers.settingValue("null"))
    expectEquals("1", Parsers.settingValue(" 1 \n"))
}

fun testBatteryStorageUptime() {
    val battery = Parsers.battery(
        """
        Current Battery Service state:
          AC powered: false
          USB powered: true
          level: 87
          status: 2
          health: 2
          plugged: 2
          temperature: 310
        """.trimIndent()
    )
    expectEquals(87, battery.level)
    expectEquals("Заряжается", battery.status)
    expectEquals(31.0, battery.temperatureC)
    expectEquals("USB", battery.plugged)

    val storage = Parsers.storage(
        """
        Filesystem     1K-blocks    Used Available Use% Mounted on
        /dev/block/dm-0 112345678 45000000 67345678  41% /data
        """.trimIndent()
    )
    expectEquals(112345678L * 1024, storage.totalBytes)
    expectEquals(67345678L * 1024, storage.freeBytes)
    expect(storage.usedFraction != null && storage.usedFraction!! in 0f..1f)

    expectEquals(123456L, Parsers.uptime("123456.78 98765.43"))
    expectEquals(1080 to 2400, Parsers.wmSize("Physical size: 1080x2400"))
    expectEquals(420, Parsers.wmDensity("Physical density: 420"))
    expectEquals(listOf("192.168.1.42"), Parsers.ipAddresses("default via 192.168.1.1 dev wlan0 src 192.168.1.42"))
}

fun testPathUtils() {
    expectEquals("/", PathUtils.normalize(""))
    expectEquals("/sdcard", PathUtils.normalize("/sdcard/"))
    expectEquals("/sdcard/Download", PathUtils.join("/sdcard", "Download"))
    expectEquals("/sdcard", PathUtils.join("/sdcard/Download", ".."))
    expectEquals("/etc/hosts", PathUtils.join("/sdcard", "/etc/hosts"))
    expectEquals("/sdcard", PathUtils.parent("/sdcard/file.txt"))
    expectEquals("/", PathUtils.parent("/sdcard"))
    expectEquals("file.txt", PathUtils.basename("/sdcard/file.txt"))
    expectEquals("txt", PathUtils.extension("/sdcard/file.txt"))
    expect(PathUtils.isTextFile("/sdcard/build.prop"))
    expect(PathUtils.isTextFile("/sdcard/.bashrc"))
    expect(!PathUtils.isTextFile("/sdcard/base.apk"))
    expectEquals("a_b_c", PathUtils.sanitizeLocalName("a/b\\c"))
    expect(PathUtils.validateRemote("/sdcard/file"))
    expect(!PathUtils.validateRemote("sdcard/file"))
    expect(!PathUtils.validateRemote("/sdcard/\nfile"))
}

fun testModePermissions() {
    expectEquals("drwxr-xr-x", Parsers.modeToPermissions(0x4000 or 0b111101101))
    expectEquals("-rw-r--r--", Parsers.modeToPermissions(0x8000 or 0b110100100))
    expectEquals("lrwxrwxrwx", Parsers.modeToPermissions(0xA000 or 0b111111111))
}

fun testSettingsCatalog() {
    val def = SettingsCatalog.find(SettingNamespace.GLOBAL, "adb_enabled")
    expect(def != null)
    expectEquals(SettingType.BOOL, def!!.type)
    expectEquals(Risk.HIGH, def.risk)
    expect(SettingsCatalog.validate(SettingType.INT, "42"))
    expect(!SettingsCatalog.validate(SettingType.INT, "4.2"))
    expect(SettingsCatalog.validate(SettingType.FLOAT, "0.5"))
    expect(SettingsCatalog.validate(SettingType.BOOL, "true"))
    expectEquals("1", SettingsCatalog.normalizeBool("true"))
    expectEquals("0", SettingsCatalog.normalizeBool("false"))
    expect(SettingsCatalog.forNamespace(SettingNamespace.SYSTEM).isNotEmpty())
}

fun testRiskAssessor() {
    expectEquals(Risk.HIGH, RiskAssessor.assess("rm -rf /").level)
    expectEquals(Risk.HIGH, RiskAssessor.assess("rm -rf /system").level)
    expectEquals(Risk.HIGH, RiskAssessor.assess("pm uninstall com.example").level)
    expectEquals(Risk.HIGH, RiskAssessor.assess("reboot bootloader").level)
    expectEquals(Risk.HIGH, RiskAssessor.assess("dd if=/tmp/x of=/dev/block/sda").level)
    expectEquals(Risk.MEDIUM, RiskAssessor.assess("reboot").level)
    expectEquals(Risk.MEDIUM, RiskAssessor.assess("settings put global wifi_on 0").level)
    expectEquals(Risk.LOW, RiskAssessor.assess("getprop ro.product.model").level)
    expectEquals(Risk.LOW, RiskAssessor.assess("ls -la /sdcard").level)
}

fun testTerminalHistory() {
    val history = TerminalHistory(maxSize = 3)
    history.add("ls")
    history.add("pwd")
    history.add("ls")
    expectEquals(listOf("pwd", "ls"), history.entries)
    history.begin("draft")
    expectEquals("ls", history.previous())
    expectEquals("pwd", history.previous())
    expectEquals("pwd", history.previous())
    expectEquals("ls", history.next())
    expectEquals("draft", history.next())
    history.add("a"); history.add("b"); history.add("c"); history.add("d")
    expectEquals(listOf("b", "c", "d"), history.entries)
}

fun testCollections() {
    val apps = listOf(
        AppInfo("com.zeta", label = "Zeta", system = false, enabled = true, uid = 100),
        AppInfo("com.alpha", label = "Alpha", system = true, enabled = false, uid = 200),
        AppInfo("com.beta", label = "Beta", system = false, enabled = true, uid = 50),
    )
    expectEquals(listOf("com.beta", "com.zeta"), Collections.filterApps(apps, "", AppFilter.USER, AppSort.NAME).map { it.packageName })
    expectEquals(listOf("com.alpha"), Collections.filterApps(apps, "", AppFilter.DISABLED, AppSort.PACKAGE).map { it.packageName })
    expectEquals(listOf("com.alpha"), Collections.filterApps(apps, "alph", AppFilter.ALL, AppSort.NAME).map { it.packageName })

    val files = listOf(
        RemoteFile("b.txt", "/b.txt", false, sizeBytes = 10, mtimeSec = 2),
        RemoteFile("dir", "/dir", true, sizeBytes = 0, mtimeSec = 1),
        RemoteFile("a.txt", "/a.txt", false, sizeBytes = 20, mtimeSec = 3),
    )
    expectEquals(listOf("dir", "a.txt", "b.txt"), Collections.filterFiles(files, "", FileSort.NAME).map { it.name })
    expectEquals(listOf("dir", "a.txt", "b.txt"), Collections.filterFiles(files, "", FileSort.SIZE).map { it.name })
    expectEquals(listOf("a.txt"), Collections.filterFiles(files, ".txt", FileSort.NAME).map { it.name }.filter { it == "a.txt" })

    val settings = listOf(
        SettingEntry(SettingNamespace.GLOBAL, "wifi_on", "1", description = "Wi-Fi"),
        SettingEntry(SettingNamespace.GLOBAL, "adb_enabled", "1", description = "Отладка"),
    )
    expectEquals(1, Collections.filterSettings(settings, "отлад").size)
    expectEquals(2, Collections.filterSettings(settings, "").size)
}

fun testFormatters() {
    expectEquals("—", Formatters.bytes(null))
    expectEquals("512 Б", Formatters.bytes(512))
    expectContains(Formatters.bytes(1536), "КиБ")
    expectContains(Formatters.bytes(5 * 1024 * 1024), "МиБ")
    expectEquals("2д 3ч", Formatters.duration(2 * 86400 + 3 * 3600))
    expectEquals("5м", Formatters.duration(300))
    expectEquals("87%", Formatters.battery(87))
    expectEquals("—", Formatters.battery(null))
}

private val allTests: List<Pair<String, () -> Unit>> = listOf(
    "ShellQuote" to ::testShellQuote,
    "Parsers.getprop" to ::testGetprop,
    "Parsers.packageList" to ::testPackageList,
    "Parsers.settings" to ::testSettingsParsers,
    "Parsers.battery/storage/uptime" to ::testBatteryStorageUptime,
    "PathUtils" to ::testPathUtils,
    "Parsers.modeToPermissions" to ::testModePermissions,
    "SettingsCatalog" to ::testSettingsCatalog,
    "RiskAssessor" to ::testRiskAssessor,
    "TerminalHistory" to ::testTerminalHistory,
    "Collections" to ::testCollections,
    "Formatters" to ::testFormatters,
)

fun runAllTests(): Int {
    var failures = 0
    allTests.forEach { (name, test) ->
        try {
            test()
            println("PASS $name")
        } catch (error: Throwable) {
            failures++
            println("FAIL $name: ${error.message}")
        }
    }
    println("${allTests.size - failures}/${allTests.size} tests passed")
    return failures
}

fun main() {
    val failures = runAllTests()
    if (failures > 0) kotlin.system.exitProcess(1)
}
