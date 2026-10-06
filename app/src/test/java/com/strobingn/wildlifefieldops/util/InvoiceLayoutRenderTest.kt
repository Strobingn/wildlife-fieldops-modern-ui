package com.strobingn.wildlifefieldops.util

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Renders Sir's City Island invoice and estimate to real PDFs (vector text,
 * tools/render-standard-document-pdf.java) and PNGs
 * (tools/render-standard-document-pages.java) from the same layout ops the app
 * paints with PdfDocument. Output: app/build/invoice-layout-render, or
 * FIELDOPS_RENDER_DIR when set.
 */
class InvoiceLayoutRenderTest {
    @Test
    fun rendersCityIslandInvoiceAndEstimateToPdfAndPng() {
        val packet = CityIslandSample.packet()
        val documents = listOf(
            StandardDocuments.build(DocumentKind.INVOICE, packet),
            StandardDocuments.build(DocumentKind.ESTIMATE, packet.copy(documentNumber = "EST-1791311188225"))
        )
        val dir = File(System.getenv("FIELDOPS_RENDER_DIR")?.takeIf { it.isNotBlank() } ?: "build/invoice-layout-render")
        dir.mkdirs()
        val dump = File(dir, "layout.txt")
        dump.writeText(documents.joinToString("\n") { document -> dumpOf(document) })

        val logo = locate("app/src/main/assets/wildlife_whisperer_logo.png.b64")
        val classDir = File(dir, "classes").apply { mkdirs() }
        val javac = File(System.getProperty("java.home"), "bin/javac").absolutePath
        val java = File(System.getProperty("java.home"), "bin/java").absolutePath
        run(javac, "-d", classDir.absolutePath,
            locate("tools/render-standard-document-pages.java").absolutePath,
            locate("tools/render-standard-document-pdf.java").absolutePath)
        run(java, "-cp", classDir.absolutePath, "render_standard_document_pdf", dump.absolutePath, logo.absolutePath, dir.absolutePath)
        run(java, "-cp", classDir.absolutePath, "render_standard_document_pages", dump.absolutePath, logo.absolutePath, dir.absolutePath)

        documents.forEach { document ->
            val pdf = File(dir, "${document.kind.filePrefix}.pdf")
            assertTrue("${pdf.name} written", pdf.length() > 1000)
            assertTrue("${pdf.name} is a PDF", pdf.readBytes().copyOfRange(0, 5).decodeToString() == "%PDF-")
            val pdfText = pdf.readBytes().decodeToString()
            assertTrue(pdfText.contains("/Count ${document.layout().pages.size}"))
            assertTrue(File(dir, "${document.kind.filePrefix}.png").length() > 1000)
            assertTrue(File(dir, "${document.kind.filePrefix}-grayscale.png").length() > 1000)
        }
    }

    private fun dumpOf(document: StandardDocument): String =
        document.layout().pages.mapIndexed { index, page ->
            val name = if (index == 0) document.kind.filePrefix else "${document.kind.filePrefix}-p${index + 1}"
            buildString {
                append("PAGE\t$name\t${escape(document.title)}\t${page.width}\t${page.height}\n")
                page.ops.forEach { op ->
                    when (op.kind) {
                        "text" -> append(
                            "text\t${escape(op.text)}\t${op.x}\t${op.y}\t${op.size}\t${if (op.bold) 1 else 0}\t" +
                                "${if (op.italic) 1 else 0}\t${op.align}\t${op.color}\n"
                        )
                        "line" -> append("line\t${op.x}\t${op.y}\t${op.x2}\t${op.y2}\t${op.size}\t${op.color}\n")
                        "rect", "fillrect" -> append("${op.kind}\t${op.x}\t${op.y}\t${op.x2}\t${op.y2}\t${op.color}\n")
                        "logo" -> append("logo\t${op.x}\t${op.y}\t${op.x2}\n")
                    }
                }
            }
        }.joinToString("\n")

    private fun locate(relative: String): File {
        val candidates = listOf(File(relative), File("../$relative"), File("/workspace/$relative"))
        return candidates.firstOrNull { it.exists() } ?: candidates.first()
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "")

    private fun run(vararg command: String) {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { output.ifBlank { "command failed: ${command.joinToString(" ")}" } }
    }
}
