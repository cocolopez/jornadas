package com.perez.jornadas

import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Genera un .xlsx (Excel) sin librerías externas: un zip con XML.
 * Hojas: "Resumen" (total por persona), "Por mes" y una hoja por persona con cada día,
 * su pago, el viático del día, cuántos van, la parte de viático y el total, con fórmulas.
 */
object XlsxExporter {
    private val AR = Locale("es", "AR")

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun ref(c: Int, r: Int) = "${'A' + c}$r"
    private fun num(v: Double) = BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).toPlainString()

    private fun t(c: Int, r: Int, s: String, st: Int = 0) =
        """<c r="${ref(c, r)}" t="inlineStr" s="$st"><is><t xml:space="preserve">${esc(s)}</t></is></c>"""
    private fun n(c: Int, r: Int, v: Double, st: Int = 0) =
        """<c r="${ref(c, r)}" s="$st"><v>${num(v)}</v></c>"""
    private fun f(c: Int, r: Int, formula: String, cached: Double, st: Int = 0) =
        """<c r="${ref(c, r)}" s="$st"><f>${esc(formula)}</f><v>${num(cached)}</v></c>"""
    private fun row(r: Int, vararg cells: String) = """<row r="$r">${cells.joinToString("")}</row>"""
    private fun headers(r: Int, vararg h: String) = row(r, *h.mapIndexed { i, s -> t(i, r, s, 1) }.toTypedArray())

    private fun sheet(widths: List<Int>, rows: List<String>, freeze: Boolean): String {
        val view = if (freeze)
            """<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/><selection pane="bottomLeft"/></sheetView></sheetViews>"""
        else """<sheetViews><sheetView workbookViewId="0"/></sheetViews>"""
        val cols = widths.mapIndexed { i, w -> """<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""" }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">$view<cols>$cols</cols><sheetData>${rows.joinToString("")}</sheetData></worksheet>"""
    }

    private val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="2"><numFmt numFmtId="164" formatCode="&quot;$&quot;\ #,##0.00"/><numFmt numFmtId="165" formatCode="dd/mm/yyyy"/></numFmts><fonts count="4"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font><font><b/><sz val="14"/><name val="Calibri"/></font></fonts><fills count="4"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF0F766E"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFD3F1EA"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="9"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="2" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="164" fontId="1" fillId="3" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1"/><xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyAlignment="1"><alignment horizontal="left"/></xf><xf numFmtId="0" fontId="1" fillId="3" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="1" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyAlignment="1"><alignment horizontal="center"/></xf><xf numFmtId="1" fontId="1" fillId="3" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyAlignment="1"><alignment horizontal="center"/></xf></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""

    fun build(persons: List<Person>, works: List<Workday>, ov: Map<Long, Double>, def: Double, period: String): ByteArray {
        val counts = works.groupingBy { it.date }.eachCount()
        fun part(w: Workday): Double = (ov[w.date] ?: def) / (counts[w.date] ?: 1)
        val used = mutableSetOf("Resumen", "Por mes")
        val sheets = mutableListOf<Pair<String, String>>()
        val names = persons.associate { it.id to it.name }

        // ---- Resumen: total por persona ----
        val r1 = mutableListOf(
            row(1, t(0, 1, "Jornadas — $period", 6)),
            headers(3, "Persona", "Días", "Pago", "Viáticos", "Total")
        )
        var r = 4
        val first = r
        var sd = 0; var sp = 0.0; var se = 0.0
        for (p in persons) {
            val ws = works.filter { it.personId == p.id }
            val pay = ws.sumOf { it.pay }
            val ex = ws.sumOf { part(it) }
            r1 += row(r, t(0, r, p.name), n(1, r, ws.size.toDouble(), 7), n(2, r, pay, 2), n(3, r, ex, 2), f(4, r, "C$r+D$r", pay + ex, 2))
            sd += ws.size; sp += pay; se += ex; r++
        }
        if (persons.isNotEmpty()) {
            r1 += row(
                r, t(0, r, "TOTAL", 5), f(1, r, "SUM(B$first:B${r - 1})", sd.toDouble(), 8),
                f(2, r, "SUM(C$first:C${r - 1})", sp, 3), f(3, r, "SUM(D$first:D${r - 1})", se, 3),
                f(4, r, "SUM(E$first:E${r - 1})", sp + se, 3)
            )
        }
        sheets += "Resumen" to sheet(listOf(26, 10, 16, 16, 16), r1, false)

        // ---- Por mes ----
        val r2 = mutableListOf(headers(1, "Mes", "Persona", "Días", "Pago", "Viáticos", "Total"))
        val groups = works.groupBy {
            val d = LocalDate.ofEpochDay(it.date)
            Pair(d.year * 100 + d.monthValue, it.personId)
        }.entries.map { Triple(it.key.first, it.key.second, it.value) }
            .sortedWith(compareBy({ it.first }, { it.second }))
        r = 2
        val first2 = r
        sd = 0; sp = 0.0; se = 0.0
        for ((ym, pid, ws) in groups) {
            val label = Month.of(ym % 100).getDisplayName(TextStyle.FULL, AR).replaceFirstChar { it.uppercase() } + " " + (ym / 100)
            val pay = ws.sumOf { it.pay }
            val ex = ws.sumOf { part(it) }
            r2 += row(r, t(0, r, label), t(1, r, names[pid] ?: "?"), n(2, r, ws.size.toDouble(), 7), n(3, r, pay, 2), n(4, r, ex, 2), f(5, r, "D$r+E$r", pay + ex, 2))
            sd += ws.size; sp += pay; se += ex; r++
        }
        if (groups.isNotEmpty()) {
            r2 += row(
                r, t(0, r, "TOTAL", 5), t(1, r, "", 5), f(2, r, "SUM(C$first2:C${r - 1})", sd.toDouble(), 8),
                f(3, r, "SUM(D$first2:D${r - 1})", sp, 3), f(4, r, "SUM(E$first2:E${r - 1})", se, 3),
                f(5, r, "SUM(F$first2:F${r - 1})", sp + se, 3)
            )
        }
        sheets += "Por mes" to sheet(listOf(18, 22, 10, 16, 16, 16), r2, true)

        // ---- Una hoja por persona ----
        for (p in persons) {
            val ws = works.filter { it.personId == p.id }.sortedBy { it.date }
            val rr = mutableListOf(
                headers(1, "Fecha", "Día", "Lugar / nota", "Pago", "Viático del día (total)", "Van", "Viático (su parte)", "Total del día")
            )
            var sumPay = 0.0; var sumPart = 0.0
            ws.forEachIndexed { i, w ->
                val rw = i + 2
                val d = LocalDate.ofEpochDay(w.date)
                val cnt = counts[w.date] ?: 1
                val dayExp = ov[w.date] ?: def
                val pt = dayExp / cnt
                rr += row(
                    rw,
                    n(0, rw, (w.date + 25569).toDouble(), 4),
                    t(1, rw, d.dayOfWeek.getDisplayName(TextStyle.FULL, AR).replaceFirstChar { it.uppercase() }),
                    t(2, rw, w.note),
                    n(3, rw, w.pay, 2), n(4, rw, dayExp, 2), n(5, rw, cnt.toDouble(), 7),
                    f(6, rw, "E$rw/F$rw", pt, 2), f(7, rw, "D$rw+G$rw", w.pay + pt, 2)
                )
                sumPay += w.pay; sumPart += pt
            }
            if (ws.isNotEmpty()) {
                val tr = ws.size + 2
                rr += row(
                    tr, t(0, tr, "TOTAL", 5), t(1, tr, "${ws.size} días", 5), t(2, tr, "", 5),
                    f(3, tr, "SUM(D2:D${tr - 1})", sumPay, 3), t(4, tr, "", 5), t(5, tr, "", 5),
                    f(6, tr, "SUM(G2:G${tr - 1})", sumPart, 3), f(7, tr, "SUM(H2:H${tr - 1})", sumPay + sumPart, 3)
                )
            } else rr += row(2, t(0, 2, "Sin días en este período"))

            val base = p.name.filter { it !in "[]:*?/\\" }.trim().take(31).ifBlank { "Persona" }
            var nm = base
            var k = 2
            while (nm in used) { nm = base.take(28) + " $k"; k++ }
            used += nm
            sheets += nm to sheet(listOf(12, 13, 22, 16, 20, 7, 18, 16), rr, true)
        }

        // ---- Empaquetar ----
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            fun add(name: String, content: String) {
                z.putNextEntry(ZipEntry(name)); z.write(content.toByteArray(Charsets.UTF_8)); z.closeEntry()
            }
            val overrides = sheets.indices.joinToString("") {
                """<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
            }
            add("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>$overrides</Types>""")
            add("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            val sheetTags = sheets.mapIndexed { i, s -> """<sheet name="${esc(s.first)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""" }.joinToString("")
            add("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>$sheetTags</sheets></workbook>""")
            val rels = sheets.indices.joinToString("") {
                """<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>"""
            }
            add("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$rels<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            add("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, s -> add("xl/worksheets/sheet${i + 1}.xml", s.second) }
        }
        return bos.toByteArray()
    }
}
