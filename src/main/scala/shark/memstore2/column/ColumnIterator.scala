/*
 * Copyright (C) 2012 The Regents of The University California.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package shark.memstore2.column

import java.nio.ByteBuffer
import org.apache.hadoop.io.Writable
import java.nio.ByteOrder

/** Iterator interface for a column. The iterator should be initialized by a
 * byte buffer, and next can be invoked to get the value for each cell.
 *
 * Adding a new compression/encoding scheme to the code requires several
 * things. First among them is an addition to the list of iterators here. An
 * iterator that knows how to iterate through an encoding-specific ByteBuffer is
 * required. This ByteBuffer would have been created by the one of the concrete
 * [[shark.memstore2.buffer.ColumnBuilder]] classes.  
 * 
 * 
 * The relationship/composition possibilities between the new
 * encoding/compression and existing schemes dictates how the new iterator is
 * implemented.  Null Encoding and RLE Encoding working as generic wrappers that
 * can be wrapped around any data type.  Dictionary Encoding does not work in a
 * hierarchial manner instead requiring the creation of a separate
 * DictionaryEncoded Iterator per Data type.
 * 
 * The changes required for the LZF encoding's Builder/Iterator might be the
 * easiest to look to get a feel for what is required -
 * [[shark.memstore2.buffer.LZFColumnIterator]]. See SHA 225f4d90d8721a9d9e8f
 * 
 * The base class ColumnIterator is the read side of this equation. For the
 * write side see [[shark.memstore2.buffer.ColumnBuilder]].
 * 
 */
trait ColumnIterator {

  private var _initialized = false
  
  def init(): Unit = {}
  def next(): Unit = {
    if (!_initialized) {
      init()
      _initialized = true
    }
    computeNext()
  }
  def computeNext(): Unit

  // Should be implemented as a read-only operation by the ColumnIterator
  // Can be called any number of times
  def current(): Object
}

abstract class DefaultColumnIterator[T, V](val buffer: ByteBuffer,
  val columnType: ColumnType[T, V]) extends CompressedColumnIterator{}

object Implicits {
  implicit def intToCompressionType(i: Int): CompressionType = i match {
    case -1 => DEFAULT
    case 0 => RLECompressionType
    case _ => throw new UnsupportedOperationException("Compression Type " + i)
  }

  implicit def intToColumnType(i: Int): ColumnType[_, _] = i match {
    case 0 => INT
    case 1 => LONG
    case 2 => FLOAT
    case 3 => DOUBLE
    case 4 => BOOLEAN
    case 5 => BYTE
    case 6 => SHORT
    case 7 => VOID
    case 8 => STRING
    case 9 => TIMESTAMP
    case 10 => BINARY
    case 11 => GENERIC
  }
}

object ColumnIterator {
  
  import shark.memstore2.column.Implicits._

  def newIterator(b: ByteBuffer): ColumnIterator = {
    val buffer = b.duplicate().order(ByteOrder.nativeOrder())
    val columnType: ColumnType[_, _] = buffer.getInt()
    val v = columnType match {
      case INT => new IntColumnIterator(buffer)
      case LONG => new LongColumnIterator(buffer)
      case FLOAT => new FloatColumnIterator(buffer)
      case DOUBLE => new DoubleColumnIterator(buffer)
      case BOOLEAN => new BooleanColumnIterator(buffer)
      case BYTE => new ByteColumnIterator(buffer)
      case SHORT => new ShortColumnIterator(buffer)
      case VOID => new VoidColumnIterator(buffer)
      case STRING => new StringColumnIterator(buffer)
      case BINARY => new BinaryColumnIterator(buffer)
      case TIMESTAMP => new TimestampColumnIterator(buffer)
      case GENERIC => new GenericColumnIterator(buffer)
    }
    new NullableColumnIterator(v, buffer)
  }
  
}

