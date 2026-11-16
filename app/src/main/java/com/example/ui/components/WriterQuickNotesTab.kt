package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.writer.QuickNoteEntity
import com.example.ui.theme.CallMissedRed
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MusicSourceAmber
import com.example.ui.theme.NavyDivider
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceElevated
import com.example.ui.theme.SymbianBevelHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TileBrightAzure
import com.example.ui.theme.symbianMetallicBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WriterQuickNotesTab(
    notes: List<QuickNoteEntity>,
    searchQuery: String,
    pinnedOnly: Boolean,
    hasActiveBook: Boolean,
    onSearchChange: (String) -> Unit,
    onTogglePinnedOnly: () -> Unit,
    onAddNote: (text: String, tag: String) -> Unit,
    onTogglePinNote: (QuickNoteEntity) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onAppendToBookPlan: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var draftText by remember { mutableStateOf("") }
    var draftTag by remember { mutableStateOf("") }

    fun submitNote() {
        val text = draftText.trim()
        if (text.isBlank()) return
        onAddNote(text, draftTag.trim())
        draftText = ""
        draftTag = ""
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Filter & Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NavySurface.copy(alpha = 0.85f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Поиск заметок",
                tint = TextMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Поиск по заметкам и #тегам…",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                    cursorBrush = SolidColor(ElectricCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notes_search_input")
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (pinnedOnly) MusicSourceAmber.copy(alpha = 0.25f) else NavySurfaceElevated)
                    .border(
                        1.dp,
                        if (pinnedOnly) MusicSourceAmber else NavyDivider,
                        RoundedCornerShape(3.dp)
                    )
                    .clickable(onClick = onTogglePinnedOnly)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("notes_pinned_filter")
            ) {
                Text(
                    text = if (pinnedOnly) "★ Закреплённые" else "Все (${notes.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (pinnedOnly) MusicSourceAmber else TextSecondary
                )
            }
        }
        HorizontalDivider(color = NavyDivider)

        // Saved Messages feed
        if (notes.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(R.drawable.ic_symbian_notes),
                        contentDescription = null,
                        modifier = Modifier.size(38.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Избранное пусто",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Записывайте идеи, диалоги, черновики сцен и мысли как короткие сообщения — они сохраняются локально и их можно сразу добавить в план книги.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("quick_notes_list"),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notes, key = { it.id }) { note ->
                    QuickNoteBubbleCard(
                        note = note,
                        hasActiveBook = hasActiveBook,
                        onTogglePin = { onTogglePinNote(note) },
                        onCopy = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cb?.setPrimaryClip(ClipData.newPlainText("KeyDeck Note", note.text))
                            onShowMessage("Скопировано в буфер")
                        },
                        onSendToPlan = { onAppendToBookPlan(note.text) },
                        onDelete = { onDeleteNote(note.id) }
                    )
                }
            }
        }

        HorizontalDivider(color = NavyDivider)

        // Bottom Saved-Messages-style composer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .symbianMetallicBar()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .width(105.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(NavySurface)
                        .border(1.dp, NavyDivider, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    if (draftTag.isEmpty()) {
                        Text("#тег", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    BasicTextField(
                        value = draftTag,
                        onValueChange = { draftTag = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = ElectricCyan),
                        cursorBrush = SolidColor(ElectricCyan),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(NavySurface)
                        .border(1.dp, SymbianBevelHighlight.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    if (draftText.isEmpty()) {
                        Text(
                            text = "Быстрая заметка в избранное (Enter — отправить)…",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    BasicTextField(
                        value = draftText,
                        onValueChange = { draftText = it },
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                        cursorBrush = SolidColor(ElectricCyan),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submitNote() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_note_composer_input")
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (draftText.isNotBlank()) TileBrightAzure else NavySurfaceElevated)
                        .border(1.dp, SymbianBevelHighlight.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .clickable(enabled = draftText.isNotBlank(), onClick = { submitNote() })
                        .testTag("quick_note_send_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Сохранить заметку",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickNoteBubbleCard(
    note: QuickNoteEntity,
    hasActiveBook: Boolean,
    onTogglePin: () -> Unit,
    onCopy: () -> Unit,
    onSendToPlan: () -> Unit,
    onDelete: () -> Unit
) {
    val timeStr = remember(note.createdAt) {
        SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(note.createdAt))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (note.isPinned) NavySurfaceElevated else NavySurface)
            .border(
                width = 1.dp,
                color = if (note.isPinned) MusicSourceAmber.copy(alpha = 0.7f) else NavyDivider,
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                if (note.tag.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (note.tag.startsWith("#")) note.tag else "#${note.tag}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ElectricCyan,
                        modifier = Modifier
                            .background(ElectricCyan.copy(alpha = 0.14f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTogglePin, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = if (note.isPinned) "Открепить" else "Закрепить",
                        tint = if (note.isPinned) MusicSourceAmber else TextMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }
                if (hasActiveBook) {
                    IconButton(onClick = onSendToPlan, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.BookmarkAdd,
                            contentDescription = "В план книги",
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Копировать",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Удалить",
                        tint = CallMissedRed.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = note.text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = TextPrimary
        )
    }
}
