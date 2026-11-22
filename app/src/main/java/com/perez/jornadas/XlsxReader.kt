package com.perez.jornadas

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** Celdas de una hoja como texto. Las celdas vacías devuelven null. */
class SheetData(private val cells: Map<Int, String>, val maxRow: Int) {
    operator fun get(r: Int, c: Int): String? = cells[r * 100 + c]?.takeIf { it.isNotBlank() }
    fun num(r: Int, c: Int): Double? = get(r, c)?.trim()?.toDoubleOrNull()
}

/**
 * Lector mínimo de .xlsx (sin librerías). Entiende textos compartidos (lo que escribe Excel o
 * LibreOffice al volver a guardar) y textos en línea (lo que escribe esta app).
 */
object XlsxReader {
    private fun parser(data: ByteArray): XmlPullParser =
        Xml.newPullParser().apply { setInput(ByteArrayInputStream(data), "UTF-8") }

    private fun XmlPullParser.local(): String = (name ?: "").substringAfter(':')

    private fun splitRef(ref: String): Pair<Int, Int> {
        var i = 0
        var col = 0
        while (i < ref.length && ref[i].isLetter()) {
            col = col * 26 + (ref[i].uppercaseChar() - 'A' + 1)
            i++
        }
        val row = ref.substring(i).toIntOrNull() ?: 0
        return row to (col - 1)
    }

    fun read(bytes: ByteArray): Map<String, SheetData> {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                files[e.name] = z.readBytes()
                e = z.nextEntry
            }
        }

        // Textos compartidos
        val shared = ArrayList<String>()
        files["xl/sharedStrings.xml"]?.let { data ->
            val p = parser(data)
            var inSi = false
            var inT = false
            var rph = 0
            val sb = StringBuilder()
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    when (p.local()) {
                        "si" -> { inSi = true; sb.setLength(0) }
                        "rPh" -> rph++
                        "t" -> inT = true
                    }
                } else if (ev == XmlPullParser.TEXT) {
                    if (inSi && inT && rph == 0) sb.append(p.text)
                } else if (ev == XmlPullParser.END_TAG) {
                    when (p.local()) {
                        "t" -> inT = false
                        "rPh" -> rph--
                        "si" -> { shared.add(sb.toString()); inSi = false }
                    }
                }
                ev = p.next()
            }
        }

        // Hojas: nombre -> id de relación
        val sheetRefs = ArrayList<Pair<String, String>>()
        run {
            val p = parser(files["xl/workbook.xml"] ?: error("Falta workbook.xml"))
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && p.local() == "sheet") {
                    var nm = ""
                    var rid = ""
                    for (i in 0 until p.attributeCount) {
                        val an = p.getAttributeName(i).substringAfter(':')
                        if (an == "name") nm = p.getAttributeValue(i)
                        if (an == "id") rid = p.getAttributeValue(i)
                    }
                    sheetRefs.add(nm to rid)
                }
                ev = p.next()
            }
        }
        // Relaciones: id -> archivo
        val rels = HashMap<String, String>()
        run {
            val p = parser(files["xl/_rels/workbook.xml.rels"] ?: error("Falta workbook.xml.rels"))
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && p.local() == "Relationship") {
                    var id = ""
                    var target = ""
                    for (i in 0 until p.attributeCount) {
                        when (p.getAttributeName(i)) {
                            "Id" -> id = p.getAttributeValue(i)
                            "Target" -> target = p.getAttributeValue(i)
                        }
                    }
                    rels[id] = target
                }
                ev = p.next()
            }
        }

        val out = LinkedHashMap<String, SheetData>()
        for ((name, rid) in sheetRefs) {
            val target = rels[rid] ?: continue
            val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
            val data = files[path] ?: continue
            out[name] = parseSheet(data, shared)
        }
        return out
    }

    private fun parseSheet(data: ByteArray, shared: List<String>): SheetData {
        val p = parser(data)
        val cells = HashMap<Int, String>()
        var maxRow = 0
        var ref = ""
        var type = ""
        var inV = false
        var inIs = false
        var inT = false
        val v = StringBuilder()
        val isb = StringBuilder()
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.local()) {
                    "c" -> {
                        ref = p.getAttributeValue(null, "r") ?: ""
                        type = p.getAttributeValue(null, "t") ?: ""
                        v.setLength(0); isb.setLength(0)
                    }
                    "v" -> inV = true
                    "is" -> inIs = true
                    "t" -> inT = true
                }
            } else if (ev == XmlPullParser.TEXT) {
                if (inV) v.append(p.text) else if (inIs && inT) isb.append(p.text)
            } else if (ev == XmlPullParser.END_TAG) {
                when (p.local()) {
                    "v" -> inV = false
                    "t" -> inT = false
                    "is" -> inIs = false
                    "c" -> {
                        val value = when (type) {
                            "s" -> v.toString().trim().toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                            "inlineStr" -> isb.toString()
                            else -> v.toString()
                        }
                        val (r, c) = splitRef(ref)
                        if (r > 0 && c >= 0) {
                            cells[r * 100 + c] = value
                            if (r > maxRow) maxRow = r
                        }
                    }
                }
            }
            ev = p.next()
        }
        return SheetData(cells, maxRow)
    }
}
