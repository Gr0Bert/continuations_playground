package contarticle

object MonadicEmbedding extends App {
	import Domain._
	import MonadicOptional._
	import OptionalEmbedding.Continuation

	import scala.language.higherKinds

	// Can any Monad be embedded into Continuation? Yes! Lets do it!
	def embedM[R, T, M[_]](x: M[T])(implicit M: Monad[M]): Continuation[M[R], T] = Continuation[M[R], T](k => M.flatMap(x)(k))
	def runM[T, M[_]](m: Continuation[M[T], T])(implicit M: Monad[M]): M[T] = m.run(x => M.pure(x))

	def programOptional[R](id: Long)(implicit M: Monad[Optional]): Continuation[Optional[R], Info] =
		embedM[R, User, Optional](Optional.pure(getUser(id))).flatMap{ user =>
			embedM[R, Info, Optional](Optional.pure(getInfo(user)))
		}

	assert(runM(programOptional[Info](123)) == Optional.pure(Info("Tom")))
	assert(runM(programOptional[Info](1234)) == Nothing)
}

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
	def optional[R, T](v: T, ifNull: () => R): Continuation[R, T] =
		Continuation((k: T => R) =>
			if (v != null) {
				k(v)
			} else {
				ifNull()
			}
		)

	def programOptional[R](id: Long)(ifNull: () => R): Continuation[R, Info] =
		optional(getUser(id), ifNull).flatMap{ user =>
			optional(getInfo(user), ifNull)
		}

	val ifNull = () => "Error: null"
	assert(programOptional(123)(ifNull).map(_.name).run(identity) == "Tom")
	assert(programOptional(1234)(ifNull).map(_.name).run(identity) == ifNull())
}