# Заметки по ADB

## Подключение

### USB OTG
1. Целевое устройство: «Отладка по USB» включена.
2. Кабель должен поддерживать данные, а телефон-хост — USB Host/OTG.
3. EzADB запрашивает системное разрешение на USB-устройство.
4. На целевом устройстве подтвердите RSA-ключ ADB.
5. После переподключения кабеля устройство нужно выбрать заново.

### Wi-Fi ADB (классический порт 5555)
- Можно включить из EzADB кнопкой «Wi-Fi ADB» (`setprop service.adb.tcp.port 5555; stop adbd; start adbd`).
- На некоторых прошивках это требует root; тогда используйте компьютер: `adb tcpip 5555`.
- Подключайтесь к IP целевого устройства и порту 5555 в той же сети.

### Беспроводная отладка Android 11+
1. Целевое устройство: «Беспроводная отладка» → «Подключение с помощью кода».
2. В EzADB вкладка «Сопряжение»: IP, порт сопряжения, 6-значный код.
3. После сопряжения подключитесь к **порту подключения** (он отличается от порта сопряжения).
4. mDNS может найти оба сервиса автоматически.

## Оболочка

- При наличии `shell_v2` используется PTY-сессия (`shell,v2,TERM=xterm-256color,pty:`).
- Без `shell_v2` используется raw `shell:` поток.
- `stty -echo` отключает локальное эхо, чтобы команды не дублировались в выводе.
- Ctrl+C отправляется как байт `0x03`, Ctrl+D — `0x04`.
- Размер окна передаётся через shell v2 window-size control packet.

## Файлы

- Просмотр каталогов и stat выполняются через ADB sync (`LIST`/`LST2`, `STAT`/`STA2`).
- Передача файлов использует `SEND`/`RECV` (v1/v2 в зависимости от возможностей устройства).
- `mkdir`, `mv`, `rm`, `chmod` выполняются через shell с POSIX-экранированием аргументов.
- Текстовый редактор ограничен 1 МиБ, чтобы не загружать большие файлы в память.

## Приложения

- Список: `pm list packages -f -U --show-versioncode`, системные `-s`, отключённые `-d`.
- Включение/отключение: `pm enable` / `pm disable-user --user 0`.
- Установка: потоковая `cmd package install` / `pm install` через Kadb.
- Извлечение APK: `pm path <package>` → sync pull `base.apk`.

## Настройки

- Чтение: `settings list <namespace>`, `settings get <namespace> <key>`.
- Запись: `settings put <namespace> <key> <value>`.
- Удаление: `settings delete <namespace> <key>`.
- Пространства: `global`, `secure`, `system`.
- Часть ключей требует `WRITE_SECURE_SETTINGS`; пользователь `shell` обычно имеет это право, но OEM-прошивки могут отличаться.

## Быстрые переключатели

| Переключатель | Чтение | Запись |
|---|---|---|
| Wi-Fi | `settings get global wifi_on` | `svc wifi enable/disable` |
| Bluetooth | `settings get global bluetooth_on` | `svc bluetooth enable/disable` |
| Режим полёта | `settings get global airplane_mode_on` | `settings put` + broadcast `AIRPLANE_MODE` |
| Мобильные данные | `settings get global mobile_data` | `svc data enable/disable` |
| Геолокация | `settings get secure location_mode` | `settings put secure location_mode 3/0` |
| Автоповорот | `settings get system accelerometer_rotation` | `settings put system ...` |
| Не спать | `settings get global stay_on_while_plugged_in` | `settings put global ... 7/0` |
| Тёмная тема | `cmd uimode night` | `cmd uimode night yes/no` |
| Анимации | `settings get global window_animation_scale` | три scale-ключа `1/0` |
| Экран | `dumpsys power` | `input keyevent KEYCODE_WAKEUP/SLEEP` |

Если команда возвращает ошибку, переключатель показывает её текст и не меняет состояние UI.
