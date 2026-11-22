package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.writer.BookEntity
import com.example.ui.theme.LocalVistaTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.symbianMetallicBar
import com.example.ui.theme.vistaInsetBox
import com.example.ui.theme.vistaWindowFrame

@Composable
fun CreateBookDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, author: String, genre: String) -> Unit
) {
    val theme = LocalVistaTheme.current
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }

    val maxDialogH = (LocalConfiguration.current.screenHeightDp.dp * 0.90f).coerceAtLeast(240.dp)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 500.dp)
                .heightIn(max = maxDialogH)
                .vistaWindowFrame(RoundedCornerShape(5.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianMetallicBar()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "НОВАЯ КНИГА",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = theme.focusBorder
                )
            }
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VistaLabeledInput(
                    label = "Название книги",
                    value = title,
                    onValueChange = { title = it },
                    placeholder = "Например, Старый город",
                    inputModifier = Modifier.testTag("create_book_title_input")
                )
                VistaLabeledInput(
                    label = "Автор (необязательно)",
                    value = author,
                    onValueChange = { author = it },
                    placeholder = "Имя автора"
                )
                VistaLabeledInput(
                    label = "Жанр / заметка",
                    value = genre,
                    onValueChange = { genre = it },
                    placeholder = "Фантастика, заметки…"
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    VistaButton(text = "Отмена", onClick = onDismiss)
                    Spacer(Modifier.width(8.dp))
                    VistaButton(
                        text = "Создать",
                        isPrimary = true,
                        enabled = title.isNotBlank(),
                        onClick = { onCreate(title, author, genre) },
                        modifier = Modifier.testTag("create_book_confirm")
                    )
                }
            }
        }
    }
}

@Composable
fun EditBookMetaDialog(
    book: BookEntity,
    onDismiss: () -> Unit,
    onSave: (title: String, author: String, genre: String) -> Unit,
    onDeleteBook: () -> Unit
) {
    val theme = LocalVistaTheme.current
    var title by remember(book.id) { mutableStateOf(book.title) }
    var author by remember(book.id) { mutableStateOf(book.author) }
    var genre by remember(book.id) { mutableStateOf(book.genre) }

    val maxDialogH = (LocalConfiguration.current.screenHeightDp.dp * 0.90f).coerceAtLeast(240.dp)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 500.dp)
                .heightIn(max = maxDialogH)
                .vistaWindowFrame(RoundedCornerShape(5.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianMetallicBar()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ПАРАМЕТРЫ КНИГИ",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = theme.focusBorder
                )
            }
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VistaLabeledInput(
                    label = "Название книги",
                    value = title,
                    onValueChange = { title = it },
                    placeholder = "Название"
                )
                VistaLabeledInput(
                    label = "Автор",
                    value = author,
                    onValueChange = { author = it },
                    placeholder = "Автор"
                )
                VistaLabeledInput(
                    label = "Жанр",
                    value = genre,
                    onValueChange = { genre = it },
                    placeholder = "Жанр"
                )
                VistaButton(
                    text = "Удалить книгу и главы",
                    onClick = onDeleteBook
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    VistaButton(text = "Отмена", onClick = onDismiss)
                    Spacer(Modifier.width(8.dp))
                    VistaButton(
                        text = "Сохранить",
                        isPrimary = true,
                        enabled = title.isNotBlank(),
                        onClick = { onSave(title, author, genre) }
                    )
                }
            }
        }
    }
}

@Composable
fun AddChapterDialog(
    suggestedNumber: Int,
    onDismiss: () -> Unit,
    onCreate: (title: String) -> Unit
) {
    val theme = LocalVistaTheme.current
    var title by remember { mutableStateOf("Глава $suggestedNumber") }

    val maxDialogH = (LocalConfiguration.current.screenHeightDp.dp * 0.90f).coerceAtLeast(240.dp)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 500.dp)
                .heightIn(max = maxDialogH)
                .vistaWindowFrame(RoundedCornerShape(5.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianMetallicBar()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "НОВАЯ ГЛАВА",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = theme.focusBorder
                )
            }
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VistaLabeledInput(
                    label = "Заголовок главы",
                    value = title,
                    onValueChange = { title = it },
                    placeholder = "Глава 1",
                    inputModifier = Modifier.testTag("add_chapter_title_input")
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    VistaButton(text = "Отмена", onClick = onDismiss)
                    Spacer(Modifier.width(8.dp))
                    VistaButton(
                        text = "Добавить",
                        isPrimary = true,
                        enabled = title.isNotBlank(),
                        onClick = { onCreate(title) },
                        modifier = Modifier.testTag("add_chapter_confirm")
                    )
                }
            }
        }
    }
}

@Composable
private fun VistaLabeledInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    inputModifier: Modifier = Modifier
) {
    val theme = LocalVistaTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .vistaInsetBox()
                .padding(horizontal = 9.dp, vertical = 8.dp)
        ) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                cursorBrush = SolidColor(theme.accent),
                modifier = inputModifier.fillMaxWidth()
            )
        }
    }
}
