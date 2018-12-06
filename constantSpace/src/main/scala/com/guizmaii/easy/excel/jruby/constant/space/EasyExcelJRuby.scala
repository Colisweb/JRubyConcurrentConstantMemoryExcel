package com.guizmaii.easy.excel.jruby.constant.space

import java.util.UUID

import com.norbitltd.spoiwo.model.enums.CellFill
import com.norbitltd.spoiwo.model.{Cell => SpoiwoCell, Row => SpoiwoRow, Sheet => SpoiwoSheet, _}
import kantan.csv.{CellDecoder, CellEncoder, RowDecoder, RowEncoder}
import org.apache.poi.ss.util.WorkbookUtil

import scala.collection.immutable.SortedSet
import scala.reflect.ClassTag

private[space] object KantanExtension {

  /**
    * Unsafe implementation.
    *
    * TODO: Prefer a safe version ?
    */
  implicit def arrayDecoder[A: ClassTag](
      implicit CellDecoder: CellDecoder[A]
  ): RowDecoder[Array[A]] =
    RowDecoder.fromUnsafe { array =>
      val acc = Array.empty[A]
      for (a <- array) acc :+ CellDecoder.unsafeDecode(a)
      acc
    }

  implicit def arrayEncoder[A](implicit CellEncoder: CellEncoder[A]): RowEncoder[Array[A]] =
    (array: Array[A]) => array.map(CellEncoder.encode)

}

private[space] object SpoiwoUtils {

  final val blankCell = SpoiwoCell.Empty

  @inline def stringCell(value: String): SpoiwoCell = SpoiwoCell(value)

  @inline def numericCell(value: Double): SpoiwoCell = SpoiwoCell(value)

  private final val spoiwoCellDecoder: CellDecoder[SpoiwoCell] =
    CellDecoder.fromUnsafe { s =>
      val Array(cellType, data) = s.split(":", 2)
      cellType match {
        case Types.BLANK_CELL   => SpoiwoUtils.blankCell
        case Types.STRING_CELL  => SpoiwoUtils.stringCell(data)
        case Types.NUMERIC_CELL => SpoiwoUtils.numericCell(data.toDouble)
      }
    }

  implicit final val spoiwoRowDecoder: RowDecoder[SpoiwoRow] =
    RowDecoder.fromUnsafe { strings =>
      SpoiwoRow().withCells(strings.map(spoiwoCellDecoder.unsafeDecode))
    }

}

object Types {

  private[space] final val BLANK_CELL   = "b"
  private[space] final val STRING_CELL  = "s"
  private[space] final val NUMERIC_CELL = "n"

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
  import KantanExtension._
  import kantan.csv._
  import kantan.csv.ops._

  private[space] final val headerStyle =
    CellStyle(
      fillPattern = CellFill.None,
      fillForegroundColor = Color.White,
      font = Font(bold = true)
    )

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
    import com.norbitltd.spoiwo.natures.xlsx.Model2XlsxConversions._
    import SpoiwoUtils.spoiwoRowDecoder

    val spoiwoSheet = SpoiwoSheet(name = sheet.name).addRow(sheet.header)

    sheet.pages
      .foreach {
        case Page(_, path) =>
          val spoiwoRows = new java.io.File(path).unsafeReadCsv[Array, SpoiwoRow](rfc)
          spoiwoSheet.addRows(spoiwoRows)
      }

    spoiwoSheet.saveAsXlsx(fileName)
  }

  private def tmpFileName(sheet: ConstantMemorySheet, pageIndex: Int): String = s"$pageIndex-${sheet.tmpFileName}"

}
