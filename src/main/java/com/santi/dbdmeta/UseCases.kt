package com.santi.dbdmeta

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

enum class TierCategory(val entityType: EntityType, val title: String) {
    KILLERS(EntityType.KILLER, "Killers"),
    KILLER_PERKS(EntityType.KILLER_PERK, "Killer perks"),
    SURVIVOR_PERKS(EntityType.SURVIVOR_PERK, "Survivor perks"),
    SURVIVOR_UNLOCKS(EntityType.SURVIVOR_UNLOCK, "Survivor unlocks")
}

class InitializeAppUseCase(
    private val dbdRepository: DbdRepository,
    private val metaRepository: MetaRepository
) {
    suspend operator fun invoke() {
        dbdRepository.initialize()
        metaRepository.initialize()
    }
}

class SyncDbdBaseDataUseCase(
    private val dbdRepository: DbdRepository,
    private val metaRepository: MetaRepository
) {
    suspend operator fun invoke(): AppResult<Unit> {
        val result = dbdRepository.syncBaseData()
        if (result is AppResult.Success) {
            val patches = (dbdRepository.observePatches() as? kotlinx.coroutines.flow.StateFlow)?.value.orEmpty()
            val latest = patches.firstOrNull()?.id
            val previous = patches.drop(1).firstOrNull()?.id
            if (latest != null) {
                metaRepository.ensurePatchTierEntries(latest, previous)
            }
        }
        return result
    }
}

class ObservePatchesUseCase(
    private val dbdRepository: DbdRepository
) {
    operator fun invoke(): Flow<List<Patch>> = dbdRepository.observePatches()
}

class ObserveSyncStatusUseCase(
    private val dbdRepository: DbdRepository
) {
    operator fun invoke(): Flow<String?> = dbdRepository.syncStatus
}

class GetHomeSummaryUseCase(
    private val dbdRepository: DbdRepository,
    private val metaRepository: MetaRepository
) {
    operator fun invoke(patchId: String): Flow<AppSummary> =
        combine(
            dbdRepository.observeKillers(),
            dbdRepository.observePerks(Role.KILLER),
            dbdRepository.observePerks(Role.SURVIVOR),
            dbdRepository.observePatches(),
            metaRepository.observeTierEntries()
        ) { killers, killerPerks, survivorPerks, patches, tiers ->
            AppSummary(
                killerCount = killers.size,
                killerPerkCount = killerPerks.size,
                survivorPerkCount = survivorPerks.size,
                patchCount = patches.size,
                sTierCount = tiers.count { it.patchId == patchId && it.tier == Tier.S },
                needsReviewCount = tiers.count { it.patchId == patchId && "needs-review" in it.tags }
            )
        }
}

class GetTierListUseCase(
    private val dbdRepository: DbdRepository,
    private val metaRepository: MetaRepository
) {
    operator fun invoke(category: TierCategory, patchId: String): Flow<List<TierSection>> {
        val tierFlow = metaRepository.observeTierEntries()
        val contentFlow = when (category) {
            TierCategory.KILLERS -> dbdRepository.observeKillers().map { killers ->
                killers.map { ContentItem(it.id, it.name, it.difficulty ?: "Killer") }
            }
            TierCategory.KILLER_PERKS -> dbdRepository.observePerks(Role.KILLER).map { perks ->
                perks.map { ContentItem(it.id, it.name, it.categories.joinToString(", ").ifBlank { "Killer perk" }) }
            }
            TierCategory.SURVIVOR_PERKS -> dbdRepository.observePerks(Role.SURVIVOR).map { perks ->
                perks.map { ContentItem(it.id, it.name, it.categories.joinToString(", ").ifBlank { "Survivor perk" }) }
            }
            TierCategory.SURVIVOR_UNLOCKS -> dbdRepository.observeSurvivors().map { survivors ->
                survivors.map { ContentItem(it.id, it.name, "Unlock priority") }
            }
        }

        return combine(contentFlow, tierFlow) { content, tiers ->
            val byId = tiers
                .filter { it.patchId == patchId && it.entityType == category.entityType }
                .associateBy { it.entityId }

            val contentItems = content.map { item ->
                TierListItem(
                    id = item.id,
                    title = item.title,
                    subtitle = item.subtitle,
                    entry = byId[item.id] ?: fallbackEntry(item.id, category.entityType, patchId)
                )
            }

            val orphanTiers = byId.values
                .filterNot { tier -> contentItems.any { it.id == tier.entityId } }
                .map { tier ->
                    TierListItem(
                        id = tier.entityId,
                        title = tier.entityId.replace('-', ' ').replaceFirstChar { it.uppercase() },
                        subtitle = "Curated entry",
                        entry = tier
                    )
                }

            (contentItems + orphanTiers)
                .sortedWith(compareByDescending<TierListItem> { it.entry.score }.thenBy { it.title })
                .groupBy { it.entry.tier }
                .let { grouped ->
                    Tier.entries.map { tier ->
                        TierSection(tier, grouped[tier].orEmpty())
                    }
                }
        }
    }

    private fun fallbackEntry(entityId: String, entityType: EntityType, patchId: String): TierEntry =
        TierEntry(
            entityId = entityId,
            entityType = entityType,
            patchId = patchId,
            tier = Tier.C,
            previousTier = null,
            score = 50.0,
            confidence = Confidence.LOW,
            changeDirection = ChangeDirection.UNKNOWN,
            reason = "Pendiente de revisar para este parche.",
            tags = listOf("needs-review")
        )
}

class GetTierItemUseCase(
    private val getTierList: GetTierListUseCase
) {
    operator fun invoke(category: TierCategory, patchId: String, itemId: String): Flow<TierListItem?> =
        getTierList(category, patchId).map { sections ->
            sections.flatMap { it.items }.firstOrNull { it.id == itemId }
        }
}

class GetPatchNotesUseCase(
    private val dbdRepository: DbdRepository
) {
    operator fun invoke(): Flow<List<Patch>> = dbdRepository.observePatches()
}

class GetPatchReviewUseCase(
    private val dbdRepository: DbdRepository
) {
    operator fun invoke(): Flow<List<PatchChange>> =
        combine(
            dbdRepository.observePatches(),
            dbdRepository.observeKillers(),
            dbdRepository.observePerks(Role.KILLER),
            dbdRepository.observePerks(Role.SURVIVOR)
        ) { patches, killers, killerPerks, survivorPerks ->
            val patch = patches.firstOrNull() ?: return@combine emptyList()
            val notes = patch.notesHtml.stripHtml()
            val candidates = killers.map { ReviewCandidate(it.id, it.name, EntityType.KILLER) } +
                killerPerks.map { ReviewCandidate(it.id, it.name, EntityType.KILLER_PERK) } +
                survivorPerks.map { ReviewCandidate(it.id, it.name, EntityType.SURVIVOR_PERK) }

            candidates.mapNotNull { candidate ->
                val index = notes.indexOf(candidate.name, ignoreCase = true)
                if (index < 0) return@mapNotNull null
                val start = (index - 160).coerceAtLeast(0)
                val end = (index + 260).coerceAtMost(notes.length)
                val snippet = notes.substring(start, end).trim()
                PatchChange(
                    patchId = patch.id,
                    entityId = candidate.id,
                    entityName = candidate.name,
                    entityType = candidate.type,
                    direction = snippet.inferDirection(),
                    summary = snippet,
                    confidence = Confidence.MEDIUM
                )
            }.distinctBy { "${it.patchId}|${it.entityType}|${it.entityId}" }
        }
}

class GetPatchComparisonUseCase(
    private val dbdRepository: DbdRepository,
    private val metaRepository: MetaRepository
) {
    operator fun invoke(): Flow<PatchComparison?> =
        combine(
            dbdRepository.observePatches(),
            metaRepository.observeTierEntries()
        ) { patches, tiers ->
            val latest = patches.firstOrNull()?.id ?: return@combine null
            val previous = patches.drop(1).firstOrNull()?.id ?: return@combine null
            val latestTiers = tiers.filter { it.patchId == latest }
            if (latestTiers.isEmpty()) return@combine null

            PatchComparison(
                fromPatchId = previous,
                toPatchId = latest,
                upCount = latestTiers.count { it.changeDirection == ChangeDirection.UP },
                downCount = latestTiers.count { it.changeDirection == ChangeDirection.DOWN },
                sameCount = latestTiers.count { it.changeDirection == ChangeDirection.SAME },
                needsReviewCount = latestTiers.count { "needs-review" in it.tags }
            )
        }
}

class GetKillerBuildsUseCase(
    private val getTierList: GetTierListUseCase
) {
    operator fun invoke(patchId: String): Flow<List<KillerBuild>> =
        getTierList(TierCategory.KILLERS, patchId).map { sections ->
            val tiersById = sections.flatMap { it.items }.associateBy { it.id }
            defaultKillerBuilds.map { build ->
                val tierItem = tiersById[build.killerId]
                build.copy(
                    metaTier = tierItem?.entry?.tier,
                    killerScore = tierItem?.entry?.score ?: build.killerScore
                )
            }.sortedWith(
                compareByDescending<KillerBuild> { it.killerScore }
                    .thenBy { it.killerName }
                    .thenBy { it.buildName }
            )
        }
}

class UpdateTierEntryUseCase(
    private val metaRepository: MetaRepository
) {
    suspend operator fun invoke(entry: TierEntry) = metaRepository.upsertTierEntry(entry)
}

private data class ContentItem(
    val id: String,
    val title: String,
    val subtitle: String
)

private data class ReviewCandidate(
    val id: String,
    val name: String,
    val type: EntityType
)

private fun String.inferDirection(): ChangeDirection {
    val lower = lowercase()
    return when {
        listOf("increased", "increase", "faster", "reduced cooldown", "decreased cooldown", "buff").any { it in lower } -> ChangeDirection.UP
        listOf("decreased", "slower", "reduced", "increased cooldown", "nerf").any { it in lower } -> ChangeDirection.DOWN
        listOf("rework", "reworked", "changed").any { it in lower } -> ChangeDirection.UNKNOWN
        else -> ChangeDirection.UNKNOWN
    }
}

private val defaultKillerBuilds = listOf(
    KillerBuild(
        killerId = "the-nurse",
        killerName = "The Nurse",
        buildName = "Generic aura pressure",
        perks = listOf("Lethal Pursuer", "Barbecue & Chilli", "No Holds Barred", "Nowhere to Hide"),
        source = "Otzdarva recommended builds",
        sourceUpdated = "2025-10-04",
        note = "Aura/info build for a top GamesRadar S-tier killer. Good when you want constant target acquisition."
    ),
    KillerBuild(
        killerId = "the-nurse",
        killerName = "The Nurse",
        buildName = "Scourge control",
        perks = listOf("Scourge Hook: Pain Resonance", "Scourge Hook: Floods of Rage", "Scourge Hook: Jagged Compass", "Grim Embrace"),
        source = "Otzdarva recommended builds",
        sourceUpdated = "2025-10-04",
        note = "Hook-based slowdown plus aura/info. Best if you can reliably down and hook quickly."
    ),
    KillerBuild(
        killerId = "the-blight",
        killerName = "The Blight",
        buildName = "Generic meta pressure",
        perks = listOf("Scourge Hook: Pain Resonance", "Pop Goes the Weasel", "Grim Embrace", "Barbecue & Chilli"),
        source = "Otzdarva recommended builds",
        sourceUpdated = "2025-10-04",
        note = "Strong slowdown and info for a GamesRadar S-tier mobility killer."
    ),
    KillerBuild(
        killerId = "the-blight",
        killerName = "The Blight",
        buildName = "Aggressive aura",
        perks = listOf("Lethal Pursuer", "Barbecue & Chilli", "Pop Goes the Weasel", "Nowhere to Hide"),
        source = "Otzdarva recommended builds",
        sourceUpdated = "2025-10-04",
        note = "More tempo and chase routing, less pure slowdown."
    ),
    KillerBuild(
        killerId = "the-spirit",
        killerName = "The Spirit",
        buildName = "Generic slowdown",
        perks = listOf("Corrupt Intervention", "Friends 'til the End", "Scourge Hook: Pain Resonance", "Pop Goes the Weasel"),
        source = "Otzdarva recommended builds",
        sourceUpdated = "2025-10-04",
        note = "Reliable all-rounder for a high-tier chase killer."
    ),
    KillerBuild(
        killerId = "the-ghoul",
        killerName = "The Ghoul",
        buildName = "Generic control",
        perks = listOf("Scourge Hook: Pain Resonance", "Scourge Hook: Jagged Compass", "Eruption", "Coup de Grace"),
        source = "Otzdarva recommended builds",
        sourceUpdated = "2025-10-04",
        note = "Control and chase mix for a GamesRadar A+ killer."
    ),
    KillerBuild(
        killerId = "the-wraith",
        killerName = "The Wraith",
        buildName = "Generic pressure",
        perks = listOf("Corrupt Intervention", "Scourge Hook: Pain Resonance", "Grim Embrace", "Bamboozle"),
        source = "Otzdarva recommended builds",
        sourceUpdated = "2025-10-04",
        note = "Simple, practical pressure build for Wraith's hit-and-run pacing."
    ),
    KillerBuild(
        killerId = "the-huntress",
        killerName = "The Huntress",
        buildName = "Generic build",
        perks = listOf("Corrupt Intervention", "Barbecue & Chilli", "Grim Embrace", "No Way Out"),
        source = "Otzdarva recommended builds",
        sourceUpdated = "2025-10-04",
        note = "Stabilizes early game and gives reliable hook/endgame value."
    ),
    KillerBuild(
        killerId = "the-hillbilly",
        killerName = "The Hillbilly",
        buildName = "Generic build",
        perks = listOf("Pop Goes the Weasel", "Scourge Hook: Pain Resonance", "Barbecue & Chilli", "Bamboozle"),
        source = "Otzdarva recommended builds",
        sourceUpdated = "2025-10-04",
        note = "Regression plus info, with Bamboozle helping shorten windows in chase."
    ),
    KillerBuild(
        killerId = "the-doctor",
        killerName = "The Doctor",
        buildName = "Generic build",
        perks = listOf("Corrupt Intervention", "Scourge Hook: Pain Resonance", "Grim Embrace", "Merciless Storm"),
        source = "Otzdarva recommended builds",
        sourceUpdated = "2025-10-04",
        note = "Slowdown plus skill-check pressure that fits Doctor's information/control style."
    ),
    KillerBuild(
        killerId = "the-pig",
        killerName = "The Pig",
        buildName = "Chase and slowdown",
        perks = listOf("Corrupt Intervention", "Scourge Hook: Pain Resonance", "Coup de Grace", "Bamboozle"),
        source = "Otzdarva recommended builds",
        sourceUpdated = "2025-10-04",
        note = "Gives Pig better chase threat while keeping hook-based slowdown."
    ),
    KillerBuild(
        killerId = "the-animatronic",
        killerName = "The Animatronic",
        buildName = "Generic build",
        perks = listOf("Scourge Hook: Pain Resonance", "Scourge Hook: Jagged Compass", "Scourge Hook: Weeping Wounds", "Hex: Ruin"),
        source = "Otzdarva recommended builds",
        sourceUpdated = "2025-10-04",
        note = "Scourge/hex-heavy pressure build from Otz's recommended list."
    )
)
