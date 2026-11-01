# ФотоПорядок

Первая версия Android-приложения: локально читает медиатеку, показывает фотографии сеткой и сортирует по дате добавления. Фотографии не загружаются в сеть и не удаляются.

## Сборка
Откройте проект в Android Studio или запустите `gradle :app:assembleDebug`.
GitHub Actions workflow `.github/workflows/build-apk.yml` собирает debug APK по push или вручную.
