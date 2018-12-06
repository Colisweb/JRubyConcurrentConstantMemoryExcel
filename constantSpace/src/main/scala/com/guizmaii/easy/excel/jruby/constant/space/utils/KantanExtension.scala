package com.guizmaii.easy.excel.jruby.constant.space.utils

import kantan.csv.{CellDecoder, CellEncoder, RowDecoder, RowEncoder}

import scala.reflect.ClassTag

private[space] object KantanExtension {

  /**
    * Unsafe implementation.
    *
    * TODO: Prefer a safe version ?
    */
  implicit final def arrayDecoder[A: ClassTag](
      implicit CellDecoder: CellDecoder[A]
  ): RowDecoder[Array[A]] =
    RowDecoder.fromUnsafe { array =>
      val acc = Array.empty[A]
      for (a <- array) acc :+ CellDecoder.unsafeDecode(a)
      acc
    }

  implicit final def arrayEncoder[A](implicit CellEncoder: CellEncoder[A]): RowEncoder[Array[A]] =
    (array: Array[A]) => array.map(CellEncoder.encode)

}
