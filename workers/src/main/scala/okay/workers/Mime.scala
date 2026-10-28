package okay.workers

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

/** an address and, maybe, the name shown with it */
final case class Address(email: String, name: String = ""):
  def header: String = if name.isEmpty then email else s"${Mime.word(name)} <$email>"

/** A LETTER: who from, to whom, its subject, its text and its HTML */
final case class Letter(from: Address, to: Address, subject: String, text: String, html: String,
                        replyTo: Option[Address] = None)

/**
 * A LETTER AS A RAW MIME MESSAGE — what Cloudflare's `send_email` takes: the headers it asks for (`From`, `To`,
 * `Subject`, `Message-ID`, `Date`), names and the subject as RFC 2047 words, the text and the HTML as one
 * multipart/alternative, each in base64 (UTF-8, lines of 76)
 */
object Mime:
  def message(letter: Letter, messageId: String, now: Long): String =
    val boundary = "okay-" + messageId.filter(_.isLetterOrDigit).take(24)
    val headers = Vector(
      "From: " + letter.from.header,
      "To: " + letter.to.header) ++ letter.replyTo.map(a => "Reply-To: " + a.header) ++ Vector(
      "Subject: " + word(letter.subject),
      s"Message-ID: <$messageId>",
      "Date: " + date(now),
      "MIME-Version: 1.0",
      s"Content-Type: multipart/alternative; boundary=\"$boundary\"")
    def part(kind: String, body: String) =
      s"--$boundary\r\nContent-Type: $kind; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n" + base64(body)
    headers.mkString("", "\r\n", "\r\n\r\n") + part("text/plain", letter.text) + "\r\n" +
      part("text/html", letter.html) + s"\r\n--$boundary--\r\n"

  /** a header's text: as it is when plain ASCII, else RFC 2047 words of at most 45 bytes each */
  def word(s: String): String =
    if s.forall(c => c >= ' ' && c < 127) then s
    else
      val words = Vector.newBuilder[String]
      val cur = new StringBuilder
      var bytes = 0
      var i = 0
      while i < s.length do
        val cp = s.codePointAt(i)
        val ch = new String(Character.toChars(cp))
        i += Character.charCount(cp)
        val n = ch.getBytes(UTF_8).length
        if bytes + n > 45 then
          words += cur.toString; cur.clear(); bytes = 0
        cur.append(ch); bytes += n
      if cur.nonEmpty then words += cur.toString
      words.result().map(w => "=?UTF-8?B?" + Base64.getEncoder.encodeToString(w.getBytes(UTF_8)) + "?=")
        .mkString("\r\n ")

  def base64(s: String): String =
    Base64.getEncoder.encodeToString(s.getBytes(UTF_8)).grouped(76).mkString("\r\n")

  private val days = Vector("Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Wed")
  private val months = Vector("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

  /** RFC 5322's date of an instant, in UTC — no java.time, which Scala.js does not have */
  def date(millis: Long): String =
    val (y, m, d) = Civil.ymd(Math.floorDiv(millis, 86400000L))
    val secs = Math.floorMod(millis / 1000, 86400L)
    val dow = days(Math.floorMod(Math.floorDiv(millis, 86400000L), 7L).toInt)
    f"$dow, $d%d ${months(m - 1)} $y%04d ${secs / 3600}%02d:${secs / 60 % 60}%02d:${secs % 60}%02d +0000"

/** days since 1970-01-01 as a civil date and back (Howard Hinnant's algorithms) */
object Civil:
  def ymd(days: Long): (Int, Int, Int) =
    val z = days + 719468
    val era = Math.floorDiv(z, 146097L)
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = (doy - (153 * mp + 2) / 5 + 1).toInt
    val m = (if mp < 10 then mp + 3 else mp - 9).toInt
    ((yoe + era * 400 + (if m <= 2 then 1 else 0)).toInt, m, d)

  /** an instant as ISO 8601 in UTC: `2026-10-28T14:03:00Z` */
  def iso(millis: Long): String =
    val (y, m, d) = ymd(Math.floorDiv(millis, 86400000L))
    val secs = Math.floorMod(millis / 1000, 86400L)
    f"$y%04d-$m%02d-$d%02dT${secs / 3600}%02d:${secs / 60 % 60}%02d:${secs % 60}%02dZ"
