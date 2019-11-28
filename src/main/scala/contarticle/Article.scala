package contarticle

// This article is supposed to be read from start to end.
object Domain {
	// Domain models
	case class User(id: Long)
	case class Info(name: String)

	// Data storage:
	val users = Map(123L -> User(123))
	val info = Map(123L -> Info("Tom"))

	// Access functions
	def getUser(id: Long): User = {
		users.getOrElse(id, null)
	}

	def getInfo(user: User): Info = {
		info.getOrElse(user.id, null)
	}
}

object Article extends App {
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

object MonadicOptional extends App {
	import scala.language.higherKinds
	import Domain._

	// Is there another way to conquer the problem with nulls?
	// Turned out it is monads. Without additional theory let's represent them as an interface:
	trait Monad[F[_]] {
		def pure[A](v: A): F[A] // the way to create instance of a Monad
		def map[A, B](fa: F[A])(f: A => B): F[B] // the way to change value "inside" the monad
		def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B] // the way to compose two monads
	}

	// Lets define algebraic data type Optional with two children: Just and Nothing.
	sealed trait Optional[+T] {
		// those functions just convinient wrappers on top of monad implementation
		def map[B](f: T => B)(implicit M: Monad[Optional]): Optional[B] = M.map(this)(f)
		def flatMap[B](f: T => Optional[B])(implicit M: Monad[Optional]): Optional[B] = M.flatMap(this)(f)
	}
	// Represents existence of a value.
	final case class Just[+A](value: A) extends Optional[A]
	// Represents absence of a value.
	final case object Nothing extends Optional[Nothing]

	object Optional {
		// also a wrapper around Monad implementation
		def pure[A](v: A)(implicit M: Monad[Optional]): Optional[A] = M.pure(v)

		// Implementation of a monad
		implicit val monadOptional: Monad[Optional] = new Monad[Optional] {
			override def pure[A](v: A): Optional[A] = if (v == null) Nothing else Just(v)
			override def map[A, B](fa: Optional[A])(f: A => B): Optional[B] = flatMap(fa)((v: A) => pure(f(v)))
			// Pay attention how close is this code to "optional" function definde earlier.
			override def flatMap[A, B](fa: Optional[A])(f: A => Optional[B]): Optional[B] =
				fa match {
					case Just(value) => f(value)
					case Nothing => Nothing
				}
		}
	}

	// This how the program can be rewritten with newly defined Optional monad.
	def programOptional[R](id: Long): Optional[Info] =
		Optional.pure(getUser(id)).flatMap{ user =>
			Optional.pure(getInfo(user))
		}

	assert(programOptional(123) == Just(Info("Tom")))
	assert(programOptional(1234) == Nothing)

	// There is also a few laws each monad implementation should follow.
	// In reality some implementations just pretending to be monads and not following the laws, but it is not the case with Optional.
	val a = 1
	val f = (x: Int) => Optional.pure(x + 1)
	val g = (x: Int) => Optional.pure(x * 2)
	assert(Optional.pure(a).flatMap(f) == f(a)) // left identity
	assert(Optional.pure(a).flatMap(Optional.pure) == Optional.pure(a)) // right identity
	assert(Optional.pure(a).flatMap(f).flatMap(g) == Optional.pure(a).flatMap(x => f(x).flatMap(g))) // associativity
	// Clearly Optional follows all three laws and can be called a monad.
}

object OptionalEmbedding extends App {
	import Domain._
	import MonadicOptional._

	// The close resemblance between monads and continuations should lead to some discoveries.
	// Lets try to pretend that Continuation is a monad:
	case class Continuation[R, +A](run: (A => R) => R) {
		def map[B](f: A => B): Continuation[R, B] = {
			this.flatMap((a: A) => Continuation(k => k(f(a))))
		}

		def flatMap[B](f: A => Continuation[R, B]): Continuation[R, B] = {
			Continuation(k => run(a => f(a).run(k)))
		}
	}

	object Continuation {
		def pure[R, T](v: T): Continuation[R, T] = Continuation[R, T](k => k(v))
	}

	// Close enough, lets check if the laws are satisfied.
	val a = 1
	val f = (x: Int) => Continuation.pure[Int, Int](x + 1)
	val g = (x: Int) => Continuation.pure[Int, Int](x * 2)
	assert(Continuation.pure(a).flatMap(f).run(identity) == f(a).run(identity)) // left identity
	assert(Continuation.pure(a).flatMap(Continuation.pure[Int, Int]).run(identity) == Continuation.pure(a).run(identity)) // right identity
	assert(Continuation.pure(a).flatMap(f).flatMap(g).run(identity) == Continuation.pure(a).flatMap(x => f(x).flatMap(g)).run(identity)) // associativity

	// Turns out that the laws are satisfied and Continuations could be represented as monads.
	// But what kind of monad is a Continuation? I mean - what is it actually doing?
	// It turns out that Continuation is a monad which is doing nothing except passing the values.
	// Maybe there is a way to compose Optional and Continuation?
	// This way there will be more evidence to their close relations and, perhaps, Continuation will mimic Optional?
	// embed - creates a Continuation out of Optional.
	def embed[R, T](x: Optional[T]): Continuation[Optional[R], T] = Continuation[Optional[R], T](k => x.flatMap(k))
	// run - run the Continuation.
	def run[T](m: Continuation[Optional[T], T]): Optional[T] = m.run(x => Optional.pure(x))

	// Seems like a safe version of a program.
	def programOptional[R](id: Long): Continuation[Optional[R], Info] =
		embed(Optional.pure(getUser(id))).flatMap{ user =>
			embed(Optional.pure(getInfo(user)))
		}

	assert(run(programOptional[Info](123)) == Optional.pure(Info("Tom")))
	assert(run(programOptional[Info](1234)) == Nothing)
}

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

object OtherExamplesOfCallCC extends App {
	import CallCC._
	import CallCC.Continuation._

	// Here some other examples of callCC usage.
	def divExcpt[R](x: Int, y: Int, h: String => Continuation[R, Int]): Continuation[R, Int] =
		callCC[R, Int, String]{ (ok: Int => Continuation[R, String]) =>
			for {
				err <- callCC[R, String, Unit]{ (notOK: String => Continuation[R, Unit]) =>
					for {
						_ <- if (y == 0) notOK("Denominator 0") else Continuation.unit[R]
						r <- ok(x / y)
					} yield r
				}
				r <- h(err)
			} yield r
		}

	def divExcptDesugared[R](x: Int, y: Int, h: String => Continuation[R, Int]): Continuation[R, Int] =
		callCC[R, Int, String]{ (ok: Int => Continuation[R, String]) =>
			callCC[R, String, Unit]{ (notOK: String => Continuation[R, Unit]) =>
				(if (y == 0) notOK("Denominator 0") else Continuation.unit[R]).flatMap{ _ =>
					ok(x / y)
				}
			}.flatMap(error => h(error))
		}

	def handleError(error: String): Continuation[Either[String, Int], Int] = {
		Continuation(
			(k: (Int) => Either[String, Int]) => {
				Left(error)
			}
		)
	}

	assert(divExcpt(10, 2, handleError).run(x => Right(x)) == Right(5))
	assert(divExcpt(10, 0, handleError).run(x => Right(x)) == Left("Denominator 0"))

	def tryCont[R, T](code: () => T, errorCase: Throwable => Continuation[R, T]): Continuation[R, T] = {
		callCC[R, T, Throwable]{ success =>
			callCC[R, Throwable, Throwable]{ failure =>
				import scala.util._
				Try(code()) match {
					case Success(value) => success(value)
					case Failure(error) => failure(error)
				}
			}.flatMap{ errorMessage =>
				errorCase(errorMessage)
			}
		}
	}

	def tryCont2[R, T](code: () => T, errorCase: Throwable => Continuation[R, T]): Continuation[R, T] = {
		import scala.util._
		def proceed(v: T): Continuation[R, T] = Continuation(k => k(v))
		Try(code()) match {
			case Success(value) => proceed(value)
			case Failure(exception) =>
				Continuation.pure(exception).flatMap{ errorMessage =>
					errorCase(errorMessage)
				}
		}
	}

	def handleThrowable(error: Throwable): Continuation[Either[String, Int], Int] = {
		Continuation(_ => Left(error.getMessage))
	}

	assert(tryCont(() => throw new RuntimeException("test"), handleThrowable).run(x => Right(x)) == Left("test"))
	assert(tryCont(() => 10 / 2, handleThrowable).run(x => Right(x)) == Right(5))
	assert(tryCont2(() => throw new RuntimeException("test"), handleThrowable).run(x => Right(x)) == Left("test"))
	assert(tryCont2(() => 10 / 2, handleThrowable).run(x => Right(x)) == Right(5))
}

// Translation of Mothers of all monads article source code into Scala.
// http://blog.sigfpe.com/2008/12/mother-of-all-monads.html
object MotherOfAllMonadsExamples extends App {
	import CallCC.{Continuation => Cont}

	val ex1: Cont[String, Int] =
		for {
			a <- Cont.pure(1)
			b <- Cont.pure(10)
		} yield {
			a + b
		}

	val test1 = ex1.run(_.toString)
	assert(test1 == "11")

	val ex1_1: Cont[String, Int] =
		for {
			a <- Cont.pure(1)
			b <- Cont[String, Int](fred => ???)
		} yield {
			a + b
		}

	val ex2 =
		for {
			a <- Cont.pure(1)
			b <- Cont[String, Int](fred => fred(10))
		} yield {
			a + b
		}

	val test2 = ex2.run(_.toString)
	assert(test2 == "11")

	val ex3 =
		for {
			a <- Cont.pure(1)
			b <- Cont[String, Int](fred => "escape")
		} yield {
			a + b
		}

	val test3 = ex3.run(_.toString)
	assert(test3 == "escape")

	val ex4 =
		for {
			a <- Cont.pure(1)
			b <- Cont[String, Int](fred => fred(10) ++ fred(20))
		} yield {
			a + b
		}

	val test4 = ex4.run(_.toString)
	assert(test4 == "1121")

	val ex5 =
		for {
			a <- List(1)
			b <- List(10, 20)
		} yield {
			a + b
		}

	assert(ex5 == List(11, 21))

	val ex6 =
		for {
			a <- Cont.pure(1)
			b <- Cont[List[Int], Int](fred => fred(10) ++ fred(20))
		} yield {
			a + b
		}

	val test6 = ex6.run(x => List(x))
	assert(test6 == List(11, 21))

	val ex8 =
		for {
			a <- Cont.pure(1)
			b <- Cont[List[Int], Int](fred => List(10, 20).flatMap(fred))
		} yield {
			a + b
		}

	val test8 = ex8.run(x => List(x))
	assert(test8 == List(11, 21))

	def i[T](x: List[T]): Cont[List[T], T] = Cont[List[T], T](fred => x.flatMap(fred))
	def run[T](m: Cont[List[T], T]): List[T] = m.run(x => List(x))

	val test9 =
		for {
			a <- i(List(1, 2))
			b <- i(List(10, 20))
		} yield {
			a + b
		}

	assert(run(test9) == List(11, 21, 12, 22))

	import cats.Monad
	import cats.implicits._
	import cats.effect.IO

	import scala.language.higherKinds
	def iM[R, T, M[_]](x: M[T])(implicit M: Monad[M]): Cont[M[R], T] = Cont[M[R], T](fred => x.flatMap(fred))
	def runM[T, M[_]](m: Cont[M[T], T])(implicit M: Monad[M]): M[T] = m.run(x => M.pure(x))

	val test10 =
		for {
			_ <- iM[Unit, Unit, IO](IO(println("What is your name?")))
			name <- iM[Unit, String, IO](IO(scala.io.StdIn.readLine()))
			_ <- iM[Unit, Unit, IO](IO(println("Merry Xmas " ++ name)))
		} yield {
			()
		}

	runM(test10)
}

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

// Special thanks to https://gist.github.com/Odomontois for providing this nice implementation!
object ContinuationMonadTrait extends App {
	import Domain._

	trait Continuation[R, +A] extends ((A => R) => R) {
		def map[B](f: A => B): Continuation[R, B] =
			flatMap(a => _(f(a)))

		def flatMap[B](f: A => Continuation[R, B]): Continuation[R, B] =
			k => this(a => f(a)(k))
	}

	object Continuation {
		def pure[R, A](a: A): Continuation[R, A] = _(a)

		def unit[R]: Continuation[R, Unit] = pure(())

		def callCC[R, A, B](f: (A => Continuation[R, B]) => Continuation[R, A]): Continuation[R, A] =
			k => f(a => _ => k(a))(k)
	}

	import Continuation._
	def optional[R, T](v: T, ifNull: => Continuation[R, T]): Continuation[R, T] =
		callCC[R, T, T](k =>
			callCC[R, T, T](exit => if (v != null) k(v) else exit(v)).flatMap(_ => ifNull) // ignore because it is always null
		)

	def programOptional[R, T](id: Long)(ifNull: Continuation[R, Null]): Continuation[R, Info] =
		optional[R, User](getUser(id), ifNull).flatMap { user =>
			optional[R, Info](getInfo(user), ifNull)
		}

	val ifNull: Continuation[String, Null] = _ => "Error: null"
	assert(programOptional(123)(ifNull).map(_.name)(identity) == "Tom")
	assert(programOptional(1234)(ifNull).map(_.name)(identity) == "Error: null")
}