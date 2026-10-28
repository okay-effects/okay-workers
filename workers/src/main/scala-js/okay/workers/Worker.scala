package okay.workers

import okay.scala3.*
import okay.scala3.std.*

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.typedarray.ArrayBuffer

/** A WORKER'S ENVIRONMENT: its vars and secrets by name, its bindings (D1, R2, `send_email`), and the request's
 * context (`waitUntil`) */
final class Env(val raw: js.Dynamic, val context: js.Dynamic, val state: js.Dynamic = null):
  /** a var or a secret: none when not set or empty */
  def text(name: String): Option[String] =
    val v = raw.selectDynamic(name)
    if js.isUndefined(v) || v == null then None else Some(v.toString).filter(_.nonEmpty)
  def binding(name: String): js.Dynamic = raw.selectDynamic(name)
  /** in a Durable Object: its storage (`Durable.sql(env.storage)`) */
  def storage: js.Dynamic = state.storage
  /** `f` kept running after the response is sent */
  def waitUntil(f: Future[Any]): Unit =
    if !js.isUndefined(context) && context != null then context.waitUntil(f.toJSPromise)

/**
 * THE WORKER: `fetch(request, env, ctx)` of a program — the request read (its body as bytes), `answer`'s program
 * started on `Async`, its response given; a program that fails is a 500 with no detail, the error on the console.
 * The application exports it from its module (`@JSExportTopLevel("fetch") val fetch = Worker.serve(…)`) and the
 * entry (`template/entry.mjs`) makes it the module's default
 */
object Worker:
  type Fetch = js.Function3[js.Dynamic, js.Dynamic, js.Dynamic, js.Promise[js.Dynamic]]

  def serve(answer: Env => Request => Response ! Async): Fetch = (request, env, ctx) =>
    serveIn(Env(env, ctx), answer)(request)

  /** a request the Worker passes, untouched, to the Durable Object `pick` names in `namespace` (a binding); the rest
   * answered here by `answer` */
  def front(namespace: String, pick: Request => Option[String])(answer: Env => Request => Response ! Async): Fetch =
    val here = serve(answer)
    (request, env, ctx) =>
      val r = Request(request.method.asInstanceOf[String], request.url.asInstanceOf[String],
        Js.headers(request.headers))
      pick(r) match
        case Some(name) =>
          val ns = env.selectDynamic(namespace)
          ns.get(ns.idFromName(name)).fetch(request).asInstanceOf[js.Promise[js.Dynamic]]
        case None => here(request, env, ctx)

  /** a Durable Object's `fetch`: as `serve`, the program given the object's state (`env.storage`) */
  def durable(state: js.Dynamic, env: js.Dynamic)(answer: Env => Request => Response ! Async)
    : js.Function1[js.Dynamic, js.Promise[js.Dynamic]] =
    request => serveIn(Env(env, null, state), answer)(request)

  private def serveIn(e: Env, answer: Env => Request => Response ! Async)(request: js.Dynamic)
    : js.Promise[js.Dynamic] =
    read(request).flatMap(r => Async.toFuture(answer(e)(r)))
      .recover { case err =>
        js.Dynamic.global.console.error("okay-workers: " + err.toString)
        Response.text(500, "Internal error")
      }.map(response).toJSPromise

  def read(r: js.Dynamic): Future[Request] =
    val method = r.method.asInstanceOf[String]
    val body: Future[Bytes] =
      if method == "GET" || method == "HEAD" then Future.successful(Bytes.empty)
      else Js.future[ArrayBuffer](r.arrayBuffer()).map(Js.bytes)
    body.map(b => Request(method, r.url.asInstanceOf[String], Js.headers(r.headers), b))

  def response(r: Response): js.Dynamic =
    val bodyless = r.status == 204 || r.status == 304 || (r.status >= 300 && r.status < 400 && r.body.isEmpty)
    js.Dynamic.newInstance(js.Dynamic.global.Response)(if bodyless then null else Js.array(r.body),
      js.Dynamic.literal(status = r.status, headers = Js.headersOf(r.headers)))
