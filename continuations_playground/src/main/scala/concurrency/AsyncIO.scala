package concurrency

import scala.concurrent.Future

object AsyncIO extends App {
	sealed trait IO[+A] {
		def flatMap[B](f: A => IO[B]): IO[B] = Cont(this, f)
		def map[B](f: A => B): IO[B] = flatMap(a => Done(f(a)))
		def raise(e: Throwable) = Raise(e)
	}
	def handle[A](fa: IO[A])(h: Throwable => IO[A]): IO[A] = Handle(fa, h)
	final case class Done[A](result: A) extends IO[A]
	final case class More[A](f: () => IO[A]) extends IO[A]
	final case class Cont[A, B](a: IO[A], f: A => IO[B]) extends IO[B]
	final case class Async[A](k: (Either[Throwable, A] => Unit) => Unit) extends IO[A]
	final case class Raise[A](e: Throwable) extends IO[A]
	final case class Handle[A](a: IO[A], h: Throwable => IO[A]) extends IO[A]

	object IO {
		import scala.concurrent.ExecutionContext.global
		val ec = global
		val shift: IO[Unit] = Async { cb =>
			val rightUnit = Right(())
			ec.execute { () =>
				cb(rightUnit)
			}
		}
		
		def async[A](k: (Either[Throwable, A] => Unit) => Unit): IO[A] = Async(k)
	}

	final def runAsync[A](t: IO[A])(cb: Either[Throwable, A] => Unit): Unit = {
		t match {
			case Done(result) => cb(Right(result))
			case Raise(e) => cb(Left(e))
			case Async(k) => k(cb)
			case Cont(Async(k), f) =>
				val rest = (a: Either[Throwable, Any]) => {
					a match {
						case Left(value) =>
							cb(Left(value))
						case Right(value) =>
							runAsync[A]{
								loop(f(value), Nil).asInstanceOf[IO[A]]
							}(cb)
							()
					}
				}
				k(rest)
			case rest => runAsync[A](loop(rest, Nil).asInstanceOf[IO[A]])(cb) 
		}

		@scala.annotation.tailrec
		def loop(t: IO[Any], handler: List[Throwable => IO[Any]]): IO[Any] = {
			println(handler.size)
			t match {
				case Raise(e) => handler match {
					case h :: rest => 
						println("use handler")
						loop(h(e), rest)
					case Nil =>
						println("raise")
						Raise(e)
				}
				case Handle(a, h) => loop(a, h :: handler)
				case More(k) => loop(k(), handler)
				case Cont(Done(v), f) => loop(f(v), handler)
				case Cont(More(k), f) => loop(Cont(k(), f), handler)
				case Cont(Cont(b, g), f) => loop(Cont(b, (x: Any) => Cont(g(x), f)), handler)
				case x => x
			}
		}
	}

	// return after every call
	def andThenTrampolined[A, B, C](f: A => IO[B], g: B => IO[C]): A => IO[C] =
		(a: A) => More(() => {
			Cont(f(a), result => g(result))
		})

	def idTrampolined[A](a: A): IO[A] = Done(a)

	val t =
			IO.async[Int](k => {
				Future(() => {
					println{
						s"In future thread: ${Thread.currentThread().getName}"
					}
					k(Right(5))
				})(IO.ec).map{ x =>
					x()
				}(IO.ec)
			}).flatMap { x =>
				handle{
					println {
					Thread.currentThread().getName
				}
				IO.shift.flatMap{ _ =>
					println{
						s"In done thread: ${Thread.currentThread().getName}"
					}
					Done(x)
				}.raise(new RuntimeException("test"))
			}(_ => Done(-1))
		}
	println{
		runAsync(t)((x: Either[Throwable, Int]) => println(x))
//		runAsync(
//			List.fill(100000)(idTrampolined[Int](_)).foldLeft(idTrampolined[Int](_))(andThenTrampolined)(1))(
//			(x: Either[Throwable, Int]) => println(x)
//		)
	}
}
