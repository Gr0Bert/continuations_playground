package contarticle

object ContinuationExample extends App {
	import Domain._

	// The notion of the "rest of the program" has a name on it's own - "continuation"
	// Let's try to extract a continuation signature from example with "optional":
	// (A => R) - continuation or representation of the "rest of the program."
	// this function will be called with a value of type A and returns a value of type R,
	// which, in turn, will be returned to a caller.
	// Lets sum it up:
	// R - is the return type of ALL computation.
	// A - is the type of a value passed to continuation.
	type Continuation[R, A] = (A => R) => R

	// Lets try to use newly defined Continuation type:
	def optional[R, A](v: A): Continuation[R, A] =
		(k: A => R) => {
			if (v != null) {
				k(v)
			} else {
				null.asInstanceOf[R]
			}
		}

	def programOptional(id: Long): Info =
		optional(getUser(id)) { user =>
			optional(getInfo(user))(identity)
		}

	assert(programOptional(123) == Info("Tom"))
	assert(programOptional(1234) == null)
}