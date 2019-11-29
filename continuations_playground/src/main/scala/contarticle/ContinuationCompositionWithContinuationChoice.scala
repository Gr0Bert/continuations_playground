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
	def optionalContinuationChoise[R, T](v: T, ifNull: () => Continuation[R, T]): Continuation[R, T] =
		Continuation((k: T => R) =>
			if (v != null) {
				k(v)
			} else {
				ifNull().run(k)
			}
		)

	// Notice that ifNull return type is  Continuation[R, Null]
	// Null - because it is a subtype of every AnyRef type and we can pass any AnyRef value to continuation.
	// In this example it could be values of either User or Info.
	def programOptionalContinuationChoise[R, T](id: Long)(ifNull: () => Continuation[R, Null]): Continuation[R, Info] =
		optionalContinuationChoise[R, User](getUser(id), ifNull).flatMap{ user =>
			optionalContinuationChoise[R, Info](getInfo(user), ifNull)
		}

	val ifNullCont = () => Continuation[String, Null](_ => "Error: null")
	assert(programOptionalContinuationChoise(123)(ifNullCont).map(_.name).run(identity) == "Tom")
	assert(programOptionalContinuationChoise(1234)(ifNullCont).map(_.name).run(identity) == "Error: null")
}
