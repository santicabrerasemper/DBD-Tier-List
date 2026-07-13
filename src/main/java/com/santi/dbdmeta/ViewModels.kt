@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.santi.dbdmeta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val selectedPatchId: String = "10.0.0",
    val patches: List<Patch> = emptyList(),
    val summary: AppSummary = AppSummary(),
    val syncStatus: String? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

data class TierListUiState(
    val selectedPatchId: String = "10.0.0",
    val category: TierCategory = TierCategory.KILLERS,
    val sections: List<TierSection> = Tier.entries.map { TierSection(it, emptyList()) },
    val query: String = "",
    val needsReviewOnly: Boolean = false,
    val isLoading: Boolean = true
)

data class PatchesUiState(
    val patches: List<Patch> = emptyList(),
    val selectedPatch: Patch? = null,
    val changes: List<PatchChange> = emptyList(),
    val comparison: PatchComparison? = null,
    val isLoading: Boolean = true
)

data class DetailUiState(
    val selectedPatchId: String = "10.0.0",
    val category: TierCategory = TierCategory.KILLERS,
    val item: TierListItem? = null,
    val isLoading: Boolean = true
)

data class KillerBuildsUiState(
    val selectedPatchId: String = "10.0.0",
    val builds: List<KillerBuild> = emptyList(),
    val query: String = "",
    val sAndAOnly: Boolean = true,
    val isLoading: Boolean = true
)

data class AdminUiState(
    val selectedPatchId: String = "10.0.0",
    val sections: List<TierSection> = emptyList(),
    val message: String? = null
)

class HomeViewModel(
    private val initializeApp: InitializeAppUseCaseFacade,
    private val syncDbdBaseData: SyncDbdBaseDataUseCase,
    observePatches: ObservePatchesUseCase,
    observeSyncStatus: ObserveSyncStatusUseCase,
    getHomeSummary: GetHomeSummaryUseCase
) : ViewModel() {
    private val selectedPatchId = MutableStateFlow("10.0.0")
    private val loading = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)

    private val baseState = combine(
        selectedPatchId,
        observePatches(),
        observeSyncStatus(),
        selectedPatchId.flatMapLatest { getHomeSummary(it) },
        loading
    ) { patchId, patches, status, summary, isLoading ->
        HomeUiState(
            selectedPatchId = patchId,
            patches = patches,
            summary = summary,
            syncStatus = status,
            isLoading = isLoading,
            errorMessage = null
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(baseState, error) { state, message ->
        state.copy(errorMessage = message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        refresh()
        viewModelScope.launch {
            observePatches().collect { patches ->
                patches.firstOrNull()?.id?.let { latest ->
                    if (selectedPatchId.value == "10.0.0" && latest != "10.0.0") {
                        selectedPatchId.value = latest
                    }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            initializeApp()
            when (val result = syncDbdBaseData()) {
                is AppResult.Error -> error.value = result.message
                is AppResult.Success -> Unit
            }
            loading.value = false
        }
    }

    fun selectPatch(patchId: String) {
        selectedPatchId.value = patchId
    }
}

class TierListViewModel(
    private val initializeApp: InitializeAppUseCaseFacade,
    private val category: TierCategory,
    getTierList: GetTierListUseCase,
    observePatches: ObservePatchesUseCase
) : ViewModel() {
    private val selectedPatchId = MutableStateFlow("10.0.0")
    private val query = MutableStateFlow("")
    private val needsReviewOnly = MutableStateFlow(false)
    private val loading = MutableStateFlow(true)

    val uiState: StateFlow<TierListUiState> = combine(
        selectedPatchId,
        query,
        needsReviewOnly,
        selectedPatchId.flatMapLatest { getTierList(category, it) },
        loading
    ) { patchId, search, reviewOnly, sections, isLoading ->
        val filtered = sections.map { section ->
            section.copy(
                items = section.items.filter {
                    val matchesSearch = search.isBlank() ||
                        it.title.contains(search, ignoreCase = true) ||
                        it.entry.reason.contains(search, ignoreCase = true) ||
                        it.entry.tags.any { tag -> tag.contains(search, ignoreCase = true) }
                    val matchesReview = !reviewOnly || "needs-review" in it.entry.tags
                    matchesSearch && matchesReview
                }
            )
        }

        TierListUiState(
            selectedPatchId = patchId,
            category = category,
            sections = filtered,
            query = search,
            needsReviewOnly = reviewOnly,
            isLoading = isLoading
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TierListUiState(category = category)
    )

    init {
        viewModelScope.launch {
            loading.value = true
            initializeApp()
            loading.value = false
        }
        viewModelScope.launch {
            observePatches().collect { patches ->
                patches.firstOrNull()?.id?.let { latest ->
                    if (selectedPatchId.value == "10.0.0" && latest != "10.0.0") {
                        selectedPatchId.value = latest
                    }
                }
            }
        }
    }

    fun updateQuery(value: String) {
        query.value = value
    }

    fun toggleNeedsReviewOnly() {
        needsReviewOnly.value = !needsReviewOnly.value
    }
}

class PatchesViewModel(
    private val initializeApp: InitializeAppUseCaseFacade,
    getPatchNotes: GetPatchNotesUseCase,
    getPatchReview: GetPatchReviewUseCase,
    getPatchComparison: GetPatchComparisonUseCase
) : ViewModel() {
    val uiState: StateFlow<PatchesUiState> = combine(
        getPatchNotes(),
        getPatchReview(),
        getPatchComparison()
    ) { patches, changes, comparison ->
            PatchesUiState(
                patches = patches,
                selectedPatch = patches.firstOrNull(),
                changes = changes,
                comparison = comparison,
                isLoading = false
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PatchesUiState())

    init {
        viewModelScope.launch { initializeApp() }
    }
}

class DetailViewModel(
    private val initializeApp: InitializeAppUseCaseFacade,
    private val category: TierCategory,
    private val itemId: String,
    getTierItem: GetTierItemUseCase,
    observePatches: ObservePatchesUseCase
) : ViewModel() {
    private val selectedPatchId = MutableStateFlow("10.0.0")

    val uiState: StateFlow<DetailUiState> = combine(
        selectedPatchId,
        selectedPatchId.flatMapLatest { getTierItem(category, it, itemId) }
    ) { patchId, item ->
        DetailUiState(
            selectedPatchId = patchId,
            category = category,
            item = item,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState(category = category))

    init {
        viewModelScope.launch { initializeApp() }
        viewModelScope.launch {
            observePatches().collect { patches ->
                patches.firstOrNull()?.id?.let { latest ->
                    if (selectedPatchId.value == "10.0.0" && latest != "10.0.0") {
                        selectedPatchId.value = latest
                    }
                }
            }
        }
    }
}

class KillerBuildsViewModel(
    private val initializeApp: InitializeAppUseCaseFacade,
    observePatches: ObservePatchesUseCase,
    getKillerBuilds: GetKillerBuildsUseCase
) : ViewModel() {
    private val selectedPatchId = MutableStateFlow("10.0.0")
    private val query = MutableStateFlow("")
    private val sAndAOnly = MutableStateFlow(true)
    private val loading = MutableStateFlow(true)

    val uiState: StateFlow<KillerBuildsUiState> = combine(
        selectedPatchId,
        query,
        sAndAOnly,
        selectedPatchId.flatMapLatest { getKillerBuilds(it) },
        loading
    ) { patchId, search, highMetaOnly, builds, isLoading ->
        val filtered = builds.filter { build ->
            val matchesSearch = search.isBlank() ||
                build.killerName.contains(search, ignoreCase = true) ||
                build.buildName.contains(search, ignoreCase = true) ||
                build.perks.any { it.contains(search, ignoreCase = true) }
            val matchesTier = !highMetaOnly || build.metaTier == Tier.S || build.metaTier == Tier.A
            matchesSearch && matchesTier
        }
        KillerBuildsUiState(
            selectedPatchId = patchId,
            builds = filtered,
            query = search,
            sAndAOnly = highMetaOnly,
            isLoading = isLoading
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KillerBuildsUiState())

    init {
        viewModelScope.launch {
            loading.value = true
            initializeApp()
            loading.value = false
        }
        viewModelScope.launch {
            observePatches().collect { patches ->
                patches.firstOrNull()?.id?.let { latest ->
                    if (selectedPatchId.value == "10.0.0" && latest != "10.0.0") {
                        selectedPatchId.value = latest
                    }
                }
            }
        }
    }

    fun updateQuery(value: String) {
        query.value = value
    }

    fun toggleSAndAOnly() {
        sAndAOnly.value = !sAndAOnly.value
    }
}

class AdminViewModel(
    private val initializeApp: InitializeAppUseCaseFacade,
    private val updateTierEntry: UpdateTierEntryUseCase,
    getTierList: GetTierListUseCase
) : ViewModel() {
    private val selectedPatchId = MutableStateFlow("10.0.0")
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AdminUiState> = combine(
        selectedPatchId,
        selectedPatchId.flatMapLatest { getTierList(TierCategory.KILLERS, it) },
        message
    ) { patchId, sections, msg ->
        AdminUiState(patchId, sections, msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminUiState())

    init {
        viewModelScope.launch { initializeApp() }
    }

    fun setTier(item: TierListItem, tier: Tier) {
        viewModelScope.launch {
            updateTierEntry(
                item.entry.copy(
                    tier = tier,
                    score = tier.defaultScore(),
                    confidence = Confidence.MEDIUM,
                    reason = item.entry.reason.ifBlank { "Ajuste manual." },
                    manual = true
                )
            )
            message.value = "${item.title} actualizado a $tier"
        }
    }
}

fun Tier.defaultScore(): Double = when (this) {
    Tier.S -> 92.0
    Tier.A -> 82.0
    Tier.B -> 68.0
    Tier.C -> 50.0
    Tier.D -> 30.0
}

class AppViewModelFactory<T : ViewModel>(
    private val create: () -> T
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
}
