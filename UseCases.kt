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
    private val dbdRepository: DbdRepository
) {
    suspend operator fun invoke(): AppResult<Unit> = dbdRepository.syncBaseData()
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
                sTierCount = tiers.count { it.patchId == patchId && it.tier == Tier.S }
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

class GetPatchNotesUseCase(
    private val dbdRepository: DbdRepository
) {
    operator fun invoke(): Flow<List<Patch>> = dbdRepository.observePatches()
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
