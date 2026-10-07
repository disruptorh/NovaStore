# Update Engine

`UpdateEngine` (core:updater) — главный компонент обнаружения обновлений. Он не знает
деталей источников и не знает деталей установки.

## Пайплайн (§15)

```
InstalledAppsRepository.scan()      — PackageManager, IO-диспатчер, кэш Room
        ↓
Resolve package names по включённым провайдерам (SourceRegistry)
        ↓
getLatestVersion(packageName)       — по каждому package
        ↓
Compare versionCode                 — VersionComparator
        ↓
Create UpdateCandidate              — с source, downloadInfo, size, verification info
        ↓
Apply user filters                  — allow downgrade (OFF по умолчанию), авто-фильтры
        ↓
Create UpdateQueue                  — очередь обновлений (Room: UpdateEntity)
```

## VersionComparator (§16)

- Основной источник истины — `versionCode` (Long). `versionName` — только отображение.
- Никаких строковых сравнений «10.2» > «9.9».
- Edge cases: отсутствующий versionCode (кандидат отбрасывается), downgrade
  (запрещён; отдельная настройка «Allow downgrade», default OFF), equal version
  (не кандидат), invalid/inconsistent metadata (кандидат отбрасывается, ошибка в лог).

## CompatibilityChecker (§18)

До загрузки/установки: Android API (SDK_INT) vs minSdk версии; ABI устройства
(`Build.SUPPORTED_ABIS`) vs артефакт; свободное место vs size (+ headroom);
packageName/version согласованы; split-требования. Несовместимо → `INcompatibleDevice`,
в авто-установку не попадает, в UI — бейдж «Not compatible».

## Машина состояний (§70)

`UpdateStateMachine` — единый источник истины (никаких isDownloading/isInstalled
boolean-флагов):

```
DISCOVERED → RESOLVED → QUEUED → DOWNLOADING → DOWNLOADED → VERIFYING → VERIFIED
→ WAITING_FOR_USER → INSTALLING → INSTALLED → CONFIRMED
|→ FAILED / CANCELLED (из любого шага пайплайна)
```

Переходы только по допустимому графу; каждая транзиция персистится (Room) → после
ребута `BootReceiver` восстанавливает состояние задач без повторной установки
успешно завершённых (§69).

## Update Transaction (§71)

`UpdateCandidate → DownloadTask → VerificationTask → InstallationTask →
VerifyInstalledTask → UpdateHistory` — устойчивый lifecycle, каждая стадия пишет
`UpdateHistoryEntity` (packageName, oldVersion, newVersion, timestamp, source,
result, error).

## Multi-source (§73–74)

Cuando varias fuentes ofrecen una actualización para el mismo paquete,
`SourceResolutionPolicy` decide de forma determinista, en este orden:
1) la fuente **preferida por el usuario** para la app (si ofrece update, gana
   directamente, antes de la política) — `UpdateEngine.resolveOne`;
2) **prioridad del repositorio** (menor primero) — un repo random nunca
   arrebata el catálogo a una fuente mejor colocada;
3) **mayor versionCode** (desempate entre fuentes de igual prioridad);
4) source id.
Los candidatos incompatibles (firma distinta de la instalada, ABI, minSdk...)
jamás entran en la selección. Firma distinta a la instalada → bloqueo de
auto-update con explicación visible; Root no se salta esta regla.

## Update All (§36)

`UpdateAllUseCase` (domain): Scan → Resolve → Compatibility → Queue → Download →
Verify → Install → Verify installed version → Next. Одиночная ошибка сохраняется,
очередь не теряется, остальные обновления продолжаются, в конце — summary
(успешно/неудачно). Реализация оркестрирует use cases/domain-сервисы, не содержа
деталей каждого слоя (§125).

## Post-install verification (§72)

`PackageManager.getPackageInfo()` → packageName + versionCode (+ certificate при
необходимости) сверяются с ожидаемыми. `UpdateResult = SUCCESS` только после этого.

## Автообновления (§37–39)

По умолчанию OFF. Настройки: Wi-Fi only, charging only, battery threshold, schedule
(Immediately/Daily/Weekly) — через WorkManager constraints
(NetworkType.UNMETERED / requiresCharging / requiresBatteryNotLow). Включение
подтверждается пользователем явно; «Confirm before installation» (default ON).

## Автоматический режим установки (§35)

`InstallationStrategyResolver`: AUTOMATIC (root доступен → Root, иначе Standard),
STANDARD, ROOT (недоступен → установка не выполняется, показывается причина),
MANAGED. Скрытых переключений нет.
