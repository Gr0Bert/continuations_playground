package contarticle

object SimpleNullChecks extends App {
	import Domain._

	// Let's say we have a program which may fail if nonexistent user will be passed in.
	// Notice that this program is written in a so called "direct style", which means that runtime
	// takes care to determine what line of code should be executed next.
	// One could say that control flow defined "implicitly" by runtime internals.
	def programMayFail(id: Long): Info = {
		val user = getUser(id)
		val info = getInfo(user)
		info
	}

	assert(programMayFail(123) == Info("Tom"))
	// This case throws NullPointerException.
	assert(scala.util.Try(programMayFail(1234)).isFailure)

	// How can we protect ourselves? The easiest way is to add null checks.
	// But this way has it's own drawbacks: such checkings are not composable.
	def programNullChecked(id: Long): Info = {
		val user = getUser(id)
		if (user != null) {
			val info = getInfo(user)
			info
		} else {
			null
		}
	}

	// Clearly the program now is safe. But can we do better? Can we make such checkings composable.
	assert(programNullChecked(123) == Info("Tom"))
	assert(programNullChecked(1234) == null)
}