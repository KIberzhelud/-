package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.writer.BookEntity
import com.example.data.writer.ChapterEntity
import com.example.data.writer.DocxExporter
import com.example.data.writer.QuickNoteEntity
import com.example.data.writer.WriterDatabase
import com.example.data.writer.WriterRepository
import com.example.model.BookSectionTab
import com.example.model.WriterSubTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WriterUiState(
    val subTab: WriterSubTab = WriterSubTab.BOOKS,
    val bookSectionTab: BookSectionTab = BookSectionTab.EDITOR,
    val books: List<BookEntity> = emptyList(),
    val selectedBookId: Long? = null,
    val selectedBookChapters: List<ChapterEntity> = emptyList(),
    val selectedChapterId: Long? = null,
    val quickNotes: List<QuickNoteEntity> = emptyList(),
    val notesSearchQuery: String = "",
    val notesPinnedOnly: Boolean = false,
    val statusMessage: String? = null
) {
    val activeBook: BookEntity?
        get() = books.firstOrNull { it.id == selectedBookId } ?: books.firstOrNull()

    val activeChapter: ChapterEntity?
        get() = selectedBookChapters.firstOrNull { it.id == selectedChapterId }
            ?: selectedBookChapters.firstOrNull()

    val filteredNotes: List<QuickNoteEntity>
        get() = quickNotes.filter { note ->
            val pinOk = !notesPinnedOnly || note.isPinned
            val queryOk = notesSearchQuery.isBlank() ||
                note.text.contains(notesSearchQuery, ignoreCase = true) ||
                note.tag.contains(notesSearchQuery, ignoreCase = true)
            pinOk && queryOk
        }
}

class WriterViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = WriterRepository(WriterDatabase.getInstance(application).writerDao())

    private val _subTab = MutableStateFlow(WriterSubTab.BOOKS)
    private val _bookSectionTab = MutableStateFlow(BookSectionTab.EDITOR)
    private val _selectedBookId = MutableStateFlow<Long?>(null)
    private val _selectedChapterId = MutableStateFlow<Long?>(null)
    private val _notesSearchQuery = MutableStateFlow("")
    private val _notesPinnedOnly = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)

    private val dataFlow = combine(
        repo.books,
        repo.allChapters,
        repo.quickNotes
    ) { books, chapters, notes ->
        Triple(books, chapters, notes)
    }

    private val selectionFlow = combine(
        _subTab,
        _bookSectionTab,
        _selectedBookId,
        _selectedChapterId,
        combine(_notesSearchQuery, _notesPinnedOnly, _statusMessage) { q, pin, msg -> Triple(q, pin, msg) }
    ) { sub, section, bookId, chapterId, notesMeta ->
        SelectionBundle(sub, section, bookId, chapterId, notesMeta.first, notesMeta.second, notesMeta.third)
    }

    private data class SelectionBundle(
        val subTab: WriterSubTab,
        val sectionTab: BookSectionTab,
        val bookId: Long?,
        val chapterId: Long?,
        val notesQuery: String,
        val notesPinnedOnly: Boolean,
        val statusMsg: String?
    )

    val uiState: StateFlow<WriterUiState> = combine(dataFlow, selectionFlow) { data, sel ->
        val (books, allChapters, notes) = data
        val activeBook = books.firstOrNull { it.id == sel.bookId } ?: books.firstOrNull()
        val bookChapters = if (activeBook != null) {
            allChapters.filter { it.bookId == activeBook.id }
        } else {
            emptyList()
        }
        val activeChapterId = sel.chapterId?.takeIf { id -> bookChapters.any { it.id == id } }
            ?: bookChapters.firstOrNull()?.id

        WriterUiState(
            subTab = sel.subTab,
            bookSectionTab = sel.sectionTab,
            books = books,
            selectedBookId = activeBook?.id,
            selectedBookChapters = bookChapters,
            selectedChapterId = activeChapterId,
            quickNotes = notes,
            notesSearchQuery = sel.notesQuery,
            notesPinnedOnly = sel.notesPinnedOnly,
            statusMessage = sel.statusMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = WriterUiState()
    )

    fun selectSubTab(tab: WriterSubTab) {
        _subTab.value = tab
    }

    fun selectBookSection(section: BookSectionTab) {
        _bookSectionTab.value = section
    }

    fun selectBook(bookId: Long) {
        _selectedBookId.value = bookId
        _selectedChapterId.value = null
    }

    fun selectChapter(chapterId: Long) {
        _selectedChapterId.value = chapterId
        _bookSectionTab.value = BookSectionTab.EDITOR
    }

    fun createBook(title: String, author: String = "", genre: String = "", outline: String = "") {
        if (title.isBlank()) return
        viewModelScope.launch {
            val id = repo.createBook(title, author, genre, outline)
            _selectedBookId.value = id
            _selectedChapterId.value = null
            _bookSectionTab.value = if (outline.isBlank()) BookSectionTab.PLAN else BookSectionTab.EDITOR
            showStatus("Книга «${title.trim()}» создана")
        }
    }

    fun updateBookMeta(book: BookEntity, title: String, author: String, genre: String) {
        viewModelScope.launch {
            repo.updateBookMeta(book, title, author, genre)
            showStatus("Параметры книги сохранены")
        }
    }

    fun updateBookOutline(book: BookEntity, outlinePlan: String) {
        viewModelScope.launch {
            repo.updateBookOutline(book, outlinePlan)
        }
    }

    fun deleteBook(bookId: Long) {
        viewModelScope.launch {
            repo.deleteBook(bookId)
            if (_selectedBookId.value == bookId) {
                _selectedBookId.value = null
                _selectedChapterId.value = null
            }
            showStatus("Книга удалена")
        }
    }

    fun addChapter(bookId: Long, title: String) {
        viewModelScope.launch {
            val id = repo.addChapter(bookId, title)
            _selectedChapterId.value = id
            _bookSectionTab.value = BookSectionTab.EDITOR
            showStatus("Глава добавлена")
        }
    }

    fun updateChapterContent(chapter: ChapterEntity, title: String, content: String) {
        viewModelScope.launch {
            repo.updateChapterContent(chapter, title, content)
        }
    }

    fun moveChapter(bookId: Long, chapterId: Long, delta: Int) {
        viewModelScope.launch {
            repo.moveChapter(bookId, chapterId, delta)
        }
    }

    fun deleteChapter(chapterId: Long) {
        viewModelScope.launch {
            repo.deleteChapter(chapterId)
            if (_selectedChapterId.value == chapterId) {
                _selectedChapterId.value = null
            }
            showStatus("Глава удалена")
        }
    }

    fun generateChaptersFromOutline(book: BookEntity) {
        viewModelScope.launch {
            val count = repo.generateChaptersFromOutline(book)
            if (count > 0) {
                showStatus("Создано глав по плану: $count")
            } else {
                showStatus("Новых строк в плане не найдено")
            }
        }
    }

    fun addQuickNote(text: String, tag: String = "") {
        if (text.isBlank()) return
        viewModelScope.launch {
            repo.addQuickNote(text, tag)
        }
    }

    fun togglePinQuickNote(note: QuickNoteEntity) {
        viewModelScope.launch {
            repo.togglePinQuickNote(note)
        }
    }

    fun deleteQuickNote(noteId: Long) {
        viewModelScope.launch {
            repo.deleteQuickNote(noteId)
        }
    }

    fun updateNotesSearch(query: String) {
        _notesSearchQuery.value = query
    }

    fun toggleNotesPinnedFilter() {
        _notesPinnedOnly.value = !_notesPinnedOnly.value
    }

    fun appendNoteToActivePlan(noteText: String) {
        val book = uiState.value.activeBook
        if (book == null) {
            showStatus("Сначала создайте или выберите книгу")
            return
        }
        viewModelScope.launch {
            val updated = if (book.outlinePlan.isBlank()) {
                "- $noteText"
            } else {
                "${book.outlinePlan.trimEnd()}\n- $noteText"
            }
            repo.updateBookOutline(book, updated)
            showStatus("Добавлено в план «${book.title}»")
        }
    }

    fun exportDocxToUri(uri: Uri) {
        val state = uiState.value
        val book = state.activeBook ?: return
        viewModelScope.launch {
            val result = DocxExporter.writeDocxToUri(
                context = getApplication(),
                uri = uri,
                book = book,
                chapters = state.selectedBookChapters
            )
            if (result.isSuccess) {
                showStatus("Экспортировано в .docx: ${book.title}")
            } else {
                showStatus("Ошибка экспорта: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun prepareShareDocx(onUriReady: (Uri, String) -> Unit) {
        val state = uiState.value
        val book = state.activeBook
        if (book == null) {
            showStatus("Выберите книгу для экспорта")
            return
        }
        viewModelScope.launch {
            val result = DocxExporter.createShareableDocxUri(
                context = getApplication(),
                book = book,
                chapters = state.selectedBookChapters
            )
            result.onSuccess { uri ->
                onUriReady(uri, "${DocxExporter.sanitizeFilename(book.title)}.docx")
            }.onFailure { err ->
                showStatus("Ошибка создания .docx: ${err.message}")
            }
        }
    }

    fun exportNotesToUri(uri: Uri) {
        val notes = uiState.value.filteredNotes
        if (notes.isEmpty()) {
            showStatus("Нет заметок для экспорта")
            return
        }
        viewModelScope.launch {
            val content = buildNotesPlainText(notes)
            val app = getApplication<Application>()
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Не удалось открыть файл")
                }
            }
            if (result.isSuccess) {
                showStatus("Заметки сохранены в файл (${notes.size})")
            } else {
                showStatus("Ошибка сохранения: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun prepareShareNotes(onUriReady: (Uri, String) -> Unit) {
        val notes = uiState.value.filteredNotes
        if (notes.isEmpty()) {
            showStatus("Нет заметок для экспорта")
            return
        }
        val app = getApplication<Application>()
        viewModelScope.launch {
            val content = buildNotesPlainText(notes)
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val dir = java.io.File(app.cacheDir, "exports").apply { mkdirs() }
                    val file = java.io.File(dir, "KeyDeck_Заметки.txt")
                    file.writeText(content, Charsets.UTF_8)
                    androidx.core.content.FileProvider.getUriForFile(
                        app,
                        "${app.packageName}.fileprovider",
                        file
                    )
                }
            }
            result.onSuccess { uri ->
                onUriReady(uri, "KeyDeck_Заметки.txt")
            }.onFailure { err ->
                showStatus("Ошибка экспорта заметок: ${err.message}")
            }
        }
    }

    private fun buildNotesPlainText(notes: List<QuickNoteEntity>): String {
        val fmt = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
        return buildString {
            appendLine("=== KeyDeck Блокнот / Избранное ===")
            appendLine()
            notes.forEach { note ->
                val date = fmt.format(java.util.Date(note.createdAt))
                val pinPrefix = if (note.isPinned) "[★] " else ""
                val tagSuffix = if (note.tag.isNotBlank()) " #${note.tag.removePrefix("#")}" else ""
                appendLine("$pinPrefix$date$tagSuffix")
                appendLine(note.text)
                appendLine("---")
            }
        }
    }

    fun showStatus(message: String) {
        _statusMessage.value = message
        viewModelScope.launch {
            delay(2400L)
            if (_statusMessage.value == message) {
                _statusMessage.value = null
            }
        }
    }
}
