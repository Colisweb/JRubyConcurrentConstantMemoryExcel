package com.guizmaii.easy.excel.jruby.constant.space

import java.io.File
import java.nio.file.Path

import com.guizmaii.easy.excel.jruby.constant.space.Types.{BLANK_CELL, NUMERIC_CELL, STRING_CELL}
import com.norbitltd.spoiwo.model.enums.CellFill
import com.norbitltd.spoiwo.model.{CellStyle, Color, Font, Row => SpoiwoRow}
import kantan.csv.CellEncoder

import scala.collection.immutable.SortedSet

// Sadly, it seems that I can't declare this trait in an object if I want to be able to use it in JRuby.
sealed abstract class Cell extends Product with Serializable
object Cell {
  private[space] final case object BlankCell                 extends Cell
  private[space] final case class StringCell(value: String)  extends Cell
  private[space] final case class NumericCell(value: Double) extends Cell

  private[space] implicit final val encoder: CellEncoder[Cell] = {
    case BlankCell          => s"$BLANK_CELL:"
    case StringCell(value)  => s"$STRING_CELL:$value"
    case NumericCell(value) => s"$NUMERIC_CELL:$value"
  }
}

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

  private[space] type Row = Array[Cell]

}
