package okay.workers

import okay.scala3.*

/** A REQUEST: its method, its whole URL, its headers (names in lower case, in order; a name may come twice) and its
 * body */
final case class Request(method: String, url: String, headers: Vector[(String, String)] = Vector.empty,
                         body: Bytes = Bytes.empty):
  /** a header's first value, by its name in any case */
  def header(name: String): Option[String] =
    val n = name.toLowerCase
    headers.collectFirst { case (k, v) if k == n => v }
  /** the path, without the query: `/pl/track` */
  def path: String =
    val rest = url.indexOf("://") match
      case -1 => url
      case i => url.indexOf('/', i + 3) match
        case -1 => "/"
        case j => url.substring(j)
    rest.takeWhile(c => c != '?' && c != '#')
  /** the host, without the port */
  def host: String =
    val i = url.indexOf("://")
    if i < 0 then "" else url.substring(i + 3).takeWhile(c => c != '/' && c != ':' && c != '?')
  /** the query's parameters, decoded, in order */
  def query: Vector[(String, String)] = url.indexOf('?') match
    case -1 => Vector.empty
    case i => Form.decode(url.substring(i + 1).takeWhile(_ != '#'))
  def param(name: String): Option[String] = query.collectFirst { case (k, v) if k == name => v }
  /** a cookie by its name */
  def cookie(name: String): Option[String] =
    headers.collect { case ("cookie", v) => v }.flatMap(_.split(';')).map(_.trim).collectFirst {
      case c if c.startsWith(name + "=") => Form.unescape(c.drop(name.length + 1))
    }

/** AN ANSWER: its status, its headers (a name may come twice: `set-cookie`), its body */
final case class Response(status: Int, headers: Vector[(String, String)] = Vector.empty, body: Bytes = Bytes.empty):
  def ok: Boolean = status >= 200 && status < 300
  def text: String = body.text
  def header(name: String): Option[String] =
    val n = name.toLowerCase
    headers.collectFirst { case (k, v) if k.toLowerCase == n => v }
  def withHeader(name: String, value: String): Response = copy(headers = headers :+ (name -> value))

object Response:
  def text(status: Int, body: String, contentType: String = "text/plain; charset=utf-8"): Response =
    Response(status, Vector("content-type" -> contentType), Bytes.text(body))
  def html(body: String, status: Int = 200): Response = text(status, body, "text/html; charset=utf-8")
  def json(body: String, status: Int = 200): Response = text(status, body, "application/json; charset=utf-8")
  /** a redirect: 302 by default, 301 for a move for good */
  def redirect(location: String, status: Int = 302): Response = Response(status, Vector("location" -> location))
  /** no answer at all — the network, a refusal before any status: its reason the body */
  def failed(reason: String): Response = text(0, reason)

/** `application/x-www-form-urlencoded`, both ways — a query, a form */
object Form:
  def decode(s: String): Vector[(String, String)] =
    s.split('&').toVector.filter(_.nonEmpty).map { kv =>
      kv.indexOf('=') match
        case -1 => unescape(kv) -> ""
        case i => unescape(kv.substring(0, i)) -> unescape(kv.substring(i + 1))
    }
  def encode(kvs: Iterable[(String, String)]): String = kvs.map((k, v) => s"${escape(k)}=${escape(v)}").mkString("&")

  def unescape(s: String): String =
    val bytes = new java.io.ByteArrayOutputStream()
    var i = 0
    while i < s.length do
      s.charAt(i) match
        case '+' => bytes.write(' '); i += 1
        case '%' if i + 2 < s.length && hex(s.charAt(i + 1)) >= 0 && hex(s.charAt(i + 2)) >= 0 =>
          bytes.write(hex(s.charAt(i + 1)) * 16 + hex(s.charAt(i + 2))); i += 3
        case c =>
          val b = c.toString.getBytes(java.nio.charset.StandardCharsets.UTF_8)
          bytes.write(b, 0, b.length); i += 1
    new String(bytes.toByteArray, java.nio.charset.StandardCharsets.UTF_8)

  def escape(s: String): String =
    val sb = new StringBuilder
    s.getBytes(java.nio.charset.StandardCharsets.UTF_8).foreach { b =>
      val c = (b & 0xff).toChar
      if (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || "-_.~".indexOf(c) >= 0 then
        sb.append(c)
      else sb.append('%').append("0123456789ABCDEF".charAt((b >> 4) & 0xf)).append("0123456789ABCDEF".charAt(b & 0xf))
    }
    sb.toString

  private def hex(c: Char): Int =
    if c >= '0' && c <= '9' then c - '0' else if c >= 'a' && c <= 'f' then c - 'a' + 10
    else if c >= 'A' && c <= 'F' then c - 'A' + 10 else -1

/** A REQUEST OUT AND ITS ANSWER, nothing else: who answers is a handler's — the Worker's `fetch`, the JDK's client, a
 * test's function. No answer at all is status 0, its reason the body */
enum Http[X]:
  case Send(request: Request) extends Http[Response]

object Http:
  def send[R[_]](request: Request)(using In[Http, R]): Response ! R = perform[Http](Send(request))

  /** tests: every request answered by `f`, in place */
  def answering[R[_], Rest[_], A](f: Request => Response)(p: A ! R)(using Split[Http, R, Rest]): A ! Rest =
    handle(p, Handler.tail[Http, Rest, A, A](pure)([X] => (op: Http[X]) => op match
      case Send(r) => f(r)))
