package concurrency

import scala.concurrent.Future

object AsyncIO extends App {
	sealed trait IO[+A] {
		def flatMap[B](f: A => IO[B]): IO[B] = Cont(this, f)
		def map[B](f: A => B): IO[B] = flatMap(a => Done(f(a)))
	}
	final case class Done[A](result: A) extends IO[A]
	final case class More[A](f: () => IO[A]) extends IO[A]
	final case class Cont[A, B](a: IO[A], f: A => IO[B]) extends IO[B]
	final case class Async[A](k: (A => Unit) => Unit) extends IO[A]

	object IO {
		import scala.concurrent.ExecutionContext.global
		val ec = global
		val shift: IO[Unit] = Async { cb =>
			val rightUnit = Right(())
			ec.execute { () =>
				cb(rightUnit)
			}
		}
		
		def async[A](k: (A => Unit) => Unit): IO[A] = Async(k)
	}

	final def runAsync[A](t: IO[A], cb: A => Unit): Unit = {
		t match {
			case Done(result) => cb(result)
			case Async(k) => k(cb)
			case Cont(Async(k), f) =>
				val rest = (a: Any) => {
					runAsync(loop(f(a)), cb)
					()
				}
				k(rest)
			case rest => runAsync(loop(rest), cb) 
		}

		@scala.annotation.tailrec
		def loop(t: IO[A]): IO[A] = {
			t match {
				case More(k) => loop(k())
				case Cont(Done(v), f) => loop(f(v))
				case Cont(More(k), f) => loop(Cont(k(), f))
				case Cont(Cont(b, g), f) =>
					loop(
						Cont(b, (x: Any) => Cont(g(x), f))
					)
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
				k(5)
			})(IO.ec).map{ x =>
				x()
			}(IO.ec)
		}).flatMap { x =>
			println {
				Thread.currentThread().getName
			}
			IO.shift.flatMap{ _ =>
				println{
					s"In done thread: ${Thread.currentThread().getName}"
				}
				Done(x)
			}
		}
	println{
		runAsync(t, (x: Int) => println(x))
//		runAsync(List.fill(100000)(idTrampolined[Int](_)).foldLeft(idTrampolined[Int](_))(andThenTrampolined)(1), (x: Int) => println(x))
	}
}
