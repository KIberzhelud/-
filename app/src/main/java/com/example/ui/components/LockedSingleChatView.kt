package com.example.ui.components

import android.content.ClipDescription
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LockedChatThread
import com.example.ui.L10n
import com.example.ui.LocalAppLang
import com.example.ui.theme.LocalVistaTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.symbianGlossyTile
import com.example.ui.theme.vistaAeroButton
import com.example.ui.theme.vistaInsetBox
import com.example.ui.theme.vistaWindowFrame

/**
 * Isolated single-conversation pane rendered directly in the launcher tile area when opening a
 * chat from search. Keeps user strictly locked inside the conversation (BackHandler exits back
 * to tiles cleanly) and accepts dropped files/images for quick sending.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LockedSingleChatView(
    thread: LockedChatThread,
    onSendReply: (String) -> Boolean,
    onDropUri: (android.net.Uri, String) -> Unit,
    onOpenInApp: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onClose() }

    val theme = LocalVistaTheme.current
    val lang = LocalAppLang.current
    var draft by remember(thread.key) { mutableStateOf("") }
    var isDragOver by remember { mutableStateOf(false) }

    val dropTarget = remember(thread.key) {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                isDragOver = true
            }

            override fun onEntered(event: DragAndDropEvent) {
                isDragOver = true
            }

            override fun onExited(event: DragAndDropEvent) {
                isDragOver = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                isDragOver = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                isDragOver = false
                val clip = event.toAndroidDragEvent().clipData ?: return false
                for (i in 0 until clip.itemCount) {
                    val uri = clip.getItemAt(i).uri
                    if (uri != null) {
                        val mime = clip.description.getMimeType(0) ?: "*/*"
                        onDropUri(uri, mime)
                        return true
                    }
                }
                return false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .vistaWindowFrame(RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    event.mimeTypes().any { it.startsWith("image/") || it == "*/*" || it == ClipDescription.MIMETYPE_TEXT_URILIST }
                },
                target = dropTarget
            )
            .let {
                if (isDragOver) it.border(2.dp, theme.accent, RoundedCornerShape(4.dp)) else it
            }
            .padding(8.dp)
    ) {
        // Top conversation header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .testTag("locked_chat_back_btn")
                    .vistaAeroButton(isPrimary = false, isActive = false)
                    .clickable(onClick = onClose)
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text("← Назад", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
            }
            thread.appIcon?.let { icon ->
                Image(bitmap = icon, contentDescription = thread.appName, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = thread.contactName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${thread.appName} • ${thread.timeFormatted}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = theme.accent,
                    maxLines = 1
                )
            }
            if (!thread.key.startsWith("contact:")) {
                VistaButton(
                    text = "Открыть",
                    onClick = { onOpenInApp(thread.key) }
                )
            }
        }

        // Messages container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .vistaInsetBox()
                .padding(8.dp)
        ) {
            if (thread.messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Нет истории сообщений • введите ответ ниже",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(thread.messages) { msg ->
                        val isMe = msg.sender == "Вы"
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .symbianGlossyTile(bucket = if (isMe) 1 else 3, shape = RoundedCornerShape(4.dp))
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = msg.sender,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isMe) theme.accent else TextSecondary
                            )
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            if (isDragOver) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(theme.background.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Отпустите файл для отправки → ${thread.contactName}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = theme.accent
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = L10n.dropToChatHint(lang, thread.contactName),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))

        // Reply input row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .vistaInsetBox(isFocused = draft.isNotEmpty())
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                    cursorBrush = SolidColor(theme.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (draft.isNotBlank() && onSendReply(draft)) {
                                draft = ""
                            }
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 34.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (draft.isEmpty()) {
                                Text(
                                    text = if (thread.canReplyInline || !thread.fallbackPhoneNumber.isNullOrBlank()) {
                                        "Сообщение для ${thread.contactName}…"
                                    } else {
                                        "Быстрый ответ (SMS / уведомление)…"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("locked_chat_input")
                )
            }
            VistaButton(
                text = "Отпр.",
                isPrimary = true,
                enabled = draft.isNotBlank(),
                onClick = {
                    if (onSendReply(draft)) {
                        draft = ""
                    }
                },
                modifier = Modifier.testTag("locked_chat_send_btn")
            )
        }
    }
}
