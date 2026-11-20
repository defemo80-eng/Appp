package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.VideoEntity
import com.example.data.model.VideoVisibility
import com.example.ui.components.EmptyStateView
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun PublishScreen(
    viewModel: MainViewModel,
    defaultShort: Boolean = false,
    onOpenChannel: (String) -> Unit
) {
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    val userChannels by viewModel.userChannels.collectAsStateWithLifecycle()
    val userVideos by viewModel.userVideos.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    if (activeAccount == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeiPlayBlack)
                .statusBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            EmptyStateView(
                icon = Icons.Outlined.AccountCircle,
                title = "Inicia sesión para publicar",
                subtitle = "Crea tu cuenta DeiPlay y abre tu canal para subir videos y Shorts.",
                actionLabel = "Iniciar sesión / Registrarse",
                onAction = { viewModel.openAuthModal() }
            )
        }
        return
    }

    var showCreateChannelDialog by remember { mutableStateOf(false) }

    var selectedChannelId by remember(userChannels) {
        mutableStateOf(userChannels.firstOrNull()?.id.orEmpty())
    }
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedThumbUri by remember { mutableStateOf<Uri?>(null) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Entretenimiento") }
    var tags by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("Español") }
    var country by remember { mutableStateOf(activeAccount?.country ?: "Global") }
    var isShort by remember { mutableStateOf(defaultShort) }
    var visibility by remember { mutableStateOf(VideoVisibility.PUBLIC) }
    var downloadAllowed by remember { mutableStateOf(true) }

    var uploadProgress by remember { mutableFloatStateOf(0f) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    var uploadJob by remember { mutableStateOf<Job?>(null) }

    var editingVideo by remember { mutableStateOf<VideoEntity?>(null) }

    // Zero-permission Android Photo Picker (Google Play Policy compliant)
    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) selectedVideoUri = uri }

    val pickThumbLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) selectedThumbUri = uri }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DeiPlayBlack)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Estudio de Publicación", style = MaterialTheme.typography.headlineMedium, color = DeiPlayWhite)
            Text(
                text = "Sube videos o Shorts reales desde tu dispositivo a tu canal de DeiPlay.",
                style = MaterialTheme.typography.bodySmall,
                color = DeiPlayTextSecondary
            )
        }

        if (userChannels.isEmpty()) {
            item {
                Surface(
                    color = DeiPlaySurfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Primero crea tu canal", style = MaterialTheme.typography.titleMedium, color = DeiPlayGold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Cada creador necesita al menos un canal para publicar videos y conseguir suscriptores.",
                            style = MaterialTheme.typography.bodySmall,
                            color = DeiPlayTextSecondary
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { showCreateChannelDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = DeiPlayRed),
                            modifier = Modifier.testTag("publish_create_channel_button")
                        ) {
                            Text("Crear mi canal", color = DeiPlayWhite)
                        }
                    }
                }
            }
        } else {
            item {
                // Channel Selector
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    var chMenu by remember { mutableStateOf(false) }
                    val currentCh = userChannels.firstOrNull { it.id == selectedChannelId } ?: userChannels.first()
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { chMenu = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Canal: ${currentCh.name} (${currentCh.handle})", color = DeiPlayWhite)
                        }
                        DropdownMenu(expanded = chMenu, onDismissRequest = { chMenu = false }, containerColor = DeiPlaySurfaceHigh) {
                            userChannels.forEach { ch ->
                                DropdownMenuItem(
                                    text = { Text("${ch.name} (${ch.handle})", color = DeiPlayWhite) },
                                    onClick = {
                                        selectedChannelId = ch.id
                                        chMenu = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showCreateChannelDialog = true }) {
                        Text("+ Canal", color = DeiPlayGold)
                    }
                }
            }

            item {
                // File pickers
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            pickVideoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DeiPlaySurfaceHigh),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("publish_pick_video_button")
                    ) {
                        Icon(Icons.Outlined.VideoFile, null, tint = DeiPlayRed)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (selectedVideoUri != null) "Video seleccionado" else "Elegir video",
                            color = DeiPlayWhite
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            pickThumbLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Image, null, tint = DeiPlayGold)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (selectedThumbUri != null) "Portada lista" else "Miniatura (opcional)",
                            color = DeiPlayWhite
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("publish_title_input")
                )
            }

            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    var catExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { catExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Cat: $category", color = DeiPlayWhite)
                        }
                        DropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }, containerColor = DeiPlaySurfaceHigh) {
                            categories.map { it.name }.filter { it != "Todos" && it != "Recientes" }.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat, color = DeiPlayWhite) },
                                    onClick = {
                                        category = cat
                                        catExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    var visExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { visExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(visibility.label, color = DeiPlayGold)
                        }
                        DropdownMenu(expanded = visExpanded, onDismissRequest = { visExpanded = false }, containerColor = DeiPlaySurfaceHigh) {
                            VideoVisibility.entries.forEach { vis ->
                                DropdownMenuItem(
                                    text = { Text(vis.label, color = DeiPlayWhite) },
                                    onClick = {
                                        visibility = vis
                                        visExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = language,
                        onValueChange = { language = it },
                        label = { Text("Idioma") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = country,
                        onValueChange = { country = it },
                        label = { Text("País") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Etiquetas (separadas por coma)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Publicar como Short vertical", color = DeiPlayWhite, modifier = Modifier.weight(1f))
                        Switch(checked = isShort, onCheckedChange = { isShort = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Permitir descargas autorizadas", color = DeiPlayWhite, modifier = Modifier.weight(1f))
                        Switch(checked = downloadAllowed, onCheckedChange = { downloadAllowed = it })
                    }
                }
            }

            if (isUploading) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(progress = { uploadProgress }, color = DeiPlayRed, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Almacenando archivo (${(uploadProgress * 100).toInt()}%)...", color = DeiPlayGold, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                uploadJob?.cancel()
                                isUploading = false
                                uploadProgress = 0f
                                viewModel.emitMessage("Subida cancelada")
                            }) {
                                Text("Cancelar", color = DeiPlayRedBright)
                            }
                        }
                    }
                }
            }

            if (uploadError != null) {
                item {
                    Text(uploadError!!, color = DeiPlayRedBright, style = MaterialTheme.typography.bodySmall)
                }
            }

            item {
                Button(
                    onClick = {
                        val uri = selectedVideoUri
                        if (uri == null) {
                            uploadError = "Selecciona un archivo de video primero"
                            return@Button
                        }
                        val chId = selectedChannelId.ifBlank { userChannels.first().id }
                        uploadError = null
                        isUploading = true
                        uploadProgress = 0f
                        uploadJob = scope.launch {
                            viewModel.publishVideo(
                                channelId = chId,
                                videoUri = uri,
                                thumbnailUri = selectedThumbUri,
                                title = title,
                                description = description,
                                category = category,
                                tagsCsv = tags,
                                language = language,
                                country = country,
                                isShort = isShort,
                                visibility = visibility,
                                downloadAllowed = downloadAllowed,
                                onProgress = { uploadProgress = it },
                                onResult = { res ->
                                    isUploading = false
                                    res.onSuccess {
                                        title = ""
                                        description = ""
                                        tags = ""
                                        selectedVideoUri = null
                                        selectedThumbUri = null
                                        viewModel.emitMessage("¡Video publicado correctamente en DeiPlay!")
                                    }.onFailure { err ->
                                        uploadError = err.localizedMessage ?: "Error al publicar"
                                    }
                                }
                            )
                        }
                    },
                    enabled = !isUploading,
                    colors = ButtonDefaults.buttonColors(containerColor = DeiPlayRed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("publish_submit_button")
                ) {
                    Text("Publicar ahora", color = DeiPlayWhite, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        // User's published videos management
        if (userVideos.isNotEmpty()) {
            item {
                Spacer(Modifier.height(10.dp))
                Text("Mis publicaciones (${userVideos.size})", style = MaterialTheme.typography.titleLarge, color = DeiPlayWhite)
            }
            items(userVideos, key = { "myvid_${it.video.id}" }) { item ->
                val v = item.video
                Surface(
                    color = DeiPlaySurfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(v.title, style = MaterialTheme.typography.titleSmall, color = DeiPlayWhite)
                            Text(
                                text = "${v.visibility} • ${if (v.isShort) "Short" else "Video"} • ${v.viewCount} vistas",
                                style = MaterialTheme.typography.bodySmall,
                                color = DeiPlayTextSecondary
                            )
                        }
                        IconButton(onClick = { editingVideo = v }) {
                            Icon(Icons.Outlined.Edit, "Editar publicación", tint = DeiPlayGold)
                        }
                        IconButton(onClick = { viewModel.deleteUserVideo(v.id) }) {
                            Icon(Icons.Outlined.Delete, "Eliminar publicación", tint = DeiPlayRedBright)
                        }
                    }
                }
            }
        }
    }

    if (showCreateChannelDialog) {
        CreateChannelDialog(
            defaultCountry = activeAccount?.country ?: "Global",
            onConfirm = { name, handle, desc, chCountry, avatar, banner ->
                viewModel.createChannel(name, handle, desc, chCountry, avatar, banner) { res ->
                    res.onSuccess { ch ->
                        selectedChannelId = ch.id
                        showCreateChannelDialog = false
                        viewModel.emitMessage("Canal ${ch.name} creado")
                    }.onFailure { err ->
                        viewModel.emitMessage(err.localizedMessage ?: "Error al crear canal")
                    }
                }
            },
            onDismiss = { showCreateChannelDialog = false }
        )
    }

    editingVideo?.let { vid ->
        var editTitle by remember { mutableStateOf(vid.title) }
        var editDesc by remember { mutableStateOf(vid.description) }
        var editVis by remember { mutableStateOf(VideoVisibility.valueOf(vid.visibility)) }
        var editDl by remember { mutableStateOf(vid.downloadAllowed) }
        AlertDialog(
            onDismissRequest = { editingVideo = null },
            containerColor = DeiPlaySurface,
            title = { Text("Editar publicación", color = DeiPlayWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = editTitle, onValueChange = { editTitle = it }, label = { Text("Título") })
                    OutlinedTextField(value = editDesc, onValueChange = { editDesc = it }, label = { Text("Descripción") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Permitir descargas", color = DeiPlayWhite, modifier = Modifier.weight(1f))
                        Switch(checked = editDl, onCheckedChange = { editDl = it })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VideoVisibility.entries.forEach { vis ->
                            FilterChip(
                                selected = editVis == vis,
                                onClick = { editVis = vis },
                                label = { Text(vis.label) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateVideoMetadata(vid.id, editTitle, editDesc, vid.category, editVis, editDl)
                        editingVideo = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeiPlayRed)
                ) { Text("Guardar", color = DeiPlayWhite) }
            },
            dismissButton = {
                TextButton(onClick = { editingVideo = null }) { Text("Cancelar", color = DeiPlayTextSecondary) }
            }
        )
    }
}

@Composable
fun CreateChannelDialog(
    defaultCountry: String,
    onConfirm: (String, String, String, String, Uri?, Uri?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var country by remember { mutableStateOf(defaultCountry) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var bannerUri by remember { mutableStateOf<Uri?>(null) }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { avatarUri = it }
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { bannerUri = it }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeiPlaySurface,
        title = { Text("Crear nuevo canal", color = DeiPlayWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del canal *") },
                    singleLine = true,
                    modifier = Modifier.testTag("channel_name_input")
                )
                OutlinedTextField(
                    value = handle,
                    onValueChange = { handle = it },
                    label = { Text("@usuario único *") },
                    singleLine = true,
                    modifier = Modifier.testTag("channel_handle_input")
                )
                OutlinedTextField(
                    value = country,
                    onValueChange = { country = it },
                    label = { Text("País") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción del canal") },
                    maxLines = 3
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (avatarUri != null) "Avatar listo" else "Foto perfil", color = DeiPlayGold)
                    }
                    OutlinedButton(
                        onClick = { bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (bannerUri != null) "Portada lista" else "Portada", color = DeiPlayWhite)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, handle, description, country, avatarUri, bannerUri) },
                colors = ButtonDefaults.buttonColors(containerColor = DeiPlayRed),
                modifier = Modifier.testTag("channel_create_confirm")
            ) {
                Text("Crear canal", color = DeiPlayWhite)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = DeiPlayTextSecondary) }
        }
    )
}
