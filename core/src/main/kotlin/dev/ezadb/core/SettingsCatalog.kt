package dev.ezadb.core

object SettingsCatalog {
    val definitions: List<SettingDefinition> = listOf(
        // GLOBAL
        SettingDefinition(SettingNamespace.GLOBAL, "airplane_mode_on", SettingType.BOOL, "Режим полёта", Risk.HIGH, listOf("0", "1")),
        SettingDefinition(SettingNamespace.GLOBAL, "wifi_on", SettingType.BOOL, "Wi-Fi", Risk.MEDIUM, listOf("0", "1")),
        SettingDefinition(SettingNamespace.GLOBAL, "bluetooth_on", SettingType.BOOL, "Bluetooth", Risk.MEDIUM, listOf("0", "1")),
        SettingDefinition(SettingNamespace.GLOBAL, "mobile_data", SettingType.BOOL, "Мобильные данные", Risk.MEDIUM, listOf("0", "1")),
        SettingDefinition(SettingNamespace.GLOBAL, "adb_enabled", SettingType.BOOL, "Отладка по USB", Risk.HIGH, listOf("0", "1")),
        SettingDefinition(SettingNamespace.GLOBAL, "development_settings_enabled", SettingType.BOOL, "Меню разработчика", Risk.MEDIUM, listOf("0", "1")),
        SettingDefinition(SettingNamespace.GLOBAL, "stay_on_while_plugged_in", SettingType.INT, "Не спать при зарядке (битовая маска: 1=AC, 2=USB, 4=Wireless)", Risk.MEDIUM, listOf("0", "1", "2", "3", "4", "7")),
        SettingDefinition(SettingNamespace.GLOBAL, "window_animation_scale", SettingType.FLOAT, "Масштаб анимации окон", Risk.LOW, listOf("0", "0.5", "1", "1.5", "2")),
        SettingDefinition(SettingNamespace.GLOBAL, "transition_animation_scale", SettingType.FLOAT, "Масштаб анимации переходов", Risk.LOW, listOf("0", "0.5", "1", "1.5", "2")),
        SettingDefinition(SettingNamespace.GLOBAL, "animator_duration_scale", SettingType.FLOAT, "Длительность аниматоров", Risk.LOW, listOf("0", "0.5", "1", "1.5", "2")),
        SettingDefinition(SettingNamespace.GLOBAL, "auto_time", SettingType.BOOL, "Автоматическое время", Risk.LOW, listOf("0", "1")),
        SettingDefinition(SettingNamespace.GLOBAL, "auto_time_zone", SettingType.BOOL, "Автоматический часовой пояс", Risk.LOW, listOf("0", "1")),
        SettingDefinition(SettingNamespace.GLOBAL, "low_power", SettingType.BOOL, "Режим энергосбережения", Risk.MEDIUM, listOf("0", "1")),
        SettingDefinition(SettingNamespace.GLOBAL, "hidden_api_policy", SettingType.INT, "Политика скрытых API", Risk.HIGH, listOf("0", "1", "2", "3")),
        SettingDefinition(SettingNamespace.GLOBAL, "heads_up_notifications_enabled", SettingType.BOOL, "Всплывающие уведомления", Risk.LOW, listOf("0", "1")),
        SettingDefinition(SettingNamespace.GLOBAL, "zen_mode", SettingType.INT, "Режим «Не беспокоить»", Risk.LOW, listOf("0", "1", "2", "3")),
        // SECURE
        SettingDefinition(SettingNamespace.SECURE, "location_mode", SettingType.INT, "Режим геолокации (0=выкл, 3=высокая точность)", Risk.MEDIUM, listOf("0", "1", "2", "3")),
        SettingDefinition(SettingNamespace.SECURE, "location_providers_allowed", SettingType.STRING, "Разрешённые провайдеры геолокации", Risk.MEDIUM, listOf("gps", "network", "gps,network", "")),
        SettingDefinition(SettingNamespace.SECURE, "backup_enabled", SettingType.BOOL, "Резервное копирование", Risk.LOW, listOf("0", "1")),
        SettingDefinition(SettingNamespace.SECURE, "screensaver_enabled", SettingType.BOOL, "Заставка", Risk.LOW, listOf("0", "1")),
        SettingDefinition(SettingNamespace.SECURE, "immersive_mode_confirmations", SettingType.STRING, "Подтверждения полноэкранного режима", Risk.LOW, listOf("confirmed", "")),
        SettingDefinition(SettingNamespace.SECURE, "enabled_notification_assistant", SettingType.STRING, "Компонент помощника уведомлений", Risk.MEDIUM),
        SettingDefinition(SettingNamespace.SECURE, "lockscreen.password_type", SettingType.INT, "Тип блокировки экрана", Risk.HIGH),
        SettingDefinition(SettingNamespace.SECURE, "usb_migration_expected", SettingType.BOOL, "Ожидание переноса по USB", Risk.LOW, listOf("0", "1")),
        // SYSTEM
        SettingDefinition(SettingNamespace.SYSTEM, "accelerometer_rotation", SettingType.BOOL, "Автоповорот экрана", Risk.LOW, listOf("0", "1")),
        SettingDefinition(SettingNamespace.SYSTEM, "screen_brightness", SettingType.INT, "Яркость экрана (0–255)", Risk.LOW, listOf("0", "64", "128", "192", "255")),
        SettingDefinition(SettingNamespace.SYSTEM, "screen_brightness_mode", SettingType.BOOL, "Автоматическая яркость", Risk.LOW, listOf("0", "1")),
        SettingDefinition(SettingNamespace.SYSTEM, "screen_off_timeout", SettingType.INT, "Тайм-аут экрана, мс", Risk.LOW, listOf("15000", "30000", "60000", "120000", "300000", "600000", "1800000")),
        SettingDefinition(SettingNamespace.SYSTEM, "haptic_feedback_enabled", SettingType.BOOL, "Виброотклик", Risk.LOW, listOf("0", "1")),
        SettingDefinition(SettingNamespace.SYSTEM, "sound_effects_enabled", SettingType.BOOL, "Звуки нажатий", Risk.LOW, listOf("0", "1")),
        SettingDefinition(SettingNamespace.SYSTEM, "vibrate_when_ringing", SettingType.BOOL, "Вибрация при звонке", Risk.LOW, listOf("0", "1")),
        SettingDefinition(SettingNamespace.SYSTEM, "font_scale", SettingType.FLOAT, "Масштаб шрифта", Risk.LOW, listOf("0.85", "1", "1.15", "1.3", "1.5")),
        SettingDefinition(SettingNamespace.SYSTEM, "show_touches", SettingType.BOOL, "Показывать касания", Risk.LOW, listOf("0", "1")),
        SettingDefinition(SettingNamespace.SYSTEM, "pointer_location", SettingType.BOOL, "Координаты касаний", Risk.LOW, listOf("0", "1")),
        SettingDefinition(SettingNamespace.SYSTEM, "pointer_speed", SettingType.FLOAT, "Скорость указателя", Risk.LOW, listOf("-7", "0", "7")),
    )

    fun forNamespace(namespace: SettingNamespace): List<SettingDefinition> = definitions.filter { it.namespace == namespace }

    fun find(namespace: SettingNamespace, key: String): SettingDefinition? =
        definitions.firstOrNull { it.namespace == namespace && it.key == key }

    fun validate(type: SettingType, value: String): Boolean = when (type) {
        SettingType.INT -> value.toLongOrNull() != null
        SettingType.FLOAT -> value.toFloatOrNull() != null
        SettingType.BOOL -> value == "0" || value == "1" || value.equals("true", true) || value.equals("false", true)
        SettingType.STRING -> !value.contains('\u0000') && !value.contains('\n')
    }

    fun normalizeBool(value: String): String = when {
        value == "1" || value.equals("true", true) -> "1"
        value == "0" || value.equals("false", true) -> "0"
        else -> value
    }
}
