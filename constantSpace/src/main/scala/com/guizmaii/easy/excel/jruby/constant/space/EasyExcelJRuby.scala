package com.guizmaii.easy.excel.jruby.constant.space

import java.util.UUID

import com.norbitltd.spoiwo.model.enums.CellFill
import com.norbitltd.spoiwo.model.{Row => SpoiwoRow, Sheet => SpoiwoSheet, _}
import kantan.csv.CellEncoder
import org.apache.poi.ss.util.WorkbookUtil

import scala.collection.immutable.SortedSet

object Types {

  private[space] final val BLANK_CELL   = "b"
  private[space] final val STRING_CELL  = "s"
  private[space] final val NUMERIC_CELL = "n"

  private[space] final val headerStyle =
    CellStyle(
      fillPattern = CellFill.None,
      fillForegroundColor = Color.White,
      font = Font(bold = true)
    )

  final case class Page private[space] (name: String, path: String)

  private[space] object Page {
    implicit final val ordering: Ordering[Page] = Ordering.by(_.name)
  }

  final case class ConstantMemorySheet private[space] (
      name: String,
      header: SpoiwoRow,
      tmpFileName: UUID,
      pages: SortedSet[Page]
  )

  sealed abstract class Cell extends Product with Serializable
  private[space] object Cell {
    final case object BlankCell                 extends Cell
    final case class StringCell(value: String)  extends Cell
    final case class NumericCell(value: Double) extends Cell

    implicit final val encoder: CellEncoder[Cell] = {
      case BlankCell          => s"$BLANK_CELL:"
      case StringCell(value)  => s"$STRING_CELL:$value"
      case NumericCell(value) => s"$NUMERIC_CELL:$value"
    }
  }

  private[space] type Row = Array[Cell]

}

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
    file.writeCsv[Row](page, rfc.withHeader)

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
