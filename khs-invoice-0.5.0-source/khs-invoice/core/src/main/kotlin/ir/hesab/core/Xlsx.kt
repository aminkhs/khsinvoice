package ir.hesab.core

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Minimal standards-based workbook. Text cells preserve all 64-bit rial amounts exactly. */
object Xlsx {
    private fun xml(value: String) = value.filter { it=='\n' || it=='\r' || it=='\t' || it >= ' ' }.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
    private fun column(index: Int): String { var n=index+1; var result=""; while(n>0) { n--; result=('A'+n%26)+result; n/=26 }; return result }
    fun encode(rows: List<List<String>>): ByteArray {
        val buffer=ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            fun entry(name: String,content: String) { zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
            entry("[Content_Types].xml","""<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
            entry("_rels/.rels","""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            entry("xl/workbook.xml","""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="تراکنش‌ها" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            entry("xl/_rels/workbook.xml.rels","""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            val data=rows.mapIndexed { r,row -> "<row r=\"${r+1}\">"+row.mapIndexed { c,value -> "<c r=\"${column(c)}${r+1}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(value)}</t></is></c>" }.joinToString("")+"</row>" }.joinToString("")
            entry("xl/worksheets/sheet1.xml","""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0" rightToLeft="1"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><cols><col min="1" max="8" width="25" customWidth="1"/></cols><sheetData>$data</sheetData></worksheet>""")
        }
        return buffer.toByteArray()
    }
}
