package okay.workers

import okay.scala3.*

import java.sql.{Connection, PreparedStatement, ResultSet, Types}

/** `Sql` ON THE JVM, over JDBC — SQLite's driver in tests, so a program's SQL is the same D1 runs. Each operation
 * answered in place; a batch in a transaction */
object Jdbc:
  def run[R[_], Rest[_], A](c: Connection)(p: A ! R)(using Split[Sql, R, Rest]): A ! Rest =
    handle(p, Handler.tail[Sql, Rest, A, A](pure)([X] => (op: Sql[X]) => op match
      case Sql.All(s) => all(c, s)
      case Sql.Run(s) => change(c, s)
      case Sql.Batch(ss) => batch(c, ss)))

  def all(c: Connection, s: Statement): Vector[Row] =
    val ps = prepared(c, s)
    try
      if !ps.execute() then Vector.empty
      else
        val rs = ps.getResultSet
        val meta = rs.getMetaData
        val names = (1 to meta.getColumnCount).map(meta.getColumnLabel)
        val rows = Vector.newBuilder[Row]
        while rs.next() do rows += Row(names.zipWithIndex.map((n, i) => n -> value(rs, i + 1)).toMap)
        rows.result()
    finally ps.close()

  def change(c: Connection, s: Statement): Long =
    val ps = prepared(c, s)
    try ps.executeUpdate().toLong finally ps.close()

  def batch(c: Connection, ss: Vector[Statement]): Unit =
    val auto = c.getAutoCommit
    c.setAutoCommit(false)
    try
      ss.foreach(s => change(c, s))
      c.commit()
    catch
      case e: Throwable =>
        c.rollback()
        throw e
    finally c.setAutoCommit(auto)

  private def prepared(c: Connection, s: Statement): PreparedStatement =
    val ps = c.prepareStatement(s.sql)
    s.params.zipWithIndex.foreach { (v, i) =>
      v match
        case Value.Text(t) => ps.setString(i + 1, t)
        case Value.Integer(n) => ps.setLong(i + 1, n)
        case Value.Real(d) => ps.setDouble(i + 1, d)
        case Value.Blob(b) => ps.setBytes(i + 1, b.toArray)
        case Value.Null => ps.setNull(i + 1, Types.NULL)
    }
    ps

  private def value(rs: ResultSet, i: Int): Value = rs.getObject(i) match
    case null => Value.Null
    case n: java.lang.Integer => Value.Integer(n.longValue)
    case n: java.lang.Long => Value.Integer(n)
    case d: java.lang.Double => Value.Real(d)
    case d: java.lang.Float => Value.Real(d.toDouble)
    case b: Array[Byte] => Value.Blob(Bytes(b))
    case o => Value.Text(o.toString)
