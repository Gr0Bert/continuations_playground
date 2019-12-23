package concurrency

import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import java.util.concurrent.{CountDownLatch, ScheduledThreadPoolExecutor, TimeUnit}

import concurrency.Primitives.Deferred

import scala.concurrent.Future

object AsyncIO {
	sealed trait IO[+A] {
		def flatMap[B](f: A => IO[B]): IO[B] = Cont(this, f)
		def map[B](f: A => B): IO[B] = flatMap(a => Done(f(a)))
		def raise(e: Throwable) = Raise(e)
	}
	def handle[A](fa: IO[A])(h: Throwable => IO[A]): IO[A] = Handle(fa, h)
	final case class Done[A](result: A) extends IO[A]
	final case class More[A](f: () => IO[A]) extends IO[A]
	final case class Cont[A, B](a: IO[A], f: A => IO[B]) extends IO[B]
	final case class Async[A](k: (AtomicReference[IO[Unit]], Either[Throwable, A] => Unit) => Unit, name: String = "default") extends IO[A]
	final case class Raise[A](e: Throwable) extends IO[A]
	final case class Handle[A](a: IO[A], h: Throwable => IO[A]) extends IO[A]
	
	object IO {
		import scala.concurrent.ExecutionContext.global
		val ec = global
		val shift: IO[Unit] = Async { 
			case (cancel, cb) =>
				val rightUnit = Right(())
				ec.execute { () =>
					cb(rightUnit)
				}
				cancel.get
		}

		def apply[A](a: => A): IO[A] = More(() => Done(a))
		
		def cancellable[A](k: (Either[Throwable, A] => Unit) => IO[Unit]): IO[A] = {
			Async { 
				case (cancel, program) =>
					val cancelToken = k(program)
					val defaultToken = cancel.get
					cancel.set(cancelToken.flatMap(_ => defaultToken))
			}
		}
		
		def async[A](k: (Either[Throwable, A] => Unit) => Unit): IO[A] = {
			Async{
				case (_, cb) => k(cb)
			}
		}

		def start[A](fa: IO[A]): IO[(IO[A], IO[Unit])] = {
			val l = new CountDownLatch(1)
			@volatile var cancel: AtomicReference[IO[Unit]] = null
			val ref = new AtomicReference[Deferred[A]]()
			Async { 
				case (_, k)=> 
					val fiber = 
						Async (
							(fiberCancel: AtomicReference[IO[Unit]], fiberProgram: Either[Throwable, Unit] => Unit) => {
								println("asd")
								cancel = fiberCancel
								l.countDown()
								fiberProgram(Right(()))
							}, name = "fiber async"
						).flatMap{_ =>
							println("bvc")
							Deferred[A].flatMap{ d =>
								ref.set(d)
								fa.flatMap(d.complete)
							}
						}
					println("run async ))))))")
					runAsync(fiber)(_ => ())
					println("before wait")
					l.await()
					println("after wait")
					k(Right(ref.get().get, cancel.get()))
			}
		}
	}

	case class InterpretationState(
		current: IO[Any],
		errorHandlers: List[Throwable => IO[Any]],
		cancelToken: AtomicReference[IO[Unit]],
		isCancelled: AtomicBoolean
	)
	
	final def runAsync[A](t: IO[A], cancel: IO[Unit] = IO(()))(cb: Either[Throwable, A] => Unit): Unit = {
		def safeCall(k: () => IO[Any]): IO[Any] = try {
			k()
		} catch {
			case e: Throwable => Raise(e)
		}
		val erasedCb = cb.asInstanceOf[Either[Throwable, Any] => Unit]
		def loop(state: InterpretationState): IO[Any] = {
			if (state.isCancelled.get) {
				state.cancelToken.get.flatMap(_ => Done(cb(Left(new RuntimeException("Completed.")))))
			} else {
				state.current match {
					case Async(k, _) => Done(k(state.cancelToken, erasedCb))
					case Done(result) => Done(erasedCb(Right(result)))
					case Raise(e) => state.errorHandlers match {
						case h :: errorHandlers => loop(state.copy(current = h(e), errorHandlers = errorHandlers))
						case Nil => Done(cb(Left(e)))
					}
					case Handle(a, h) => loop(state.copy(current = a, errorHandlers = h :: state.errorHandlers))
					case More(k) => loop(state.copy(current = safeCall(k)))
					case Cont(Done(v), f) => loop(state.copy(current = f(v)))
					case Cont(More(k), f) => loop(state.copy(current = Cont(safeCall(k), f)))
					case Cont(Cont(b, g), f) => loop(state.copy(current = Cont(b, (x: Any) => Cont(g(x), f))))
					case Cont(Handle(a, h), f) => loop(state.copy(current = Cont(a, f), errorHandlers = h :: state.errorHandlers))
					case Cont(Raise(e), _) => loop(state.copy(current = Raise(e)))
					case Cont(Async(k, name), f) =>
						val rest = (a: Either[Throwable, Any]) => {
							a match {
								case Left(value) => erasedCb(Left(value))
								case Right(value) =>
									loop(
										state.copy(
									  	current = f(value)
										)
									)
									()
							}
						}
						println(s"call continuation $name")
						k(state.cancelToken, rest)
						Done(())
				}
			}
		}
		val isCancelled = new AtomicBoolean(false)
		val cancelToken = new AtomicReference(cancel.flatMap(_ => IO(isCancelled.set(true))))
		loop(InterpretationState(t, Nil, cancelToken, isCancelled))
	}
}

object Examples0 extends App {
	import AsyncIO._
	
	def andThenTrampolined[A, B, C](f: A => IO[B], g: B => IO[C]): A => IO[C] =
		(a: A) => More(() => {
			Cont(f(a), result => g(result))
		})

	def idTrampolined[A](a: A): IO[A] = Done(a)

	val t =
		handle{
			IO.cancellable[Int]{ k =>
				Future(() => {
					println{
						s"In future thread: ${Thread.currentThread().getName}"
					}
					k(Right(5))
				})(IO.ec).map{ x =>
					x()
				}(IO.ec)
				IO(())
			}.flatMap { x =>
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

object Examples1 extends App {
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

object Examples2 extends App {
	import java.util.concurrent.ScheduledExecutorService
	import AsyncIO._

	def sleep(implicit ec: ScheduledExecutorService): IO[Unit] = {
		IO.cancellable { cb =>
			val run = new Runnable { 
				def run() = {
					println("complete cb")
					cb(Right(()))
				}
			}
			val future = ec.scheduleAtFixedRate(run, 1, 3, TimeUnit.SECONDS)
			IO{
				println("Cancel token activated")
				future.cancel(true)
			}
		}
	}
	implicit val ec = new ScheduledThreadPoolExecutor(4)
	runAsync{
		IO.start(sleep.flatMap(_ => IO(println("Hi!")))).flatMap{ 
			case (join, cancel) => 
				join.flatMap(_ => IO(println("world"))) .flatMap(_ => cancel)
		}
	}(println)
}