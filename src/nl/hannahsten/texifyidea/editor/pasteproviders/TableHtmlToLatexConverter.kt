package nl.hannahsten.texifyidea.editor.pasteproviders

import nl.hannahsten.texifyidea.action.wizard.table.ColumnType
import nl.hannahsten.texifyidea.action.wizard.table.LatexTableWizardAction
import nl.hannahsten.texifyidea.action.wizard.table.TableCreationDialogWrapper
import nl.hannahsten.texifyidea.action.wizard.table.TableCreationTableModel
import nl.hannahsten.texifyidea.file.LatexFile
import nl.hannahsten.texifyidea.util.toVector
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.*

/**
 * Convert HTML tables to LaTeX using the [TableCreationDialogWrapper].
 */
class TableHtmlToLatexConverter : HtmlToLatexConverter {

    override fun convertHtmlToLatex(htmlIn: Element, file: LatexFile): String {
        return LatexTableWizardAction().getTableTextWithDialog(
            file.project,
            htmlIn.ownerDocument()?.toTableDialogWrapper(file) ?: return ""
        )
    }

    /**
     * Creates the Table Creation Dialog filled in with the data from the clipboard.
     */
    @Suppress("USELESS_CAST")
    private fun Document.toTableDialogWrapper(latexFile: LatexFile): TableCreationDialogWrapper? {
        // 1) 获取所有行
        val rows = select("table tr")
        if (rows.isEmpty()) return null

        // 2) 识别表头行：优先 <thead>，其次含 <th>，再者含 colspan/rowspan，最后 fallback 第一行
        val theadRows = select("table thead tr")
        val headerRows = when {
            theadRows.isNotEmpty() -> theadRows
            rows.any { it.select("th").isNotEmpty() } -> rows.filter { it.select("th").isNotEmpty() }
            rows.any { it.select("td,th").any { cell -> cell.hasAttr("colspan") || cell.hasAttr("rowspan") } } ->
                rows.filter { it.select("td,th").any { cell -> cell.hasAttr("colspan") || cell.hasAttr("rowspan") } }
            else -> listOf(rows.first())
        }
        val dataRows = rows - headerRows.toSet()
        if (dataRows.isEmpty()) return null

        // 3) 展开多行表头为单行：按 colspan 构建矩阵并汇总
        fun expandHeader(headers: List<Element>): List<String> {
            data class H(val text: String, val colspan: Int)
            val mat = mutableListOf<MutableList<H?>>()

            headers.forEachIndexed { r, tr ->
                if (mat.size <= r) mat.add(mutableListOf())
                val rowList = mat[r]
                var c = 0
                tr.select("td, th").forEach { th ->
                    while (rowList.size > c && rowList[c] != null) c++
                    val span = th.attr("colspan").toIntOrNull() ?: 1
                    val cell = H(th.text(), span)
                    for (dc in 0 until span) {
                        while (rowList.size <= c + dc) rowList.add(null)
                        rowList[c + dc] = if (dc == 0) cell else H("", 1)
                    }
                    c += span
                }
            }
            val cols = mat.maxOf { it.size }
            return (0 until cols).map { col ->
                mat.joinToString(" \\\\ ") { row -> row.getOrNull(col)?.text.orEmpty() }
            }
        }
        val header = expandHeader(headerRows.filterNotNull()).toVector()

        // 4) 转换数据行
        val content: Vector<Vector<Any?>> = dataRows.map { tr ->
            tr?.select("td, th")?.map { td -> convertHtmlToLatex(listOf(td), latexFile) as Any? }?.toVector()
        }.toVector()

        // 5) 推断列类型
        val columnTypes = (0 until header.size).map { col ->
            if (dataRows.all { it?.select("td, th")?.getOrNull(col)?.text()?.toDoubleOrNull() != null }) {
                ColumnType.NUMBERS_COLUMN
            } else ColumnType.TEXT_COLUMN
        }

        // 6) 返回向导包装
        return TableCreationDialogWrapper(
            columnTypes,
            TableCreationTableModel(content, header)
        )
    }
}