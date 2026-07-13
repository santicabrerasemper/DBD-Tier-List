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
                    onOpenPatches = { navController.navigate("patches") },
                    onOpenAdmin = { navController.navigate("admin") }
                )
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
                TierListScreen(viewModel = vm, onBack = { navController.popBackStack() })
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
    onBack: () -> Unit
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
            }

            state.sections.forEach { section ->
                item { SectionHeader(section.tier, section.items.size) }
                items(section.items, key = { "${section.tier}-${it.id}" }) { item ->
                    TierItemRow(item)
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
