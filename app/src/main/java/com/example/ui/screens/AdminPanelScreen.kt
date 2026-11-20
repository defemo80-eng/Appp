package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ChannelEntity
import com.example.data.local.VideoEntity
import com.example.data.model.AccountRole
import com.example.data.model.ReportStatus
import com.example.data.model.formatRelativeDate
import com.example.data.model.formatViewCount
import com.example.ui.components.EmptyStateView
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

@Composable
fun AdminPanelScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    val allAccounts by viewModel.allAccounts.collectAsStateWithLifecycle()
    val allChannels by viewModel.allChannelsAdmin.collectAsStateWithLifecycle()
    val allVideos by viewModel.allVideosAdmin.collectAsStateWithLifecycle()
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val platformNotice by viewModel.platformNotice.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()

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
                Text("Consola Administrativa", style = MaterialTheme.typography.titleLarge, color = DeiPlayWhite)
                Text("Moderación, auditoría y métricas reales", style = MaterialTheme.typography.labelSmall, color = DeiPlayGold)
            }
        }

        if (activeAccount?.role != AccountRole.ADMIN.name) {
            EmptyStateView(
                icon = Icons.Outlined.AdminPanelSettings,
                title = "Acceso restringido a administradores",
                subtitle = "Solo las cuentas con rol ADMIN autorizado pueden acceder. La cuenta fundadora registrada recibe rol ADMIN automáticamente."
            )
            return@Column
        }

        var selectedTab by remember { mutableIntStateOf(0) }
        val tabs = listOf("Estadísticas", "Reportes", "Usuarios", "Canales", "Videos", "Config", "Auditoría")

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = DeiPlaySurface,
            contentColor = DeiPlayRed,
            edgePadding = 12.dp
        ) {
            tabs.forEachIndexed { idx, label ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(label, color = if (selectedTab == idx) DeiPlayWhite else DeiPlayTextMuted) }
                )
            }
        }

        var searchUserQuery by remember { mutableStateOf("") }
        var searchVideoQuery by remember { mutableStateOf("") }
        var editingChannelSubscribers by remember { mutableStateOf<ChannelEntity?>(null) }
        var editingVideoMetrics by remember { mutableStateOf<VideoEntity?>(null) }

        LazyColumn(
            contentPadding = PaddingValues(14.dp, 12.dp, 14.dp, 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    item {
                        Surface(
                            color = DeiPlaySurfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Nota de arquitectura: Esta aplicación es nativa Android. Un panel web independiente con URL propia requiere desplegar un backend/hosting web externo (ej. Firebase Hosting). Toda la moderación y auditoría local opera en tiempo real aquí.",
                                style = MaterialTheme.typography.bodySmall,
                                color = DeiPlayGold,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricCard("Usuarios", allAccounts.size.toString(), Icons.Outlined.People, DeiPlayInfo, Modifier.weight(1f))
                            MetricCard("Canales", allChannels.size.toString(), Icons.Outlined.Tv, DeiPlayGold, Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricCard("Videos", allVideos.count { !it.video.isShort }.toString(), Icons.Outlined.VideoLibrary, DeiPlayRed, Modifier.weight(1f))
                            MetricCard("Shorts", allVideos.count { it.video.isShort }.toString(), Icons.Outlined.Bolt, DeiPlayRedBright, Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricCard("Vistas reales", formatViewCount(allVideos.sumOf { it.video.viewCount }), Icons.Outlined.Visibility, DeiPlaySuccess, Modifier.weight(1f))
                            MetricCard("Reportes pend.", reports.count { it.status == ReportStatus.PENDING.name }.toString(), Icons.Outlined.Flag, DeiPlayWarning, Modifier.weight(1f))
                        }
                    }
                }
                1 -> {
                    if (reports.isEmpty()) {
                        item { EmptyStateView(Icons.Outlined.CheckCircle, "Sin reportes pendientes", "La comunidad está al día.") }
                    } else {
                        items(reports, key = { it.id }) { rep ->
                            Surface(color = DeiPlaySurfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("[${rep.targetType}] ${rep.targetTitle}", style = MaterialTheme.typography.titleSmall, color = DeiPlayWhite)
                                    Text("Motivo: ${rep.reason}", style = MaterialTheme.typography.bodySmall, color = DeiPlayTextSecondary)
                                    Text("Estado: ${rep.status} • ${formatRelativeDate(rep.createdAt)}", style = MaterialTheme.typography.labelSmall, color = DeiPlayGold)
                                    if (rep.status == ReportStatus.PENDING.name) {
                                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                            TextButton(onClick = { viewModel.adminResolveReport(rep, removeTarget = false) }) {
                                                Text("Desestimar", color = DeiPlayTextSecondary)
                                            }
                                            Button(
                                                onClick = { viewModel.adminResolveReport(rep, removeTarget = true) },
                                                colors = ButtonDefaults.buttonColors(containerColor = DeiPlayRed)
                                            ) { Text("Retirar contenido", color = DeiPlayWhite) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    item {
                        OutlinedTextField(
                            value = searchUserQuery,
                            onValueChange = { searchUserQuery = it },
                            label = { Text("Buscar cuenta por nombre o correo") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    val filteredAccounts = allAccounts.filter {
                        searchUserQuery.isBlank() ||
                            it.username.contains(searchUserQuery, true) ||
                            it.email.contains(searchUserQuery, true)
                    }
                    items(filteredAccounts, key = { it.id }) { acc ->
                        Surface(color = DeiPlaySurfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${acc.username} (${acc.role})", style = MaterialTheme.typography.titleSmall, color = DeiPlayWhite)
                                    Text("${acc.email} • ${acc.country}", style = MaterialTheme.typography.bodySmall, color = DeiPlayTextSecondary)
                                    if (acc.isSuspended) Text("SUSPENDIDA", style = MaterialTheme.typography.labelSmall, color = DeiPlayRedBright)
                                }
                                if (acc.id != activeAccount?.id) {
                                    Button(
                                        onClick = { viewModel.adminSetUserSuspended(acc.id, !acc.isSuspended) },
                                        colors = ButtonDefaults.buttonColors(containerColor = if (acc.isSuspended) DeiPlaySuccess else DeiPlayRed)
                                    ) {
                                        Text(if (acc.isSuspended) "Reactivar" else "Suspender", color = DeiPlayWhite)
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    items(allChannels, key = { it.id }) { ch ->
                        Surface(color = DeiPlaySurfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${ch.name} (${ch.handle})", style = MaterialTheme.typography.titleSmall, color = DeiPlayWhite)
                                    Text("${ch.subscriberCount} suscriptores • ${ch.sourceName}", style = MaterialTheme.typography.bodySmall, color = DeiPlayTextSecondary)
                                }
                                IconButton(
                                    onClick = { editingChannelSubscribers = ch },
                                    modifier = Modifier.testTag("admin_edit_subscribers_${ch.id}")
                                ) {
                                    Icon(Icons.Outlined.Edit, "Alterar suscriptores", tint = DeiPlayGold)
                                }
                                Button(
                                    onClick = { viewModel.adminToggleChannelModeration(ch, !ch.isModerated) },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (ch.isModerated) DeiPlaySuccess else DeiPlayRed)
                                ) {
                                    Text(if (ch.isModerated) "Restaurar" else "Moderar", color = DeiPlayWhite)
                                }
                            }
                        }
                    }
                }
                4 -> {
                    item {
                        OutlinedTextField(
                            value = searchVideoQuery,
                            onValueChange = { searchVideoQuery = it },
                            label = { Text("Buscar publicaciones...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    val vids = allVideos.filter {
                        searchVideoQuery.isBlank() || it.video.title.contains(searchVideoQuery, true)
                    }
                    items(vids, key = { "adm_${it.video.id}" }) { item ->
                        val v = item.video
                        Surface(color = DeiPlaySurfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(v.title, style = MaterialTheme.typography.titleSmall, color = DeiPlayWhite)
                                    Text("${v.sourceAttribution} • ${v.viewCount} vistas • ${v.likeCount} likes", style = MaterialTheme.typography.bodySmall, color = DeiPlayTextSecondary)
                                    if (v.isRemoved) Text("RETIRADO POR MODERACIÓN", style = MaterialTheme.typography.labelSmall, color = DeiPlayRedBright)
                                }
                                IconButton(
                                    onClick = { editingVideoMetrics = v },
                                    modifier = Modifier.testTag("admin_edit_video_metrics_${v.id}")
                                ) {
                                    Icon(Icons.Outlined.Tune, "Alterar vistas y likes", tint = DeiPlayGold)
                                }
                                Button(
                                    onClick = { viewModel.adminRemoveVideo(v, !v.isRemoved) },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (v.isRemoved) DeiPlaySuccess else DeiPlayRed),
                                    modifier = Modifier.testTag("admin_toggle_video_${v.id}")
                                ) {
                                    Text(if (v.isRemoved) "Restaurar" else "Retirar", color = DeiPlayWhite)
                                }
                            }
                        }
                    }
                }
                5 -> {
                    item {
                        var newCat by remember { mutableStateOf("") }
                        Text("Administrar categorías", style = MaterialTheme.typography.titleMedium, color = DeiPlayWhite)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = newCat, onValueChange = { newCat = it }, label = { Text("Nueva categoría") }, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                if (newCat.isNotBlank()) {
                                    viewModel.adminAddCategory(newCat)
                                    newCat = ""
                                }
                            }, colors = ButtonDefaults.buttonColors(containerColor = DeiPlayRed)) {
                                Text("Agregar", color = DeiPlayWhite)
                            }
                        }
                    }
                    items(categories, key = { "adm_cat_${it.name}" }) { cat ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(cat.name, color = DeiPlayWhite, modifier = Modifier.weight(1f))
                            if (cat.name != "Todos") {
                                TextButton(onClick = { viewModel.adminDeleteCategory(cat.name) }) {
                                    Text("Eliminar", color = DeiPlayRedBright)
                                }
                            }
                        }
                    }
                    item {
                        var nTitle by remember(platformNotice) { mutableStateOf(platformNotice?.title.orEmpty()) }
                        var nMsg by remember(platformNotice) { mutableStateOf(platformNotice?.message.orEmpty()) }
                        var nRules by remember(platformNotice) { mutableStateOf(platformNotice?.rulesText.orEmpty()) }
                        HorizontalDivider(color = DeiPlayOutline)
                        Spacer(Modifier.height(8.dp))
                        Text("Avisos y Normas Comunitarias", style = MaterialTheme.typography.titleMedium, color = DeiPlayGold)
                        OutlinedTextField(value = nTitle, onValueChange = { nTitle = it }, label = { Text("Título del aviso") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = nMsg, onValueChange = { nMsg = it }, label = { Text("Mensaje global") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = nRules, onValueChange = { nRules = it }, label = { Text("Normas comunitarias") }, maxLines = 6, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.adminUpdateNoticeAndRules(nTitle, nMsg, nRules) },
                            colors = ButtonDefaults.buttonColors(containerColor = DeiPlayRed),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Publicar aviso y normas", color = DeiPlayWhite) }
                    }
                }
                6 -> {
                    items(auditLogs, key = { it.id }) { log ->
                        Surface(color = DeiPlaySurfaceVariant, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("${log.action} • ${log.adminUsername}", style = MaterialTheme.typography.labelLarge, color = DeiPlayGold)
                                Text(log.targetSummary, style = MaterialTheme.typography.bodySmall, color = DeiPlayWhite)
                                Text(formatRelativeDate(log.createdAt), style = MaterialTheme.typography.labelSmall, color = DeiPlayTextMuted)
                            }
                        }
                    }
                }
            }
        }

        editingChannelSubscribers?.let { channel ->
            var subsText by remember(channel.id) { mutableStateOf(channel.subscriberCount.toString()) }
            AlertDialog(
                onDismissRequest = { editingChannelSubscribers = null },
                containerColor = DeiPlaySurface,
                title = { Text("Alterar suscriptores", color = DeiPlayWhite) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${channel.name} (${channel.handle})", style = MaterialTheme.typography.bodySmall, color = DeiPlayGold)
                        OutlinedTextField(
                            value = subsText,
                            onValueChange = { input -> subsText = input.filter { it.isDigit() } },
                            label = { Text("Cantidad de suscriptores") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_subscribers_input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(100L, 1_000L, 10_000L).forEach { delta ->
                                OutlinedButton(
                                    onClick = {
                                        val cur = subsText.toLongOrNull() ?: 0L
                                        subsText = (cur + delta).toString()
                                    }
                                ) {
                                    Text("+${formatViewCount(delta)}", color = DeiPlayGold)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val newCount = subsText.toLongOrNull() ?: channel.subscriberCount
                            viewModel.adminUpdateChannelSubscribers(channel, newCount)
                            editingChannelSubscribers = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DeiPlayRed),
                        modifier = Modifier.testTag("admin_subscribers_save")
                    ) { Text("Guardar", color = DeiPlayWhite) }
                },
                dismissButton = {
                    TextButton(onClick = { editingChannelSubscribers = null }) {
                        Text("Cancelar", color = DeiPlayTextSecondary)
                    }
                }
            )
        }

        editingVideoMetrics?.let { video ->
            var viewsText by remember(video.id) { mutableStateOf(video.viewCount.toString()) }
            var likesText by remember(video.id) { mutableStateOf(video.likeCount.toString()) }
            AlertDialog(
                onDismissRequest = { editingVideoMetrics = null },
                containerColor = DeiPlaySurface,
                title = { Text("Alterar vistas y likes", color = DeiPlayWhite) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(video.title, style = MaterialTheme.typography.bodySmall, color = DeiPlayGold, maxLines = 2)
                        OutlinedTextField(
                            value = viewsText,
                            onValueChange = { input -> viewsText = input.filter { it.isDigit() } },
                            label = { Text("Cantidad de vistas") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_views_input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(100L, 1_000L, 10_000L).forEach { delta ->
                                OutlinedButton(
                                    onClick = {
                                        val cur = viewsText.toLongOrNull() ?: 0L
                                        viewsText = (cur + delta).toString()
                                    }
                                ) {
                                    Text("+${formatViewCount(delta)}", color = DeiPlayGold)
                                }
                            }
                        }
                        OutlinedTextField(
                            value = likesText,
                            onValueChange = { input -> likesText = input.filter { it.isDigit() } },
                            label = { Text("Cantidad de Me gusta (Likes)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_likes_input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(50L, 500L, 5_000L).forEach { delta ->
                                OutlinedButton(
                                    onClick = {
                                        val cur = likesText.toLongOrNull() ?: 0L
                                        likesText = (cur + delta).toString()
                                    }
                                ) {
                                    Text("+${formatViewCount(delta)}", color = DeiPlayInfo)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val newViews = viewsText.toLongOrNull() ?: video.viewCount
                            val newLikes = likesText.toLongOrNull() ?: video.likeCount
                            viewModel.adminUpdateVideoMetrics(video, newViews, newLikes)
                            editingVideoMetrics = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DeiPlayRed),
                        modifier = Modifier.testTag("admin_video_metrics_save")
                    ) { Text("Guardar", color = DeiPlayWhite) }
                },
                dismissButton = {
                    TextButton(onClick = { editingVideoMetrics = null }) {
                        Text("Cancelar", color = DeiPlayTextSecondary)
                    }
                }
            )
        }
    }
}
