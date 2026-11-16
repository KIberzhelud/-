package com.example.data.writer

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val author: String = "",
    val genre: String = "",
    val outlinePlan: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"])]
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val bookId: Long,
    val orderIndex: Int,
    val title: String,
    val content: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "quick_notes")
data class QuickNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val text: String,
    val tag: String = "",
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface WriterDao {

    @Query("SELECT * FROM books ORDER BY updatedAt DESC")
    fun observeBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY orderIndex ASC, id ASC")
    fun observeChaptersForBook(bookId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters ORDER BY bookId ASC, orderIndex ASC, id ASC")
    fun observeAllChapters(): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM quick_notes ORDER BY isPinned DESC, createdAt DESC")
    fun observeQuickNotes(): Flow<List<QuickNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBookById(bookId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity): Long

    @Update
    suspend fun updateChapter(chapter: ChapterEntity)

    @Query("DELETE FROM chapters WHERE id = :chapterId")
    suspend fun deleteChapterById(chapterId: Long)

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY orderIndex ASC, id ASC")
    suspend fun getChaptersForBookOnce(bookId: Long): List<ChapterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuickNote(note: QuickNoteEntity): Long

    @Update
    suspend fun updateQuickNote(note: QuickNoteEntity)

    @Query("DELETE FROM quick_notes WHERE id = :noteId")
    suspend fun deleteQuickNoteById(noteId: Long)
}

@Database(
    entities = [BookEntity::class, ChapterEntity::class, QuickNoteEntity::class],
    version = 1,
    exportSchema = false
)
abstract class WriterDatabase : RoomDatabase() {
    abstract fun writerDao(): WriterDao

    companion object {
        @Volatile
        private var INSTANCE: WriterDatabase? = null

        fun getInstance(context: Context): WriterDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WriterDatabase::class.java,
                    "keydeck_writer_studio.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
