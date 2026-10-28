package okay.workers

import okay.scala3.*
import okay.scala3.std.*

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.typedarray.ArrayBuffer

/** `Http` IN A WORKER (or any JavaScript with `fetch`): the global `fetch`, redirects not followed; no answer at all
 * is status 0 and the reason */
object Fetch:
  def run[R[_], Rest[_], A](p: A ! R)(using Split[Http, R, Rest], In[Async, Rest]): A ! Rest =
    handle(p, Handler.general[Http, Rest, A, A](pure)([X] => (op: Http[X], k: X => A ! Rest) => op match
      case Http.Send(r) => Async.fromFuture[Response, Rest](send(r)).flatMap(k)))

  def send(r: Request): Future[Response] =
    val init = js.Dynamic.literal(method = r.method, headers = Js.headersOf(r.headers), redirect = "manual")
    if !r.body.isEmpty then init.body = Js.array(r.body)
    Js.future[js.Dynamic](js.Dynamic.global.fetch(r.url, init)).flatMap { a =>
      Js.future[ArrayBuffer](a.arrayBuffer()).map(buf =>
        Response(a.status.asInstanceOf[Int], Js.headers(a.headers), Js.bytes(buf)))
    }.recover { case e => Response.failed(Option(e.getMessage).getOrElse(e.toString)) }
