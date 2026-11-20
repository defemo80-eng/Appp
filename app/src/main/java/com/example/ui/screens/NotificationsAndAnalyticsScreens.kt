package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AnalyticsPeriod
import com.example.data.model.CreatorAnalytics
import com.example.data.model.formatRelativeDate
import com.example.data.model.formatViewCount
import com.example.ui.components.EmptyStateView
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

@Composable
fun NotificationsScreen(
    viewModel: MainViewModel,
    onOpenVideoById: (String) -> Unit,
    onOpenChannelById: (String) -> Unit,
    onBack: () -> Unit
) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeiPlayBlack)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = DeiPlayWhite)
            }
            Text("Notificaciones", style = MaterialTheme.typography.titleLarge, color = DeiPlayWhite, modifier = Modifier.weight(1f))
            if (notifications.any { !it.isRead }) {
                TextButton(onClick = { viewModel.markAllNotificationsRead() }) {
                    Text("Marcar leídas", color = DeiPlayGold)
                }
            }
        }

        if (notifications.isEmpty()) {
            EmptyStateView(
                icon = Icons.Outlined.NotificationsNone,
                title = "Sin notificaciones",
                subtitle = "Aquí aparecerán nuevos videos, suscriptores, comentarios y avisos."
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
                items(notifications, key = { it.id }) { notif ->
                    Surface(
                        color = if (notif.isRead) DeiPlayBlack else DeiPlaySurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.markNotificationRead(notif.id)
                                when {
                                    notif.videoId != null -> onOpenVideoById(notif.videoId)
                                    notif.channelId != null -> onOpenChannelById(notif.channelId)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = if (notif.isRead) Icons.Outlined.Notifications else Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = if (notif.isRead) DeiPlayTextMuted else DeiPlayRed
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(notif.title, style = MaterialTheme.typography.titleSmall, color = DeiPlayWhite)
                                Text(notif.message, style = MaterialTheme.typography.bodySmall, color = DeiPlayTextSecondary)
                                Text(formatRelativeDate(notif.createdAt), style = MaterialTheme.typography.labelSmall, color = DeiPlayTextMuted)
                            }
                        }
                    }
                    HorizontalDivider(color = DeiPlayOutline.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
fun CreatorStudioScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    var selectedPeriod by remember { mutableStateOf(AnalyticsPeriod.ALL_TIME) }
    var analytics by remember { mutableStateOf(CreatorAnalytics()) }

    LaunchedEffect(activeAccount?.id, selectedPeriod) {
        analytics = viewModel.loadCreatorAnalytics(selectedPeriod)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeiPlayBlack)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = DeiPlayWhite)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Panel de Creadores", style = MaterialTheme.typography.titleLarge, color = DeiPlayWhite)
                Text("Métricas reales de tus canales", style = MaterialTheme.typography.labelSmall, color = DeiPlayGold)
            }
        }

        if (activeAccount == null) {
            EmptyStateView(
                icon = Icons.Outlined.Analytics,
                title = "Acceso privado de creadores",
                subtitle = "Inicia sesión para consultar estadísticas reales de tus canales y videos.",
                actionLabel = "Iniciar sesión",
                onAction = { viewModel.openAuthModal() }
            )
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnalyticsPeriod.entries.forEach { period ->
                        FilterChip(
                            selected = selectedPeriod == period,
                            onClick = { selectedPeriod = period },
                            label = { Text(period.label) }
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard("Visualizaciones", formatViewCount(analytics.totalViews), Icons.Outlined.Visibility, DeiPlayRed, Modifier.weight(1f))
                    MetricCard("Suscriptores", formatViewCount(analytics.totalSubscribers), Icons.Outlined.People, DeiPlayGold, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard("Nuevos suscript.", "+${analytics.newSubscribersInPeriod}", Icons.Outlined.PersonAdd, DeiPlaySuccess, Modifier.weight(1f))
                    MetricCard("Me gusta totales", formatViewCount(analytics.totalLikes), Icons.Outlined.ThumbUp, DeiPlayInfo, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard("Comentarios", formatViewCount(analytics.totalComments), Icons.Outlined.Comment, DeiPlayWhite, Modifier.weight(1f))
                    MetricCard("Videos / Shorts", "${analytics.publishedVideos} / ${analytics.publishedShorts}", Icons.Outlined.VideoLibrary, DeiPlayGold, Modifier.weight(1f))
                }
            }

            item {
                Text("Rendimiento real por publicación", style = MaterialTheme.typography.titleMedium, color = DeiPlayWhite)
            }

            if (analytics.topVideos.isEmpty()) {
                item {
                    Text(
                        text = "No hay publicaciones registradas en este periodo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DeiPlayTextSecondary
                    )
                }
            } else {
                items(analytics.topVideos, key = { it.videoId }) { perf ->
                    Surface(
                        color = DeiPlaySurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (perf.isShort) "[SHORT] " else "[VIDEO] ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DeiPlayGold
                                )
                                Text(
                                    text = perf.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = DeiPlayWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${perf.views} vistas • ${perf.likes} likes • ${perf.comments} comentarios • ${formatRelativeDate(perf.createdAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = DeiPlayTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DeiPlaySurfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.75.dp, DeiPlayOutline),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, color = DeiPlayWhite, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.bodySmall, color = DeiPlayTextSecondary)
        }
    }
}
