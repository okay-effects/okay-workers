package okay.workers

import okay.scala3.*

/** a blob kept: its bytes and their type */
final case class Blob(bytes: Bytes, contentType: String)

/** BLOBS BY KEY: R2 in a Worker, a map in a test */
enum Blobs[X]:
  case Put(key: String, blob: Blob) extends Blobs[Unit]
  case Get(key: String) extends Blobs[Option[Blob]]
  case Delete(key: String) extends Blobs[Unit]

object Blobs:
  def put[R[_]](key: String, bytes: Bytes, contentType: String)(using In[Blobs, R]): Unit ! R =
    perform[Blobs](Put(key, Blob(bytes, contentType)))
  def get[R[_]](key: String)(using In[Blobs, R]): Option[Blob] ! R = perform[Blobs](Get(key))
  def delete[R[_]](key: String)(using In[Blobs, R]): Unit ! R = perform[Blobs](Delete(key))

  /** the blobs in a map, kept beside the run: the map at the end, and the value */
  def memory[R[_], Rest[_], A](start: Map[String, Blob] = Map.empty)(p: A ! R)(using Split[Blobs, R, Rest])
    : (Map[String, Blob], A) ! Rest =
    handle(p, Handler.stateful[Blobs, Rest, A, (Map[String, Blob], A), Map[String, Blob]](start)(
      (m, a) => pure((m, a)))([X] => (op: Blobs[X], cell: Cell[Map[String, Blob]]) => op match
        case Put(k, b) => cell.set(cell.get + (k -> b))
        case Get(k) => cell.get.get(k)
        case Delete(k) => cell.set(cell.get - k)))
