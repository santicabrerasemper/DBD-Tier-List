package com.santi.dbdmeta

import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
enum class Role {
    KILLER,
    SURVIVOR
}

@Serializable
enum class EntityType {
    KILLER,
    KILLER_PERK,
    SURVIVOR_PERK,
    SURVIVOR_UNLOCK
}

@Serializable
enum class Tier {
    S,
    A,
    B,
    C,
    D
}

@Serializable
enum class Confidence {
    HIGH,
    MEDIUM,
    LOW
}

@Serializable
enum class ChangeDirection {
    UP,
    DOWN,
    SAME,
    NEW,
    UNKNOWN
}

@Serializable
enum class PatchType {
    RELEASE,
    HOTFIX,
    PTB,
    UNKNOWN
}

@Serializable
data class Patch(
    val id: String,
    val type: PatchType,
    val notesHtml: String
)

@Serializable
data class Killer(
    val id: String,
    val apiId: String,
    val name: String,
    val difficulty: String? = null,
    val imagePath: String? = null,
    val perkIds: List<String> = emptyList()
)

@Serializable
data class Survivor(
    val id: String,
    val apiId: String,
    val name: String,
    val imagePath: String? = null,
    val perkIds: List<String> = emptyList()
)

@Serializable
data class Perk(
    val id: String,
    val apiId: String,
    val name: String,
    val role: Role,
    val characterId: String? = null,
    val description: String,
    val categories: List<String> = emptyList(),
    val imagePath: String? = null
)

@Serializable
data class TierEntry(
    val entityId: String,
    val entityType: EntityType,
    val patchId: String,
    val tier: Tier,
    val previousTier: Tier? = null,
    val score: Double,
    val confidence: Confidence,
    val changeDirection: ChangeDirection,
    val reason: String,
    val tags: List<String> = emptyList(),
    val manual: Boolean = false
)

@Serializable
data class MetaStat(
    val entityId: String,
    val patchId: String,
    val pickRate: Double? = null,
    val killRate: Double? = null,
    val escapeRate: Double? = null,
    val usageRate: Double? = null,
    val source: String = "manual"
)

data class TierListItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val entry: TierEntry
)

data class TierSection(
    val tier: Tier,
    val items: List<TierListItem>
)

data class AppSummary(
    val killerCount: Int = 0,
    val killerPerkCount: Int = 0,
    val survivorPerkCount: Int = 0,
    val patchCount: Int = 0,
    val sTierCount: Int = 0,
    val needsReviewCount: Int = 0
)

data class PatchChange(
    val patchId: String,
    val entityId: String,
    val entityName: String,
    val entityType: EntityType,
    val direction: ChangeDirection,
    val summary: String,
    val confidence: Confidence
)

data class PatchComparison(
    val fromPatchId: String,
    val toPatchId: String,
    val upCount: Int,
    val downCount: Int,
    val sameCount: Int,
    val needsReviewCount: Int
)

data class KillerBuild(
    val killerId: String,
    val killerName: String,
    val buildName: String,
    val perks: List<String>,
    val source: String,
    val sourceUpdated: String,
    val note: String,
    val metaTier: Tier? = null,
    val killerScore: Double = 0.0
)

fun TierEntry.sourceLabel(): String = when {
    tags.any { it == "source-gamesradar" } -> "GamesRadar"
    manual -> "Manual"
    tags.any { it == "carried-over" } -> "Previous patch"
    else -> "App seed"
}

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Error(val message: String, val cause: Throwable? = null) : AppResult<Nothing>
}

fun String.toDbdId(): String =
    lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

fun String.stripHtml(): String =
    replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&rsquo;", "'")
        .replace("&lsquo;", "'")
        .replace("&quot;", "\"")
        .replace(Regex("\\s+"), " ")
        .trim()

fun Int.toPatchType(): PatchType = when (this) {
    0 -> PatchType.HOTFIX
    1 -> PatchType.PTB
    2 -> PatchType.RELEASE
    else -> PatchType.UNKNOWN
}
