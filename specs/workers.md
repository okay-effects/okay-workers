# okay-workers — Cloudflare Workers for okay-scala programs

## Overview

A program on okay-scala answers a Worker's requests: its effects are the data model, Cloudflare's bindings are
handlers. `Worker.serve` makes the ES module's `fetch(request, env, ctx)` of a function from the request to a program
over `Async` (okay-scala's `std.Async`); D1, R2, `fetch` and Cloudflare's `send_email` are handlers into `Async`.

It generalises the hand-written fronts of okay-guard (`worker/`) and okay-remote (`relay/`) — routes over
`js.Dynamic`, a Durable Object behind — into effects and handlers. Why a repository of its own: okay-http and okay-faces' Cloudflare module are on okay-simple (okay-http: "on
okay-simple alone"); this is okay-scala's, built on its `Async`, with nothing of okay-simple. What a Worker needs that
is not Cloudflare's — the request and response as values, SQL and blobs as effects, the JVM's handlers for tests — is
here too, so a program is tested on the JVM against SQLite, the same SQL as D1's.

## Interface

| effect | operations | handlers |
| --- | --- | --- |
| `Http` | `Send(Request): Response` | `Fetch.run` (JS: the global `fetch`), `Jdk.run` (JVM: `java.net.http`), `Http.answering(f)` (tests) |
| `Sql` | `All(sql, params): Vector[Row]`, `Run(sql, params): Long` (rows changed), `Batch(statements): Unit` (atomic) | `D1.run(db)` (JS), `Jdbc.run(connection)` (JVM: SQLite) |
| `Blobs` | `Put(key, bytes, contentType)`, `Get(key): Option[Blob]`, `Delete(key)` | `R2.run(bucket)` (JS), `Blobs.memory()` (both) |

Values: `Request(method, url, headers, body: Bytes)`, `Response(status, headers, body: Bytes)` with `text`,
`Response.text/html/json/redirect`; `Bytes` (an immutable `Array[Byte]` wrapper, UTF-8 helpers); `Value` of SQL
(`Text`, `Integer`, `Real`, `Null`, `Blob`); `Row` (column → `Value`, `text(c)`, `long(c)`).

JS only:

- `Worker.serve(answer: Env => Request => Response ! Async)`: the `fetch` the entry exports — the JS request read
  (its body as bytes), the program started with `Async.start`, the response made; a failure is a 500 with no detail
  and the error on the console. `Env` is the bindings and vars as given (`env.text("SITE")`, `env.binding("DB")`).
- `SendEmail(binding, EmailMessage)` and `Mime`: a letter as a raw MIME message (UTF-8, base64 text and
  HTML, RFC 2047 subject), sent through a `send_email` binding. `EmailMessage` is passed in by the entry
  (`import { EmailMessage } from "cloudflare:email"`) so the module loads outside Workers (Node's tests).
- `SendEmail.structured(binding, letter)`: Cloudflare Email Service's structured `send` (Workers Paid, beta), to any
  recipient the binding allows.
- `Durable.sql(storage)`: `Sql` in a Durable Object's SQLite storage (a `new_sqlite_classes` class: the free plan's,
  where D1 is not to be had) — synchronous, a batch in `transactionSync`. `Worker.front(namespace, pick)(answer)`: a
  request `pick` names passed untouched to that Durable Object, the rest answered in the Worker;
  `Worker.durable(state, env)(answer)`: the object's `fetch`, its storage in `env.storage`.
- `template/entry.mjs`: the Worker's entry — imports the linked module, exports `default { fetch }`.

## Behavior

- [x] A program's SQL runs the same on D1 and on SQLite over JDBC: `All` gives rows by column name, `Run` the count
      changed, `Batch` all or none (D1's `batch`, JDBC's transaction). Parameters are bound, never spliced.
- [x] `Worker.serve` gives the program the request's method, URL, headers (lower-case names) and body bytes; a
      response's status, headers (a header given twice: `Set-Cookie`) and body reach the client.
- [ ] `Fetch.run` sends a request and gives its answer; a network failure is status 0 with the reason as the body.
- [x] `Mime` makes a message Cloudflare accepts: `From`, `To`, `Reply-To`, `Subject` encoded, `Message-ID`, `Date`,
      multipart/alternative text + HTML.
- [x] Tests: on the JVM (SQLite in memory via JDBC, the HTTP server of the JDK for `Jdk.run`); on Scala.js under
      Node the pure parts and `Worker.serve` with Node's own `Request`/`Response`. D1, R2, `Fetch` and `SendEmail`
      are proved by the applications' runs under `wrangler dev` (miniflare).

## Design

Scala 3.3, JVM and Scala.js (both always built), okay-scala 0.1.1. Package `okay.workers`. Released as okay-scala
is: a Maven repository on GitHub Pages (RELEASING.md).
