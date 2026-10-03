# Security Model

## Верификация артефакта (обязательный пайплайн, §23 ТЗ)

Перед ЛЮБОЙ установкой (`DefaultArtifactVerifier`, core:security):

```
Download completed
  → File exists                      (иначе InvalidPackage)
  → File size check                  (иначе ChecksumMismatch/InvalidPackage)
  → SHA-256 (streaming, окнами 64KB) (иначе ChecksumMismatch — установка запрещена)
  → APK parsing (PackageVerifier)    (иначе InvalidPackage)
  → packageName verification         (иначе InvalidPackage)
  → versionCode verification         (иначе InvalidPackage)
  → minSdk / architecture            (иначе IncompatibleDevice)
  → certificate/signature comparison (иначе SignatureMismatch — автоматическое
                                      обновление заблокировано, root это НЕ обходит)
  → Installation
```

## Сравнение подписи

- Сертификат нового APK извлекается на устройстве; подпись установленной версии —
  через `PackageManager` (`GET_SIGNING_CERTIFICATES` на API 28+, `GET_SIGNATURES`
  как legacy-путь).
- Несовместимая подпись → «Different signing certificate. Automatic update blocked.»
  Пользователь получает пояснение, обновление не подменяет приложение чужим APK.
- Если expected certificate из metadata источника определить нельзя — используется
  безопасный fallback: неподтверждённое privileged-обновление НЕ выполняется.

## Сеть

- Только HTTPS, стандартная валидация сертификатов OkHttp (modern TLS).
- Никаких кастомных trust managers, никаких отключений hostname verification.

## Источники и установка

- Установка разрешена только из Google Play, F-Droid-совместимых репозиториев
  (встроенных, включая IzzyOnDroid, и добавленных пользователем) и
  release-каталогов GitHub/GitLab.
- APKPure и APKCombo — только метаданные: каталог остаётся доступен для
  просмотра истории версий, но файлы из этих зеркал НЕ скачиваются и НЕ
  устанавливаются (`isInstallSourceAllowed`; границы `DownloadUpdateUseCase` и
  `InstallPackageUseCase`). Если зеркальную версию умеет отдать Google Play,
  загрузка автоматически переключается на Play (`DownloadUpdateUseCase.prepare`).
- Это deny-list, а не allow-list: любой добавленный пользователем репозиторий
  остаётся устанавливаемым.

## Root (см. docs/root-installation.md)

- Root — только бэкенд установки. Root НЕ отключает верификацию и не обходит
  подписи (§107, §106 ТЗ).
- Команды строятся из структурированных аргументов; URL/metadata из сети НИКОГДА
  не попадают в shell; `sh -c "<untrusted>"` запрещён и отсутствует в коде.

## Permissions

| Permission | Обоснование |
|---|---|
| `INTERNET` | загрузка метаданных и APK |
| `ACCESS_NETWORK_STATE` | Wi-Fi/metered constraints, offline-режим |
| `REQUEST_INSTALL_PACKAGES` | стандартный PackageInstaller flow (API 26+) |
| `POST_NOTIFICATIONS` | уведомления об обновлениях/загрузках (runtime, с 33+) |
| `RECEIVE_BOOT_COMPLETED` | восстановление очереди после ребута (§69) |
| `QUERY_ALL_PACKAGES` | сканирование установленных приложений — основная функция менеджера обновлений (официальное исключение для app stores/update managers) |

Список установленных приложений не покидает устройство: он используется только
локально для сравнения версий. Ни один источник не получает inventory устройства.

## Приватность

- Списки установленных приложений не отправляются на серверы (нет своего backend).
- Пароли не сохраняются: вход в Google идёт через собственную страницу Google
  (WebView `EmbeddedSetup`) либо системный `AccountManager`; на устройстве
  сохраняется только выданный сессионный токен, и он шифруется ключом Android
  Keystore (`SessionCipher`, AES-256-GCM). Резервное копирование приложения и
  device-to-device передача отключены (`allowBackup=false` + `data_extraction_rules`).
- Токенов/секретов в Git нет; release-подпись — через локальное окружение.
- Аналитики нет; телеметрии нет.

## Что приложение НЕ делает

- Не обходит DRM, Play Integrity, лицензии, подписи, системные промпты.
- Не удаляет/не модифицирует системные компоненты, SELinux, Play Protect.
- Не выполняет произвольные команды с сервера.
- Не собирает чужие приватные данные.

## Edge cases (§68)

Обработаны: нехватка места (`InsufficientStorage`), package conflict, signature
mismatch, downgrade (запрещён по умолчанию), invalid/broken APK, отмена установки
пользователем, root denied, исчезновение пакета, потеря сети, ребут во время очереди
(восстановление без повторной установки завершённых задач).
