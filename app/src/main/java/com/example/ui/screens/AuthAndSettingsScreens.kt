package com.example.ui.screens

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AccountRole
import com.example.ui.components.ChannelAvatar
import com.example.ui.components.DeiPlayOfficialLogo
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

@Composable
fun AuthDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf("login") } // login, register, recover
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("México") }
    var resetCode by remember { mutableStateOf("") }
    var generatedDemoResetCode by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val allAccounts by viewModel.allAccounts.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeiPlaySurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeiPlayOfficialLogo(size = 34.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = when (mode) {
                        "register" -> "Crear cuenta DeiPlay"
                        "recover" -> "Recuperar contraseña"
                        else -> "Iniciar sesión"
                    },
                    color = DeiPlayWhite
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (allAccounts.isNotEmpty() && mode == "login") {
                    Text("Cuentas en este dispositivo (cambio rápido):", style = MaterialTheme.typography.labelSmall, color = DeiPlayGold)
                    allAccounts.forEach { acc ->
                        ListItem(
                            leadingContent = { ChannelAvatar(acc.avatarUri, acc.username, 32.dp) },
                            headlineContent = { Text("${acc.username} (${acc.role})", color = DeiPlayWhite) },
                            supportingContent = { Text(acc.email, color = DeiPlayTextSecondary) },
                            colors = ListItemDefaults.colors(containerColor = DeiPlaySurfaceVariant),
                            modifier = Modifier.clickable {
                                viewModel.switchAccount(acc.id)
                                onDismiss()
                            }
                        )
                    }
                    HorizontalDivider(color = DeiPlayOutline)
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(if (mode == "login") "Correo o usuario" else "Correo electrónico") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_email_input")
                )

                if (mode == "register") {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Nombre de usuario") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_username_input")
                    )
                    OutlinedTextField(
                        value = country,
                        onValueChange = { country = it },
                        label = { Text("País") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (mode == "recover" && generatedDemoResetCode != null) {
                    Text(
                        text = "Modo local (sin servidor SMTP/Firebase): tu código de recuperación es $generatedDemoResetCode",
                        style = MaterialTheme.typography.bodySmall,
                        color = DeiPlayGold
                    )
                    OutlinedTextField(
                        value = resetCode,
                        onValueChange = { resetCode = it },
                        label = { Text("Código de recuperación") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (mode != "recover" || generatedDemoResetCode != null) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(if (mode == "recover") "Nueva contraseña" else "Contraseña") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_password_input")
                    )
                }

                if (errorMsg != null) {
                    Text(errorMsg!!, style = MaterialTheme.typography.bodySmall, color = DeiPlayRedBright)
                }

                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = {
                        errorMsg = null
                        mode = if (mode == "register") "login" else "register"
                    }) {
                        Text(
                            text = if (mode == "register") "Ya tengo cuenta" else "Crear cuenta",
                            color = DeiPlayGold
                        )
                    }
                    TextButton(onClick = {
                        errorMsg = null
                        mode = if (mode == "recover") "login" else "recover"
                    }) {
                        Text(
                            text = if (mode == "recover") "Volver" else "Olvidé mi clave",
                            color = DeiPlayTextSecondary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    errorMsg = null
                    when (mode) {
                        "register" -> viewModel.register(email, username, password, country) { res ->
                            res.onFailure { errorMsg = it.localizedMessage }
                        }
                        "login" -> viewModel.login(email, password) { res ->
                            res.onFailure { errorMsg = it.localizedMessage }
                        }
                        "recover" -> {
                            if (generatedDemoResetCode == null) {
                                viewModel.requestPasswordReset(email) { res ->
                                    res.onSuccess { generatedDemoResetCode = it }
                                        .onFailure { errorMsg = it.localizedMessage }
                                }
                            } else {
                                viewModel.confirmPasswordReset(email, resetCode, password) { res ->
                                    res.onSuccess {
                                        viewModel.emitMessage("Contraseña actualizada")
                                        mode = "login"
                                    }.onFailure { errorMsg = it.localizedMessage }
                                }
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DeiPlayRed),
                modifier = Modifier.testTag("auth_submit_button")
            ) {
                Text(
                    text = when (mode) {
                        "register" -> "Registrarse"
                        "recover" -> if (generatedDemoResetCode == null) "Generar código" else "Restablecer"
                        else -> "Entrar"
                    },
                    color = DeiPlayWhite
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar", color = DeiPlayTextSecondary) }
        }
    )
}

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onOpenAdminPanel: () -> Unit,
    onOpenDownloads: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val platformNotice by viewModel.platformNotice.collectAsStateWithLifecycle()

    var showEditProfile by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showVerifyEmail by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
            Text("Ajustes de DeiPlay", style = MaterialTheme.typography.titleLarge, color = DeiPlayWhite)
        }

        LazyColumn(
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Account section
            item {
                Surface(color = DeiPlaySurfaceVariant, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (activeAccount == null) {
                            Text("Sin sesión iniciada", style = MaterialTheme.typography.titleMedium, color = DeiPlayWhite)
                            Button(
                                onClick = { viewModel.openAuthModal() },
                                colors = ButtonDefaults.buttonColors(containerColor = DeiPlayRed)
                            ) { Text("Iniciar sesión / Crear cuenta", color = DeiPlayWhite) }
                        } else {
                            val acc = activeAccount!!
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ChannelAvatar(acc.avatarUri, acc.username, 48.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${acc.username} (${acc.role})", style = MaterialTheme.typography.titleMedium, color = DeiPlayWhite)
                                    Text("${acc.email} • ${acc.country}", style = MaterialTheme.typography.bodySmall, color = DeiPlayTextSecondary)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (acc.emailVerified) Icons.Filled.CheckCircle else Icons.Outlined.MarkEmailUnread,
                                            contentDescription = null,
                                            tint = if (acc.emailVerified) DeiPlaySuccess else DeiPlayGold,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = if (acc.emailVerified) "Correo verificado" else "Correo sin verificar",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (acc.emailVerified) DeiPlaySuccess else DeiPlayGold
                                        )
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { showEditProfile = true }, modifier = Modifier.weight(1f)) {
                                    Text("Editar perfil", color = DeiPlayWhite)
                                }
                                OutlinedButton(onClick = { showChangePassword = true }, modifier = Modifier.weight(1f)) {
                                    Text("Contraseña", color = DeiPlayWhite)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!acc.emailVerified) {
                                    OutlinedButton(onClick = { showVerifyEmail = true }, modifier = Modifier.weight(1f)) {
                                        Text("Verificar correo", color = DeiPlayGold)
                                    }
                                }
                                OutlinedButton(onClick = { viewModel.openAuthModal() }, modifier = Modifier.weight(1f)) {
                                    Text("Cambiar cuenta", color = DeiPlayInfo)
                                }
                            }
                            if (acc.role == AccountRole.ADMIN.name) {
                                Button(
                                    onClick = onOpenAdminPanel,
                                    colors = ButtonDefaults.buttonColors(containerColor = DeiPlayGold),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Outlined.AdminPanelSettings, null, tint = DeiPlayBlack)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Abrir Panel Administrativo", color = DeiPlayBlack)
                                }
                            }
                        }
                    }
                }
            }

            // Sync honesty notice
            item {
                Surface(color = DeiPlaySurface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Almacenamiento local activo: Las cuentas, canales y publicaciones locales se guardan en este dispositivo porque Firebase fue omitido. Para sincronización multidispositivo en la nube se requiere conectar Firebase o un backend remoto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DeiPlayTextSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Playback & Appearance
            item {
                Text("Reproducción y Tema", style = MaterialTheme.typography.titleMedium, color = DeiPlayGold)
                SwitchRow("Tema Negro OLED puro", settings.oledBlackTheme) { checked ->
                    viewModel.updateSettings { it.copy(oledBlackTheme = checked) }
                }
                SwitchRow("Reproducción automática siguiente video", settings.autoPlayNext) { checked ->
                    viewModel.updateSettings { it.copy(autoPlayNext = checked) }
                }

                var qualExpanded by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text("Calidad predeterminada", color = DeiPlayWhite) },
                    supportingContent = { Text(settings.defaultQuality, color = DeiPlayGold) },
                    colors = ListItemDefaults.colors(containerColor = DeiPlaySurface),
                    modifier = Modifier.clickable { qualExpanded = true }
                )
                DropdownMenu(expanded = qualExpanded, onDismissRequest = { qualExpanded = false }, containerColor = DeiPlaySurfaceHigh) {
                    listOf("Auto", "1080p", "720p", "480p", "360p").forEach { q ->
                        DropdownMenuItem(
                            text = { Text(q, color = DeiPlayWhite) },
                            onClick = {
                                viewModel.updateSettings { it.copy(defaultQuality = q) }
                                qualExpanded = false
                            }
                        )
                    }
                }

                var langExpanded by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text("Idioma de interfaz", color = DeiPlayWhite) },
                    supportingContent = { Text(settings.appLanguage, color = DeiPlayInfo) },
                    colors = ListItemDefaults.colors(containerColor = DeiPlaySurface),
                    modifier = Modifier.clickable { langExpanded = true }
                )
                DropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }, containerColor = DeiPlaySurfaceHigh) {
                    listOf("Español", "English", "Português").forEach { l ->
                        DropdownMenuItem(
                            text = { Text(l, color = DeiPlayWhite) },
                            onClick = {
                                viewModel.updateSettings { it.copy(appLanguage = l) }
                                langExpanded = false
                            }
                        )
                    }
                }
            }

            // Notifications
            item {
                Text("Notificaciones", style = MaterialTheme.typography.titleMedium, color = DeiPlayGold)
                SwitchRow("Nuevos videos y Shorts de suscripciones", settings.notifyNewVideos) { checked ->
                    viewModel.updateSettings { it.copy(notifyNewVideos = checked) }
                }
                SwitchRow("Comentarios y respuestas", settings.notifyComments) { checked ->
                    viewModel.updateSettings { it.copy(notifyComments = checked) }
                }
                SwitchRow("Nuevos suscriptores", settings.notifySubscribers) { checked ->
                    viewModel.updateSettings { it.copy(notifySubscribers = checked) }
                }
                SwitchRow("Avisos administrativos", settings.notifyAdmin) { checked ->
                    viewModel.updateSettings { it.copy(notifyAdmin = checked) }
                }
            }

            // Privacy, History & Storage
            item {
                Text("Privacidad y Almacenamiento", style = MaterialTheme.typography.titleMedium, color = DeiPlayGold)
                SwitchRow("Pausar historial de reproducción", settings.pauseWatchHistory) { checked ->
                    viewModel.updateSettings { it.copy(pauseWatchHistory = checked) }
                }
                ListItem(
                    headlineContent = { Text("Borrar historial de reproducción", color = DeiPlayWhite) },
                    leadingContent = { Icon(Icons.Outlined.DeleteSweep, null, tint = DeiPlayRedBright) },
                    colors = ListItemDefaults.colors(containerColor = DeiPlaySurface),
                    modifier = Modifier.clickable { viewModel.clearWatchHistory() }
                )
                ListItem(
                    headlineContent = { Text("Almacenamiento y Descargas", color = DeiPlayWhite) },
                    supportingContent = {
                        Text("Ocupado: ${Formatter.formatFileSize(context, viewModel.getDownloadedStorageBytes())}", color = DeiPlayTextSecondary)
                    },
                    leadingContent = { Icon(Icons.Outlined.Storage, null, tint = DeiPlayGold) },
                    colors = ListItemDefaults.colors(containerColor = DeiPlaySurface),
                    modifier = Modifier.clickable(onClick = onOpenDownloads)
                )
            }

            // Community Rules & Zero Ads info
            platformNotice?.let { notice ->
                item {
                    Surface(color = DeiPlaySurfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Normas Comunitarias DeiPlay", style = MaterialTheme.typography.titleSmall, color = DeiPlayGold)
                            Spacer(Modifier.height(4.dp))
                            Text(notice.rulesText, style = MaterialTheme.typography.bodySmall, color = DeiPlayTextSecondary)
                            Spacer(Modifier.height(6.dp))
                            Text("Sin anuncios publicitarios garantizado.", style = MaterialTheme.typography.labelSmall, color = DeiPlaySuccess)
                        }
                    }
                }
            }

            // Sign out / Delete account
            if (activeAccount != null) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { viewModel.signOut() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cerrar sesión", color = DeiPlayWhite)
                        }
                        Button(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = DeiPlayRedDeep),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Eliminar cuenta", color = DeiPlayWhite)
                        }
                    }
                }
            }
        }
    }

    if (showEditProfile && activeAccount != null) {
        val acc = activeAccount!!
        var uname by remember { mutableStateOf(acc.username) }
        var bio by remember { mutableStateOf(acc.bio) }
        var country by remember { mutableStateOf(acc.country) }
        var avatarUri by remember { mutableStateOf<Uri?>(null) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { avatarUri = it }

        AlertDialog(
            onDismissRequest = { showEditProfile = false },
            containerColor = DeiPlaySurface,
            title = { Text("Editar perfil", color = DeiPlayWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = uname, onValueChange = { uname = it }, label = { Text("Usuario") })
                    OutlinedTextField(value = country, onValueChange = { country = it }, label = { Text("País") })
                    OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Biografía") })
                    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(if (avatarUri != null) "Foto seleccionada" else "Cambiar foto de perfil", color = DeiPlayGold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateProfile(uname, bio, country, avatarUri) { res ->
                            res.onSuccess {
                                showEditProfile = false
                                viewModel.emitMessage("Perfil actualizado")
                            }.onFailure { viewModel.emitMessage(it.localizedMessage ?: "Error") }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeiPlayRed)
                ) { Text("Guardar", color = DeiPlayWhite) }
            },
            dismissButton = { TextButton(onClick = { showEditProfile = false }) { Text("Cancelar", color = DeiPlayTextSecondary) } }
        )
    }

    if (showChangePassword) {
        var cur by remember { mutableStateOf("") }
        var next by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showChangePassword = false },
            containerColor = DeiPlaySurface,
            title = { Text("Cambiar contraseña", color = DeiPlayWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = cur, onValueChange = { cur = it }, label = { Text("Contraseña actual") }, visualTransformation = PasswordVisualTransformation())
                    OutlinedTextField(value = next, onValueChange = { next = it }, label = { Text("Nueva contraseña") }, visualTransformation = PasswordVisualTransformation())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.changePassword(cur, next) { res ->
                            res.onSuccess {
                                showChangePassword = false
                                viewModel.emitMessage("Contraseña cambiada")
                            }.onFailure { viewModel.emitMessage(it.localizedMessage ?: "Error") }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeiPlayRed)
                ) { Text("Actualizar", color = DeiPlayWhite) }
            },
            dismissButton = { TextButton(onClick = { showChangePassword = false }) { Text("Cancelar", color = DeiPlayTextSecondary) } }
        )
    }

    if (showVerifyEmail && activeAccount != null) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showVerifyEmail = false },
            containerColor = DeiPlaySurface,
            title = { Text("Verificar correo", color = DeiPlayWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Código local generado para ${activeAccount!!.email}: ${activeAccount!!.verificationCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DeiPlayGold
                    )
                    OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Ingresa el código") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.verifyEmail(code) { res ->
                            res.onSuccess {
                                showVerifyEmail = false
                                viewModel.emitMessage("¡Correo verificado!")
                            }.onFailure { viewModel.emitMessage(it.localizedMessage ?: "Código incorrecto") }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeiPlayRed)
                ) { Text("Verificar", color = DeiPlayWhite) }
            },
            dismissButton = { TextButton(onClick = { showVerifyEmail = false }) { Text("Cerrar", color = DeiPlayTextSecondary) } }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = DeiPlaySurface,
            title = { Text("¿Eliminar cuenta?", color = DeiPlayWhite) },
            text = { Text("Esta acción cerrará sesión y eliminará tu cuenta de este dispositivo.", color = DeiPlayTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteActiveAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeiPlayRed)
                ) { Text("Eliminar", color = DeiPlayWhite) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar", color = DeiPlayTextSecondary) } }
        )
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = DeiPlayWhite, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
