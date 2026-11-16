package com.example.data.writer

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DocxExporter {

    suspend fun writeDocxToUri(
        context: Context,
        uri: Uri,
        book: BookEntity,
        chapters: List<ChapterEntity>,
        includeOutline: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val out = context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Не удалось открыть файл для записи")
            out.use { stream ->
                writeDocxStream(stream, book, chapters, includeOutline)
            }
        }
    }

    suspend fun createShareableDocxUri(
        context: Context,
        book: BookEntity,
        chapters: List<ChapterEntity>,
        includeOutline: Boolean = true
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeName = sanitizeFilename(book.title.ifBlank { "KeyDeck_Book" })
            val file = File(dir, "$safeName.docx")
            FileOutputStream(file).use { stream ->
                writeDocxStream(stream, book, chapters, includeOutline)
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }
    }

    fun writeDocxStream(
        outputStream: OutputStream,
        book: BookEntity,
        chapters: List<ChapterEntity>,
        includeOutline: Boolean = true
    ) {
        ZipOutputStream(outputStream).use { zip ->
            addZipEntry(zip, "[Content_Types].xml", CONTENT_TYPES_XML)
            addZipEntry(zip, "_rels/.rels", RELS_XML)
            addZipEntry(zip, "word/_rels/document.xml.rels", DOCUMENT_RELS_XML)
            addZipEntry(zip, "word/styles.xml", STYLES_XML)
            addZipEntry(zip, "word/document.xml", buildDocumentXml(book, chapters, includeOutline))
        }
    }

    fun sanitizeFilename(raw: String): String {
        return raw.trim()
            .replace(Regex("""[\\/:*?"<>|]+"""), "_")
            .ifBlank { "book" }
            .take(48)
    }

    private fun addZipEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun buildDocumentXml(
        book: BookEntity,
        chapters: List<ChapterEntity>,
        includeOutline: Boolean
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append(
            """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">"""
        )
        sb.append("<w:body>")

        // Book Title
        sb.append(paragraphXml(book.title.ifBlank { "Без названия" }, style = "Title"))
        if (book.author.isNotBlank()) {
            sb.append(paragraphXml("Автор: ${book.author}", style = "Subtitle"))
        }
        if (book.genre.isNotBlank()) {
            sb.append(paragraphXml("Жанр: ${book.genre}", style = "Subtitle"))
        }
        sb.append(emptyParagraphXml())

        // Optional Book Plan / Outline
        if (includeOutline && book.outlinePlan.isNotBlank()) {
            sb.append(paragraphXml("План произведения", style = "Heading1"))
            book.outlinePlan.split("\n").forEach { line ->
                if (line.isBlank()) {
                    sb.append(emptyParagraphXml())
                } else {
                    sb.append(paragraphXml(line, style = "Normal"))
                }
            }
            sb.append(pageBreakXml())
        }

        // Chapters
        val orderedChapters = chapters.sortedWith(compareBy({ it.orderIndex }, { it.id }))
        orderedChapters.forEachIndexed { index, chapter ->
            if (index > 0) {
                sb.append(pageBreakXml())
            }
            sb.append(paragraphXml(chapter.title.ifBlank { "Глава ${index + 1}" }, style = "Heading1"))
            val bodyLines = chapter.content.split("\n")
            if (bodyLines.isEmpty()) {
                sb.append(emptyParagraphXml())
            } else {
                bodyLines.forEach { line ->
                    if (line.isBlank()) {
                        sb.append(emptyParagraphXml())
                    } else {
                        sb.append(paragraphXml(line, style = "Normal"))
                    }
                }
            }
        }

        sb.append(
            """<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr>"""
        )
        sb.append("</w:body></w:document>")
        return sb.toString()
    }

    private fun paragraphXml(text: String, style: String): String {
        val escaped = escapeXml(text)
        return buildString {
            append("<w:p>")
            append("""<w:pPr><w:pStyle w:val="$style"/></w:pPr>""")
            append("<w:r>")
            append("""<w:t xml:space="preserve">$escaped</w:t>""")
            append("</w:r>")
            append("</w:p>")
        }
    }

    private fun emptyParagraphXml(): String = "<w:p/>"

    private fun pageBreakXml(): String = """<w:p><w:r><w:br w:type="page"/></w:r></w:p>"""

    private fun escapeXml(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                '\t' -> sb.append("    ")
                else -> if (ch.code >= 0x20 || ch == '\n' || ch == '\r') sb.append(ch)
            }
        }
        return sb.toString()
    }

    private const val CONTENT_TYPES_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""

    private const val RELS_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    private const val DOCUMENT_RELS_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private const val STYLES_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
    <w:rPr><w:sz w:val="24"/><w:szCs w:val="24"/></w:rPr>
    <w:pPr><w:spacing w:after="140" w:line="276" w:lineRule="auto"/></w:pPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Title">
    <w:name w:val="Title"/>
    <w:rPr><w:b/><w:sz w:val="44"/><w:szCs w:val="44"/></w:rPr>
    <w:pPr><w:spacing w:after="160"/></w:pPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Subtitle">
    <w:name w:val="Subtitle"/>
    <w:rPr><w:i/><w:sz w:val="24"/><w:szCs w:val="24"/></w:rPr>
    <w:pPr><w:spacing w:after="120"/></w:pPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading1">
    <w:name w:val="heading 1"/>
    <w:rPr><w:b/><w:sz w:val="32"/><w:szCs w:val="32"/></w:rPr>
    <w:pPr><w:spacing w:before="240" w:after="160"/></w:pPr>
  </w:style>
</w:styles>"""
}
