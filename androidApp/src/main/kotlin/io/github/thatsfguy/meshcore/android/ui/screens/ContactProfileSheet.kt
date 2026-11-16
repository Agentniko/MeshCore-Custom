package io.github.thatsfguy.meshcore.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.thatsfguy.meshcore.android.storage.AvatarException
import io.github.thatsfguy.meshcore.android.storage.ContactProfileEntity
import io.github.thatsfguy.meshcore.android.ui.MeshCoreViewModel
import io.github.thatsfguy.meshcore.custom.ProfileCardCodec
import kotlinx.coroutines.launch

/**
 * The "Profile" block of a contact's detail sheet (MeshCore Custom): a read-only summary of
 * what the user has stored locally, with copy / open-link actions and an edit button.
 * Everything shown here was typed or picked by the user; none of it is verified.
 */
@Composable
fun ContactProfileSummary(vm: MeshCoreViewModel, keyHex: String, name: String) {
    val profile = vm.profileMap.collectAsState().value[keyHex]
    var editing by remember(keyHex) { mutableStateOf(false) }
    var confirmLink by remember(keyHex) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NodeAvatar(seed = keyHex, label = name.ifBlank { keyHex }, size = 48.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Profile (local only)", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Stored on this phone. Not verified, never sent unless you share it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (profile != null) {
            profile.label?.let { Text("Label: $it", style = MaterialTheme.typography.bodyMedium) }
            profile.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            profile.discordUser?.let { user ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Discord: $user", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { clipboard.setText(AnnotatedString(user)) }) { Text("Copy") }
                }
            }
            profile.discordId?.let { id ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Discord ID: $id", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { clipboard.setText(AnnotatedString(id)) }) { Text("Copy") }
                }
            }
            profile.discordLink?.let {
                TextButton(onClick = { confirmLink = true }) { Text("Open Discord profile") }
            }
            profile.notes?.let {
                Text("Notes", style = MaterialTheme.typography.labelMedium)
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedButton(onClick = { editing = true }) {
            Text(if (profile == null) "Add profile" else "Edit profile")
        }
    }

    if (confirmLink && profile?.discordLink != null) {
        val link = profile.discordLink
        AlertDialog(
            onDismissRequest = { confirmLink = false },
            title = { Text("Open in your browser?") },
            text = { Text("$link\n\nThis link was typed in by you and has not been checked by Discord or MeshCore.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLink = false
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }) { Text("Open") }
            },
            dismissButton = { TextButton(onClick = { confirmLink = false }) { Text("Cancel") } },
        )
    }

    if (editing) ContactProfileEditSheet(vm, keyHex, name, onDismiss = { editing = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactProfileEditSheet(vm: MeshCoreViewModel, keyHex: String, name: String, onDismiss: () -> Unit) {
    val existing = vm.profileMap.collectAsState().value[keyHex]
    val scope = rememberCoroutineScope()

    var discordUser by remember(keyHex) { mutableStateOf(existing?.discordUser.orEmpty()) }
    var discordId by remember(keyHex) { mutableStateOf(existing?.discordId.orEmpty()) }
    var discordLink by remember(keyHex) { mutableStateOf(existing?.discordLink.orEmpty()) }
    var description by remember(keyHex) { mutableStateOf(existing?.description.orEmpty()) }
    var notes by remember(keyHex) { mutableStateOf(existing?.notes.orEmpty()) }
    var label by remember(keyHex) { mutableStateOf(existing?.label.orEmpty()) }
    var favourite by remember(keyHex) { mutableStateOf(existing?.favourite ?: false) }
    var avatarLink by remember(keyHex) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = try {
                vm.profiles.setAvatarFromUri(keyHex, uri); "Photo saved on this phone."
            } catch (e: AvatarException) { e.message } catch (e: Exception) { "Could not use that image." }
            busy = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Profile for ${name.ifBlank { "this contact" }}", style = MaterialTheme.typography.titleLarge)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NodeAvatar(seed = keyHex, label = name.ifBlank { keyHex }, size = 64.dp)
                Column {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    ) { Text("Choose photo") }
                    if (existing?.avatarFile != null) {
                        TextButton(enabled = !busy, onClick = { scope.launch { vm.profiles.removeAvatar(keyHex) } }) {
                            Text("Remove photo")
                        }
                    }
                }
            }
            Text(
                "Photos are shrunk to 256 px and kept only on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = avatarLink, onValueChange = { avatarLink = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Discord avatar link (optional)") },
                supportingText = {
                    Text(
                        "In Discord, right-click their picture → Copy image address. Tapping Download makes one " +
                            "request to cdn.discordapp.com. Nothing is looked up automatically.",
                    )
                },
            )
            OutlinedButton(
                enabled = !busy && avatarLink.isNotBlank(),
                onClick = {
                    scope.launch {
                        busy = true
                        message = try {
                            vm.profiles.setAvatarFromDiscordLink(keyHex, avatarLink)
                            avatarLink = ""
                            "Discord picture saved on this phone."
                        } catch (e: AvatarException) { e.message } catch (e: Exception) { "Download failed." }
                        busy = false
                    }
                },
            ) { Text("Download Discord picture") }

            OutlinedTextField(
                value = discordUser, onValueChange = { discordUser = it.take(ProfileCardCodec.MAX_DISCORD_USER) },
                singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Discord username") },
            )
            OutlinedTextField(
                value = discordId, onValueChange = { discordId = it.filter(Char::isDigit).take(20) },
                singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Discord user ID (17–20 digits)") },
            )
            OutlinedTextField(
                value = discordLink, onValueChange = { discordLink = it.take(ProfileCardCodec.MAX_LINK) },
                singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Discord profile link") },
                supportingText = { Text("Only https://discord.com, discord.gg or discordapp.com links are kept.") },
            )
            if (ProfileCardCodec.isDiscordId(discordId) && discordLink.isBlank()) {
                TextButton(onClick = { discordLink = "https://discord.com/users/$discordId" }) { Text("Use link from ID") }
            }
            Text(
                "Discord details are labels you typed. They are not verified and no Discord login is used.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = label, onValueChange = { label = it.take(24) }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), label = { Text("Label (e.g. Family, Hiking)") },
            )
            OutlinedTextField(
                value = description, onValueChange = { description = it.take(ProfileCardCodec.MAX_DESCRIPTION) },
                modifier = Modifier.fillMaxWidth(), label = { Text("Short description") },
            )
            OutlinedTextField(
                value = notes, onValueChange = { notes = it.take(1000) },
                modifier = Modifier.fillMaxWidth(), label = { Text("Private notes") },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Favourite", Modifier.weight(1f))
                Switch(checked = favourite, onCheckedChange = { favourite = it })
            }

            val idError = discordId.isNotEmpty() && !ProfileCardCodec.isDiscordId(discordId)
            val linkError = discordLink.isNotBlank() && !ProfileCardCodec.isSafeDiscordLink(discordLink.trim())
            if (idError) Text("The Discord ID must be 17 to 20 digits.", color = MaterialTheme.colorScheme.error)
            if (linkError) Text("That link is not an accepted Discord link.", color = MaterialTheme.colorScheme.error)
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !busy && !idError && !linkError,
                    onClick = {
                        scope.launch {
                            val base = vm.profiles.get(keyHex)
                                ?: ContactProfileEntity(keyHex, null, null, null, null, null, null, false, null, 0L)
                            vm.profiles.save(
                                base.copy(
                                    discordUser = discordUser, discordId = discordId, discordLink = discordLink,
                                    description = description, notes = notes, label = label, favourite = favourite,
                                ),
                            )
                            onDismiss()
                        }
                    },
                ) { Text("Save") }
                if (existing != null) {
                    TextButton(enabled = !busy, onClick = { scope.launch { vm.profiles.delete(keyHex); onDismiss() } }) {
                        Text("Delete profile")
                    }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}
