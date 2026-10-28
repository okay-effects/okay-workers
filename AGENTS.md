# AGENTS.md — okay-workers

Read `specs/workers.md` first: the spec is written before the code and kept true after it.

- Cloudflare Workers for okay-scala programs: effects (`Http`, `Sql`, `Blobs`) and their handlers — Cloudflare's
  (`D1`, `R2`, `Fetch`, `Worker`, `SendEmail`) under `scala-js/`, the JVM's (`Jdbc`, `Jdk`) under `scala-jvm/`, the
  pure ones and the values shared. On okay-scala alone (its `std.Async`); no okay-simple.
- A handler is a translation of an effect, nothing else; an application's effects are the application's.
- Scala 3.3 LTS, lines at most 120 wide. `sbt test` runs the JVM (SQLite over JDBC) and Scala.js (Node).
- Releases: RELEASING.md — while nobody else depends on it, a change is republished under the current number.
- Commits: plain messages, no attribution trailers. Push what lands.
