package okay.workers

import okay.scala3.*

class ValuesSuite extends munit.FunSuite:
  test("a request's path, host, query and cookies") {
    val r = Request("GET", "https://szykownia.pl:443/pl/track?ref=SZY-7K3F2Q&k=a%2Bb+c#x",
      Vector("cookie" -> "a=1; lang=uk", "accept-language" -> "pl-PL,pl;q=0.9"))
    assertEquals(r.path, "/pl/track")
    assertEquals(r.host, "szykownia.pl")
    assertEquals(r.param("ref"), Some("SZY-7K3F2Q"))
    assertEquals(r.param("k"), Some("a+b c"))
    assertEquals(r.cookie("lang"), Some("uk"))
    assertEquals(r.header("Accept-Language"), Some("pl-PL,pl;q=0.9"))
    assertEquals(Request("GET", "https://a.b").path, "/")
  }

  test("a form both ways, UTF-8 and reserved characters") {
    val kv = Vector("imię" -> "Żaneta & co", "x" -> "a=b+c%")
    assertEquals(Form.decode(Form.encode(kv)), kv)
    assertEquals(Form.escape("ą b"), "%C4%85%20b")
  }

  test("blobs in memory") {
    val p = for
      _ <- Blobs.put[Blobs](k, Bytes.text("x"), "text/plain")
      got <- Blobs.get[Blobs](k)
      _ <- Blobs.delete[Blobs]("none")
    yield got
    val (m, got) = run(Blobs.memory()(p))
    assertEquals(got.map(_.bytes.text), Some("x"))
    assertEquals(m.keySet, Set(k))
  }
  private val k = "szykownia/SZY-1/1.jpg"

  test("an instant as a date and as ISO") {
    assertEquals(Mime.date(0L), "Thu, 1 Jan 1970 00:00:00 +0000")
    assertEquals(Mime.date(1793282400000L), "Thu, 29 Oct 2026 14:00:00 +0000")
    assertEquals(Civil.iso(1793282400000L), "2026-10-29T14:00:00Z")
    assertEquals(Civil.iso(951782400000L), "2000-02-29T00:00:00Z")
  }

  test("a letter as MIME: headers, encoded words, both parts") {
    val l = Letter(Address("kontakt@szykownia.pl", "Szykownia"), Address("klient@example.com"),
      "Potwierdzenie zlecenia — Szykownia", "Dziękujemy!", "<p>Dziękujemy!</p>",
      Some(Address("kontakt@szykownia.pl")))
    val m = Mime.message(l, "abc123@szykownia.pl", 0L)
    assert(m.startsWith("From: Szykownia <kontakt@szykownia.pl>\r\nTo: klient@example.com\r\n"), m)
    assert(m.contains("Reply-To: kontakt@szykownia.pl\r\n"))
    assert(m.contains("Subject: =?UTF-8?B?"))
    assert(m.contains("Message-ID: <abc123@szykownia.pl>"))
    assert(m.contains("Content-Type: text/plain; charset=UTF-8"))
    assert(m.contains("Content-Type: text/html; charset=UTF-8"))
    val subject = m.linesIterator.dropWhile(!_.startsWith("Subject:")).takeWhile(l => l.startsWith("Subject:") ||
      l.startsWith(" ")).map(_.replace("Subject: ", "").trim).toVector
    val decoded = subject.map(w => new String(java.util.Base64.getDecoder.decode(w.stripPrefix("=?UTF-8?B?")
      .stripSuffix("?=")), "UTF-8")).mkString
    assertEquals(decoded, l.subject)
    assert(subject.forall(_.length <= 75))
    assert(m.contains(Mime.base64("<p>Dziękujemy!</p>")))
  }
