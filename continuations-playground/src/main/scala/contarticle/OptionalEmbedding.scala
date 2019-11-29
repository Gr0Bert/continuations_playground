package contarticle

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