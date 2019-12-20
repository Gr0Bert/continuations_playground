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
		def safeCall(k: () => IO[Any]): IO[Any] = try {
			k()
		} catch {
			case e: Throwable => Raise(e)
		}
		val erasedCb = cb.asInstanceOf[Either[Throwable, Any] => Unit]
		def loop(current: IO[Any])(errorHandlers: List[Throwable => IO[Any]]): IO[Any] = current match {
			case Async(k) => Done(k(erasedCb))
			case Done(result) => Done(erasedCb(Right(result)))
			case Raise(e) => errorHandlers match {
				case h :: errorHandlers => loop(h(e))(errorHandlers)
				case Nil => Done(cb(Left(e)))
			}
			case Handle(a, h) => loop(a)(h :: errorHandlers)
			case More(k) => loop(safeCall(k))(errorHandlers)
			case Cont(Done(v), f) => loop(f(v))(errorHandlers)
			case Cont(More(k), f) => loop(Cont(safeCall(k), f))(errorHandlers)
			case Cont(Cont(b, g), f) => loop(Cont(b, (x: Any) => Cont(g(x), f)))(errorHandlers)
			case Cont(Handle(a, h), f) => loop(Cont(a, f))(h :: errorHandlers)
			case Cont(Raise(e), _) => loop(Raise(e))(errorHandlers)
			case Cont(Async(k), f) =>
				val rest = (a: Either[Throwable, Any]) => {
					a match {
						case Left(value) => erasedCb(Left(value))
						case Right(value) =>
							loop(f(value))(errorHandlers)
							()
					}
				}
				Done(k(rest))
		}
		loop(t)(Nil)
	}

	def andThenTrampolined[A, B, C](f: A => IO[B], g: B => IO[C]): A => IO[C] =
		(a: A) => More(() => {
			Cont(f(a), result => g(result))
		})

	def idTrampolined[A](a: A): IO[A] = Done(a)

	val t =
		handle{
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
					println {
					Thread.currentThread().getName
				}
				IO.shift.flatMap{ _ => More(() => {
					println{
						s"In done thread: ${Thread.currentThread().getName}"
					}
					throw new RuntimeException("test")
					Done(x)
				})
				}
			}
		}(_ => Done(-1))
	
	runAsync(t)((x: Either[Throwable, Int]) => println(x))
	runAsync(
		List.fill(100000)(idTrampolined[Int](_)).foldLeft(idTrampolined[Int](_))(andThenTrampolined)(1))(
		(x: Either[Throwable, Int]) => println(x)
	)
}

object Test extends App {
	import AsyncIO._
	def plusOne(x: Int): IO[Int] = Done(x + 1)
	def plusTwo(x: Int): IO[Int] = Done(x + 2)
	def plusThree(x: Int): IO[Int] = Done(x + 3)
	
	def plusOneCps(x: Int)(f: Int => Unit): Unit = f(x + 1)
	def plusTwoCps(x: Int)(f: Int => Unit): Unit = f(x + 2)
	def plusThreeCps(x: Int)(f: Int => Unit): Unit = f(x + 3)
	
	runAsync{
		plusOne(1)
			.flatMap(plusTwo)
			.flatMap{ x => 
				handle(More(() => {
					throw new RuntimeException("test")
					plusThree(x)
				})) { e: Throwable => More(() => Done(-1)) }
			}
	}(x => println(x))

	runAsync{
		Cont(
			Cont(plusOne(1), plusTwo),
			(x: Int) => Handle(
				More(() => {
					throw new RuntimeException("test")
					plusThree(x)
				}),
				(_: Throwable) => More(() => Done(-1))
			)
	)}(x => println(x))

	runAsync{
		Cont(
			plusOne(1),
			(x: Int) => 
				Cont(
					plusTwo(x),
					(x: Int) => Handle(
						More(() => {
							throw new RuntimeException("test")
							plusThree(x)
						}),
						(_: Throwable) => More(() => Done(-1))
					)		
				)
		)}(x => println(x))

	plusOneCps(1){ x =>
		plusTwoCps(x){ x =>
			try {
				throw new RuntimeException("test")
				plusThreeCps(x){ x =>
					println(x)
				}
			} catch {
				case _: Throwable => println(-1)
			}
		}
	}
}
