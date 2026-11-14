# Инструменты разработки ЭХО

Уже доступные навыки: android-cli (SDK, сборка, устройство, Compose preview),
echo-engineering (архитектура), echo-quality (качество и безопасность),
echo-product-concept (границы продукта), echo-visual-design (визуальная проверка).
Конституция этого нового проекта выше примеров предыдущей версии ЭХО в навыках.

При расширении набора полезнее узкие навыки под конкретную работу:
- Compose: состояние, recomposition, accessibility, adaptive layout, previews.
- Android lifecycle и offline: ViewModel, coroutines/Flow, Room, WorkManager.
- BLE и media: runtime permissions, аппаратные ограничения, энергопотребление.
- Android quality: unit/UI tests, Macrobenchmark, Baseline Profiles, startup,
  ANR/crash диагностика, проверка на физических устройствах.
Это направления будущих навыков, а не названия уже установленных пакетов.

Начальный набор MCP/интеграций:
1. GitHub — код, PR, CI и получение APK. В первую очередь.
2. Figma — компоненты, макеты и перенос дизайн-системы в Compose; при наличии макетов.
3. JetBrains MCP Server для совместимой версии Android Studio — IDE-диагностика,
   навигация и рефакторинг. Совместимость конкретного build IDE проверять до установки.
   [Официальный плагин JetBrains](https://plugins.jetbrains.com/plugin/26071-mcp-server).

ADB и android-cli уже закрывают локальные устройства: отдельный Android MCP
не обязателен. Не подключать неподдерживаемые серверы только ради количества.
SQLite достаточно проверять Python-инструментами; доступ MCP к рабочей БД не нужен.
Для PWA позже полезна автоматизация браузера; она не заменяет тесты Android.
Внешние интеграции разработки не становятся зависимостями мобильного приложения.
