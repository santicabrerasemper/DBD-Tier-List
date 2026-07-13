# DBD Tier List

App Android personal para consultar tier lists de Dead by Daylight por parche.

## Estado

Implementado:

- Kotlin + Jetpack Compose.
- MVVM con ViewModels y use cases.
- Fetch real desde `https://dbd.tricky.lol/api`.
- Cache local con `SharedPreferences`.
- Tiers curados iniciales.
- Home con resumen.
- Tier lists de killers, killer perks, survivor perks y survivor unlocks.
- Pantalla de patches.
- Admin local simple para cambiar tiers de killers.

## Ejecutar

Abrir esta carpeta en Android Studio y correr:

```text
assembleDebug
```

Tambien se puede usar Gradle si esta disponible:

```text
gradle assembleDebug
```

## Nota De Implementacion

El plan original define una arquitectura multi-modulo. En esta sesion el sandbox no permitio crear directorios nuevos desde shell, asi que la primera version quedo como single-module Android en la raiz, con las capas separadas por archivos:

- `Models.kt`
- `Network.kt`
- `Repositories.kt`
- `UseCases.kt`
- `ViewModels.kt`
- `DesignSystem.kt`
- `Screens.kt`
- `AppContainer.kt`

La separacion logica ya esta lista para migrarse a modulos Gradle cuando el proyecto se abra en un entorno normal.
