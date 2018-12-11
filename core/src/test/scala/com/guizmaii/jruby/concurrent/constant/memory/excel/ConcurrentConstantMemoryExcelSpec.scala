package com.guizmaii.jruby.concurrent.constant.memory.excel

import java.io.File
import java.nio.file.Files
import java.util.Date

import org.scalatest.{FlatSpec, Matchers}

class ConcurrentConstantMemoryExcelSpec extends FlatSpec with Matchers {

  "true" should "be true" in {
    true shouldBe true
  }

  import ConcurrentConstantMemoryExcel._

  val sheet_name = "SHEET_NAME"
  val headers    = Array("A", "B", "C")

  // Ugly but handy. Don't abuse of that !
  implicit final def toCell(value: String): Cell = if (value.isEmpty) blankCell else stringCell(value)
  implicit final def toCell(value: Double): Cell = numericCell(value)

  def newCMStPlz: ConcurrentConstantMemoryState = newSheet(sheet_name, headers)
  def row(cells: Cell*): Array[Cell]            = cells.toArray

  "ConcurrentConstantMemoryExcel#addRows" should "write a tmp CSV file" in {
    var cms = newCMStPlz

    val data: Array[Row] = Array(
      row("a0", "b0", 0),
      row("a1", "b1", 1),
      row("a2", "b2", 2),
    )

    cms = addRows(cms, data, 0)

    cms.pages should not be empty
    cms.pages.forall(page => Files.exists(page.path)) shouldBe true
  }

  "ConcurrentConstantMemoryExcel#writeFile" should "write the xlsx file" in {
    var cms = newCMStPlz

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

    cms = addRows(cms, data2, 10)
    cms = addRows(cms, data1, 20)
    cms = addRows(cms, data0, 15)

    val fileName = s"fileName-${new Date()}.xlsx"

    writeFile(cms, fileName)

    new File(fileName).exists() shouldBe true
    cms.pages should not be empty
    cms.pages.forall(page => Files.exists(page.path)) shouldBe true
  }

  "ConcurrentConstantMemoryExcel#writeFileAndClean" should "write the xlsx file and delete tmp csv files" in {
    var cms = newCMStPlz

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

    cms = addRows(cms, data2, 0)
    cms = addRows(cms, data1, 1)
    cms = addRows(cms, data0, 2)

    val fileName = s"fileName-${new Date()}.xlsx"

    writeFileAndClean(cms, fileName)

    new File(fileName).exists() shouldBe true
    cms.pages should not be empty
    cms.pages.forall(page => Files.exists(page.path)) shouldBe false
  }

}
