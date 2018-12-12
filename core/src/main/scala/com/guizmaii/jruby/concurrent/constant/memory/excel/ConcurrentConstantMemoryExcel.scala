package com.guizmaii.jruby.concurrent.constant.memory.excel

import java.io.{File, FileOutputStream}
import java.nio.file.Files
import java.util.UUID

import com.guizmaii.jruby.concurrent.constant.memory.excel.Cell.{BLANK_CELL, NUMERIC_CELL, STRING_CELL}
import org.apache.poi.ss.usermodel._
import org.apache.poi.ss.util.WorkbookUtil
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFFont

import scala.annotation.switch
import scala.collection.immutable.SortedSet
import scala.collection.mutable.ListBuffer

sealed abstract class Cell extends Product with Serializable
object Cell {
  private[excel] final case object BlankCell                 extends Cell
  private[excel] final case class StringCell(value: String)  extends Cell
  private[excel] final case class NumericCell(value: Double) extends Cell

  private[excel] final val BLANK_CELL   = 'b'
  private[excel] final val STRING_CELL  = 's'
  private[excel] final val NUMERIC_CELL = 'n'

  private[excel] final val encoder: Cell => String = {
    case BlankCell          => s"$BLANK_CELL:"
    case StringCell(value)  => s"$STRING_CELL:$value"
    case NumericCell(value) => s"$NUMERIC_CELL:$value"
  }

}

final case class Page private[excel] (index: Int, csvFile: File)
private[excel] object Page {
  implicit final val ordering: Ordering[Page] = Ordering.by(_.index)
}

final case class ConcurrentConstantMemoryState private[excel] (
    sheetName: String,
    headerData: Array[String],
    tmpDirectory: File,
    pages: SortedSet[Page]
)

object ConcurrentConstantMemoryExcel {

  import com.github.tototoshi.csv._

  private[excel] type Row = Array[Cell]

  private[excel] object Row {
    final val encoder: Row => ListBuffer[String] = row => row.to[ListBuffer].map(Cell.encoder)
  }

  final val blankCell: Cell = Cell.BlankCell

  final def stringCell(value: String): Cell = Cell.StringCell(value)

  final def numericCell(value: Double): Cell = Cell.NumericCell(value)

  final def newSheet(sheetName: String, headerValues: Array[String]): ConcurrentConstantMemoryState =
    ConcurrentConstantMemoryState(
      sheetName = WorkbookUtil.createSafeSheetName(sheetName),
      headerData = headerValues,
      tmpDirectory = Files.createTempDirectory(UUID.randomUUID().toString).toFile,
      pages = SortedSet.empty
    )

  final def addRows(
      cms: ConcurrentConstantMemoryState,
      pageData: Array[Row],
      pageIndex: Int
  ): ConcurrentConstantMemoryState = {
    val file   = java.io.File.createTempFile(UUID.randomUUID().toString, "csv", cms.tmpDirectory)
    val writer = CSVWriter.open(file)

    for (row <- pageData) writer.writeRow(Row.encoder(row))

    writer.close()

    cms.copy(pages = cms.pages + Page(index = pageIndex, csvFile = file))
  }

  final def writeFile(cms: ConcurrentConstantMemoryState, fileName: String): Unit = {
    // We'll manually manage the `flush` to the hard drive.
    val wb    = new SXSSFWorkbook(-1)
    val sheet = wb.createSheet(cms.sheetName)

    val font = new XSSFFont()
    font.setBold(true)

    val headerStyle = wb.createCellStyle()
    headerStyle.setAlignment(HorizontalAlignment.CENTER)
    headerStyle.setShrinkToFit(true)
    headerStyle.setFont(font)

    val commonCellStyle: CellStyle = wb.createCellStyle()
    commonCellStyle.setShrinkToFit(true)

    val header = sheet.createRow(0)
    for ((celldata, cellIndex) <- cms.headerData.zipWithIndex) {
      val cell = header.createCell(cellIndex)
      cell.setCellValue(celldata)
      cell.setCellStyle(headerStyle)
    }

    var rowIndex = 1 // `1` is because the row 0 is already written (header)
    cms.pages.foreach {
      case Page(_, file) =>
        CSVReader
          .open(file)
          .foreach { rowData =>
            val row = sheet.createRow(rowIndex)
            rowIndex += 1

            for ((cellData, cellIndex) <- rowData.zipWithIndex) {
              val cell = row.createCell(cellIndex)
              cell.setCellStyle(commonCellStyle)

              val Array(cellType, value) = cellData.split(":", 2)
              (cellType(0): @switch) match {
                case BLANK_CELL   => () // Already BLANK at cell creation
                case STRING_CELL  => cell.setCellValue(value)
                case NUMERIC_CELL => cell.setCellValue(value.toDouble)
              }
            }
          }

        sheet.flushRows()
    }

    val out = new FileOutputStream(fileName)
    wb.write(out)
    out.close()

    wb.dispose() // dispose of temporary files backing this workbook on disk
    ()
  }

  final def clean(sheet: ConcurrentConstantMemoryState, swallowIOExceptions: Boolean = false): Unit = {
    import better.files._ // better-files `delete()` method also works on directories, unlike the Java one.
    sheet.tmpDirectory.toScala.delete(swallowIOExceptions)
    ()
  }

  final def writeFileAndClean(
      sheet: ConcurrentConstantMemoryState,
      fileName: String,
      swallowIOExceptions: Boolean = false
  ): Unit = {
    writeFile(sheet, fileName)
    clean(sheet, swallowIOExceptions)
  }

}