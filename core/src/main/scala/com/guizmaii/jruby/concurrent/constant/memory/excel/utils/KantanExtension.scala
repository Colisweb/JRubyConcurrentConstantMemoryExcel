package com.guizmaii.jruby.concurrent.constant.memory.excel.utils

import kantan.csv.{CellEncoder, RowEncoder}

private[excel] object KantanExtension {

  implicit final def arrayEncoder[A](implicit CellEncoder: CellEncoder[A]): RowEncoder[Array[A]] =
    (array: Array[A]) => array.map(CellEncoder.encode)

}
