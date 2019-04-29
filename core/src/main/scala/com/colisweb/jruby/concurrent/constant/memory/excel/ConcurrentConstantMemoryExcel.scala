package com.colisweb.jruby.concurrent.constant.memory.excel

import java.io.{File, FileOutputStream}
import java.nio.file.{Files, Path}
import java.util.UUID

import cats.effect.Resource
import com.colisweb.jruby.concurrent.constant.memory.excel.utils.KantanExtension
import kantan.csv.{CellDecoder, CellEncoder}
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.atomic.Atomic
import org.apache.poi.ss.usermodel._
import org.apache.poi.ss.util.WorkbookUtil
import org.apache.poi.xssf.streaming.SXSSFWorkbook

import scala.annotation.switch
import scala.collection.immutable.SortedSet
import scala.collection.mutable.ListBuffer
import scala.io.Codec

sealed abstract class Cell extends Product with Serializable
object Cell {

  private[this] implicit final val codec: Codec = Codec.UTF8

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
  // https://nrinaudo.github.io/kantan.csv/bom.html
  import kantan.codecs.resource.bom._

  private[excel] type Row = Array[Cell]

  private[this] implicit final val codec: Codec = Codec.UTF8
  private[this] implicit final val scheduler: Scheduler =
    Scheduler.computation(name = "ConcurrentConstantMemoryExcel-computation")

  final val blankCell: Cell = Cell.BlankCell

  final def stringCell(value: String): Cell = Cell.StringCell(value)

  final def numericCell(value: Double): Cell = Cell.NumericCell(value)

  final def newWorkbookState(sheetName: String, headerValues: Array[String]): Atomic[ConcurrentConstantMemoryState] =
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

    def computeWorkbookData(wb: SXSSFWorkbook): Task[Unit] = Task {
      val sheet = wb.createSheet(cms.sheetName)
      sheet.setDefaultColumnWidth(24)

      val boldFont = wb.createFont()
      boldFont.setBold(true)

      val headerStyle = wb.createCellStyle()
      headerStyle.setAlignment(HorizontalAlignment.CENTER)
      headerStyle.setFont(boldFont)

      val header = sheet.createRow(0)
      for ((celldata, cellIndex) <- cms.headerData.zipWithIndex) {
        val cell = header.createCell(cellIndex, CellType.STRING)
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
                cellData match {
                  case Cell.BlankCell          => row.createCell(cellIndex, CellType.BLANK)
                  case Cell.NumericCell(value) => row.createCell(cellIndex, CellType.NUMERIC).setCellValue(value)
                  case Cell.StringCell(value)  => row.createCell(cellIndex, CellType.STRING).setCellValue(value)
                }
              }
            }

          sheet.flushRows()
      }
    }

    // TODO: Expose the `swallowIOExceptions` parameter in the `writeFile` function ?
    def clean(swallowIOExceptions: Boolean = false): Task[Unit] = Task {
      import better.files._ // better-files `delete()` method also works on directories, unlike the Java one.
      cms.tmpDirectory.toScala.delete(swallowIOExceptions)
      ()
    }

    // Used as a Resource to ease the clean of the temporary CSVs created during the tasks calcultation.
    val computeIntermediateTmpCsvFiles: Resource[Task, Unit] =
      Resource.make(Task.gatherUnordered(cms.tasks).flatMap(_ => Task.unit))(_ => clean())

    val workbookResource: Resource[Task, SXSSFWorkbook] =
      Resource.make {
        // We'll manually manage the `flush` to the hard drive.
        Task(new SXSSFWorkbook(-1))
      } { wb: SXSSFWorkbook =>
        Task {
          wb.dispose() // dispose of temporary files backing this workbook on disk. Necessary because not done in the `close()`. See: https://stackoverflow.com/a/50363245
          wb.close()
        }
      }

    val fileOutputStreamResource: Resource[Task, FileOutputStream] =
      Resource.make(Task(new FileOutputStream(fileName)))(out => Task(out.close()))

    computeIntermediateTmpCsvFiles
      .use { _ =>
        workbookResource.use { wb =>
          computeWorkbookData(wb).flatMap(_ => fileOutputStreamResource.use(out => Task(wb.write(out))))
        }
      }
      .runSyncUnsafe()
  }

}
