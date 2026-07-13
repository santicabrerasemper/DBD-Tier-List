package com.santi.dbdmeta

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }
    private val initMutex = Mutex()
    private var initialized = false

    private val dbdRepository: DbdRepository = DbdRepositoryImpl(
        context = appContext,
        client = DbdHttpClient(),
        json = json
    )

    private val metaRepository: MetaRepository = MetaRepositoryImpl(
        context = appContext,
        json = json
    )

    val initializeApp = InitializeAppUseCase(dbdRepository, metaRepository)
    val syncDbdBaseData = SyncDbdBaseDataUseCase(dbdRepository, metaRepository)
    val observePatches = ObservePatchesUseCase(dbdRepository)
    val observeSyncStatus = ObserveSyncStatusUseCase(dbdRepository)
    val getHomeSummary = GetHomeSummaryUseCase(dbdRepository, metaRepository)
    val getTierList = GetTierListUseCase(dbdRepository, metaRepository)
    val getTierItem = GetTierItemUseCase(getTierList)
    val getPatchNotes = GetPatchNotesUseCase(dbdRepository)
    val getPatchReview = GetPatchReviewUseCase(dbdRepository)
    val getPatchComparison = GetPatchComparisonUseCase(dbdRepository, metaRepository)
    val getKillerBuilds = GetKillerBuildsUseCase(getTierList)
    val updateTierEntry = UpdateTierEntryUseCase(metaRepository)

    suspend fun initializeOnce() {
        initMutex.withLock {
            if (!initialized) {
                initializeApp()
                initialized = true
            }
        }
    }

    fun homeViewModel(): HomeViewModel =
        HomeViewModel(
            initializeApp = InitializeAppUseCaseOnce(this),
            syncDbdBaseData = syncDbdBaseData,
            observePatches = observePatches,
            observeSyncStatus = observeSyncStatus,
            getHomeSummary = getHomeSummary
        )

    fun tierListViewModel(category: TierCategory): TierListViewModel =
        TierListViewModel(
            initializeApp = InitializeAppUseCaseOnce(this),
            category = category,
            getTierList = getTierList,
            observePatches = observePatches
        )

    fun patchesViewModel(): PatchesViewModel =
        PatchesViewModel(
            initializeApp = InitializeAppUseCaseOnce(this),
            getPatchNotes = getPatchNotes,
            getPatchReview = getPatchReview,
            getPatchComparison = getPatchComparison
        )

    fun adminViewModel(): AdminViewModel =
        AdminViewModel(
            initializeApp = InitializeAppUseCaseOnce(this),
            updateTierEntry = updateTierEntry,
            getTierList = getTierList
        )

    fun detailViewModel(category: TierCategory, itemId: String): DetailViewModel =
        DetailViewModel(
            initializeApp = InitializeAppUseCaseOnce(this),
            category = category,
            itemId = itemId,
            getTierItem = getTierItem,
            observePatches = observePatches
        )

    fun killerBuildsViewModel(): KillerBuildsViewModel =
        KillerBuildsViewModel(
            initializeApp = InitializeAppUseCaseOnce(this),
            observePatches = observePatches,
            getKillerBuilds = getKillerBuilds
        )
}

class InitializeAppUseCaseOnce(
    private val container: AppContainer
) : InitializeAppUseCaseFacade {
    override suspend operator fun invoke() {
        container.initializeOnce()
    }
}

fun interface InitializeAppUseCaseFacade {
    suspend operator fun invoke()
}
