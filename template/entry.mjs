// THE WORKER'S ENTRY (okay-workers, specs/workers.md): the module linked by Scala.js (`main.js`, an ES module whose
// `fetch` is `Worker.serve(…)`) made the Worker's default export; `EmailMessage` handed to it through the
// environment, so the linked module never imports `cloudflare:email` itself
import { EmailMessage } from "cloudflare:email";
import { fetch as answer } from "./main.js";

export default {
  fetch(request, env, ctx) {
    const withMail = Object.create(env);
    withMail.EmailMessage = EmailMessage;
    return answer(request, withMail, ctx);
  },
};
