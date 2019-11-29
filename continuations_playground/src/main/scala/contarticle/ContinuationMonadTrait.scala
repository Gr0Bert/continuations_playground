package contarticle

// Special thanks to https://github.com/Odomontois for providing this nice implementation!
object ContinuationMonadTrait extends App {
	import Domain._

	trait Continuation[R, +A] extends ((A => R) => R) {
		def map[B](f: A => B): Continuation[R, B] =
			flatMap(a => _(f(a)))

		def flatMap[B](f: A => Continuation[R, B]): Continuation[R, B] =
			k => this(a => f(a)(k))
	}

	object Continuation {
		def pure[R, A](a: A): Continuation[R, A] = _(a)

		def unit[R]: Continuation[R, Unit] = pure(())

		def callCC[R, A, B](f: (A => Continuation[R, B]) => Continuation[R, A]): Continuation[R, A] =
			k => f(a => _ => k(a))(k)
	}

	import Continuation._
	def optional[R, T](v: T, ifNull: => Continuation[R, T]): Continuation[R, T] =
		callCC[R, T, T](k =>
			callCC[R, T, T](exit => if (v != null) k(v) else exit(v)).flatMap(_ => ifNull) // ignore because it is always null
		)

	def programOptional[R, T](id: Long)(ifNull: Continuation[R, Null]): Continuation[R, Info] =
		optional[R, User](getUser(id), ifNull).flatMap { user =>
			optional[R, Info](getInfo(user), ifNull)
		}

	val ifNull: Continuation[String, Null] = _ => "Error: null"
	assert(programOptional(123)(ifNull).map(_.name)(identity) == "Tom")
	assert(programOptional(1234)(ifNull).map(_.name)(identity) == "Error: null")
}