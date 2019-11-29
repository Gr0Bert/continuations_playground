package contarticle

object OptionalCPSExample extends App {
	import Domain._

	// Let's take another look on our problem: clearly all those null checks are a duplicating code.
	// Could all those checkings be abstracted somehow?
	// It turns out that yes, but to do so one should switch a point of view on the problem:
	// Having functions, each executing with the return value of the previous one, can they be short-circuited
	// in a way, that if previous returns null next one will not be ever called?
	// It reminds exceptions - they also allows to short-circuit execution by throwing an exception.
	// To implement an idea of stopping execution at some point we need a reified notion of execution itself.
	// But what is an execution? How could it be captured? What is an execution unit?
	// Seems like we do not have much choice but use functions as reified execution.
	// Let's try to abstract over the null check keeping an ideas above in mind:
	// v: T - is a called-by-name code block of type T which could return null
	// k: T => R - is a rest of a program, captured as a function.
	def optional[R, T](v: => T)(k: T => R): R = {
		if (v != null) {
			k(v)
		} else {
			null.asInstanceOf[R]
		}
	}

	// Now the program is safe, but let's take a closer look on the control flow:
	def programOptional(id: Long): Info = {
		optional(getUser(id)) { user: User => // this function is our "k: T => R" where T - User and R - Info
			optional(getInfo(user))(identity)		// one need to call identity as the rest of computation to acquire the value from the previous step
		}
	}
	// Seems like each next step of computation now is handled explicitly as a function call.
	// This approach gave more control over execution and opened a way to a composition of a succeeding calls.

	assert(programOptional(123) == Info("Tom"))
	assert(programOptional(1234) == null)
}
