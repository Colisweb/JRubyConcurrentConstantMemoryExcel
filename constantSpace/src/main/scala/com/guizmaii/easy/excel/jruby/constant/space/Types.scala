package com.guizmaii.easy.excel.jruby.constant.space

import java.io.File
import java.nio.file.Path

import com.norbitltd.spoiwo.model.enums.CellFill
import com.norbitltd.spoiwo.model.{CellStyle, Color, Font, Row => SpoiwoRow}
import kantan.csv.CellEncoder

import scala.collection.immutable.SortedSet

object Types {

  private[space] final val BLANK_CELL   = 'b'
  private[space] final val STRING_CELL  = 's'
  private[space] final val NUMERIC_CELL = 'n'

  private[space] final val headerStyle =
    CellStyle(
      fillPattern = CellFill.None,
      fillForegroundColor = Color.White,
      font = Font(bold = true)
    )

  final case class Page private[space] (index: Int, path: Path)

  private[space] object Page {
    implicit final val ordering: Ordering[Page] = Ordering.by(_.index)
  }

  final case class ConstantMemorySheet private[space] (
      name: String,
      header: SpoiwoRow,
      tmpDirectory: File,
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
