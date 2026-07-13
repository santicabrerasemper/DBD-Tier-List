package com.santi.dbdmeta

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun DbdMetaApp(container: AppContainer) {
    val navController = rememberNavController()
    DbdMetaTheme {
        NavHost(navController = navController, startDestination = "home") {
            composable("home") {
                val vm: HomeViewModel = viewModel(factory = AppViewModelFactory { container.homeViewModel() })
                HomeScreen(
                    viewModel = vm,
                    onOpenTier = { navController.navigate("tiers/${it.name}") },
                    onOpenBuilds = { navController.navigate("killer-builds") },
                    onOpenPatches = { navController.navigate("patches") },
                    onOpenAdmin = { navController.navigate("admin") }
                )
            }
            composable("killer-builds") {
                val vm: KillerBuildsViewModel = viewModel(factory = AppViewModelFactory { container.killerBuildsViewModel() })
                KillerBuildsScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(
                route = "tiers/{category}",
                arguments = listOf(navArgument("category") { type = NavType.StringType })
            ) { entry ->
                val category = entry.arguments?.getString("category")
                    ?.let { runCatching { TierCategory.valueOf(it) }.getOrNull() }
                    ?: TierCategory.KILLERS
                val vm: TierListViewModel = viewModel(
                    key = "tier-${category.name}",
                    factory = AppViewModelFactory { container.tierListViewModel(category) }
                )
                TierListScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onOpenDetail = { item ->
                        navController.navigate("detail/${category.name}/${item.id}")
                    }
                )
            }
            composable(
                route = "detail/{category}/{itemId}",
                arguments = listOf(
                    navArgument("category") { type = NavType.StringType },
                    navArgument("itemId") { type = NavType.StringType }
                )
            ) { entry ->
                val category = entry.arguments?.getString("category")
                    ?.let { runCatching { TierCategory.valueOf(it) }.getOrNull() }
                    ?: TierCategory.KILLERS
                val itemId = entry.arguments?.getString("itemId").orEmpty()
                val vm: DetailViewModel = viewModel(
                    key = "detail-${category.name}-$itemId",
                    factory = AppViewModelFactory { container.detailViewModel(category, itemId) }
                )
                DetailScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable("patches") {
                val vm: PatchesViewModel = viewModel(factory = AppViewModelFactory { container.patchesViewModel() })
                PatchesScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable("admin") {
                val vm: AdminViewModel = viewModel(factory = AppViewModelFactory { container.adminViewModel() })
                AdminScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenTier: (TierCategory) -> Unit,
    onOpenBuilds: () -> Unit,
    onOpenPatches: () -> Unit,
    onOpenAdmin: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "DBD Tier List",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Tier lists personales por parche",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            item {
                Text(
                    text = "Patch ${state.selectedPatchId}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = state.syncStatus ?: if (state.isLoading) "Inicializando..." else "Cache listo",
                    style = MaterialTheme.typography.bodySmall
                )
                state.errorMessage?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.primary)
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::refresh) {
                        Text("Refresh data")
                    }
                    TextButton(onClick = onOpenPatches) {
                        Text("Patches")
                    }
                    TextButton(onClick = onOpenAdmin) {
                        Text("Admin")
                    }
                }
            }

            item {
                SummaryGrid(state.summary)
            }

            item {
                Button(
                    onClick = onOpenBuilds,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Killer meta builds")
                }
            }

            if (state.summary.needsReviewCount > 0) {
                item {
                    androidx.compose.material3.Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "${state.summary.needsReviewCount} tiers necesitan review",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Se copiaron desde el parche anterior. Abri Patches para ver cambios detectados.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(onClick = onOpenPatches) {
                                Text("Review patch")
                            }
                        }
                    }
                }
            }

            items(TierCategory.entries) { category ->
                Button(
                    onClick = { onOpenTier(category) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(category.title)
                }
            }
        }
    }
}

@Composable
private fun SummaryGrid(summary: AppSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCell("Killers", summary.killerCount.toString(), Modifier.weight(1f))
            SummaryCell("Killer perks", summary.killerPerkCount.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCell("Survivor perks", summary.survivorPerkCount.toString(), Modifier.weight(1f))
            SummaryCell("S tier curated", summary.sTierCount.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCell("Patches", summary.patchCount.toString(), Modifier.weight(1f))
            SummaryCell("Needs review", summary.needsReviewCount.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Card(modifier = modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(text = label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun TierListScreen(
    viewModel: TierListViewModel,
    onBack: () -> Unit,
    onOpenDetail: (TierListItem) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                TextButton(onClick = onBack) { Text("Back") }
                Text(
                    text = state.category.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Patch ${state.selectedPatchId}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::updateQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search") }
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.needsReviewOnly,
                        onClick = viewModel::toggleNeedsReviewOnly,
                        label = { Text("Needs review") }
                    )
                }
            }

            state.sections.forEach { section ->
                item { SectionHeader(section.tier, section.items.size) }
                items(section.items, key = { "${section.tier}-${it.id}" }) { item ->
                    TierItemRow(item, onClick = { onOpenDetail(item) })
                }
            }
        }
    }
}

@Composable
fun KillerBuildsScreen(
    viewModel: KillerBuildsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                TextButton(onClick = onBack) { Text("Back") }
                Text(
                    text = "Killer meta builds",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Patch ${state.selectedPatchId}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::updateQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search killer or perk") }
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.sAndAOnly,
                        onClick = viewModel::toggleSAndAOnly,
                        label = { Text("S/A meta only") }
                    )
                }
            }

            item {
                Text(
                    text = "${state.builds.size} builds",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(state.builds, key = { "${it.killerId}-${it.buildName}" }) { build ->
                KillerBuildCard(build)
            }
        }
    }
}

@Composable
private fun KillerBuildCard(build: KillerBuild) {
    androidx.compose.material3.Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetaTagChip("Tier ${build.metaTier?.name ?: "?"}")
                MetaTagChip("Score ${build.killerScore.toInt()}")
                MetaTagChip(build.sourceUpdated)
            }
            Text(
                text = build.killerName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
            Text(
                text = build.buildName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            build.perks.forEach { perk ->
                Text(
                    text = "- $perk",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = build.note,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Source: ${build.source}",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val item = state.item

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TextButton(onClick = onBack) { Text("Back") }
                Text(
                    text = item?.title ?: "Detail",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "${state.category.title} - Patch ${state.selectedPatchId}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (item != null) {
                item {
                    TierItemRow(item)
                }
                item {
                    androidx.compose.material3.Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Score explanation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = "El score es una guia interna de 0 a 100. Combina tier editorial, fuente usada, impacto del parche y ajustes manuales. Suele mapearse asi: S 90+, A 75-89, B 60-74, C 40-59, D menos de 40.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text("Current score: ${item.entry.score.toInt()}", style = MaterialTheme.typography.bodySmall)
                            Text("Source: ${item.entry.sourceLabel()}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    androidx.compose.material3.Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Patch movement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Previous tier: ${item.entry.previousTier?.name ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
                            Text("Direction: ${item.entry.changeDirection.name.lowercase()}", style = MaterialTheme.typography.bodySmall)
                            Text("Confidence: ${item.entry.confidence.name.lowercase()}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                item {
                    Text("No encontre este item para el parche actual.")
                }
            }
        }
    }
}

@Composable
fun PatchesScreen(
    viewModel: PatchesViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TextButton(onClick = onBack) { Text("Back") }
                Text(
                    text = "Patches",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
            }
            state.comparison?.let { comparison ->
                item {
                    androidx.compose.material3.Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Patch compare",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${comparison.fromPatchId} -> ${comparison.toPatchId}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetaTagChip("up ${comparison.upCount}")
                                MetaTagChip("down ${comparison.downCount}")
                                MetaTagChip("same ${comparison.sameCount}")
                                MetaTagChip("review ${comparison.needsReviewCount}")
                            }
                        }
                    }
                }
            }
            if (state.changes.isNotEmpty()) {
                item {
                    Text(
                        text = "Review needed - ${state.selectedPatch?.id.orEmpty()}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(state.changes, key = { "${it.patchId}-${it.entityType}-${it.entityId}" }) { change ->
                    androidx.compose.material3.Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetaTagChip(change.entityType.name.lowercase().replace('_', '-'))
                                MetaTagChip(change.direction.name.lowercase())
                                MetaTagChip(change.confidence.name.lowercase())
                            }
                            Text(
                                text = change.entityName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = change.summary,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                item {
                    Text(
                        text = "Patch notes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            items(state.patches.take(20), key = { it.id }) { patch ->
                androidx.compose.material3.Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = patch.id, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(text = patch.type.name, style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = patch.notesHtml.stripHtml().take(420).ifBlank { "Sin notas cacheadas todavia." },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdminScreen(
    viewModel: AdminViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                TextButton(onClick = onBack) { Text("Back") }
                Text(
                    text = "Admin local",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Edita rapido tiers de killers para Patch ${state.selectedPatchId}",
                    style = MaterialTheme.typography.bodySmall
                )
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
            }

            state.sections.forEach { section ->
                val visible = section.items.take(8)
                if (visible.isNotEmpty()) {
                    item { SectionHeader(section.tier, section.items.size) }
                    items(visible, key = { "admin-${it.id}" }) { item ->
                        TierItemRow(
                            item = item,
                            trailing = {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Tier.entries.forEach { tier ->
                                        FilterChip(
                                            selected = item.entry.tier == tier,
                                            onClick = { viewModel.setTier(item, tier) },
                                            label = { Text(tier.name) }
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
