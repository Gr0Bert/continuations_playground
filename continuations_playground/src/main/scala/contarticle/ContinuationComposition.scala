package contarticle

object ContinuationComposition extends App {
	import Domain._

	// All this continuation stuff closely reminds me a stack operations:
	// each computation have a superpower to "go to" continuation with some value
	// this value been placed on an imaginary stack
	// next computation have an access to that value and can call its continuation with it or some other value
	// when the last value being computed it will be returned to a caller.
	// Seems like small runtime with it's own control flow rules.
	// Lets try to capture them:
	// (run: (A => R) => R) - continuation
	// changeValue - changes value on "stack" before passing it to next computation
	// continueWith - continue execution with another continuation
	case class Continuation[R, +A](run: (A => R) => R) {
		// Notice f type - it takes A as a parameter. This is because it modifies the value passed to the continuation.
		def changeValue[B](f: A => B): Continuation[R, B] = {
			this.continueWith((a: A) => Continuation(k => k(f(a)))) // You can clearly see this here - f called first, then it's result passed next to a continuation.
		}

		def continueWith[B](f: A => Continuation[R, B]): Continuation[R, B] = {
			Continuation(k => run(a => f(a).run(k)))
		}
	}

	def optional[R, T](v: T): Continuation[R, T] =
		Continuation((k: T => R) =>
			if (v != null) {
				k(v)
			} else {
				null.asInstanceOf[R]
			}
		)

	// Now the program could be defined in terms of composition operators.
	def programOptional[R](id: Long): Continuation[R, Info] =
		optional(getUser(id)).continueWith{ user =>
			optional(getInfo(user))
		}

	assert(programOptional(123).run(identity) == Info("Tom"))
	assert(programOptional(1234).run(identity) == null)
}
