package contarticle

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
