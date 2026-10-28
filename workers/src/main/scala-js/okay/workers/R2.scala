package okay.workers

import okay.scala3.*
import okay.scala3.std.*

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.typedarray.ArrayBuffer

/** `Blobs` IN A WORKER: an R2 bucket's binding */
object R2:
  def run[R[_], Rest[_], A](bucket: js.Dynamic)(p: A ! R)(using Split[Blobs, R, Rest], In[Async, Rest]): A ! Rest =
    handle(p, Handler.general[Blobs, Rest, A, A](pure)([X] => (op: Blobs[X], k: X => A ! Rest) => op match
      case Blobs.Put(key, b) => Async.fromFuture[Unit, Rest](put(bucket, key, b)).flatMap(k)
      case Blobs.Get(key) => Async.fromFuture[Option[Blob], Rest](get(bucket, key)).flatMap(k)
      case Blobs.Delete(key) => Async.fromFuture[Unit, Rest](
        Js.future[js.Any](bucket.delete(key)).map(_ => ())).flatMap(k)))

  def put(bucket: js.Dynamic, key: String, b: Blob): Future[Unit] =
    Js.future[js.Any](bucket.put(key, Js.array(b.bytes),
      js.Dynamic.literal(httpMetadata = js.Dynamic.literal(contentType = b.contentType)))).map(_ => ())

  def get(bucket: js.Dynamic, key: String): Future[Option[Blob]] =
    Js.future[js.Dynamic](bucket.get(key)).flatMap { o =>
      if o == null || js.isUndefined(o) then Future.successful(None)
      else
        val kind = Option(o.httpMetadata).filterNot(js.isUndefined).flatMap(m =>
          Option(m.contentType).filterNot(js.isUndefined).map(_.toString)).getOrElse("application/octet-stream")
        Js.future[ArrayBuffer](o.arrayBuffer()).map(buf => Some(Blob(Js.bytes(buf), kind)))
    }
