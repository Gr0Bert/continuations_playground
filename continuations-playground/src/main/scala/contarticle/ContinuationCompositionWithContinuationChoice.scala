package contarticle

object ContinuationCompositionWithContinuationChoice extends App {
	import Domain._

	case class Continuation[R, +A](run: (A => R) => R) {
		def map[B](f: A => B): Continuation[R, B] = {
			this.flatMap((a: A) => Continuation(k => k(f(a))))
		}

		def flatMap[B](f: A => Continuation[R, B]): Continuation[R, B] = {
			Continuation(k => run(a => f(a).run(k)))
		}
	}

	// What if one want to pass a continuation as another path of execution?
	// Easy - just run it inside!
	def optional[R, T](v: T, ifNull: () => Continuation[R, T]): Continuation[R, T] =
		Continuation((k: T => R) =>
			if (v != null) {
				k(v)
			} else {
				ifNull().run(k)
			}
		)

	// Notice that ifNull return type is  Continuation[R, Null]
	// Null
	def programOptional[R, T](id: Long)(ifNull: () => Continuation[R, Null]): Continuation[R, Info] =
		optional[R, User](getUser(id), ifNull).flatMap{ user =>
			optional[R, Info](getInfo(user), ifNull)
		}

	// Null - because this continuation could be Either called with value of type User or Info
	val ifNull = () => Continuation[String, Null](_ => "Error: null")
	assert(programOptional(123)(ifNull).map(_.name).run(identity) == "Tom")
	assert(programOptional(1234)(ifNull).map(_.name).run(identity) == "Error: null")
}
