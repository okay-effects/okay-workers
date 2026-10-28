> **Superseded by [okay-effects/okay-serverless](https://github.com/okay-effects/okay-serverless); archived.** Its
> mail as MIME (now `Mails` with HTML, `Mails.deliver`) and its tests of an object's SQL under Node moved there
> (2026-10-28). The release 0.1.0 stays on GitHub Pages for anyone who resolved it.

# okay-workers

Cloudflare Workers for [okay-scala](https://github.com/okay-effects/okay-scala) programs. A Worker is a function from
a request to a program; D1, R2, `fetch` and `send_email` are handlers.

```scala
@JSExportTopLevel("fetch")
val fetch = Worker.serve(env => request => Fetch.run(D1.run(env.binding("DB"))(R2.run(env.binding("PHOTOS"))(
  App.answer(request)))))
```

- Effects: `Http` (`Send`), `Sql` (`All`, `Run`, `Batch`), `Blobs` (`Put`, `Get`, `Delete`).
- In a Worker (Scala.js, an ES module): `Worker.serve`, `D1.run`, `R2.run`, `Fetch.run`, `SendEmail.send` with `Mime`,
  `SendEmail.structured`, `Durable.sql` (a Durable Object's SQLite), `Worker.routed` / `Worker.durable`.
- On the JVM, for tests: `Jdbc.run` (SQLite: the same SQL as D1's), `Jdk.run`; everywhere `Blobs.memory()`,
  `Http.answering(f)`.
- `template/entry.mjs`: the Worker's entry around the linked `main.js`.

```scala-sbt
resolvers ++= Seq("okay-scala" at "https://okay-effects.github.io/okay-scala/maven",
  "okay-workers" at "https://okay-effects.github.io/okay-workers/maven")
libraryDependencies += "io.github.okay-effects" %%% "okay-workers" % "0.1.0"
```

Spec: [specs/workers.md](specs/workers.md). Apache 2.0.
