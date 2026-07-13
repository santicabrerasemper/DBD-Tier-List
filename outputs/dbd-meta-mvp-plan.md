# DBD Meta Tier List MVP - Kotlin, Jetpack Compose y MVVM

## 1. Objetivo Del MVP

Crear una app Android personal para consultar el meta de Dead by Daylight por parche.

La app debe permitir:

- Ver tier list de killers.
- Ver tier list de perks de killer.
- Ver tier list de perks de survivor.
- Ver prioridad de survivors segun los perks que desbloquean.
- Seleccionar un parche.
- Ver cambios del parche que afectan el meta.
- Ajustar tiers manualmente cuando haga falta.

La app no depende de una API oficial de Behaviour. Usa una combinacion de:

- Datos base desde `https://dbd.tricky.lol/api`.
- Patch notes desde `https://dbd.tricky.lol/api/patchnotes`.
- Tiers curados localmente.
- Stats opcionales cargadas manualmente desde NightLight u otra fuente.

## 2. Producto Esperado

Primera version:

- App Android nativa.
- UI con Jetpack Compose.
- Arquitectura MVVM.
- Datos cacheados localmente.
- Sin login.
- Sin backend propio.
- Sin publicacion necesaria.

Pantallas principales:

- Home / resumen del parche.
- Tier list de killers.
- Tier list de perks de killer.
- Tier list de perks de survivor.
- Survivor unlock priority.
- Detalle de killer/perk/survivor.
- Admin local simple para editar tiers.

## 3. Arquitectura General

Arquitectura recomendada:

```text
app
core:model
core:network
core:database
core:common
core:designsystem
data:dbd
data:meta
domain
feature:home
feature:tiers
feature:patches
feature:details
feature:admin
```

Flujo de datos:

```text
Remote API / Local JSON
        |
Data Sources
        |
Repositories
        |
Use Cases
        |
ViewModels
        |
Compose UI
```

Regla importante:

- Compose solo muestra estado y emite eventos.
- ViewModel coordina estado.
- Use cases contienen reglas de negocio.
- Repositories deciden de donde salen los datos.
- Data sources hablan con API, Room o archivos locales.

## 4. Modulo `app`

Responsabilidad:

- Inicializar la app.
- Configurar navegacion.
- Configurar inyeccion de dependencias.
- Aplicar tema global.

Contenido:

```text
app/
  MainActivity.kt
  DbdMetaApp.kt
  navigation/
    AppNavHost.kt
    AppRoutes.kt
  di/
    AppModule.kt
```

Dependencias:

- `feature:home`
- `feature:tiers`
- `feature:patches`
- `feature:details`
- `feature:admin`
- `core:designsystem`

Paso a paso:

1. Crear `MainActivity`.
2. Crear `DbdMetaApp`.
3. Crear `AppNavHost`.
4. Definir rutas:
   - `home`
   - `tiers/{category}`
   - `patches`
   - `details/{type}/{id}`
   - `admin`
5. Conectar Hilt o Koin.

## 5. Modulo `core:model`

Responsabilidad:

- Definir modelos compartidos por toda la app.
- No debe depender de Android UI.

Modelos base:

```kotlin
data class Patch(
    val id: String,
    val type: PatchType,
    val notesHtml: String,
    val releasedAt: String? = null
)

data class Killer(
    val id: String,
    val apiId: String,
    val name: String,
    val difficulty: String?,
    val imagePath: String?,
    val perkIds: List<String>
)

data class Survivor(
    val id: String,
    val apiId: String,
    val name: String,
    val imagePath: String?,
    val perkIds: List<String>
)

data class Perk(
    val id: String,
    val name: String,
    val role: Role,
    val characterId: String?,
    val description: String,
    val categories: List<String>,
    val imagePath: String?
)
```

Modelos de meta:

```kotlin
data class TierEntry(
    val entityId: String,
    val entityType: EntityType,
    val patchId: String,
    val tier: Tier,
    val previousTier: Tier?,
    val score: Double,
    val confidence: Confidence,
    val changeDirection: ChangeDirection,
    val reason: String,
    val tags: List<MetaTag>
)

enum class Tier {
    S, A, B, C, D
}
```

Paso a paso:

1. Crear enums `Role`, `Tier`, `EntityType`, `PatchType`.
2. Crear modelos de contenido: `Killer`, `Survivor`, `Perk`, `Patch`.
3. Crear modelos de meta: `TierEntry`, `MetaStat`, `PatchChange`.
4. Mantener modelos simples, sin logica pesada.

## 6. Modulo `core:network`

Responsabilidad:

- Configurar cliente HTTP.
- Exponer servicios Retrofit.
- Manejar errores de red.

Endpoints necesarios:

```text
GET https://dbd.tricky.lol/api/characters?role=killer
GET https://dbd.tricky.lol/api/characters?role=survivor
GET https://dbd.tricky.lol/api/perks?role=killer
GET https://dbd.tricky.lol/api/perks?role=survivor
GET https://dbd.tricky.lol/api/patchnotes?pretty
GET https://dbd.tricky.lol/api/versions
```

Contenido:

```text
core/network/
  DbdApiService.kt
  NetworkModule.kt
  NetworkResult.kt
```

Servicio:

```kotlin
interface DbdApiService {
    @GET("api/characters")
    suspend fun getCharacters(
        @Query("role") role: String
    ): JsonObject

    @GET("api/perks")
    suspend fun getPerks(
        @Query("role") role: String
    ): JsonObject

    @GET("api/patchnotes")
    suspend fun getPatchNotes(
        @Query("pretty") pretty: Boolean = true
    ): List<PatchNoteDto>

    @GET("api/versions")
    suspend fun getVersions(): List<String>
}
```

Paso a paso:

1. Agregar Retrofit + Kotlinx Serialization o Moshi.
2. Crear `DbdApiService`.
3. Crear `NetworkModule`.
4. Crear wrapper `NetworkResult`.
5. Agregar timeout razonable.
6. Agregar logs solo en debug.

## 7. Modulo `core:database`

Responsabilidad:

- Cache local con Room.
- Permitir uso offline.
- Guardar tiers editados.

Tablas:

```text
patches
killers
survivors
perks
patch_changes
meta_stats
tier_entries
sync_metadata
```

Contenido:

```text
core/database/
  DbdMetaDatabase.kt
  dao/
    PatchDao.kt
    CharacterDao.kt
    PerkDao.kt
    TierDao.kt
    MetaStatDao.kt
  entity/
    PatchEntity.kt
    KillerEntity.kt
    SurvivorEntity.kt
    PerkEntity.kt
    TierEntryEntity.kt
```

Paso a paso:

1. Crear entidades Room.
2. Crear DAOs.
3. Crear `DbdMetaDatabase`.
4. Crear mappers Entity <-> Model.
5. Definir estrategia de cache:
   - contenido base se actualiza manualmente o al abrir;
   - tiers locales nunca se pisan sin confirmacion;
   - patch notes se guardan por version.

## 8. Modulo `core:common`

Responsabilidad:

- Utilidades compartidas.
- Helpers de resultado, fechas, strings y normalizacion.

Contenido:

```text
core/common/
  Result.kt
  DispatchersProvider.kt
  TextNormalizer.kt
  HtmlCleaner.kt
```

Funciones utiles:

```kotlin
fun String.normalizeDbdId(): String
fun String.stripHtml(): String
fun String.containsAnyKeyword(keywords: List<String>): Boolean
```

Paso a paso:

1. Crear `AppResult`.
2. Crear `DispatchersProvider`.
3. Crear helpers para limpiar HTML de patch notes.
4. Crear normalizador de nombres para matchear perks por texto.

## 9. Modulo `core:designsystem`

Responsabilidad:

- Tema Compose.
- Colores por tier.
- Componentes reutilizables.

Componentes:

```text
TierBadge
TierRow
EntityAvatar
PatchSelector
MetaTagChip
ConfidenceBadge
SearchBar
FilterChipRow
LoadingState
ErrorState
EmptyState
```

Colores sugeridos:

```text
S: rojo oscuro / dorado
A: rojo
B: violeta moderado
C: azul
D: gris
```

Paso a paso:

1. Crear `DbdMetaTheme`.
2. Crear componentes base.
3. Crear tarjetas compactas para listas.
4. Crear badges de:
   - buff
   - nerf
   - rework
   - indirect buff
   - indirect nerf
5. Mantener UI densa y escaneable.

## 10. Modulo `data:dbd`

Responsabilidad:

- Consumir API no oficial.
- Transformar DTOs a modelos.
- Guardar datos base en cache.

Contenido:

```text
data/dbd/
  remote/
    DbdRemoteDataSource.kt
    dto/
      CharacterDto.kt
      PerkDto.kt
      PatchNoteDto.kt
  local/
    DbdLocalDataSource.kt
  mapper/
    CharacterMapper.kt
    PerkMapper.kt
    PatchMapper.kt
  DbdRepositoryImpl.kt
```

Repositorio:

```kotlin
interface DbdRepository {
    suspend fun syncBaseData(): AppResult<Unit>
    fun observeKillers(): Flow<List<Killer>>
    fun observeSurvivors(): Flow<List<Survivor>>
    fun observePerks(role: Role): Flow<List<Perk>>
    fun observePatches(): Flow<List<Patch>>
}
```

Paso a paso:

1. Implementar remote data source.
2. Implementar local data source.
3. Mapear respuesta flexible de `JsonObject`.
4. Guardar killers, survivors y perks.
5. Guardar patch notes.
6. Exponer datos como `Flow`.

Nota tecnica:

La API devuelve objetos con keys dinamicas. Conviene parsear como `JsonObject` y mapear cada entry.

## 11. Modulo `data:meta`

Responsabilidad:

- Leer tiers curados.
- Guardar overrides locales.
- Leer stats manuales opcionales.
- Persistir resultados calculados.

Archivos locales sugeridos:

```text
assets/meta/default_tiers.json
assets/meta/default_stats.json
assets/meta/patch_overrides.json
```

Ejemplo de tier curado:

```json
{
  "patchId": "10.0.0",
  "entries": [
    {
      "entityId": "nurse",
      "entityType": "killer",
      "tier": "S",
      "score": 96,
      "reason": "Alto potencial competitivo y sigue ignorando muchas reglas de chase.",
      "confidence": "high",
      "tags": ["power_tier"]
    }
  ]
}
```

Contenido:

```text
data/meta/
  local/
    MetaLocalDataSource.kt
    MetaAssetDataSource.kt
  mapper/
    TierMapper.kt
    MetaStatMapper.kt
  MetaRepositoryImpl.kt
```

Repositorio:

```kotlin
interface MetaRepository {
    fun observeTierEntries(
        patchId: String,
        entityType: EntityType
    ): Flow<List<TierEntry>>

    suspend fun upsertTierEntry(entry: TierEntry)
    suspend fun importMetaStats(stats: List<MetaStat>)
}
```

Paso a paso:

1. Crear JSON inicial editable.
2. Cargarlo desde assets en primera ejecucion.
3. Guardarlo en Room.
4. Permitir editar tier desde admin.
5. Permitir importar stats luego.

## 12. Modulo `domain`

Responsabilidad:

- Contener reglas de negocio.
- Combinar contenido, patch notes, stats y tiers.
- Calcular score.

Use cases:

```text
SyncDbdBaseDataUseCase
GetCurrentPatchUseCase
GetTierListUseCase
GetPatchChangesUseCase
CalculateTierScoreUseCase
UpdateTierEntryUseCase
SearchEntitiesUseCase
```

Motor de scoring para killers:

```kotlin
score =
    killRateScore * 0.30 +
    pickRateScore * 0.15 +
    trendScore * 0.15 +
    patchImpactScore * 0.20 +
    editorialScore * 0.20
```

Motor de scoring para perks:

```kotlin
score =
    usageRateScore * 0.35 +
    trendScore * 0.20 +
    performanceScore * 0.20 +
    patchImpactScore * 0.15 +
    editorialScore * 0.10
```

Conversion a tier:

```text
S: 90 - 100
A: 75 - 89
B: 60 - 74
C: 40 - 59
D: 0 - 39
```

Paso a paso:

1. Crear interfaces de repositorios.
2. Crear use cases.
3. Implementar motor de scoring simple.
4. Priorizar tier curado si existe.
5. Usar score automatico como fallback.

Regla para survivors:

- No calcular "power tier" de survivor.
- Calcular "unlock priority" por valor de sus perks.
- Mostrar popularidad solo si hay stats.

## 13. Modulo `feature:home`

Responsabilidad:

- Mostrar resumen del parche actual.
- Mostrar accesos rapidos a cada tier list.
- Mostrar cambios importantes.

UI:

```text
HomeScreen
  PatchSelector
  MetaSummaryCards
  ChangedThisPatchSection
  QuickNavGrid
```

ViewModel:

```kotlin
data class HomeUiState(
    val selectedPatchId: String,
    val patches: List<Patch>,
    val summary: MetaSummary,
    val importantChanges: List<PatchChange>,
    val isLoading: Boolean,
    val errorMessage: String?
)
```

Paso a paso:

1. Crear `HomeViewModel`.
2. Observar parche seleccionado.
3. Cargar resumen.
4. Mostrar cards:
   - killers S tier
   - perks subiendo
   - nerfs importantes
   - cambios sin revisar
5. Navegar a tier lists.

## 14. Modulo `feature:tiers`

Responsabilidad:

- Mostrar tier lists.
- Filtrar y buscar.
- Agrupar por S/A/B/C/D.

Categorias:

```text
killers
killer_perks
survivor_perks
survivor_unlocks
```

UI:

```text
TierListScreen
  TopBar
  PatchSelector
  CategoryTabs
  SearchBar
  FilterChipRow
  TierSectionList
```

Filtros:

- Todos
- Buffed
- Nerfed
- Rework
- Rising
- Falling
- High confidence
- Manual tier

ViewModel:

```kotlin
data class TierListUiState(
    val selectedPatchId: String,
    val category: TierCategory,
    val query: String,
    val filters: Set<TierFilter>,
    val sections: List<TierSection>,
    val isLoading: Boolean,
    val errorMessage: String?
)
```

Paso a paso:

1. Crear `TierListViewModel`.
2. Crear `TierCategory`.
3. Crear filtros.
4. Agrupar entries por tier.
5. Ordenar por score descendente.
6. Mostrar razon corta y tags.

## 15. Modulo `feature:patches`

Responsabilidad:

- Mostrar lista de parches.
- Mostrar patch notes limpias.
- Mostrar cambios detectados.

UI:

```text
PatchListScreen
PatchDetailScreen
PatchChangeList
```

Parser inicial:

- Detectar palabras:
  - increased
  - decreased
  - reworked
  - cooldown
  - duration
  - movement speed
  - generator
  - healing
  - hindered
  - haste

Paso a paso:

1. Cargar patch notes desde cache.
2. Limpiar HTML para lectura.
3. Extraer candidatos a cambios.
4. Marcar cambios como:
   - buff
   - nerf
   - rework
   - bugfix
   - unknown
5. Dejar flag `needsReview`.

Importante:

El parser no debe decidir solo el meta. Solo ayuda a detectar cosas que revisar.

## 16. Modulo `feature:details`

Responsabilidad:

- Mostrar detalle de killer, perk o survivor.
- Mostrar historial de tier por parche.
- Mostrar notas editoriales.

Pantallas:

```text
KillerDetailScreen
PerkDetailScreen
SurvivorDetailScreen
```

Contenido por killer:

- Nombre.
- Imagen.
- Dificultad.
- Perks propios.
- Tier actual.
- Historial de tiers.
- Cambios recientes.
- Notas personales.

Contenido por perk:

- Nombre.
- Rol.
- Descripcion limpia.
- Categorias.
- Tier actual.
- Cambios recientes.
- Builds/sinergias manuales.

Contenido por survivor:

- Nombre.
- Perks propios.
- Unlock priority.
- Valor de cada perk.

Paso a paso:

1. Crear rutas de detalle.
2. Crear ViewModels por tipo o uno generico.
3. Mostrar informacion base.
4. Mostrar tier history.
5. Permitir navegar al admin para editar.

## 17. Modulo `feature:admin`

Responsabilidad:

- Editar tiers localmente.
- Ajustar score.
- Agregar razon.
- Marcar tags.
- Importar stats manuales.

Como es una app personal, el admin puede estar dentro de la app sin login.

UI:

```text
AdminScreen
  PatchSelector
  EntitySearch
  TierEditor
  TagsEditor
  ReasonEditor
  SaveButton
```

Campos editables:

- Tier.
- Score editorial.
- Reason.
- Confidence.
- Tags.
- Previous tier.
- Change direction.

Paso a paso:

1. Crear `AdminViewModel`.
2. Crear buscador de entidades.
3. Crear editor de tier.
4. Guardar cambios en Room.
5. Refrescar tier list automaticamente.

## 18. Sincronizacion De Datos

Primera ejecucion:

```text
1. App abre.
2. SyncDbdBaseDataUseCase consulta API.
3. Guarda killers, survivors, perks y patches en Room.
4. Carga tiers default desde assets.
5. Muestra Home.
```

Ejecuciones siguientes:

```text
1. App abre con cache local.
2. Muestra datos inmediatamente.
3. Permite refrescar manualmente.
4. Si hay nuevos patches, los guarda.
5. No pisa tiers editados.
```

Boton recomendado:

```text
Refresh data
```

No hacer sync agresivo automatico. Para uso personal alcanza con refresco manual.

## 19. Orden De Implementacion

### Fase 1 - Base Del Proyecto

1. Crear proyecto Android con Kotlin + Compose.
2. Configurar modulos Gradle.
3. Configurar Hilt o Koin.
4. Crear tema base.
5. Crear navegacion.

Resultado:

- App abre.
- Navegacion basica funcionando.
- Pantallas placeholder.

### Fase 2 - Datos Base

1. Implementar `core:network`.
2. Implementar `data:dbd`.
3. Implementar `core:database`.
4. Consumir killers.
5. Consumir perks.
6. Consumir patch notes.
7. Cachear en Room.

Resultado:

- App puede mostrar killers y perks reales desde la API.

### Fase 3 - Tier List Local

1. Crear `data:meta`.
2. Crear JSON inicial de tiers.
3. Cargar tiers desde assets.
4. Mostrar tier list agrupada.
5. Crear filtros simples.

Resultado:

- App muestra tier lists por categoria.

### Fase 4 - Patch Awareness

1. Mostrar selector de parche.
2. Mostrar patch notes.
3. Detectar cambios candidatos.
4. Asociar cambios a perks/killers cuando sea posible.
5. Mostrar tags en tier list.

Resultado:

- App empieza a explicar por que algo subio o bajo.

### Fase 5 - Admin Personal

1. Crear pantalla admin.
2. Editar tier.
3. Editar razon.
4. Editar confidence.
5. Guardar cambios.
6. Refrescar UI.

Resultado:

- Podes mantener tu propio meta por parche dentro de la app.

### Fase 6 - Stats Manuales

1. Definir formato JSON/CSV de stats.
2. Importar pick rate, kill rate, escape rate o usage rate.
3. Integrar stats al score.
4. Mostrar source/confidence.

Resultado:

- La tier list mezcla criterio personal con datos externos.

## 20. Dependencias Recomendadas

```kotlin
// UI
androidx.compose.ui
androidx.compose.material3
androidx.navigation.compose
androidx.lifecycle.viewmodel.compose

// Async
kotlinx.coroutines

// Network
retrofit
okhttp
kotlinx.serialization

// Database
androidx.room

// DI
hilt

// Images
coil-compose

// Testing
junit
turbine
mockk
```

## 21. Estructura De Paquetes Sugerida

```text
com.santi.dbdmeta
  app
  core
    model
    network
    database
    common
    designsystem
  data
    dbd
    meta
  domain
  feature
    home
    tiers
    patches
    details
    admin
```

## 22. Reglas Del MVP

- No buscar perfeccion automatica del meta.
- Todo tier puede ser editado manualmente.
- El parser de patch notes solo sugiere.
- Los survivors se rankean por valor de desbloqueo, no por poder propio.
- El score automatico nunca debe ocultar la explicacion humana.
- La app debe funcionar offline con cache.
- El primer objetivo es utilidad personal, no escalabilidad publica.

## 23. Definicion De "Listo" Para El MVP

El MVP esta listo cuando:

- Puedo abrir la app.
- Puedo refrescar datos base.
- Puedo elegir un parche.
- Puedo ver killers en tiers S/A/B/C/D.
- Puedo ver perks de killer en tiers S/A/B/C/D.
- Puedo ver perks de survivor en tiers S/A/B/C/D.
- Puedo ver survivors por prioridad de unlock.
- Puedo editar un tier manualmente.
- Puedo escribir una razon.
- Puedo ver si algo subio, bajo o se mantuvo.

## 24. Siguiente Paso Tecnico

Crear el proyecto Android y empezar por:

```text
Fase 1: app + navegacion + tema + placeholders
Fase 2: core:network + data:dbd + primer fetch real
```

La primera validacion tecnica debe ser:

```text
Mostrar en pantalla una lista real de killers obtenida desde:
https://dbd.tricky.lol/api/characters?role=killer
```

Si eso funciona, el resto del MVP queda bien encaminado.
