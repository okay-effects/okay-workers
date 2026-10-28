package okay.workers

import okay.scala3.*
import okay.scala3.std.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.typedarray.ArrayBuffer

/** the Worker's `fetch` under Node, whose `Request` and `Response` are the Workers' */
class WorkerSuite extends munit.FunSuite:
  private val echo = Worker.serve(env => r =>
    Async.sleep[Async](1).map(_ => Response.text(201, s"${r.method} ${r.path} ${r.header("x-a").getOrElse("")} " +
      s"${r.body.text} ${env.text("SITE").getOrElse("-")}").withHeader("set-cookie", "a=1")
      .withHeader("set-cookie", "b=2")))

  test("a request in, a response out: method, path, headers, body, env, two cookies") {
    val req = js.Dynamic.newInstance(js.Dynamic.global.Request)("https://x.dev/pl/a?q=1",
      js.Dynamic.literal(method = "POST", headers = js.Dynamic.literal("X-A" -> "h"), body = "zażółć"))
    echo(req, js.Dynamic.literal(SITE = "szykownia"), js.Dynamic.literal()).toFuture.flatMap { res =>
      assertEquals(res.status.asInstanceOf[Int], 201)
      assertEquals(res.headers.getSetCookie().asInstanceOf[js.Array[String]].toVector, Vector("a=1", "b=2"))
      res.arrayBuffer().asInstanceOf[js.Promise[ArrayBuffer]].toFuture.map(b =>
        assertEquals(Js.bytes(b).text, "POST /pl/a h zażółć szykownia"))
    }
  }

  test("a program that fails is a 500 with no detail") {
    val failing = Worker.serve(_ => _ => defer[Response, Async](throw new RuntimeException("secret")))
    val req = js.Dynamic.newInstance(js.Dynamic.global.Request)("https://x.dev/")
    failing(req, js.Dynamic.literal(), js.Dynamic.literal()).toFuture.flatMap { res =>
      assertEquals(res.status.asInstanceOf[Int], 500)
      res.text().asInstanceOf[js.Promise[String]].toFuture.map(t => assert(!t.contains("secret")))
    }
  }

  test("a front: to an object by name, to the assets, or here") {
    val asked = scala.collection.mutable.Buffer.empty[String]
    def answering(what: String) = js.Dynamic.literal(fetch = ((_: js.Dynamic) => {
      asked += what
      js.Promise.resolve[js.Dynamic](js.Dynamic.newInstance(js.Dynamic.global.Response)(what))
    }): js.Function1[js.Dynamic, js.Promise[js.Dynamic]])
    val ns = js.Dynamic.literal(idFromName = ((n: String) => n): js.Function1[String, String],
      get = ((id: String) => answering("object " + id)): js.Function1[String, js.Dynamic])
    val env = js.Dynamic.literal(SITES = ns, ASSETS = answering("assets"))
    val front = Worker.routed("SITES")(r =>
      if r.path.startsWith("/api") then Worker.Route.ToObject("szykownia")
      else if r.path == "/" then Worker.Route.Here else Worker.Route.ToAssets)(
      _ => _ => pure(Response.redirect("/pl/")))
    def go(path: String) = front(js.Dynamic.newInstance(js.Dynamic.global.Request)("https://x.dev" + path), env,
      js.Dynamic.literal()).toFuture
    for
      a <- go("/api/order")
      b <- go("/pl/")
      c <- go("/")
    yield
      assertEquals(asked.toVector, Vector("object szykownia", "assets"))
      assertEquals(c.status.asInstanceOf[Int], 302)
  }
