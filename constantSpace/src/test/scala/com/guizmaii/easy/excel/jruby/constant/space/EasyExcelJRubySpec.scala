package com.guizmaii.easy.excel.jruby.constant.space

import java.io.File
import java.nio.file.Files
import java.util.Date

import com.guizmaii.easy.excel.jruby.constant.space.Types._
import org.scalatest.{FlatSpec, Matchers}

class EasyExcelJRubySpec extends FlatSpec with Matchers {

  "true" should "be true" in {
    true shouldBe true
  }

  import EasyExcelJRuby._

  val sheet_name = "SHEET_NAME"
  val headers    = Array("A", "B", "C")

  // Ugly but handy. Don't abuse of that !
  implicit final def toCell(value: String): Cell = if (value.isEmpty) blankCell else stringCell(value)
  implicit final def toCell(value: Double): Cell = numericCell(value)

  def newSheetPlz: ConstantMemorySheet = newSheet(sheet_name, headers)
  def row(cells: Cell*): Array[Cell]   = cells.toArray

  "EasyExcelJRuby#addRows" should "write a tmp CSV file" in {
    var sheet = newSheetPlz

    val data: Array[Row] = Array(
      row("a0", "b0", 0),
      row("a1", "b1", 1),
      row("a2", "b2", 2),
    )

    sheet = addRows(sheet, data, 0)

    sheet.pages should not be empty
    sheet.pages.forall(page => Files.exists(page.path)) shouldBe true
  }

  "EasyExcelJRuby#writeFile" should "write the xlsx file" in {
    var sheet = newSheetPlz

    val data0: Array[Row] = Array(
      row("a0", "b0", 0),
      row("a1", "b1", 1),
      row("a2", "b2", 2),
    )

    val data1: Array[Row] = Array(
      row("a01", "b01", 10),
      row("a11", "b11", 11),
      row("a21", "b21", 12),
    )

    val data2: Array[Row] = Array(
      row("a02", "", 20),
      row("a12", "", 21),
      row("a22", "", 22),
    )

    sheet = addRows(sheet, data2, 10)
    sheet = addRows(sheet, data1, 20)
    sheet = addRows(sheet, data0, 15)

    val fileName = s"fileName-${new Date()}.xlsx"

    writeFile(sheet, fileName)

    new File(fileName).exists() shouldBe true
    sheet.pages should not be empty
    sheet.pages.forall(page => Files.exists(page.path)) shouldBe true
  }

  "EasyExcelJRuby#writeFileAndClean" should "write the xlsx file and delete tmp csv files" in {
    var sheet = newSheetPlz

    val data0: Array[Row] = Array(
      row("a0", "b0", 0),
      row("a1", "b1", 1),
      row("a2", "b2", 2),
    )

    val data1: Array[Row] = Array(
      row("a01", "b01", 10),
      row("a11", "b11", 11),
      row("a21", "b21", 12),
    )

    val data2: Array[Row] = Array(
      row("a02", "", 20),
      row("a12", "", 21),
      row("a22", "", 22),
    )

    sheet = addRows(sheet, data2, 0)
    sheet = addRows(sheet, data1, 1)
    sheet = addRows(sheet, data0, 2)

    val fileName = s"fileName-${new Date()}.xlsx"

    writeFileAndClean(sheet, fileName)

    new File(fileName).exists() shouldBe true
    sheet.pages should not be empty
    sheet.pages.forall(page => Files.exists(page.path)) shouldBe false
  }

}
