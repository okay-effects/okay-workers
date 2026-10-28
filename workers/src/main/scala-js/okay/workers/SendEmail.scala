package okay.workers

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

/** A LETTER THROUGH CLOUDFLARE'S `send_email` BINDING: the letter as a raw MIME message (`Mime`) in an
 * `EmailMessage`, the constructor given by the entry (`import { EmailMessage } from "cloudflare:email"`) — so this
 * module loads where there is no `cloudflare:email`. The recipient must be a verified destination of the account */
object SendEmail:
  def send(binding: js.Dynamic, emailMessage: js.Dynamic, letter: Letter, messageId: String, now: Long)
    : Future[Unit] =
    val raw = Mime.message(letter, messageId, now)
    val message = js.Dynamic.newInstance(emailMessage)(letter.from.email, letter.to.email, raw)
    Js.future[js.Any](binding.send(message)).map(_ => ())
