package dev.ezadb.data

import android.content.Context
import dev.ezadb.R
import dev.ezadb.core.QuickToggleState
import dev.ezadb.core.SettingNamespace
import dev.ezadb.core.ShellQuote

class QuickToggleRepository(
    private val context: Context,
    private val repo: DeviceRepository,
) {
    private data class Spec(
        val id: String,
        val titleRes: Int,
        val subtitleRes: Int,
        val requiresRoot: Boolean = false,
        val read: suspend () -> Boolean?,
        val write: suspend (Boolean) -> String,
    )

    private val specs: List<Spec> = listOf(
        Spec("wifi", R.string.toggle_wifi, R.string.toggle_wifi_desc, read = { boolSetting(SettingNamespace.GLOBAL, "wifi_on") }, write = { repo.execChecked("svc", "wifi", if (it) "enable" else "disable") }),
        Spec("bluetooth", R.string.toggle_bluetooth, R.string.toggle_bluetooth_desc, read = { boolSetting(SettingNamespace.GLOBAL, "bluetooth_on") }, write = { repo.execChecked("svc", "bluetooth", if (it) "enable" else "disable") }),
        Spec("airplane", R.string.toggle_airplane, R.string.toggle_airplane_desc, read = { boolSetting(SettingNamespace.GLOBAL, "airplane_mode_on") }, write = { on ->
            repo.shell(
                "settings put global airplane_mode_on ${if (on) 1 else 0}; " +
                    "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state $on"
            ).allOutput
        }),
        Spec("mobile_data", R.string.toggle_mobile_data, R.string.toggle_mobile_data_desc, read = { boolSetting(SettingNamespace.GLOBAL, "mobile_data") }, write = { repo.execChecked("svc", "data", if (it) "enable" else "disable") }),
        Spec("location", R.string.toggle_location, R.string.toggle_location_desc, read = {
            repo.getSetting(SettingNamespace.SECURE, "location_mode")?.toIntOrNull()?.let { it != 0 }
        }, write = { repo.putSetting(SettingNamespace.SECURE, "location_mode", if (it) "3" else "0") }),
        Spec("auto_rotate", R.string.toggle_auto_rotate, R.string.toggle_auto_rotate_desc, read = { boolSetting(SettingNamespace.SYSTEM, "accelerometer_rotation") }, write = { repo.putSetting(SettingNamespace.SYSTEM, "accelerometer_rotation", if (it) "1" else "0") }),
        Spec("stay_awake", R.string.toggle_stay_awake, R.string.toggle_stay_awake_desc, read = {
            repo.getSetting(SettingNamespace.GLOBAL, "stay_on_while_plugged_in")?.toIntOrNull()?.let { it != 0 }
        }, write = { repo.putSetting(SettingNamespace.GLOBAL, "stay_on_while_plugged_in", if (it) "7" else "0") }),
        Spec("dark_mode", R.string.toggle_dark_mode, R.string.toggle_dark_mode_desc, read = { repo.nightMode() }, write = { repo.setNightMode(it) }),
        Spec("animations", R.string.toggle_animations, R.string.toggle_animations_desc, read = {
            repo.getSetting(SettingNamespace.GLOBAL, "window_animation_scale")?.toFloatOrNull()?.let { it > 0f }
        }, write = { repo.setAnimationScales(if (it) "1" else "0") }),
        Spec("screen", R.string.toggle_screen, R.string.toggle_screen_desc, read = {
            val output = repo.shell("dumpsys power").allOutput
            when {
                output.contains("mWakefulness=Awake", true) -> true
                output.contains("Display Power: state=ON", true) -> true
                output.contains("mWakefulness=Asleep", true) -> false
                output.contains("Display Power: state=OFF", true) -> false
                else -> null
            }
        }, write = { repo.setScreenOn(it) }),
        Spec("show_touches", R.string.toggle_show_touches, R.string.toggle_show_touches_desc, read = { boolSetting(SettingNamespace.SYSTEM, "show_touches") }, write = { repo.putSetting(SettingNamespace.SYSTEM, "show_touches", if (it) "1" else "0") }),
        Spec("pointer_location", R.string.toggle_pointer_location, R.string.toggle_pointer_location_desc, read = { boolSetting(SettingNamespace.SYSTEM, "pointer_location") }, write = { repo.putSetting(SettingNamespace.SYSTEM, "pointer_location", if (it) "1" else "0") }),
        Spec("haptic", R.string.toggle_haptic, R.string.toggle_haptic_desc, read = { boolSetting(SettingNamespace.SYSTEM, "haptic_feedback_enabled") }, write = { repo.putSetting(SettingNamespace.SYSTEM, "haptic_feedback_enabled", if (it) "1" else "0") }),
        Spec("auto_brightness", R.string.toggle_auto_brightness, R.string.toggle_auto_brightness_desc, read = { boolSetting(SettingNamespace.SYSTEM, "screen_brightness_mode") }, write = { repo.putSetting(SettingNamespace.SYSTEM, "screen_brightness_mode", if (it) "1" else "0") }),
    )

    suspend fun readAll(): List<QuickToggleState> = specs.map { spec ->
        val value = runCatching { spec.read() }
        QuickToggleState(
            id = spec.id,
            title = context.getString(spec.titleRes),
            subtitle = context.getString(spec.subtitleRes),
            enabled = value.getOrNull(),
            error = value.exceptionOrNull()?.message,
            requiresRoot = spec.requiresRoot,
        )
    }

    suspend fun set(id: String, enabled: Boolean): String {
        val spec = specs.firstOrNull { it.id == id } ?: error(context.getString(R.string.unknown_toggle, id))
        return spec.write(enabled)
    }

    fun title(id: String): String = specs.firstOrNull { it.id == id }?.let { context.getString(it.titleRes) } ?: id

    private suspend fun boolSetting(namespace: SettingNamespace, key: String): Boolean? =
        repo.getSetting(namespace, key)?.let { value ->
            when (value.trim().lowercase()) {
                "1", "true" -> true
                "0", "false" -> false
                else -> null
            }
        }
}
