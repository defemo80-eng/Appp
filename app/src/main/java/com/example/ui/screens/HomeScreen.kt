package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.AccountEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.ChannelEntity
import com.example.data.local.VideoWithChannel
import com.example.data.model.VideoSourceType
import com.example.data.model.formatDuration
import com.example.data.model.formatViewCount
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import java.io.File

@Composable
fun HomeTopBar(
    activeAccount: AccountEntity?,
    unreadNotifications: Int,
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenPublish: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenProfileOrAuth: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onOpenDrawer,
                modifier = Modifier.testTag("home_menu_button")
            ) {
                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.cd_menu), tint = DeiPlayWhite)
            }

            DeiPlayOfficialLogo(size = 38.dp)

            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Dei",
                        style = MaterialTheme.typography.titleLarge,
                        color = DeiPlayWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Play",
                        style = MaterialTheme.typography.titleLarge,
                        color = DeiPlayRed,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = stringResource(R.string.slogan),
                    style = MaterialTheme.typography.labelSmall,
                    color = DeiPlayGold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onOpenSearch,
                modifier = Modifier.testTag("home_search_button")
            ) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.cd_search), tint = DeiPlayWhite)
            }

            IconButton(
                onClick = onOpenPublish,
                modifier = Modifier.testTag("home_create_button")
            ) {
                Icon(Icons.Outlined.VideoCall, contentDescription = "Crear video", tint = DeiPlayWhite)
            }

            BadgedBox(
                badge = {
                    if (unreadNotifications > 0) {
                        Badge(containerColor = DeiPlayRed) {
                            Text(
                                text = if (unreadNotifications > 9) "9+" else unreadNotifications.toString(),
                                color = DeiPlayWhite
                            )
                        }
                    }
                }
            ) {
                IconButton(
                    onClick = onOpenNotifications,
                    modifier = Modifier.testTag("home_notifications_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = stringResource(R.string.cd_notifications),
                        tint = DeiPlayWhite
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .clickable(onClick = onOpenProfileOrAuth)
                    .padding(4.dp)
                    .testTag("home_profile_button")
            ) {
                if (activeAccount != null) {
                    ChannelAvatar(
                        avatarUrl = activeAccount.avatarUri,
                        name = activeAccount.username,
                        size = 34.dp
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = DeiPlayRed,
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Person, null, tint = DeiPlayWhite, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Entrar", style = MaterialTheme.typography.labelMedium, color = DeiPlayWhite)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    categories: List<CategoryEntity>,
    videos: List<VideoWithChannel>,
    shorts: List<VideoWithChannel>,
    channels: List<ChannelEntity>,
    activeAccount: AccountEntity?,
    selectedCategory: String,
    selectedCountry: String,
    selectedLanguage: String,
    isSyncing: Boolean,
    syncError: String?,
    onWatchVideo: (VideoWithChannel) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenShorts: (String?) -> Unit,
    onOpenSearch: () -> Unit
) {
    val context = LocalContext.current
    val subscriptions by viewModel.userSubscriptions.collectAsState()
    val watchLaterIds by viewModel.watchLaterIds.collectAsState()
    val subscribedChannelIds = remember(subscriptions) { subscriptions.map { it.channelId }.toSet() }
    val subscriptionVideos = remember(videos, subscribedChannelIds) {
        videos.filter { it.video.channelId in subscribedChannelIds }
    }
    val trendingVideos = remember(videos) {
        videos.sortedByDescending { it.video.viewCount }.take(10)
    }
    val channelPublications = remember(videos) {
        videos.filter { it.video.sourceType == VideoSourceType.DEIPLAY_USER.name }
    }

    var reportTarget by remember { mutableStateOf<VideoWithChannel?>(null) }

    val countries = listOf("Todos", "Global", "México", "Argentina", "España", "Colombia", "El Salvador", "EE.UU.", "Brasil", "Japón")
    val languages = listOf("Todos", "Español", "English", "Português", "Français", "日本語", "Global")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // Categories row
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val cats = if (categories.isEmpty()) listOf(CategoryEntity("Todos")) else categories.filter { it.isActive }
                items(cats, key = { it.name }) { cat ->
                    val selected = cat.name == selectedCategory
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.setHomeCategory(cat.name) },
                        label = {
                            Text(
                                text = cat.name,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DeiPlayWhite,
                            selectedLabelColor = DeiPlayBlack,
                            containerColor = DeiPlaySurfaceVariant,
                            labelColor = DeiPlayWhite
                        ),
                        border = null
                    )
                }
            }
        }

        // World Country & Language filters
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var countryMenu by remember { mutableStateOf(false) }
                var langMenu by remember { mutableStateOf(false) }

                Box {
                    AssistChip(
                        onClick = { countryMenu = true },
                        leadingIcon = { Icon(Icons.Outlined.Public, null, tint = DeiPlayGold, modifier = Modifier.size(16.dp)) },
                        label = { Text("País: $selectedCountry", color = DeiPlayWhite) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = DeiPlaySurface)
                    )
                    DropdownMenu(
                        expanded = countryMenu,
                        onDismissRequest = { countryMenu = false },
                        containerColor = DeiPlaySurfaceHigh
                    ) {
                        countries.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c, color = DeiPlayWhite) },
                                onClick = {
                                    viewModel.setHomeCountry(c)
                                    countryMenu = false
                                }
                            )
                        }
                    }
                }

                Box {
                    AssistChip(
                        onClick = { langMenu = true },
                        leadingIcon = { Icon(Icons.Outlined.Translate, null, tint = DeiPlayInfo, modifier = Modifier.size(16.dp)) },
                        label = { Text("Idioma: $selectedLanguage", color = DeiPlayWhite) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = DeiPlaySurface)
                    )
                    DropdownMenu(
                        expanded = langMenu,
                        onDismissRequest = { langMenu = false },
                        containerColor = DeiPlaySurfaceHigh
                    ) {
                        languages.forEach { l ->
                            DropdownMenuItem(
                                text = { Text(l, color = DeiPlayWhite) },
                                onClick = {
                                    viewModel.setHomeLanguage(l)
                                    langMenu = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                IconButton(onClick = { viewModel.refreshWorldCatalog() }) {
                    if (isSyncing) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = DeiPlayRed, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Refresh, "Actualizar catálogo mundial", tint = DeiPlayTextSecondary)
                    }
                }
            }
        }

        // Sync error banner (transparent real network feedback)
        if (syncError != null) {
            item {
                Surface(
                    color = DeiPlaySurfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DeiPlayRed.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.WifiOff, null, tint = DeiPlayRedBright)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Sin conexión al catálogo mundial ($syncError)",
                            style = MaterialTheme.typography.bodySmall,
                            color = DeiPlayWhite,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.refreshWorldCatalog() }) {
                            Text("Reintentar", color = DeiPlayGold)
                        }
                    }
                }
            }
        }

        // Featured Hero Banner Carousel
        if (trendingVideos.isNotEmpty()) {
            item {
                val heroItems = trendingVideos.take(4)
                val pagerState = rememberPagerState(pageCount = { heroItems.size })
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        pageSpacing = 12.dp
                    ) { page ->
                        val hero = heroItems[page]
                        FeaturedHeroCard(
                            item = hero,
                            onPlay = { onWatchVideo(hero) }
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(heroItems.size) { idx ->
                            val active = pagerState.currentPage == idx
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(if (active) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(if (active) DeiPlayRed else DeiPlayTextMuted)
                            )
                        }
                    }
                }
            }
        }

        // Subscribed channels feed (if any)
        if (subscriptionVideos.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = Icons.Filled.Subscriptions,
                    iconTint = DeiPlayRed,
                    title = "De tus suscripciones",
                    actionLabel = null,
                    onAction = null
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(subscriptionVideos.take(10), key = { "sub_${it.video.id}" }) { item ->
                        CompactHorizontalVideoCard(item = item, onClick = { onWatchVideo(item) })
                    }
                }
            }
        }

        // Community channel publications (every upload from any DeiPlay channel)
        if (channelPublications.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = Icons.Filled.Campaign,
                    iconTint = DeiPlayGold,
                    title = "Publicaciones de canales DeiPlay",
                    actionLabel = null,
                    onAction = null
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(channelPublications, key = { "dei_pub_${it.video.id}" }) { item ->
                        CompactHorizontalVideoCard(item = item, onClick = { onWatchVideo(item) })
                    }
                }
            }
        }

        // Tendencias Mundiales
        if (trendingVideos.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = Icons.Filled.LocalFireDepartment,
                    iconTint = DeiPlayRed,
                    title = "Tendencias Mundiales",
                    actionLabel = "Explorar",
                    onAction = onOpenSearch
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(trendingVideos, key = { "trend_${it.video.id}" }) { item ->
                        CompactHorizontalVideoCard(item = item, onClick = { onWatchVideo(item) })
                    }
                }
            }
        }

        // Shorts strip (9:16 vertical cards)
        val shortsPreview = shorts.ifEmpty { videos.filter { it.video.isShort } }
        if (shortsPreview.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = Icons.Filled.Bolt,
                    iconTint = DeiPlayRedBright,
                    title = "Shorts",
                    actionLabel = "Ver todo",
                    onAction = { onOpenShorts(null) }
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(shortsPreview.take(12), key = { "short_strip_${it.video.id}" }) { item ->
                        ShortGridCard(
                            item = item,
                            onClick = { onOpenShorts(item.video.id) },
                            modifier = Modifier.width(136.dp)
                        )
                    }
                }
            }
        }

        // Recommended International & Famous YouTube Channels
        if (channels.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = Icons.Outlined.Public,
                    iconTint = DeiPlayGold,
                    title = "Canales Famosos y Mundiales",
                    actionLabel = "Buscar todos",
                    onAction = onOpenSearch
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(channels.take(20), key = { "ch_rec_${it.id}" }) { ch ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = DeiPlaySurfaceVariant,
                            border = BorderStroke(0.75.dp, DeiPlayOutline),
                            modifier = Modifier
                                .width(156.dp)
                                .clickable { onOpenChannel(ch.id) }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ChannelAvatar(avatarUrl = ch.avatarUrl, name = ch.name, size = 54.dp)
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = ch.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = DeiPlayWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (ch.isVerified) {
                                        Spacer(Modifier.width(3.dp))
                                        Icon(Icons.Filled.Verified, "Verificado", tint = DeiPlayGold, modifier = Modifier.size(13.dp))
                                    }
                                }
                                Text(
                                    text = if (ch.subscriberCount > 0L) "${formatViewCount(ch.subscriberCount)} suscriptores" else ch.handle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DeiPlayTextMuted,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${ch.sourceName} • ${ch.country}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DeiPlayGold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Main International & Recent Feed
        item {
            SectionHeader(
                icon = Icons.Outlined.Explore,
                iconTint = DeiPlayGold,
                title = "Explorar Mundo • Sin Anuncios",
                actionLabel = null,
                onAction = null
            )
        }

        if (videos.isEmpty() && !isSyncing) {
            item {
                EmptyStateView(
                    icon = Icons.Outlined.VideoLibrary,
                    title = "Catálogo mundial vacío",
                    subtitle = "No hay videos con los filtros actuales o aún no se sincronizan YouTube e Internet Archive.",
                    actionLabel = "Sincronizar ahora",
                    onAction = { viewModel.refreshWorldCatalog() }
                )
            }
        } else if (videos.isEmpty() && isSyncing) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = DeiPlayRed)
                        Spacer(Modifier.height(12.dp))
                        Text("Conectando catálogo mundial autorizado...", color = DeiPlayTextSecondary)
                    }
                }
            }
        } else {
            items(videos, key = { it.video.id }) { item ->
                val isSaved = item.video.id in watchLaterIds
                VideoCard(
                    item = item,
                    onClick = { onWatchVideo(item) },
                    onChannelClick = onOpenChannel,
                    onSaveWatchLater = { viewModel.toggleWatchLater(item.video.id, isSaved) },
                    onShare = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_TEXT, "Mira '${item.video.title}' en DeiPlay (${item.video.primaryVideoUrl})")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Compartir video"))
                    },
                    onReport = { reportTarget = item },
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }
        }
    }

    reportTarget?.let { target ->
        ReportContentDialog(
            title = target.video.title,
            onConfirm = { reason ->
                viewModel.submitReport("VIDEO", target.video.id, target.video.title, reason)
                reportTarget = null
            },
            onDismiss = { reportTarget = null }
        )
    }
}

@Composable
private fun FeaturedHeroCard(
    item: VideoWithChannel,
    onPlay: () -> Unit
) {
    val thumbModel: Any? = if (item.video.thumbnailUrl.startsWith("/")) {
        File(item.video.thumbnailUrl)
    } else item.video.thumbnailUrl

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = DeiPlaySurfaceVariant,
        border = BorderStroke(1.dp, DeiPlayRed.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clickable(onClick = onPlay)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = thumbModel,
                contentDescription = item.video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.90f),
                                Color.Black.copy(alpha = 0.55f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(0.76f)
                    .padding(16.dp)
            ) {
                Surface(
                    color = DeiPlayGold.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "SIN ANUNCIOS • ${item.video.sourceAttribution.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = DeiPlayGold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = item.video.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = DeiPlayWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${item.channel?.name ?: item.video.sourceAttribution} • ${formatViewCount(item.video.viewCount)} vistas",
                    style = MaterialTheme.typography.bodySmall,
                    color = DeiPlayTextSecondary,
                    maxLines = 1
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = DeiPlayRed),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = DeiPlayWhite, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reproducir", color = DeiPlayWhite, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun CompactHorizontalVideoCard(
    item: VideoWithChannel,
    onClick: () -> Unit
) {
    val thumbModel: Any? = if (item.video.thumbnailUrl.startsWith("/")) {
        File(item.video.thumbnailUrl)
    } else item.video.thumbnailUrl

    Column(
        modifier = Modifier
            .width(210.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(DeiPlaySurfaceVariant)
        ) {
            AsyncImage(
                model = thumbModel,
                contentDescription = item.video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (item.video.durationSeconds > 0L || item.video.isShort) {
                Surface(
                    color = if (item.video.isShort) DeiPlayRed.copy(alpha = 0.90f) else Color.Black.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(5.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = if (item.video.isShort) "SHORTS" else formatDuration(item.video.durationSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = DeiPlayWhite,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.video.title,
            style = MaterialTheme.typography.titleSmall,
            color = DeiPlayWhite,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${item.channel?.name ?: item.video.sourceAttribution} • ${formatViewCount(item.video.viewCount)}",
            style = MaterialTheme.typography.bodySmall,
            color = DeiPlayTextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    actionLabel: String?,
    onAction: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = DeiPlayWhite,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, color = DeiPlayGold)
                Icon(Icons.Filled.ChevronRight, null, tint = DeiPlayGold, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun ReportContentDialog(
    title: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val reasons = listOf(
        "Contenido engañoso o spam",
        "Infracción de derechos de autor",
        "Contenido inapropiado o violento",
        "Información falsa o peligrosa"
    )
    var selectedReason by remember { mutableStateOf(reasons.first()) }
    var customReason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeiPlaySurface,
        title = { Text("Reportar contenido", color = DeiPlayWhite) },
        text = {
            Column {
                Text(title, style = MaterialTheme.typography.bodySmall, color = DeiPlayTextSecondary, maxLines = 2)
                Spacer(Modifier.height(10.dp))
                reasons.forEach { reason ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = reason }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason },
                            colors = RadioButtonDefaults.colors(selectedColor = DeiPlayRed)
                        )
                        Text(reason, color = DeiPlayWhite, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customReason,
                    onValueChange = { customReason = it },
                    label = { Text("Detalles adicionales (opcional)") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalReason = if (customReason.isNotBlank()) "$selectedReason: $customReason" else selectedReason
                    onConfirm(finalReason)
                },
                colors = ButtonDefaults.buttonColors(containerColor = DeiPlayRed)
            ) {
                Text("Enviar reporte", color = DeiPlayWhite)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = DeiPlayTextSecondary) }
        }
    )
}
