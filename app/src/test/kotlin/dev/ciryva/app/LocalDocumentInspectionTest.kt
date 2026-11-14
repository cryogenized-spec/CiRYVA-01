package dev.ciryva.app

import dev.ciryva.core.stream.ResponseAttachment
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalDocumentInspectionTest {
    @Test fun docxRequiresBothWordDocumentAndContentTypesMembers() {
        val file = File.createTempFile("word-test-", ".zip")
        try {
            ZipOutputStream(file.outputStream()).use { archive ->
                archive.putNextEntry(ZipEntry("[Content_Types].xml"))
                archive.write("<Types/>".toByteArray())
                archive.closeEntry()
                archive.putNextEntry(ZipEntry("word/document.xml"))
                archive.write("<document/>".toByteArray())
                archive.closeEntry()
            }
            assertEquals(ResponseAttachment.DOCX_MIME,
                LocalAttachmentStore.inspectDocument(file, "customer.docx"))
        } finally { file.delete() }
    }

    @Test fun arbitraryZipRenamedDocxIsRejected() {
        val file = File.createTempFile("fake-test-", ".zip")
        try {
            ZipOutputStream(file.outputStream()).use { archive ->
                archive.putNextEntry(ZipEntry("random.txt"))
                archive.write("not Word".toByteArray())
                archive.closeEntry()
            }
            assertThrows(IllegalArgumentException::class.java) {
                LocalAttachmentStore.inspectDocument(file, "customer.docx")
            }
        } finally { file.delete() }
    }

    @Test fun textRequiresUtf8AndDocumentExtension() {
        val file = File.createTempFile("text-test-", ".txt")
        try {
            file.writeText("# Café\n\n- [x] Item")
            assertEquals("text/markdown",
                LocalAttachmentStore.inspectDocument(file, "notes.md"))
            assertEquals("text/csv",
                LocalAttachmentStore.inspectDocument(file, "table.csv"))
            assertEquals("text/plain",
                LocalAttachmentStore.inspectDocument(file, "notes.txt"))
            file.writeBytes(byteArrayOf(0, -1, -2, -3))
            assertThrows(IllegalArgumentException::class.java) {
                LocalAttachmentStore.inspectDocument(file, "binary.txt")
            }
            assertThrows(IllegalArgumentException::class.java) {
                LocalAttachmentStore.inspectDocument(file, "binary.bin")
            }
        } finally { file.delete() }
    }
}
