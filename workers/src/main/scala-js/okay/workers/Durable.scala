package okay.workers

import okay.scala3.*

import scala.scalajs.js
import scala.scalajs.js.typedarray.ArrayBuffer

/**
 * `Sql` IN A DURABLE OBJECT: its SQLite storage (`state.storage.sql`, a class of `new_sqlite_classes`, the free plan's
 * too) — synchronous, so each statement is answered in place; a batch in `transactionSync`, all or none. The same
 * SQL as D1's and as SQLite's over JDBC on the JVM
 */
object Durable:
  def sql[R[_], Rest[_], A](storage: js.Dynamic)(p: A ! R)(using Split[Sql, R, Rest]): A ! Rest =
    handle(p, Handler.tail[Sql, Rest, A, A](pure)([X] => (op: Sql[X]) => op match
      case Sql.All(s) => all(storage, s)
      case Sql.Run(s) => change(storage, s)
      case Sql.Batch(ss) => batch(storage, ss)))

  def all(storage: js.Dynamic, s: Statement): Vector[Row] =
    cursor(storage, s).toArray().asInstanceOf[js.Array[js.Dictionary[js.Any]]].toVector.map(o =>
      Row(o.toMap.map((k, v) => k -> value(v))))

  def change(storage: js.Dynamic, s: Statement): Long =
    val c = cursor(storage, s)
    c.toArray() // run it to its end: the count is known then
    c.rowsWritten.asInstanceOf[Double].toLong

  def batch(storage: js.Dynamic, ss: Vector[Statement]): Unit =
    storage.transactionSync((() => ss.foreach(s => change(storage, s))): js.Function0[Unit])

  private def cursor(storage: js.Dynamic, s: Statement): js.Dynamic =
    val args: Seq[js.Any] = s.params.map[js.Any] {
      case Value.Text(t) => t
      case Value.Integer(n) => n.toDouble
      case Value.Real(d) => d
      case Value.Blob(b) => Js.array(b).buffer
      case Value.Null => null
    }
    storage.sql.applyDynamic("exec")((s.sql: js.Any) +: args*)

  private[workers] def value(v: js.Any): Value =
    if v == null || js.isUndefined(v) then Value.Null
    else (v: Any) match
      case s: String => Value.Text(s)
      case d: Double => if d.isWhole && math.abs(d) < 9.0e15 then Value.Integer(d.toLong) else Value.Real(d)
      case i: Int => Value.Integer(i.toLong)
      case b: Boolean => Value.Integer(if b then 1 else 0)
      case _ if v.isInstanceOf[ArrayBuffer] => Value.Blob(Js.bytes(v.asInstanceOf[ArrayBuffer]))
      case _ => Value.Text(String.valueOf(v))
