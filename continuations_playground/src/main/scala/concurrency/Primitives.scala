package concurrency

import java.util.concurrent.atomic.AtomicReference

import concurrency.AsyncIO.IO.{async, shift}

import scala.annotation.tailrec

object Primitives {
	import AsyncIO._
	
	sealed trait Ref[A] {
		protected val store: AtomicReference[A]
		def get: IO[A] = IO(store.get())
		def set(a: A): IO[Unit] = IO(store.set(a))
		def getAndSet(a: A): IO[A] = IO(store.getAndSet(a))
		def update(f: A => A): IO[Unit] = modify(a => (f(a), ()))
		def modify[B](f: A => (A, B)): IO[B] = {
			@tailrec
			def spin: B = {
				val c = store.get
				val (u, b) = f(c)
				if (!store.compareAndSet(c, u)) spin
				else b
			}
			IO(spin)
		}
	}
	
	object Ref {
		def apply[A](): IO[Ref[A]] = IO{
			new Ref[A] {
				override protected val store: AtomicReference[A] = new AtomicReference[A]()
			}
		}
	}
	
	object Deferred {
		sealed trait DeferredState[A]
		final case class Absent[A](handlers: List[A => Any]) extends DeferredState[A]
		final case class Present[A](a: A) extends DeferredState[A]

		def apply[A]: IO[Deferred[A]] = IO{
			new Deferred[A]{
				override protected val ref = new AtomicReference[DeferredState[A]](Absent(Nil))
			}
		}
	}
	sealed trait Deferred[A] {
		import Deferred._
		protected val ref: AtomicReference[DeferredState[A]]
		final def get: IO[A] = {
			IO.async{ cb =>
				@tailrec
				def register(): Option[A] =
					ref.get match {
						case Present(a) => Some(a)
						case s @ Absent(waiting) =>
							val updated = Absent(((a: A) => cb(Right(a))) :: waiting)
							if (ref.compareAndSet(s, updated)) None
							else register()
					}
				register().foreach(a => cb(Right(a)))
				println(ref.get())
			}
		}

		@tailrec
		final def complete(a: A): IO[Unit] = {
			println("complete")
			ref.get match {
				case Present(_) =>
					throw new IllegalStateException("Deferred already completed")

				case s @ Absent(_) =>
					if (ref.compareAndSet(s, Present(a))) {
						val list = s.handlers
						if (list.nonEmpty)
							IO(list.foreach(cb => cb(a)))
						else
							IO(())
					} else {
						complete(a)
					}
			}
		}
	}
}

object Test extends App {
	import AsyncIO._
	import Primitives._

	def spawn[A](fa: IO[A]): IO[IO[A]] = async{ k =>
		val p = Deferred[A].flatMap{ d =>
			runAsync(shift.flatMap(_ => fa.flatMap(d.complete)))(_ => ())
			d.get
		}
		k(Right(p))
	}

	runAsync{
		for {
			x <- spawn{
				IO(Thread.sleep(5000)).flatMap(_ => Done("world"))
			}.flatMap(identity)
			_ <- IO(println(s"Hello, $x!"))
		} yield {
			()
		}
	}(x => println(x))
	Thread.sleep(10000)
}