package okay.workers

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.typedarray.{ArrayBuffer, Int8Array, Uint8Array}

/** between JavaScript's values and ours */
private[workers] object Js:
  def bytes(buffer: ArrayBuffer): Bytes = Bytes(new Int8Array(buffer).toArray)
  def array(b: Bytes): Uint8Array =
    val a = b.toArray
    val out = new Uint8Array(a.length)
    var i = 0
    while i < a.length do
      out(i) = (a(i) & 0xff).toShort
      i += 1
    out
  def future[T](p: js.Any): Future[T] = p.asInstanceOf[js.Promise[T]].toFuture
  def headers(h: js.Dynamic): Vector[(String, String)] =
    val out = Vector.newBuilder[(String, String)]
    h.forEach((v: String, k: String) => out += k.toLowerCase -> v)
    out.result()
  def headersOf(hs: Vector[(String, String)]): js.Dynamic =
    val h = js.Dynamic.newInstance(js.Dynamic.global.Headers)()
    hs.foreach((k, v) => h.append(k, v))
    h
