package contarticle

// Translation of same Hackage examples from documentation of ConT onto Scala.
// http://hackage.haskell.org/package/mtl-2.2.2/docs/Control-Monad-Cont.html
object HackageContExamples extends App {
	import CallCC.{Continuation => Cont}
	import CallCC.Continuation._


	// Example 1: Simple Continuation Usage
	// Calculating length of a list continuation-style
	def calculateLength[R, T](xs: Iterable[T]): Cont[R, Int] = Cont.pure(xs.size)

	// Here we use calculateLength by making it to pass its result to print
	assert(calculateLength("123").run(identity) == 3)

	// It is possible to chain Cont blocks with flatMap
	def double[R](x: Int): Cont[R, Int] = Cont.pure(x * 2)

	assert(calculateLength("123").flatMap(double[Int]).run(identity) == 6)

	/*
	Example 2: Using callCC
	Returns a string depending on the length of the name parameter.
	If the provided string is empty, returns an error.
	Otherwise, returns a welcome message.
	1. Runs an anonymous Cont block and extracts value from it with .run(identity). Here identity is the continuation, passed to the Cont block.
	2. Binds response to the result of the following callCC block, binds exit to the continuation.
	3. Validates name. This approach illustrates advantage of using callCC over return. We pass the continuation to validateName, and interrupt execution of the Cont block from inside of validateName.
	4. Returns the welcome message from the callCC block. This line is not executed if validateName fails.
	5. Returns from the Cont block.
	*/

	def whatsYourName(name: String): String =
		(for {
			response <- callCC[String, String, String] { exit => 						// 2
				for {
					_ <- validateName(name, exit) 															// 3
				} yield {
					"Welcome, " ++ name ++ "!" 																	// 4
				}
			}
		} yield {
			response																												// 5
		}).run(identity)																									// 1

	def whatsYourNameCont(name: String) =
		Cont[String, String]{ k =>
			if (name == null)
				k("You forgot to tell me your name!")
			else
				k("Welcome, " ++ name ++ "!")
		}.run(identity)

	def validateName[R](name: String, exit: String => Cont[R, String]): Cont[R, String] =
		if (name == null)
			exit("You forgot to tell me your name!")
		else
			Cont.pure[R, String]("")

	assert(whatsYourNameCont("Tom") == "Welcome, Tom!")
	assert(whatsYourNameCont(null) == "You forgot to tell me your name!")
}
