package com.colisweb.jruby.concurrent.constant.memory.excel

import java.io.File
import java.nio.file.Files
import java.util.Date

import monix.execution.atomic.Atomic
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

  def newCMSPlz: Atomic[ConcurrentConstantMemoryState] = newWorkbookState(sheet_name, headers)
  def row(cells: Cell*): Array[Cell]                   = cells.toArray

  "ConcurrentConstantMemoryExcel#addRows" should "write a tmp CSV file" in {
    val cms = newCMSPlz

    val data: Array[Row] = Array(
      row("a0", "b0", 0),
      row("a1", "b1", 1),
      row("a2", "b2", 2),
    )

    addRows(cms, data, 0)

    cms.get().pages should not be empty
  }

  "ConcurrentConstantMemoryExcel#writeFile" should "write the xlsx file" in {
    val cms = newCMSPlz

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

    addRows(cms, data2, 10)
    addRows(cms, data1, 20)
    addRows(cms, data0, 15)

    val fileName = s"target/fileName-${new Date()}.xlsx"

    writeFile(cms, fileName)

    new File(fileName).exists() shouldBe true
    cms.get().pages should not be empty
    cms.get().pages.forall(page => Files.exists(page.path)) shouldBe false // clean the tmp CSV files automatically.
  }

}
