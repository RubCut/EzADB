# Сторонние компоненты

| Компонент | Назначение | Лицензия |
|---|---|---|
| [Kadb](https://github.com/flyfishxu/Kadb) 2.1.5 | ADB-протокол, USB/TCP, shell, sync, pairing | Apache 2.0 |
| [Kotlin](https://kotlinlang.org/) | Язык и stdlib | Apache 2.0 |
| [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) | Асинхронность | Apache 2.0 |
| [Okio](https://github.com/square/okio) | I/O для ADB sync | Apache 2.0 |
| [Bouncy Castle](https://www.bouncycastle.org/) | Криптография ADB/TLS | MIT-style |
| [spake2-java](https://github.com/Flyfish233/spake2-java) | SPAKE2 для сопряжения | MIT |
| [HiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass) | Доступ к Conscrypt для TLS exporter | Apache 2.0 |
| [AndroidX / Jetpack Compose](https://developer.android.com/jetpack/androidx) | UI, lifecycle, navigation, datastore, documentfile | Apache 2.0 |

Исходный код Kadb вендорится в `kadb/` без изменений исходных файлов; build-файл адаптирован под toolchain EzADB.
См. `kadb/LICENSE` и `kadb/NOTICE`.

EzADB в целом распространяется под GPL-3.0. Apache 2.0 совместим с GPL-3.0.
