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
    val isLoading: Boolean = true
)

data class PatchesUiState(
    val patches: List<Patch> = emptyList(),
    val selectedPatch: Patch? = null,
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
    private val loading = MutableStateFlow(true)

    val uiState: StateFlow<TierListUiState> = combine(
        selectedPatchId,
        query,
        selectedPatchId.flatMapLatest { getTierList(category, it) },
        loading
    ) { patchId, search, sections, isLoading ->
        val filtered = if (search.isBlank()) {
            sections
        } else {
            sections.map { section ->
                section.copy(
                    items = section.items.filter {
                        it.title.contains(search, ignoreCase = true) ||
                            it.entry.reason.contains(search, ignoreCase = true) ||
                            it.entry.tags.any { tag -> tag.contains(search, ignoreCase = true) }
                    }
                )
            }
        }

        TierListUiState(
            selectedPatchId = patchId,
            category = category,
            sections = filtered,
            query = search,
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
}

class PatchesViewModel(
    private val initializeApp: InitializeAppUseCaseFacade,
    getPatchNotes: GetPatchNotesUseCase
) : ViewModel() {
    val uiState: StateFlow<PatchesUiState> = getPatchNotes()
        .map { patches ->
            PatchesUiState(
                patches = patches,
                selectedPatch = patches.firstOrNull(),
                isLoading = false
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PatchesUiState())

    init {
        viewModelScope.launch { initializeApp() }
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
