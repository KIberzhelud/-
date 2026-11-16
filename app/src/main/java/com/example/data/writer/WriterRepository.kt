package com.example.data.writer

import kotlinx.coroutines.flow.Flow

class WriterRepository(private val dao: WriterDao) {

    val books: Flow<List<BookEntity>> = dao.observeBooks()
    val allChapters: Flow<List<ChapterEntity>> = dao.observeAllChapters()
    val quickNotes: Flow<List<QuickNoteEntity>> = dao.observeQuickNotes()

    suspend fun createBook(
        title: String,
        author: String = "",
        genre: String = "",
        outlinePlan: String = ""
    ): Long {
        val now = System.currentTimeMillis()
        val bookId = dao.insertBook(
            BookEntity(
                title = title.trim(),
                author = author.trim(),
                genre = genre.trim(),
                outlinePlan = outlinePlan,
                createdAt = now,
                updatedAt = now
            )
        )
        dao.insertChapter(
            ChapterEntity(
                bookId = bookId,
                orderIndex = 0,
                title = "Глава 1",
                content = "",
                updatedAt = now
            )
        )
        return bookId
    }

    suspend fun updateBookOutline(book: BookEntity, outlinePlan: String) {
        dao.updateBook(book.copy(outlinePlan = outlinePlan, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateBookMeta(book: BookEntity, title: String, author: String, genre: String) {
        dao.updateBook(
            book.copy(
                title = title.trim().ifBlank { book.title },
                author = author.trim(),
                genre = genre.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteBook(bookId: Long) {
        dao.deleteBookById(bookId)
    }

    suspend fun addChapter(bookId: Long, title: String): Long {
        val existing = dao.getChaptersForBookOnce(bookId)
        val nextIndex = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val finalTitle = title.trim().ifBlank { "Глава ${existing.size + 1}" }
        return dao.insertChapter(
            ChapterEntity(
                bookId = bookId,
                orderIndex = nextIndex,
                title = finalTitle,
                content = "",
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateChapterContent(chapter: ChapterEntity, title: String, content: String) {
        val now = System.currentTimeMillis()
        dao.updateChapter(
            chapter.copy(
                title = title.ifBlank { chapter.title },
                content = content,
                updatedAt = now
            )
        )
    }

    suspend fun moveChapter(bookId: Long, chapterId: Long, delta: Int) {
        val chapters = dao.getChaptersForBookOnce(bookId).toMutableList()
        val idx = chapters.indexOfFirst { it.id == chapterId }
        if (idx < 0) return
        val target = idx + delta
        if (target !in chapters.indices) return
        val swapped = chapters[idx]
        chapters[idx] = chapters[target]
        chapters[target] = swapped
        chapters.forEachIndexed { index, ch ->
            if (ch.orderIndex != index) {
                dao.updateChapter(ch.copy(orderIndex = index))
            }
        }
    }

    suspend fun deleteChapter(chapterId: Long) {
        dao.deleteChapterById(chapterId)
    }

    suspend fun generateChaptersFromOutline(book: BookEntity): Int {
        val lines = book.outlinePlan
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                line.removePrefix("-").removePrefix("•").removePrefix("*")
                    .replace(Regex("""^\d+[.)]\s*"""), "")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .toList()

        if (lines.isEmpty()) return 0

        val existing = dao.getChaptersForBookOnce(book.id)
        val existingTitles = existing.map { it.title.lowercase() }.toSet()
        var nextIndex = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1
        var added = 0
        for (line in lines) {
            if (!existingTitles.contains(line.lowercase())) {
                dao.insertChapter(
                    ChapterEntity(
                        bookId = book.id,
                        orderIndex = nextIndex++,
                        title = line,
                        content = "",
                        updatedAt = System.currentTimeMillis()
                    )
                )
                added++
            }
        }
        return added
    }

    suspend fun addQuickNote(text: String, tag: String = ""): Long {
        return dao.insertQuickNote(
            QuickNoteEntity(
                text = text.trim(),
                tag = tag.trim(),
                isPinned = false,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun togglePinQuickNote(note: QuickNoteEntity) {
        dao.updateQuickNote(note.copy(isPinned = !note.isPinned))
    }

    suspend fun deleteQuickNote(noteId: Long) {
        dao.deleteQuickNoteById(noteId)
    }
}
