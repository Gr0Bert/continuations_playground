package contarticle

object ContinuationCompositionWithSimpleChoice extends App {
	import Domain._

	case class Continuation[R, +A](run: (A => R) => R) {
		def map[B](f: A => B): Continuation[R, B] = {
			this.flatMap((a: A) => Continuation(k => k(f(a))))
		}

		def flatMap[B](f: A => Continuation[R, B]): Continuation[R, B] = {
			Continuation(k => run(a => f(a).run(k)))
		}
	}

	// Lets explore an opportunity to make a choice - return a different value, say, specific error.
	// All we need - just a function which return the value of the R (result) type.
	def optionalSimpleChoise[R, T](v: T, ifNull: () => R): Continuation[R, T] =
		Continuation((k: T => R) =>
			if (v != null) {
				k(v)
			} else {
				ifNull()
			}
		)

	def programOptionalSimpleChoise[R](id: Long)(ifNull: () => R): Continuation[R, Info] =
		optionalSimpleChoise(getUser(id), ifNull).flatMap{ user =>
			optionalSimpleChoise(getInfo(user), ifNull)
		}

	val ifNull = () => "Error: null"
	assert(programOptionalSimpleChoise(123)(ifNull).map(_.name).run(identity) == "Tom")
	assert(programOptionalSimpleChoise(1234)(ifNull).map(_.name).run(identity) == ifNull())
}