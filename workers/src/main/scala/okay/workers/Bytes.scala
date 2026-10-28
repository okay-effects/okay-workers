package okay.workers

import java.nio.charset.StandardCharsets.UTF_8

/** BYTES AS A VALUE: an array never changed after it is given — a request's body, a blob, a photo */
final class Bytes private (private val array: Array[Byte]):
  def length: Int = array.length
  def isEmpty: Boolean = array.isEmpty
  /** a copy: the value stays as it is */
  def toArray: Array[Byte] = array.clone()
  /** the bytes as UTF-8 text */
  def text: String = new String(array, UTF_8)
  override def equals(o: Any): Boolean = o match
    case b: Bytes => java.util.Arrays.equals(array, b.array)
    case _ => false
  override def hashCode: Int = java.util.Arrays.hashCode(array)
  override def toString: String = s"Bytes($length)"

object Bytes:
  val empty: Bytes = new Bytes(Array.emptyByteArray)
  def apply(a: Array[Byte]): Bytes = new Bytes(a.clone())
  def text(s: String): Bytes = new Bytes(s.getBytes(UTF_8))
