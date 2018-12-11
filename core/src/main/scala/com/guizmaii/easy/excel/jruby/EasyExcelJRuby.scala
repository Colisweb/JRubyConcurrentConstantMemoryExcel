package com.guizmaii.easy.excel.jruby

import com.norbitltd.spoiwo.model._
import com.norbitltd.spoiwo.model.enums.CellFill
import org.apache.poi.ss.util.WorkbookUtil

object EasyExcelJRuby {

  final val headerStyle =
    CellStyle(
      fillPattern = CellFill.None,
      fillForegroundColor = Color.White,
      font = Font(bold = true)
    )

  final val blankCell: Cell = Cell.Empty

  final def stringCell(value: String): Cell = Cell(value)

  final def numericCell(value: Double): Cell = Cell(value)

  final def row(cells: Array[Cell]): Row = Row(cells)

  final def newSheet(sheetNane: String): Sheet = Sheet(name = WorkbookUtil.createSafeSheetName(sheetNane))

  final def addHeader(sheet: Sheet, values: Array[String]): Sheet =
    sheet.addRow(Row().withCellValues(values.toList).withStyle(headerStyle))

  final def addRows(sheet: Sheet, rows: Array[Row]): Sheet = sheet.addRows(rows)

  final def writeFile(sheet: Sheet, fileName: String): Unit = {
    import com.norbitltd.spoiwo.natures.xlsx.Model2XlsxConversions._

    sheet.saveAsXlsx(fileName)
  }

}
