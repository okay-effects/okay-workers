package okay.workers

import okay.scala3.*

/** A VALUE OF SQL, as SQLite (and so D1) has them */
enum Value:
  case Text(s: String)
  case Integer(n: Long)
  case Real(d: Double)
  case Blob(b: Bytes)
  case Null

object Value:
  given Conversion[String, Value] = Text(_)
  given Conversion[Long, Value] = Integer(_)
  given Conversion[Int, Value] = n => Integer(n.toLong)
  given Conversion[Double, Value] = Real(_)
  /** `None` is NULL */
  def of(o: Option[String]): Value = o.fold(Null)(Text(_))

/** A ROW: its columns by name */
final case class Row(columns: Map[String, Value]):
  def get(c: String): Value = columns.getOrElse(c, Value.Null)
  def text(c: String): String = textOption(c).getOrElse("")
  def textOption(c: String): Option[String] = get(c) match
    case Value.Text(s) => Some(s)
    case Value.Integer(n) => Some(n.toString)
    case Value.Real(d) => Some(d.toString)
    case _ => None
  def long(c: String): Long = get(c) match
    case Value.Integer(n) => n
    case Value.Real(d) => d.toLong
    case Value.Text(s) => s.toLongOption.getOrElse(0L)
    case _ => 0L

/** a statement and its parameters, bound in order to its `?` — never spliced */
final case class Statement(sql: String, params: Vector[Value] = Vector.empty)

object Statement:
  def apply(sql: String, params: Value*): Statement = new Statement(sql, params.toVector)

/** SQL, AS DATA: what a program asks of its database — rows, a change, several changes at once. D1 answers it in a
 * Worker, SQLite over JDBC on the JVM (the same SQL) */
enum Sql[X]:
  case All(statement: Statement) extends Sql[Vector[Row]]
  /** the rows it changed */
  case Run(statement: Statement) extends Sql[Long]
  /** all or none */
  case Batch(statements: Vector[Statement]) extends Sql[Unit]

object Sql:
  def all[R[_]](sql: String, params: Value*)(using In[Sql, R]): Vector[Row] ! R =
    perform[Sql](All(Statement(sql, params.toVector)))
  def first[R[_]](sql: String, params: Value*)(using In[Sql, R]): Option[Row] ! R =
    all[R](sql, params*).map(_.headOption)
  def run[R[_]](sql: String, params: Value*)(using In[Sql, R]): Long ! R =
    perform[Sql](Run(Statement(sql, params.toVector)))
  def batch[R[_]](statements: Vector[Statement])(using In[Sql, R]): Unit ! R = perform[Sql](Batch(statements))
