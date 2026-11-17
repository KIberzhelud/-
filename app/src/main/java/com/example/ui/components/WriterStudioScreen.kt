package com.example.ui.components

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.writer.BookEntity
import com.example.data.writer.ChapterEntity
import com.example.data.writer.DocxExporter
import com.example.model.BookSectionTab
import com.example.model.WriterSubTab
import com.example.ui.theme.CallIncomingGreen
import com.example.ui.theme.CallMissedRed
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.FocusBorderCyan
import com.example.ui.theme.MusicSourceAmber
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavyDivider
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceElevated
import com.example.ui.theme.SymbianBevelHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TileBrightAzure
import com.example.ui.theme.TileDeepCobalt
import com.example.ui.theme.symbianMetallicBar
import com.example.ui.theme.vistaAeroButton
import com.example.ui.theme.vistaInsetBox
import com.example.ui.theme.vistaWindowFrame
import com.example.viewmodel.WriterUiState
import com.example.viewmodel.WriterViewModel
import kotlinx.coroutines.delay

@Composable
fun WriterStudioScreen(
    writerViewModel: WriterViewModel,
    isLandscape: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by writerViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showCreateBookDialog by remember { mutableStateOf(false) }
    var showEditBookDialog by remember { mutableStateOf(false) }
    var showAddChapterDialog by remember { mutableStateOf(false) }

    val saveDocxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )
    ) { uri ->
        if (uri != null) {
            writerViewModel.exportDocxToUri(uri)
        }
    }

    val saveNotesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            writerViewModel.exportNotesToUri(uri)
        }
    }

    BackHandler {
        onClose()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NavyBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .testTag("writer_studio_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Symbian Writer Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianMetallicBar()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .vistaAeroButton()
                        .clickable(onClick = onClose)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("writer_close_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Вернуться в лаунчер",
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Лаунчер",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                WriterModeTabChip(
                    label = WriterSubTab.BOOKS.title,
                    iconRes = R.drawable.ic_symbian_editor,
                    selected = state.subTab == WriterSubTab.BOOKS,
                    onClick = { writerViewModel.selectSubTab(WriterSubTab.BOOKS) },
                    modifier = Modifier.testTag("writer_tab_books")
                )
                Spacer(Modifier.width(6.dp))
                WriterModeTabChip(
                    label = "${WriterSubTab.NOTES.title} (${state.quickNotes.size})",
                    iconRes = R.drawable.ic_symbian_notes,
                    selected = state.subTab == WriterSubTab.NOTES,
                    onClick = { writerViewModel.selectSubTab(WriterSubTab.NOTES) },
                    modifier = Modifier.testTag("writer_tab_notes")
                )

                Spacer(Modifier.weight(1f))

                if (state.subTab == WriterSubTab.BOOKS && state.activeBook != null) {
                    val activeBook = state.activeBook!!
                    Box(
                        modifier = Modifier
                            .vistaAeroButton(isPrimary = true)
                            .clickable {
                                val fileName = "${DocxExporter.sanitizeFilename(activeBook.title)}.docx"
                                saveDocxLauncher.launch(fileName)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("writer_export_docx_save")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(R.drawable.ic_symbian_docx),
                                contentDescription = "Сохранить DOCX",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = ".DOCX",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .vistaAeroButton()
                            .clickable {
                                writerViewModel.prepareShareDocx { uri, fileName ->
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, fileName)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "Отправить книгу (.docx)")
                                    )
                                }
                            }
                            .padding(horizontal = 7.dp, vertical = 6.dp)
                            .testTag("writer_export_docx_share")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Поделиться .docx",
                            tint = ElectricCyan,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                } else if (state.subTab == WriterSubTab.NOTES && state.filteredNotes.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .vistaAeroButton(isPrimary = true)
                            .clickable {
                                saveNotesLauncher.launch("KeyDeck_Заметки.txt")
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("writer_export_notes_save")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(R.drawable.ic_symbian_notes),
                                contentDescription = "Сохранить заметки",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = ".TXT",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .vistaAeroButton()
                            .clickable {
                                writerViewModel.prepareShareNotes { uri, fileName ->
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, fileName)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "Отправить заметки (.txt)")
                                    )
                                }
                            }
                            .padding(horizontal = 7.dp, vertical = 6.dp)
                            .testTag("writer_export_notes_share")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Поделиться заметками",
                            tint = ElectricCyan,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
            HorizontalDivider(color = NavyDivider)

            when (state.subTab) {
                WriterSubTab.BOOKS -> {
                    BooksWorkspace(
                        state = state,
                        isLandscape = isLandscape,
                        writerViewModel = writerViewModel,
                        onOpenCreateBook = { showCreateBookDialog = true },
                        onOpenEditBook = { showEditBookDialog = true },
                        onOpenAddChapter = { showAddChapterDialog = true }
                    )
                }

                WriterSubTab.NOTES -> {
                    WriterQuickNotesTab(
                        notes = state.filteredNotes,
                        searchQuery = state.notesSearchQuery,
                        pinnedOnly = state.notesPinnedOnly,
                        hasActiveBook = state.activeBook != null,
                        onSearchChange = writerViewModel::updateNotesSearch,
                        onTogglePinnedOnly = writerViewModel::toggleNotesPinnedFilter,
                        onAddNote = writerViewModel::addQuickNote,
                        onTogglePinNote = writerViewModel::togglePinQuickNote,
                        onDeleteNote = writerViewModel::deleteQuickNote,
                        onAppendToBookPlan = writerViewModel::appendNoteToActivePlan,
                        onShowMessage = writerViewModel::showStatus,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Studio status toast
        AnimatedVisibility(
            visible = state.statusMessage != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp, start = 16.dp, end = 16.dp)
        ) {
            state.statusMessage?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = NavySurfaceElevated,
                    modifier = Modifier.border(1.dp, ElectricCyan, RoundedCornerShape(6.dp))
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    if (showCreateBookDialog) {
        CreateBookDialog(
            onDismiss = { showCreateBookDialog = false },
            onCreate = { title, author, genre ->
                writerViewModel.createBook(title, author, genre)
                showCreateBookDialog = false
            }
        )
    }

    if (showEditBookDialog && state.activeBook != null) {
        val book = state.activeBook!!
        EditBookMetaDialog(
            book = book,
            onDismiss = { showEditBookDialog = false },
            onSave = { title, author, genre ->
                writerViewModel.updateBookMeta(book, title, author, genre)
                showEditBookDialog = false
            },
            onDeleteBook = {
                writerViewModel.deleteBook(book.id)
                showEditBookDialog = false
            }
        )
    }

    if (showAddChapterDialog && state.activeBook != null) {
        val book = state.activeBook!!
        AddChapterDialog(
            suggestedNumber = state.selectedBookChapters.size + 1,
            onDismiss = { showAddChapterDialog = false },
            onCreate = { title ->
                writerViewModel.addChapter(book.id, title)
                showAddChapterDialog = false
            }
        )
    }
}

@Composable
private fun BooksWorkspace(
    state: WriterUiState,
    isLandscape: Boolean,
    writerViewModel: WriterViewModel,
    onOpenCreateBook: () -> Unit,
    onOpenEditBook: () -> Unit,
    onOpenAddChapter: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Book shelf bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NavySurface.copy(alpha = 0.9f))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .vistaAeroButton(isPrimary = true)
                    .clickable(onClick = onOpenCreateBook)
                    .padding(horizontal = 9.dp, vertical = 5.dp)
                    .testTag("writer_new_book_button")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Книга", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                }
            }

            state.books.forEach { book ->
                val selected = book.id == state.activeBook?.id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selected) TileBrightAzure.copy(alpha = 0.35f) else NavySurfaceElevated)
                        .border(
                            1.dp,
                            if (selected) FocusBorderCyan else NavyDivider,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { writerViewModel.selectBook(book.id) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (selected) ElectricCyan else TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (state.activeBook != null) {
                IconButton(onClick = onOpenEditBook, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Параметры книги",
                        tint = TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
        HorizontalDivider(color = NavyDivider)

        val activeBook = state.activeBook
        if (activeBook == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(R.drawable.ic_symbian_editor),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Студия книг KeyDeck",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Создавайте книги, планируйте сюжет заранее, разбивайте на главы и экспортируйте в .docx",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(14.dp))
                    VistaButton(
                        text = "Создать первую книгу",
                        isPrimary = true,
                        onClick = onOpenCreateBook,
                        modifier = Modifier.testTag("writer_create_first_book")
                    )
                }
            }
            return
        }

        if (isLandscape) {
            // Landscape split studio: Outline/Chapters on left, Chapter Editor on right
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .width(245.dp)
                        .fillMaxHeight()
                        .background(NavySurface.copy(alpha = 0.65f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SectionPill(
                            title = BookSectionTab.PLAN.title,
                            selected = state.bookSectionTab == BookSectionTab.PLAN,
                            onClick = { writerViewModel.selectBookSection(BookSectionTab.PLAN) },
                            modifier = Modifier.weight(1f)
                        )
                        SectionPill(
                            title = "Главы (${state.selectedBookChapters.size})",
                            selected = state.bookSectionTab != BookSectionTab.PLAN,
                            onClick = { writerViewModel.selectBookSection(BookSectionTab.CHAPTERS) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    HorizontalDivider(color = NavyDivider)

                    if (state.bookSectionTab == BookSectionTab.PLAN) {
                        BookPlanPane(
                            book = activeBook,
                            onOutlineChange = { writerViewModel.updateBookOutline(activeBook, it) },
                            onGenerateChapters = { writerViewModel.generateChaptersFromOutline(activeBook) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        ChaptersListPane(
                            chapters = state.selectedBookChapters,
                            selectedChapterId = state.activeChapter?.id,
                            onSelectChapter = writerViewModel::selectChapter,
                            onAddChapter = onOpenAddChapter,
                            onMoveChapter = { chId, delta -> writerViewModel.moveChapter(activeBook.id, chId, delta) },
                            onDeleteChapter = writerViewModel::deleteChapter,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                VerticalDivider(color = NavyDivider)

                ChapterTextEditorPane(
                    chapter = state.activeChapter,
                    onChapterChange = { ch, title, content ->
                        writerViewModel.updateChapterContent(ch, title, content)
                    },
                    onAddChapter = onOpenAddChapter,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        } else {
            // Portrait: 3 switchable sections (План | Главы | Текст)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NavySurfaceElevated)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SectionPill(
                    title = BookSectionTab.PLAN.title,
                    selected = state.bookSectionTab == BookSectionTab.PLAN,
                    onClick = { writerViewModel.selectBookSection(BookSectionTab.PLAN) },
                    modifier = Modifier.weight(1f).testTag("book_section_plan")
                )
                SectionPill(
                    title = "Главы (${state.selectedBookChapters.size})",
                    selected = state.bookSectionTab == BookSectionTab.CHAPTERS,
                    onClick = { writerViewModel.selectBookSection(BookSectionTab.CHAPTERS) },
                    modifier = Modifier.weight(1f).testTag("book_section_chapters")
                )
                SectionPill(
                    title = BookSectionTab.EDITOR.title,
                    selected = state.bookSectionTab == BookSectionTab.EDITOR,
                    onClick = { writerViewModel.selectBookSection(BookSectionTab.EDITOR) },
                    modifier = Modifier.weight(1f).testTag("book_section_editor")
                )
            }
            HorizontalDivider(color = NavyDivider)

            when (state.bookSectionTab) {
                BookSectionTab.PLAN -> {
                    BookPlanPane(
                        book = activeBook,
                        onOutlineChange = { writerViewModel.updateBookOutline(activeBook, it) },
                        onGenerateChapters = { writerViewModel.generateChaptersFromOutline(activeBook) },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }

                BookSectionTab.CHAPTERS -> {
                    ChaptersListPane(
                        chapters = state.selectedBookChapters,
                        selectedChapterId = state.activeChapter?.id,
                        onSelectChapter = writerViewModel::selectChapter,
                        onAddChapter = onOpenAddChapter,
                        onMoveChapter = { chId, delta -> writerViewModel.moveChapter(activeBook.id, chId, delta) },
                        onDeleteChapter = writerViewModel::deleteChapter,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }

                BookSectionTab.EDITOR -> {
                    ChapterTextEditorPane(
                        chapter = state.activeChapter,
                        onChapterChange = { ch, title, content ->
                            writerViewModel.updateChapterContent(ch, title, content)
                        },
                        onAddChapter = onOpenAddChapter,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun BookPlanPane(
    book: BookEntity,
    onOutlineChange: (String) -> Unit,
    onGenerateChapters: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draftPlan by remember(book.id) { mutableStateOf(book.outlinePlan) }

    LaunchedEffect(book.id, book.outlinePlan) {
        if (book.outlinePlan != draftPlan && draftPlan.isEmpty()) {
            draftPlan = book.outlinePlan
        }
    }

    LaunchedEffect(book.id, draftPlan) {
        if (draftPlan != book.outlinePlan) {
            delay(250L)
            onOutlineChange(draftPlan)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "План книги",
                style = MaterialTheme.typography.titleSmall,
                color = ElectricCyan
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(TileBrightAzure)
                    .border(1.dp, SymbianBevelHighlight.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .clickable {
                        onOutlineChange(draftPlan)
                        onGenerateChapters()
                    }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .testTag("generate_chapters_from_plan")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Создать главы", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Пишите по одной главе на строку (например: «1. Встреча на перроне»), затем нажмите «Создать главы».",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .vistaInsetBox()
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            if (draftPlan.isEmpty()) {
                Text(
                    text = "Пролог\nГлава 1. Начало пути\nГлава 2. Старый город...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
            BasicTextField(
                value = draftPlan,
                onValueChange = { draftPlan = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = TextPrimary,
                    lineHeight = 22.sp
                ),
                cursorBrush = SolidColor(ElectricCyan),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("book_outline_input")
            )
        }
    }
}

@Composable
private fun ChaptersListPane(
    chapters: List<ChapterEntity>,
    selectedChapterId: Long?,
    onSelectChapter: (Long) -> Unit,
    onAddChapter: () -> Unit,
    onMoveChapter: (chapterId: Long, delta: Int) -> Unit,
    onDeleteChapter: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Главы книги",
                style = MaterialTheme.typography.titleSmall,
                color = ElectricCyan
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(TileBrightAzure)
                    .clickable(onClick = onAddChapter)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("add_chapter_button")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Глава", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                }
            }
        }
        HorizontalDivider(color = NavyDivider)

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(chapters, key = { _, ch -> ch.id }) { idx, chapter ->
                val isSelected = chapter.id == selectedChapterId
                val words = remember(chapter.content) {
                    chapter.content.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (isSelected) TileBrightAzure.copy(alpha = 0.25f) else NavySurfaceElevated)
                        .border(
                            1.dp,
                            if (isSelected) FocusBorderCyan else NavyDivider,
                            RoundedCornerShape(5.dp)
                        )
                        .clickable { onSelectChapter(chapter.id) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${idx + 1}. ${chapter.title}",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isSelected) FocusBorderCyan else TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Слов: $words • Симв.: ${chapter.content.length}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                    if (idx > 0) {
                        IconButton(onClick = { onMoveChapter(chapter.id, -1) }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Rounded.ArrowUpward, contentDescription = "Выше", tint = TextSecondary, modifier = Modifier.size(15.dp))
                        }
                    }
                    if (idx < chapters.lastIndex) {
                        IconButton(onClick = { onMoveChapter(chapter.id, +1) }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Rounded.ArrowDownward, contentDescription = "Ниже", tint = TextSecondary, modifier = Modifier.size(15.dp))
                        }
                    }
                    IconButton(onClick = { onDeleteChapter(chapter.id) }, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Удалить главу", tint = CallMissedRed, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterTextEditorPane(
    chapter: ChapterEntity?,
    onChapterChange: (chapter: ChapterEntity, title: String, content: String) -> Unit,
    onAddChapter: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (chapter == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Нет выбранной главы", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onAddChapter,
                    colors = ButtonDefaults.buttonColors(containerColor = TileBrightAzure)
                ) {
                    Text("Добавить главу")
                }
            }
        }
        return
    }

    var draftTitle by remember(chapter.id) { mutableStateOf(chapter.title) }
    var draftContent by remember(chapter.id) { mutableStateOf(chapter.content) }

    LaunchedEffect(chapter.id, draftTitle, draftContent) {
        if (draftTitle != chapter.title || draftContent != chapter.content) {
            delay(250L)
            onChapterChange(chapter, draftTitle, draftContent)
        }
    }

    val wordCount = remember(draftContent) {
        draftContent.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        // Chapter title + stats
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(NavySurfaceElevated)
                .border(1.dp, NavyDivider, RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = draftTitle,
                onValueChange = { draftTitle = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold
                ),
                cursorBrush = SolidColor(ElectricCyan),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chapter_title_input")
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$wordCount сл.",
                style = MaterialTheme.typography.labelSmall,
                color = CallIncomingGreen
            )
        }

        Spacer(Modifier.height(6.dp))

        // Quick writing punctuation chips for hardware keyboard speed
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("— " to "Тире", "«»" to "Кавычки", "\n***\n" to "Разрыв", "…" to "Многоточие").forEach { (insert, label) ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(NavySurface)
                        .border(1.dp, NavyDivider, RoundedCornerShape(3.dp))
                        .clickable { draftContent += insert }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "${draftContent.length} симв.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }

        Spacer(Modifier.height(6.dp))

        // Main chapter prose surface
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .vistaInsetBox()
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            if (draftContent.isEmpty()) {
                Text(
                    text = "Пишите текст главы здесь… Сохраняется автоматически; готовая книга экспортируется кнопкой .DOCX вверху.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
            BasicTextField(
                value = draftContent,
                onValueChange = { draftContent = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = TextPrimary,
                    fontSize = 15.5.sp,
                    lineHeight = 23.sp
                ),
                cursorBrush = SolidColor(ElectricCyan),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("chapter_content_input")
            )
        }
    }
}

@Composable
private fun WriterModeTabChip(
    label: String,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .vistaAeroButton(isPrimary = selected, isActive = selected)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (selected) Color.White else TextSecondary,
            maxLines = 1
        )
    }
}

@Composable
private fun SectionPill(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .vistaAeroButton(isPrimary = selected, isActive = selected)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (selected) Color.White else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
