package dev.ezadb.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.ezadb.R
import dev.ezadb.core.SettingEntry
import dev.ezadb.core.SettingNamespace

@Composable
fun localizedSettingDescription(entry: SettingEntry): String {
    val res = settingDescriptionRes(entry.namespace, entry.key)
    if (res != null) return stringResource(res)
    if (entry.description == "Пользовательский параметр" || entry.description == "Custom setting") {
        return stringResource(R.string.custom_setting)
    }
    return entry.description
}

fun settingDescriptionRes(namespace: SettingNamespace, key: String): Int? = when (namespace) {
    SettingNamespace.GLOBAL -> when (key) {
        "airplane_mode_on" -> R.string.setting_desc_global_airplane_mode_on
        "wifi_on" -> R.string.setting_desc_global_wifi_on
        "bluetooth_on" -> R.string.setting_desc_global_bluetooth_on
        "mobile_data" -> R.string.setting_desc_global_mobile_data
        "adb_enabled" -> R.string.setting_desc_global_adb_enabled
        "development_settings_enabled" -> R.string.setting_desc_global_development_settings_enabled
        "stay_on_while_plugged_in" -> R.string.setting_desc_global_stay_on_while_plugged_in
        "window_animation_scale" -> R.string.setting_desc_global_window_animation_scale
        "transition_animation_scale" -> R.string.setting_desc_global_transition_animation_scale
        "animator_duration_scale" -> R.string.setting_desc_global_animator_duration_scale
        "auto_time" -> R.string.setting_desc_global_auto_time
        "auto_time_zone" -> R.string.setting_desc_global_auto_time_zone
        "low_power" -> R.string.setting_desc_global_low_power
        "hidden_api_policy" -> R.string.setting_desc_global_hidden_api_policy
        "heads_up_notifications_enabled" -> R.string.setting_desc_global_heads_up_notifications_enabled
        "zen_mode" -> R.string.setting_desc_global_zen_mode
        else -> null
    }
    SettingNamespace.SECURE -> when (key) {
        "location_mode" -> R.string.setting_desc_secure_location_mode
        "location_providers_allowed" -> R.string.setting_desc_secure_location_providers_allowed
        "backup_enabled" -> R.string.setting_desc_secure_backup_enabled
        "screensaver_enabled" -> R.string.setting_desc_secure_screensaver_enabled
        "immersive_mode_confirmations" -> R.string.setting_desc_secure_immersive_mode_confirmations
        "enabled_notification_assistant" -> R.string.setting_desc_secure_enabled_notification_assistant
        "lockscreen.password_type" -> R.string.setting_desc_secure_lockscreen_password_type
        "usb_migration_expected" -> R.string.setting_desc_secure_usb_migration_expected
        else -> null
    }
    SettingNamespace.SYSTEM -> when (key) {
        "accelerometer_rotation" -> R.string.setting_desc_system_accelerometer_rotation
        "screen_brightness" -> R.string.setting_desc_system_screen_brightness
        "screen_brightness_mode" -> R.string.setting_desc_system_screen_brightness_mode
        "screen_off_timeout" -> R.string.setting_desc_system_screen_off_timeout
        "haptic_feedback_enabled" -> R.string.setting_desc_system_haptic_feedback_enabled
        "sound_effects_enabled" -> R.string.setting_desc_system_sound_effects_enabled
        "vibrate_when_ringing" -> R.string.setting_desc_system_vibrate_when_ringing
        "font_scale" -> R.string.setting_desc_system_font_scale
        "show_touches" -> R.string.setting_desc_system_show_touches
        "pointer_location" -> R.string.setting_desc_system_pointer_location
        "pointer_speed" -> R.string.setting_desc_system_pointer_speed
        else -> null
    }
}
