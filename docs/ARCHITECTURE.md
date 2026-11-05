# Архитектура EzADB

## Модули

### `:core`
Чистый Kotlin/JVM без Android-зависимостей. Содержит:
- модели (`DeviceInfo`, `AppInfo`, `RemoteFile`, `SettingEntry`, `QuickToggleState`, `TerminalLine`);
- POSIX shell-quoting (`ShellQuote`), совместимый с AOSP `escape_arg`;
- парсеры вывода ADB (`getprop`, `pm list packages`, `settings`, `dumpsys battery`, `df`, `wm`, `ip`);
- утилиты путей, форматтеры, каталог настроек, оценку риска команд, историю терминала;
- unit-тесты, запускаемые на JVM.

### `:kadb`
Вендоренный [Kadb 2.1.5](https://github.com/flyfishxu/Kadb) (Apache 2.0). Реализует ADB-протокол поверх TCP/TLS и USB Host API:
- аутентификация RSA и TLS-сопряжение;
- shell v2 / PTY / legacy shell;
- sync (list/stat/push/pull);
- установка/удаление пакетов;
- USB bulk transport с отменой чтения при отключении.

Исходники `commonMain` и `androidMain` объединены в единый Android source set `src/main/kotlin`.
Kotlin Multiplatform `expect`/`actual` объявления сведены к обычным Android-объявлениям; протокольная логика не менялась.
Gradle-модуль использует `com.android.library` + `kotlin("android")`.

### `:app`
Android-приложение (minSdk 26, targetSdk 36).

- `data/`
  - `ConnectionManager` — жизненный цикл Kadb, USB/TCP, watchdog первичной авторизации.
  - `DeviceRepository` — shell/sync-операции: устройство, приложения, файлы, настройки.
  - `QuickToggleRepository` — чтение/запись быстрых переключателей.
  - `TerminalSession` — PTY/raw поток терминала.
  - `UsbHelper`, `WirelessDiscovery` — USB permission и mDNS.
  - `PreferencesRepository` — DataStore настроек приложения.
  - `DemoBackend` — изолированное in-memory устройство для демо-режима.
- `ui/`
  - `EzApp` — адаптивная навигация (NavigationBar / NavigationRail), top bar, snackbar.
  - `screens/` — Dashboard, Terminal, Apps, Files, Settings.
  - `components/` — MD3-карточки, строки списков, диалоги подтверждения.
  - `theme/` — Material 3, Material You, акценты, формы, типографика.
  - `viewmodel/EzViewModel` — единое состояние UI и действия.

## Поток данных

```
Compose UI → EzViewModel → DeviceRepository / QuickToggleRepository → ConnectionManager → Kadb → adbd
                         ↘ DemoBackend (если включён демо-режим)
```

Состояние UI — `StateFlow<EzUiState>`. Действия выполняются в `viewModelScope`, ошибки показываются через snackbar.
Подключение и операции сериализуются на уровне смены транспорта; Kadb мультиплексирует независимые потоки внутри одного соединения.

## Тестирование

- `:core:test` — JVM-тесты парсеров, quoting, путей, каталога настроек, рисков, истории.
- CI собирает debug APK и загружает артефакт.
- Реальные ADB-операции требуют физического устройства и вручную проверяются по сценариям из README.
