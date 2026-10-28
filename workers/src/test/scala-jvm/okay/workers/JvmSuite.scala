package okay.workers

import okay.scala3.*

import java.net.InetSocketAddress
import java.sql.DriverManager

class JvmSuite extends munit.FunSuite:
  test("SQL on SQLite over JDBC: rows by name, changes, a batch all or none") {
    val c = DriverManager.getConnection("jdbc:sqlite::memory:")
    val p = for
      _ <- Sql.run[Sql]("create table t (k text primary key, n integer, d real, b blob, z text)")
      one <- Sql.run[Sql]("insert into t values (?, ?, ?, ?, ?)", "a", 1L, 2.5, Value.Blob(Bytes.text("x")), Value.Null)
      _ <- Sql.batch[Sql](Vector(Statement("insert into t (k, n) values (?, ?)", "b", 2L),
        Statement("update t set n = n + 1 where k = ?", "a")))
      rows <- Sql.all[Sql]("select * from t order by k")
    yield (one, rows)
    val (one, rows) = run(Jdbc.run(c)(p))
    assertEquals(one, 1L)
    assertEquals(rows.map(_.text("k")), Vector("a", "b"))
    assertEquals(rows.map(_.long("n")), Vector(2L, 2L))
    assertEquals(rows.head.get("d"), Value.Real(2.5))
    assertEquals(rows.head.get("b"), Value.Blob(Bytes.text("x")))
    assertEquals(rows.head.get("z"), Value.Null)
    // a batch that fails changes nothing
    val failing = Sql.batch[Sql](Vector(Statement("insert into t (k) values (?)", "c"),
      Statement("insert into t (k) values (?)", "a")))
    intercept[Exception](run(Jdbc.run(c)(failing)))
    assertEquals(run(Jdbc.run(c)(Sql.all[Sql]("select k from t"))).size, 2)
  }

  test("HTTP out through the JDK: method, headers, body, status; no answer is status 0") {
    val server = com.sun.net.httpserver.HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/echo", x => {
      val body = x.getRequestBody.readAllBytes()
      val out = (x.getRequestMethod + " " + x.getRequestHeaders.getFirst("api-key") + " " + new String(body))
        .getBytes("UTF-8")
      x.getResponseHeaders.add("x-a", "1")
      x.sendResponseHeaders(201, out.length)
      x.getResponseBody.write(out)
      x.close()
    })
    server.start()
    try
      val url = s"http://127.0.0.1:${server.getAddress.getPort}/echo"
      val r = run(Jdk.run(Http.send[Http](Request("POST", url, Vector("api-key" -> "k"), Bytes.text("ą")))))
      assertEquals(r.status, 201)
      assertEquals(r.text, "POST k ą")
      assertEquals(r.header("X-A"), Some("1"))
      assertEquals(run(Jdk.run(Http.send[Http](Request("GET", "http://127.0.0.1:1/")))).status, 0)
    finally server.stop(0)
  }
