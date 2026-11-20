package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.VideoWithChannel
import com.example.data.model.*
import com.example.ui.components.ChannelAvatar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.VideoCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    onWatchVideo: (VideoWithChannel) -> Unit,
    onOpenChannel: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val filter by viewModel.searchFilter.collectAsStateWithLifecycle()
    val results by viewModel.searchResultsVideos.collectAsStateWithLifecycle()
    val channels by viewModel.activeChannels.collectAsStateWithLifecycle()
    val playlists by viewModel.publicPlaylists.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val history by viewModel.searchHistory.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    var queryInput by remember { mutableStateOf(filter.query) }
    var showFilters by remember { mutableStateOf(false) }

    // Automatically trigger live world YouTube + Archive search when user pauses typing
    LaunchedEffect(queryInput) {
        val clean = queryInput.trim()
        if (clean.length >= 3) {
            kotlinx.coroutines.delay(550L)
            viewModel.executeWorldSearch(clean, saveHistory = false)
        }
    }

    val matchingChannels = remember(channels, filter.query) {
        val q = filter.query.trim()
        if (q.isBlank()) {
            channels.take(8)
        } else {
            channels.filter { ch ->
                matchesWorldQuery(q, ch.name, ch.handle, ch.description, ch.country, ch.sourceName)
            }
        }
    }

    val matchingPlaylists = remember(playlists, filter.query) {
        val q = filter.query.trim()
        if (q.isBlank()) emptyList()
        else playlists.filter { matchesWorldQuery(q, it.title, it.description) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeiPlayBlack)
            .statusBarsPadding()
    ) {
        // Search Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = DeiPlayWhite)
            }
            OutlinedTextField(
                value = queryInput,
                onValueChange = {
                    queryInput = it
                    viewModel.updateSearchFilter { f -> f.copy(query = it) }
                },
                placeholder = { Text("Buscar videos, Shorts, canales, países...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.executeWorldSearch(queryInput) }),
                trailingIcon = {
                    if (queryInput.isNotBlank()) {
                        IconButton(onClick = {
                            queryInput = ""
                            viewModel.updateSearchFilter { it.copy(query = "") }
                        }) {
                            Icon(Icons.Filled.Clear, "Limpiar", tint = DeiPlayTextSecondary)
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_input_field")
            )
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = { viewModel.executeWorldSearch(queryInput) },
                modifier = Modifier.testTag("search_execute_button")
            ) {
                Icon(Icons.Filled.Search, "Buscar en todo el mundo", tint = DeiPlayRed)
            }
            IconButton(onClick = { showFilters = !showFilters }) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = "Filtros",
                    tint = if (showFilters) DeiPlayGold else DeiPlayWhite
                )
            }
        }

        if (showFilters) {
            SearchFiltersPanel(
                filter = filter,
                categories = categories.map { it.name },
                onUpdate = { viewModel.updateSearchFilter(it) }
            )
        }

        if (isSyncing) {
            LinearProgressIndicator(color = DeiPlayRed, modifier = Modifier.fillMaxWidth())
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Recent searches & suggestions
            if (history.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Búsquedas recientes", style = MaterialTheme.typography.labelLarge, color = DeiPlayTextSecondary, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.clearSearchHistory() }) {
                            Text("Borrar", color = DeiPlayGold)
                        }
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(history, key = { it.query }) { item ->
                            AssistChip(
                                onClick = {
                                    queryInput = item.query
                                    viewModel.executeWorldSearch(item.query)
                                },
                                leadingIcon = { Icon(Icons.Outlined.History, null, tint = DeiPlayTextMuted, modifier = Modifier.size(16.dp)) },
                                label = { Text(item.query, color = DeiPlayWhite) }
                            )
                        }
                    }
                }
            }

            // World topic & famous creator suggestions
            if (queryInput.isBlank()) {
                item {
                    val suggestions = listOf(
                        "MrBeast", "Fede Vigevani", "Alejo Igoa", "Alfredo Larin",
                        "Karla Bustillos", "Cachetes", "ElRubius", "Mikecrack",
                        "Ibai", "Kurzgesagt", "Música", "Gaming"
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(suggestions) { sug ->
                            SuggestionChip(
                                onClick = {
                                    queryInput = sug
                                    viewModel.executeWorldSearch(sug)
                                },
                                label = { Text(sug, color = DeiPlayGold) }
                            )
                        }
                    }
                }
            }

            // Matching Channels
            if (matchingChannels.isNotEmpty()) {
                item {
                    SectionHeader(
                        icon = Icons.Outlined.AccountCircle,
                        iconTint = DeiPlayGold,
                        title = if (queryInput.isBlank()) "Canales famosos del mundo (${matchingChannels.size})" else "Canales y Creadores (${matchingChannels.size})",
                        actionLabel = null,
                        onAction = null
                    )
                }
                items(matchingChannels, key = { "sch_${it.id}" }) { ch ->
                    ListItem(
                        leadingContent = { ChannelAvatar(ch.avatarUrl, ch.name, 48.dp) },
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(ch.name, color = DeiPlayWhite)
                                if (ch.isVerified) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Filled.Verified, "Verificado", tint = DeiPlayGold, modifier = Modifier.size(15.dp))
                                }
                            }
                        },
                        supportingContent = {
                            val subPart = if (ch.subscriberCount > 0L) " • ${formatViewCount(ch.subscriberCount)} suscriptores" else ""
                            Text("${ch.handle} • ${ch.sourceName} • ${ch.country}$subPart", color = DeiPlayTextSecondary)
                        },
                        trailingContent = {
                            Icon(Icons.Filled.ChevronRight, null, tint = DeiPlayGold)
                        },
                        colors = ListItemDefaults.colors(containerColor = DeiPlayBlack),
                        modifier = Modifier
                            .clickable { onOpenChannel(ch.id) }
                            .testTag("search_channel_${ch.id}")
                    )
                }
            }

            // Matching Playlists
            if (matchingPlaylists.isNotEmpty()) {
                item {
                    SectionHeader(Icons.Outlined.PlaylistPlay, DeiPlayInfo, "Listas públicas", null, null)
                }
                items(matchingPlaylists, key = { "spl_${it.id}" }) { pl ->
                    ListItem(
                        leadingContent = { Icon(Icons.Outlined.PlaylistPlay, null, tint = DeiPlayGold) },
                        headlineContent = { Text(pl.title, color = DeiPlayWhite) },
                        supportingContent = { Text(pl.description.ifBlank { "Lista de reproducción" }, color = DeiPlayTextSecondary) },
                        colors = ListItemDefaults.colors(containerColor = DeiPlayBlack)
                    )
                }
            }

            // Video & Short Results
            item {
                SectionHeader(
                    icon = Icons.Outlined.VideoLibrary,
                    iconTint = DeiPlayRed,
                    title = "Resultados (${results.size})",
                    actionLabel = null,
                    onAction = null
                )
            }

            if (results.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Outlined.SearchOff,
                        title = if (isSyncing) "Buscando en YouTube y el mundo..." else "Sin coincidencias locales",
                        subtitle = "Pulsa el botón inferior para buscar cualquier canal o video mundial en vivo desde YouTube e Internet Archive.",
                        actionLabel = if (queryInput.isNotBlank() && !isSyncing) "Buscar '$queryInput' en YouTube y el mundo" else null,
                        onAction = if (queryInput.isNotBlank() && !isSyncing) { { viewModel.executeWorldSearch(queryInput) } } else null
                    )
                }
            } else {
                items(results, key = { "sres_${it.video.id}" }) { item ->
                    VideoCard(
                        item = item,
                        onClick = { onWatchVideo(item) },
                        onChannelClick = onOpenChannel,
                        onSaveWatchLater = { viewModel.toggleWatchLater(item.video.id, false) },
                        onShare = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, "Mira '${item.video.title}' en DeiPlay")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Compartir"))
                        },
                        onReport = { viewModel.submitReport("VIDEO", item.video.id, item.video.title, "Reportado desde búsqueda") },
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchFiltersPanel(
    filter: SearchFilter,
    categories: List<String>,
    onUpdate: ((SearchFilter) -> SearchFilter) -> Unit
) {
    Surface(
        color = DeiPlaySurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Filtros mundiales", style = MaterialTheme.typography.titleSmall, color = DeiPlayGold)

            // Sort chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SearchSort.entries) { sort ->
                    FilterChip(
                        selected = filter.sort == sort,
                        onClick = { onUpdate { it.copy(sort = sort) } },
                        label = { Text(sort.label) }
                    )
                }
            }

            // Duration & Date
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DurationFilter.entries) { dur ->
                    FilterChip(
                        selected = filter.durationFilter == dur,
                        onClick = { onUpdate { it.copy(durationFilter = dur) } },
                        label = { Text(dur.label) }
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DateFilter.entries) { df ->
                    FilterChip(
                        selected = filter.dateFilter == df,
                        onClick = { onUpdate { it.copy(dateFilter = df) } },
                        label = { Text(df.label) }
                    )
                }
            }

            // Category / Country / Language quick chips
            val cats = if (categories.isEmpty()) listOf("Todos") else categories
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cats) { cat ->
                    FilterChip(
                        selected = filter.category == cat,
                        onClick = { onUpdate { it.copy(category = cat) } },
                        label = { Text(cat) }
                    )
                }
            }
        }
    }
}
