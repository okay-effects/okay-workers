package okay.workers

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

/** A LETTER THROUGH CLOUDFLARE'S `send_email` BINDING. `send`: Email Routing's way (any plan) — the letter as a raw MIME message (`Mime`) in an
 * `EmailMessage`, the constructor given by the entry (`import { EmailMessage } from "cloudflare:email"`) — so this
 * module loads where there is no `cloudflare:email`. The recipient must be a verified destination of the account */
object SendEmail:
  def send(binding: js.Dynamic, emailMessage: js.Dynamic, letter: Letter, messageId: String, now: Long)
    : Future[Unit] =
    val raw = Mime.message(letter, messageId, now)
    val message = js.Dynamic.newInstance(emailMessage)(letter.from.email, letter.to.email, raw)
    Js.future[js.Any](binding.send(message)).map(_ => ())

  /** Cloudflare Email Service's way (`send({from, to, subject, …})`, Workers Paid, beta): to any recipient the
   * binding allows, from an address of a domain onboarded to Email Service; its message id */
  def structured(binding: js.Dynamic, letter: Letter): Future[String] =
    def address(a: Address) = js.Dynamic.literal(email = a.email, name = a.name)
    val m = js.Dynamic.literal(from = address(letter.from), to = address(letter.to), subject = letter.subject,
      html = letter.html, text = letter.text)
    letter.replyTo.foreach(a => m.replyTo = address(a))
    Js.future[js.Dynamic](binding.send(m)).map(r => if js.isUndefined(r.messageId) then "" else r.messageId.toString)
