package contarticle

object CallCC extends App {
	import Domain._

	// If you made it here then some hardcore content awaits: callCC function
	// which is a common abbreviation for call with current continuation
	// check it at wikipedia: https://www.wikiwand.com/en/Call-with-current-continuation
	case class Continuation[R, +A](run: (A => R) => R) {
		def map[B](f: A => B): Continuation[R, B] = {
			this.flatMap((a: A) => Continuation(k => k(f(a))))
		}

		def flatMap[B](f: A => Continuation[R, B]): Continuation[R, B] = {
			Continuation(k => run(a => f(a).run(k)))
		}
	}

	object Continuation {
		def pure[R, A](a: A): Continuation[R, A] = Continuation(k => k(a))

		def unit[R]: Continuation[R, Unit] = Continuation[R, Unit](k => k(()))

		// What this function does - gives an ability to jump to passed continuation (this, given by f)
		// The interesting thing is that continuation returned from f call - is the continuation returned by callCC itself.
		def callCC[R, A, B](f: (A => Continuation[R, B]) => Continuation[R, A]): Continuation[R, A] = {
			Continuation((k: A => R) => { // continuation from following map and flatMap
				f{(a: A) => 				// argument to pass to continuation
					Continuation((_: B => R) => { // wrapper to make compiler happy on nested callCC, notice (B => R) continuation is ignored
						k(a)	// actual call to following continuation in case continuation of this callCC will be used
					})
				}.run(k) // run in case some other continuation will be returned from call to f
				// all in all both cases should provide same continuation types
			})
		}
	}

	// This how optional can be implemented in term of callCC.
	import Continuation._
	def optional[R, T](v: T, ifNull: () => Continuation[R, T]): Continuation[R, T] =
		callCC[R, T, T]((k: T => Continuation[R, T]) => // here "k" would be binded to (2) and following flatMap/map calls - see the optional implementation
			callCC[R, T, T]((exit: T => Continuation[R, T]) => // here "exit" would be binded to (1)
				if (v != null) {
					k(v)
				} else {
					exit(v)
				}
			).flatMap(_ => ifNull()) // (1) ignore because it is always null
		)

	def programOptional[R, T](id: Long)(ifNull: () => Continuation[R, Null]): Continuation[R, Info] =
		optional[R, User](getUser(id), ifNull).flatMap{
			user =>																					// (2)
				optional[R, Info](getInfo(user), ifNull)			// (2)
		}

	val ifNull = () => Continuation[String, Null](_ => "Error: null")
	assert(programOptional(123)(ifNull).map(_.name).run(identity) == "Tom")
	assert(programOptional(1234)(ifNull).map(_.name).run(identity) == "Error: null")
}
