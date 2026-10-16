package com.sorenkai.writings.service

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import java.io.InputStream

class DocxToMarkdownService {
    fun convertToMarkdown(inputStream: InputStream): String {
        val document = XWPFDocument(inputStream)
        val markdown = StringBuilder()

        for (bodyElement in document.bodyElements) {
            when (bodyElement) {
                is XWPFParagraph -> {
                    val text = processParagraph(bodyElement)
                    if (text.isNotBlank()) {
                        markdown.append(text).append("\n\n")
                    }
                }
                is XWPFTable -> {
                    // Ignore layout-only artifacts as per requirements
                }
            }
        }

        return markdown.toString().trim()
    }

    private fun processParagraph(paragraph: XWPFParagraph): String {
        val text = StringBuilder()

        // Handle Headings
        val style = paragraph.styleID
        if (style != null) {
            when {
                style.startsWith("Heading1", ignoreCase = true) -> text.append("# ")
                style.startsWith("Heading2", ignoreCase = true) -> text.append("## ")
                style.startsWith("Heading3", ignoreCase = true) -> text.append("### ")
            }
        }

        // Handle Lists
        if (paragraph.numID != null) {
            val numIlvl = paragraph.numIlvl?.toInt() ?: 0
            val indent = "  ".repeat(numIlvl)

            // Simplified list handling: check if it's ordered or unordered
            // In POI, it depends on the numbering definition.
            // For a preview, we'll use '-' for bullets and '1.' for numbered if we can distinguish
            // or just '-' for all to be safe if complex.
            // Let's try to detect if it's numbered.
            val numbering = paragraph.document.numbering
            val numId = paragraph.numID
            val abstractNumId = numbering.getAbstractNumID(numId)
            val abstractNum = numbering.getAbstractNum(abstractNumId)
            val level = abstractNum.ctAbstractNum.getLvlArray(numIlvl)
            val fmt = level?.numFmt?.`val`

            if (fmt.toString() == "decimal") {
                text.append("${indent}1. ")
            } else {
                text.append("$indent- ")
            }
        }

        for (run in paragraph.runs) {
            var runText = run.getText(0) ?: ""
            if (runText.isEmpty()) continue

            if (run.isBold) {
                runText = "**$runText**"
            }
            if (run.isItalic) {
                runText = "_${runText}_"
            }
            text.append(runText)
        }

        return text.toString()
    }
}




