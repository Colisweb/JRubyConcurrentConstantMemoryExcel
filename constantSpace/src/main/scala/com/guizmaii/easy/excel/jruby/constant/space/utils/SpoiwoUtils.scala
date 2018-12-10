package com.guizmaii.easy.excel.jruby.constant.space.utils

import com.guizmaii.easy.excel.jruby.constant.space.types
import com.norbitltd.spoiwo.model.{Cell => SpoiwoCell, Row => SpoiwoRow}
import kantan.csv.{CellDecoder, RowDecoder}

import scala.annotation.switch

private[space] object SpoiwoUtils {

  final val blankCell = SpoiwoCell.Empty

  @inline final def stringCell(value: String): SpoiwoCell = SpoiwoCell(value)

  @inline final def numericCell(value: Double): SpoiwoCell = SpoiwoCell(value)

  private final val spoiwoCellDecoder: CellDecoder[SpoiwoCell] =
    CellDecoder.fromUnsafe { s =>
      val Array(cellType, data) = s.split(":", 2)
      (cellType(0): @switch) match {
        case types.BLANK_CELL   => SpoiwoUtils.blankCell
        case types.STRING_CELL  => SpoiwoUtils.stringCell(data)
        case types.NUMERIC_CELL => SpoiwoUtils.numericCell(data.toDouble)
      }
    }

  implicit final val spoiwoRowDecoder: RowDecoder[SpoiwoRow] =
    RowDecoder.fromUnsafe { strings =>
      SpoiwoRow().withCells(strings.map(spoiwoCellDecoder.unsafeDecode))
    }

}
