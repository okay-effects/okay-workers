package okay.workers

import okay.scala3.*
import okay.scala3.std.*

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.typedarray.ArrayBuffer

/** `Sql` IN A WORKER: a D1 binding — each statement prepared and bound, a batch D1's `batch` (all or none) */
object D1:
  def run[R[_], Rest[_], A](db: js.Dynamic)(p: A ! R)(using Split[Sql, R, Rest], In[Async, Rest]): A ! Rest =
    handle(p, Handler.general[Sql, Rest, A, A](pure)([X] => (op: Sql[X], k: X => A ! Rest) => op match
      case Sql.All(s) => Async.fromFuture[Vector[Row], Rest](all(db, s)).flatMap(k)
      case Sql.Run(s) => Async.fromFuture[Long, Rest](change(db, s)).flatMap(k)
      case Sql.Batch(ss) => Async.fromFuture[Unit, Rest](batch(db, ss)).flatMap(k)))

  def all(db: js.Dynamic, s: Statement): Future[Vector[Row]] =
    Js.future[js.Dynamic](bound(db, s).all()).map { r =>
      r.results.asInstanceOf[js.Array[js.Dictionary[js.Any]]].toVector.map(o =>
        Row(o.toMap.map((k, v) => k -> value(v))))
    }

  def change(db: js.Dynamic, s: Statement): Future[Long] =
    Js.future[js.Dynamic](bound(db, s).run()).map(r => r.meta.changes.asInstanceOf[Double].toLong)

  def batch(db: js.Dynamic, ss: Vector[Statement]): Future[Unit] =
    if ss.isEmpty then Future.successful(())
    else Js.future[js.Any](db.batch(js.Array(ss.map(bound(db, _))*))).map(_ => ())

  private def bound(db: js.Dynamic, s: Statement): js.Dynamic =
    val st = db.prepare(s.sql)
    if s.params.isEmpty then st
    else st.bind(s.params.map[js.Any] {
      case Value.Text(t) => t
      case Value.Integer(n) => n.toDouble
      case Value.Real(d) => d
      case Value.Blob(b) => Js.array(b).buffer
      case Value.Null => null
    }*)

  private def value(v: js.Any): Value =
    if v == null || js.isUndefined(v) then Value.Null
    else (v: Any) match
      case s: String => Value.Text(s)
      case d: Double => if d.isWhole && math.abs(d) < 9.0e15 then Value.Integer(d.toLong) else Value.Real(d)
      case i: Int => Value.Integer(i.toLong)
      case b: Boolean => Value.Integer(if b then 1 else 0)
      case _ if js.typeOf(v) == "object" && v.isInstanceOf[ArrayBuffer] => Value.Blob(Js.bytes(v.asInstanceOf[ArrayBuffer]))
      case _ if js.Array.isArray(v) =>
        Value.Blob(Bytes(v.asInstanceOf[js.Array[Double]].toArray.map(_.toInt.toByte)))
      case _ => Value.Text(String.valueOf(v))
