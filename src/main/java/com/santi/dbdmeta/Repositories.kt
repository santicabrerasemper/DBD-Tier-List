package com.santi.dbdmeta

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface DbdRepository {
    val syncStatus: StateFlow<String?>
    suspend fun initialize()
    suspend fun syncBaseData(): AppResult<Unit>
    fun observeKillers(): Flow<List<Killer>>
    fun observeSurvivors(): Flow<List<Survivor>>
    fun observePerks(role: Role): Flow<List<Perk>>
    fun observePatches(): Flow<List<Patch>>
}

interface MetaRepository {
    suspend fun initialize()
    fun observeTierEntries(): Flow<List<TierEntry>>
    suspend fun upsertTierEntry(entry: TierEntry)
    suspend fun ensurePatchTierEntries(patchId: String, previousPatchId: String?)
}

class DbdRepositoryImpl(
    context: Context,
    private val client: DbdHttpClient,
    private val json: Json
) : DbdRepository {
    private val prefs = context.getSharedPreferences("dbd_base_cache", Context.MODE_PRIVATE)
    private val killers = MutableStateFlow<List<Killer>>(emptyList())
    private val survivors = MutableStateFlow<List<Survivor>>(emptyList())
    private val killerPerks = MutableStateFlow<List<Perk>>(emptyList())
    private val survivorPerks = MutableStateFlow<List<Perk>>(emptyList())
    private val patches = MutableStateFlow<List<Patch>>(emptyList())
    private val _syncStatus = MutableStateFlow<String?>(null)

    override val syncStatus: StateFlow<String?> = _syncStatus

    override suspend fun initialize() {
        killers.value = prefs.readList("killers")
        survivors.value = prefs.readList("survivors")
        killerPerks.value = prefs.readList("killer_perks")
        survivorPerks.value = prefs.readList("survivor_perks")
        patches.value = prefs.readList("patches")
        if (patches.value.isEmpty()) {
            patches.value = listOf(Patch("10.0.0", PatchType.RELEASE, ""))
        }
    }

    override suspend fun syncBaseData(): AppResult<Unit> {
        _syncStatus.value = "Refreshing DBD data..."
        var failures = 0

        when (val result = client.getJson("api/characters?role=killer")) {
            is AppResult.Success -> result.value.toKillers().also {
                killers.value = it
                prefs.writeList("killers", it)
            }
            is AppResult.Error -> failures++
        }

        when (val result = client.getJson("api/characters?role=survivor")) {
            is AppResult.Success -> result.value.toSurvivors().also {
                survivors.value = it
                prefs.writeList("survivors", it)
            }
            is AppResult.Error -> failures++
        }

        when (val result = client.getJson("api/perks?role=killer")) {
            is AppResult.Success -> result.value.toPerks(Role.KILLER).also {
                killerPerks.value = it
                prefs.writeList("killer_perks", it)
            }
            is AppResult.Error -> failures++
        }

        when (val result = client.getJson("api/perks?role=survivor")) {
            is AppResult.Success -> result.value.toPerks(Role.SURVIVOR).also {
                survivorPerks.value = it
                prefs.writeList("survivor_perks", it)
            }
            is AppResult.Error -> failures++
        }

        when (val result = client.getJson("api/patchnotes?pretty")) {
            is AppResult.Success -> result.value.toPatches().also {
                patches.value = it
                prefs.writeList("patches", it)
            }
            is AppResult.Error -> failures++
        }

        return if (failures == 5) {
            _syncStatus.value = "Offline cache only"
            AppResult.Error("No se pudo refrescar ningun endpoint.")
        } else {
            _syncStatus.value = "Updated"
            AppResult.Success(Unit)
        }
    }

    override fun observeKillers(): Flow<List<Killer>> = killers
    override fun observeSurvivors(): Flow<List<Survivor>> = survivors
    override fun observePerks(role: Role): Flow<List<Perk>> =
        if (role == Role.KILLER) killerPerks else survivorPerks

    override fun observePatches(): Flow<List<Patch>> = patches

    private inline fun <reified T> SharedPreferences.readList(key: String): List<T> =
        getString(key, null)?.let { raw ->
            runCatching { json.decodeFromString<List<T>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList()

    private inline fun <reified T> SharedPreferences.writeList(key: String, value: List<T>) {
        edit().putString(key, json.encodeToString(value)).apply()
    }
}

class MetaRepositoryImpl(
    context: Context,
    private val json: Json
) : MetaRepository {
    private val prefs = context.getSharedPreferences("dbd_meta_cache", Context.MODE_PRIVATE)
    private val entries = MutableStateFlow<List<TierEntry>>(emptyList())

    override suspend fun initialize() {
        val saved = prefs.getString("tier_entries", null)
            ?.let { raw -> runCatching { json.decodeFromString<List<TierEntry>>(raw) }.getOrNull() }
            .orEmpty()

        entries.value = mergeDefaults(saved)
    }

    override fun observeTierEntries(): Flow<List<TierEntry>> = entries

    override suspend fun upsertTierEntry(entry: TierEntry) {
        val next = entries.value
            .filterNot {
                it.entityId == entry.entityId &&
                    it.entityType == entry.entityType &&
                    it.patchId == entry.patchId
            } + entry.copy(manual = true)

        entries.value = next
        prefs.edit().putString("tier_entries", json.encodeToString(next)).apply()
    }

    override suspend fun ensurePatchTierEntries(patchId: String, previousPatchId: String?) {
        if (entries.value.any { it.patchId == patchId }) return

        val sourcePatchId = previousPatchId
            ?.takeIf { previous -> entries.value.any { it.patchId == previous } }
            ?: entries.value.map { it.patchId }.distinct().firstOrNull()
            ?: return

        val cloned = entries.value
            .filter { it.patchId == sourcePatchId }
            .map {
                it.copy(
                    patchId = patchId,
                    previousTier = it.tier,
                    changeDirection = ChangeDirection.SAME,
                    confidence = Confidence.LOW,
                    reason = "Copiado desde $sourcePatchId. Revisar contra las notas del parche.",
                    tags = (it.tags + listOf("carried-over", "needs-review")).distinct(),
                    manual = false
                )
            }

        if (cloned.isNotEmpty()) {
            entries.value = entries.value + cloned
            prefs.edit().putString("tier_entries", json.encodeToString(entries.value)).apply()
        }
    }

    private fun mergeDefaults(saved: List<TierEntry>): List<TierEntry> {
        val savedKeys = saved.map { it.key }.toSet()
        return saved + defaultTierEntries.filterNot { it.key in savedKeys }
    }
}

private val TierEntry.key: String
    get() = "$patchId|$entityType|$entityId"

private val defaultTierEntries = gamesRadarKillerEntries() + listOf(
    TierEntry(
        entityId = "scourge-hook-pain-resonance",
        entityType = EntityType.KILLER_PERK,
        patchId = "10.0.0",
        tier = Tier.S,
        previousTier = Tier.S,
        score = 92.0,
        confidence = Confidence.HIGH,
        changeDirection = ChangeDirection.SAME,
        reason = "Regression eficiente sin perder mucho tempo si el killer puede asegurar hooks.",
        tags = listOf("regression", "meta")
    ),
    TierEntry(
        entityId = "corrupt-intervention",
        entityType = EntityType.KILLER_PERK,
        patchId = "10.0.0",
        tier = Tier.A,
        previousTier = Tier.A,
        score = 84.0,
        confidence = Confidence.HIGH,
        changeDirection = ChangeDirection.SAME,
        reason = "Inicio de partida estable para killers que necesitan setup o primer down.",
        tags = listOf("early-game")
    ),
    TierEntry(
        entityId = "windows-of-opportunity",
        entityType = EntityType.SURVIVOR_PERK,
        patchId = "10.0.0",
        tier = Tier.S,
        previousTier = Tier.S,
        score = 93.0,
        confidence = Confidence.HIGH,
        changeDirection = ChangeDirection.SAME,
        reason = "Valor enorme en solo queue: mejora pathing, reduce errores y ayuda a encadenar tiles.",
        tags = listOf("solo-queue", "chase")
    ),
    TierEntry(
        entityId = "off-the-record",
        entityType = EntityType.SURVIVOR_PERK,
        patchId = "10.0.0",
        tier = Tier.A,
        previousTier = Tier.A,
        score = 86.0,
        confidence = Confidence.HIGH,
        changeDirection = ChangeDirection.SAME,
        reason = "Anti-tunnel muy consistente despues de ser descolgado.",
        tags = listOf("anti-tunnel")
    ),
    TierEntry(
        entityId = "kate-denson",
        entityType = EntityType.SURVIVOR_UNLOCK,
        patchId = "10.0.0",
        tier = Tier.S,
        previousTier = Tier.S,
        score = 92.0,
        confidence = Confidence.HIGH,
        changeDirection = ChangeDirection.SAME,
        reason = "Prioridad alta por Windows of Opportunity.",
        tags = listOf("unlock-priority")
    )
)

private fun gamesRadarKillerEntries(): List<TierEntry> {
    val source = "GamesRadar+ killer tier list, published 18 June 2026"
    return listOf(
        "The Nurse" to SourceTier(Tier.S, 96.0, "gamesradar-s"),
        "The Blight" to SourceTier(Tier.S, 95.0, "gamesradar-s"),
        "The Slasher" to SourceTier(Tier.A, 89.0, "gamesradar-a-plus"),
        "The Ghoul" to SourceTier(Tier.A, 88.0, "gamesradar-a-plus"),
        "The Wraith" to SourceTier(Tier.A, 87.0, "gamesradar-a-plus"),
        "The Spirit" to SourceTier(Tier.A, 86.0, "gamesradar-a-plus"),
        "The Hillbilly" to SourceTier(Tier.A, 84.0, "gamesradar-a"),
        "The Trickster" to SourceTier(Tier.A, 83.0, "gamesradar-a"),
        "The Huntress" to SourceTier(Tier.A, 83.0, "gamesradar-a"),
        "The Knight" to SourceTier(Tier.A, 82.0, "gamesradar-a"),
        "The Lich" to SourceTier(Tier.A, 82.0, "gamesradar-a"),
        "The Doctor" to SourceTier(Tier.A, 81.0, "gamesradar-a"),
        "The Dark Lord" to SourceTier(Tier.A, 81.0, "gamesradar-a"),
        "The Nightmare" to SourceTier(Tier.A, 80.0, "gamesradar-a"),
        "The Pig" to SourceTier(Tier.A, 80.0, "gamesradar-a"),
        "The Legion" to SourceTier(Tier.A, 79.0, "gamesradar-a"),
        "The Plague" to SourceTier(Tier.B, 73.0, "gamesradar-b"),
        "The Xenomorph" to SourceTier(Tier.B, 72.0, "gamesradar-b"),
        "The Executioner" to SourceTier(Tier.B, 72.0, "gamesradar-b"),
        "The First" to SourceTier(Tier.B, 71.0, "gamesradar-b"),
        "The Shape" to SourceTier(Tier.B, 70.0, "gamesradar-b"),
        "The Deathslinger" to SourceTier(Tier.B, 70.0, "gamesradar-b"),
        "The Animatronic" to SourceTier(Tier.B, 69.0, "gamesradar-b"),
        "The Mastermind" to SourceTier(Tier.B, 69.0, "gamesradar-b"),
        "The Nemesis" to SourceTier(Tier.B, 68.0, "gamesradar-b"),
        "The Oni" to SourceTier(Tier.B, 68.0, "gamesradar-b"),
        "The Cannibal" to SourceTier(Tier.B, 67.0, "gamesradar-b"),
        "The Trapper" to SourceTier(Tier.B, 67.0, "gamesradar-b"),
        "The Unknown" to SourceTier(Tier.B, 66.0, "gamesradar-b"),
        "The Krasue" to SourceTier(Tier.B, 66.0, "gamesradar-b"),
        "The Clown" to SourceTier(Tier.B, 65.0, "gamesradar-b"),
        "The Artist" to SourceTier(Tier.B, 65.0, "gamesradar-b"),
        "The Singularity" to SourceTier(Tier.C, 58.0, "gamesradar-c"),
        "The Houndmaster" to SourceTier(Tier.C, 57.0, "gamesradar-c"),
        "The Demogorgon" to SourceTier(Tier.C, 56.0, "gamesradar-c"),
        "The Good Guy" to SourceTier(Tier.C, 55.0, "gamesradar-c"),
        "The Twins" to SourceTier(Tier.C, 54.0, "gamesradar-c"),
        "The Ghost Face" to SourceTier(Tier.C, 53.0, "gamesradar-c"),
        "The Cenobite" to SourceTier(Tier.C, 52.0, "gamesradar-c"),
        "The Onryo" to SourceTier(Tier.C, 51.0, "gamesradar-c"),
        "The Dredge" to SourceTier(Tier.C, 50.0, "gamesradar-c"),
        "The Hag" to SourceTier(Tier.C, 49.0, "gamesradar-c")
    ).map { (name, sourceTier) ->
        TierEntry(
            entityId = name.toDbdId(),
            entityType = EntityType.KILLER,
            patchId = "10.0.0",
            tier = sourceTier.tier,
            previousTier = sourceTier.tier,
            score = sourceTier.score,
            confidence = Confidence.MEDIUM,
            changeDirection = ChangeDirection.SAME,
            reason = "Seed editorial basado en $source. Ajustalo manualmente si tu criterio o el parche actual difiere.",
            tags = listOf("source-gamesradar", sourceTier.sourceTag)
        )
    }
}

private data class SourceTier(
    val tier: Tier,
    val score: Double,
    val sourceTag: String
)
