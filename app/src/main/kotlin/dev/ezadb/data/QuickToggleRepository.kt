package dev.ezadb.data

import dev.ezadb.core.QuickToggleState
import dev.ezadb.core.SettingNamespace
import dev.ezadb.core.ShellQuote

class QuickToggleRepository(private val repo: DeviceRepository) {
    private data class Spec(
        val id: String,
        val title: String,
        val subtitle: String,
        val requiresRoot: Boolean = false,
        val read: suspend () -> Boolean?,
        val write: suspend (Boolean) -> String,
    )

    private val specs: List<Spec> = listOf(
        Spec("wifi", "Wi-Fi", "Беспроводная сеть", read = { boolSetting(SettingNamespace.GLOBAL, "wifi_on") }, write = { repo.execChecked("svc", "wifi", if (it) "enable" else "disable") }),
        Spec("bluetooth", "Bluetooth", "Bluetooth-адаптер", read = { boolSetting(SettingNamespace.GLOBAL, "bluetooth_on") }, write = { repo.execChecked("svc", "bluetooth", if (it) "enable" else "disable") }),
        Spec("airplane", "Режим полёта", "Отключает радиомодули", read = { boolSetting(SettingNamespace.GLOBAL, "airplane_mode_on") }, write = { on ->
            repo.shell(
                "settings put global airplane_mode_on ${if (on) 1 else 0}; " +
                    "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state $on"
            ).allOutput
        }),
        Spec("mobile_data", "Мобильные данные", "Передача данных SIM", read = { boolSetting(SettingNamespace.GLOBAL, "mobile_data") }, write = { repo.execChecked("svc", "data", if (it) "enable" else "disable") }),
        Spec("location", "Геолокация", "Службы определения местоположения", read = {
            repo.getSetting(SettingNamespace.SECURE, "location_mode")?.toIntOrNull()?.let { it != 0 }
        }, write = { repo.putSetting(SettingNamespace.SECURE, "location_mode", if (it) "3" else "0") }),
        Spec("auto_rotate", "Автоповорот", "Поворот экрана по датчику", read = { boolSetting(SettingNamespace.SYSTEM, "accelerometer_rotation") }, write = { repo.putSetting(SettingNamespace.SYSTEM, "accelerometer_rotation", if (it) "1" else "0") }),
        Spec("stay_awake", "Не спать", "Экран включён при зарядке", read = {
            repo.getSetting(SettingNamespace.GLOBAL, "stay_on_while_plugged_in")?.toIntOrNull()?.let { it != 0 }
        }, write = { repo.putSetting(SettingNamespace.GLOBAL, "stay_on_while_plugged_in", if (it) "7" else "0") }),
        Spec("dark_mode", "Тёмная тема", "Системный ночной режим", read = { repo.nightMode() }, write = { repo.setNightMode(it) }),
        Spec("animations", "Анимации", "Масштаб системных анимаций", read = {
            repo.getSetting(SettingNamespace.GLOBAL, "window_animation_scale")?.toFloatOrNull()?.let { it > 0f }
        }, write = { repo.setAnimationScales(if (it) "1" else "0") }),
        Spec("screen", "Экран", "Включить / выключить дисплей", read = {
            val output = repo.shell("dumpsys power").allOutput
            when {
                output.contains("mWakefulness=Awake", true) -> true
                output.contains("Display Power: state=ON", true) -> true
                output.contains("mWakefulness=Asleep", true) -> false
                output.contains("Display Power: state=OFF", true) -> false
                else -> null
            }
        }, write = { repo.setScreenOn(it) }),
        Spec("show_touches", "Касания", "Показывать точки касания", read = { boolSetting(SettingNamespace.SYSTEM, "show_touches") }, write = { repo.putSetting(SettingNamespace.SYSTEM, "show_touches", if (it) "1" else "0") }),
        Spec("pointer_location", "Координаты", "Оверлей с координатами касаний", read = { boolSetting(SettingNamespace.SYSTEM, "pointer_location") }, write = { repo.putSetting(SettingNamespace.SYSTEM, "pointer_location", if (it) "1" else "0") }),
        Spec("haptic", "Виброотклик", "Тактильная отдача", read = { boolSetting(SettingNamespace.SYSTEM, "haptic_feedback_enabled") }, write = { repo.putSetting(SettingNamespace.SYSTEM, "haptic_feedback_enabled", if (it) "1" else "0") }),
        Spec("auto_brightness", "Автояркость", "Адаптивная яркость экрана", read = { boolSetting(SettingNamespace.SYSTEM, "screen_brightness_mode") }, write = { repo.putSetting(SettingNamespace.SYSTEM, "screen_brightness_mode", if (it) "1" else "0") }),
    )

    suspend fun readAll(): List<QuickToggleState> = specs.map { spec ->
        val value = runCatching { spec.read() }
        QuickToggleState(
            id = spec.id,
            title = spec.title,
            subtitle = spec.subtitle,
            enabled = value.getOrNull(),
            error = value.exceptionOrNull()?.message,
            requiresRoot = spec.requiresRoot,
        )
    }

    suspend fun set(id: String, enabled: Boolean): String {
        val spec = specs.firstOrNull { it.id == id } ?: error("Неизвестный переключатель: $id")
        return spec.write(enabled)
    }

    fun title(id: String): String = specs.firstOrNull { it.id == id }?.title ?: id

    private suspend fun boolSetting(namespace: SettingNamespace, key: String): Boolean? =
        repo.getSetting(namespace, key)?.let { value ->
            when (value.trim().lowercase()) {
                "1", "true" -> true
                "0", "false" -> false
                else -> null
            }
        }
}
