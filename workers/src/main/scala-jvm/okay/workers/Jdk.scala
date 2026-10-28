package okay.workers

import okay.scala3.*

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import scala.jdk.CollectionConverters.*

/** `Http` ON THE JVM: the JDK's client, each request answered in place (a tool's, a test's) */
object Jdk:
  private lazy val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

  def run[R[_], Rest[_], A](p: A ! R)(using Split[Http, R, Rest]): A ! Rest =
    handle(p, Handler.tail[Http, Rest, A, A](pure)([X] => (op: Http[X]) => op match
      case Http.Send(r) => send(r)))

  def send(r: Request): Response =
    try
      val restricted = Set("host", "content-length", "connection", "expect", "upgrade")
      val b = HttpRequest.newBuilder(URI.create(r.url))
        .method(r.method, if r.body.isEmpty then HttpRequest.BodyPublishers.noBody()
          else HttpRequest.BodyPublishers.ofByteArray(r.body.toArray))
      r.headers.filterNot((k, _) => restricted(k.toLowerCase)).foreach((k, v) => b.header(k, v))
      val answer = client.send(b.build(), HttpResponse.BodyHandlers.ofByteArray())
      Response(answer.statusCode, answer.headers.map.asScala.toVector.flatMap((k, vs) =>
        vs.asScala.map(k.toLowerCase -> _)), Bytes(answer.body))
    catch case e: Exception => Response.failed(Option(e.getMessage).getOrElse(e.toString))
