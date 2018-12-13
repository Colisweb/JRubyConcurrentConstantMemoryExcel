package com.guizmaii.jruby.concurrent.constant.memory.excel

import java.io.{File, FileOutputStream}
import java.nio.file.{Files, Path}
import java.util.UUID

import com.guizmaii.jruby.concurrent.constant.memory.excel.utils.KantanExtension
import kantan.csv.{CellDecoder, CellEncoder}
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.atomic.Atomic
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

  private[excel] implicit final val encoder: CellEncoder[Cell] = {
    case BlankCell          => s"$BLANK_CELL:"
    case StringCell(value)  => s"$STRING_CELL:$value"
    case NumericCell(value) => s"$NUMERIC_CELL:$value"
  }

  private[excel] implicit final val decoder: CellDecoder[Cell] =
    CellDecoder.fromUnsafe { s =>
      val Array(cellType, data) = s.split(":", 2)
      (cellType(0): @switch) match {
        case BLANK_CELL   => Cell.BlankCell
        case STRING_CELL  => Cell.StringCell(data)
        case NUMERIC_CELL => Cell.NumericCell(data.toDouble)
      }
    }
}

final case class Page private[excel] (index: Int, path: Path)
private[excel] object Page {
  implicit final val ordering: Ordering[Page] = Ordering.by(_.index)
}

final case class ConcurrentConstantMemoryState private[excel] (
    sheetName: String,
    headerData: Array[String],
    tmpDirectory: File,
    tasks: List[Task[Unit]],
    pages: SortedSet[Page]
)

object ConcurrentConstantMemoryExcel {

  import kantan.csv._
  import kantan.csv.ops._

  private[excel] type Row = Array[Cell]

  private[this] implicit final val scheduler: Scheduler =
    Scheduler.computation(name = "ConcurrentConstantMemoryExcel-computation")

  final val blankCell: Cell = Cell.BlankCell

  final def stringCell(value: String): Cell = Cell.StringCell(value)

  final def numericCell(value: Double): Cell = Cell.NumericCell(value)

  final def newSheet(sheetName: String, headerValues: Array[String]): Atomic[ConcurrentConstantMemoryState] =
    Atomic(
      ConcurrentConstantMemoryState(
        sheetName = WorkbookUtil.createSafeSheetName(sheetName),
        headerData = headerValues,
        tmpDirectory = Files.createTempDirectory(UUID.randomUUID().toString).toFile,
        tasks = List.empty,
        pages = SortedSet.empty
      )
    )

  final def addRows(
      atomicCms: Atomic[ConcurrentConstantMemoryState],
      computeRows: => Array[Row],
      pageIndex: Int
  ): Unit = {
    import KantanExtension.arrayEncoder

    val tmpCsvFile = java.io.File.createTempFile(UUID.randomUUID().toString, ".csv", atomicCms.get().tmpDirectory)
    val newPage    = Page(pageIndex, tmpCsvFile.toPath)
    val task       = Task(tmpCsvFile.writeCsv[Row](computeRows, rfc))

    atomicCms.transform { cms =>
      cms.copy(pages = cms.pages + newPage, tasks = cms.tasks :+ task)
    }
  }

  final def writeFile(atomicCms: Atomic[ConcurrentConstantMemoryState], fileName: String): Unit = {
    val cms = atomicCms.get()

    def doWrite(): Unit = {
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
        case Page(_, path) =>
          path
            .unsafeReadCsv[ListBuffer, ListBuffer[Cell]](rfc)
            .foreach { rowData =>
              val row = sheet.createRow(rowIndex)
              rowIndex += 1

              for ((cellData, cellIndex) <- rowData.zipWithIndex) {
                val cell = row.createCell(cellIndex)
                cell.setCellStyle(commonCellStyle)
                cellData match {
                  case Cell.BlankCell          => () // Already BLANK at cell creation
                  case Cell.StringCell(value)  => cell.setCellValue(value)
                  case Cell.NumericCell(value) => cell.setCellValue(value)
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

    // TODO: Expose the `swallowIOExceptions` parameter in the `writeFile` function ?
    def clean(swallowIOExceptions: Boolean = false): Unit = {
      import better.files._ // better-files `delete()` method also works on directories, unlike the Java one.
      cms.tmpDirectory.toScala.delete(swallowIOExceptions)
      ()
    }

    Task
      .gatherUnordered(cms.tasks)
      .map(_ => doWrite())
      .map(_ => clean())
      .runSyncUnsafe()
  }

}
