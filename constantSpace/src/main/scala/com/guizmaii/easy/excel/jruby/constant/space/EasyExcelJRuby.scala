package com.guizmaii.easy.excel.jruby.constant.space

import java.util.UUID

import com.norbitltd.spoiwo.model.{Row => SpoiwoRow, Sheet => SpoiwoSheet}
import org.apache.poi.ss.util.WorkbookUtil

import scala.collection.immutable.SortedSet

object EasyExcelJRuby {

  import Types._
  import com.guizmaii.easy.excel.jruby.constant.space.utils.KantanExtension._
  import kantan.csv._
  import kantan.csv.ops._

  final val blankCell: Cell = Cell.BlankCell

  final def stringCell(value: String): Cell = Cell.StringCell(value)

  final def numericCell(value: Double): Cell = Cell.NumericCell(value)

  final def newSheet(sheetName: String, headerValues: Array[String]): ConstantMemorySheet =
    ConstantMemorySheet(
      name = WorkbookUtil.createSafeSheetName(sheetName),
      header = SpoiwoRow().withCellValues(headerValues.toList).withStyle(headerStyle),
      tmpFileName = UUID.randomUUID(),
      pages = SortedSet.empty
    )

  final def addRows(sheet: ConstantMemorySheet, page: Array[Row], pageIndex: Int): ConstantMemorySheet = {
    val fileName = tmpFileName(sheet, pageIndex)
    val file     = java.io.File.createTempFile(fileName, "csv")
    file.writeCsv[Row](page, rfc)

    sheet.copy(pages = sheet.pages + Page(fileName, file.getAbsolutePath))
  }

  final def writeFile(sheet: ConstantMemorySheet, fileName: String): Unit = {
    import com.guizmaii.easy.excel.jruby.constant.space.utils.SpoiwoUtils.spoiwoRowDecoder
    import com.norbitltd.spoiwo.natures.xlsx.Model2XlsxConversions._

    val spoiwoSheet = SpoiwoSheet(name = sheet.name).addRow(sheet.header)

    sheet.pages
      .foreach {
        case Page(_, path) =>
          val spoiwoRows = new java.io.File(path).unsafeReadCsv[Array, SpoiwoRow](rfc)
          spoiwoSheet.addRows(spoiwoRows)
      }

    spoiwoSheet.saveAsXlsx(fileName)
  }

  private final def tmpFileName(sheet: ConstantMemorySheet, pageIndex: Int): String = s"$pageIndex-${sheet.tmpFileName}"

}
