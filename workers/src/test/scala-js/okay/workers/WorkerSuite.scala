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
