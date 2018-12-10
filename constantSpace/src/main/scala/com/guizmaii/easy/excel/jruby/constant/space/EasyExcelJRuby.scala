package com.guizmaii.easy.excel.jruby.constant.space

import java.nio.file.Files
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
      tmpDirectory = Files.createTempDirectory(UUID.randomUUID().toString).toFile,
      pages = SortedSet.empty
    )

  final def addRows(sheet: ConstantMemorySheet, pageData: Array[Row], pageIndex: Int): ConstantMemorySheet = {
    val file = java.io.File.createTempFile(UUID.randomUUID().toString, "csv", sheet.tmpDirectory)
    file.writeCsv[Row](pageData, rfc)
    sheet.copy(pages = sheet.pages + Page(pageIndex, file.toPath))
  }

  final def writeFile(sheet: ConstantMemorySheet, fileName: String): Unit = {
    import com.guizmaii.easy.excel.jruby.constant.space.utils.SpoiwoUtils.spoiwoRowDecoder

    val zero = SpoiwoSheet(name = sheet.name).addRow(sheet.header)
    val finalSheet =
      sheet.pages
        .foldLeft(zero) {
          case (sheet, Page(_, path)) =>
            val spoiwoRows = path.unsafeReadCsv[Array, SpoiwoRow](rfc)
            sheet.addRows(spoiwoRows)
        }

    import com.norbitltd.spoiwo.natures.xlsx.Model2XlsxConversions._
    finalSheet.saveAsXlsx(fileName)
  }

  final def clean(sheet: ConstantMemorySheet, swallowIOExceptions: Boolean = false): Unit = {
    import better.files._ // better-files `delete()` method also works on directories, unlike the Java one.
    sheet.tmpDirectory.toScala.delete(swallowIOExceptions)
    ()
  }

  final def writeFileAndClean(
      sheet: ConstantMemorySheet,
      fileName: String,
      swallowIOExceptions: Boolean = false
  ): Unit = {
    writeFile(sheet, fileName)
    clean(sheet, swallowIOExceptions)
  }

}
