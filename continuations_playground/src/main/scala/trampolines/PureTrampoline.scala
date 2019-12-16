package trampolines

object PureTrampoline extends App {
	sealed trait Trampoline[+A]
	final case class Done[A](result: A) extends Trampoline[A]
	final case class More[A](f: () => Trampoline[A]) extends Trampoline[A]
	final case class Cont[A, B](a: Trampoline[A], f: A => Trampoline[B]) extends Trampoline[B]

	@scala.annotation.tailrec
	final def run[A](t: Trampoline[A]): A = {
		t match {
			case Done(v) => v
			case More(k) => run(k())
			case Cont(Done(v), f) => run(f(v))
			case Cont(More(k), f) => run(Cont(k(), f))
			case Cont(Cont(b, g), f) =>
				run{
					Cont(b, (x: Any) => Cont(g(x), f))
				}
		}
	}

	// return after every call
	def andThenTrampolined[A, B, C](f: A => Trampoline[B], g: B => Trampoline[C]): A => Trampoline[C] =
		(a: A) => More(() => {
			Cont(f(a), result => g(result))
		})

	def idTrampolined[A](a: A): Trampoline[A] = Done(a)

	run{
		List.fill(100000)(idTrampolined[Int](_)).foldLeft(idTrampolined[Int](_))(andThenTrampolined)(1)
	}

	def plusOne(x: Int): Trampoline[Int] = Done(x + 1)
	def plusTwo(x: Int): Trampoline[Int] = Done(x + 2)
	def plusThree(x: Int): Trampoline[Int] = Done(x + 3)

	val res1 = 
		Cont(
			Cont(
				Cont(
					Done(1),
					plusOne
				),
				plusTwo
			),
			plusThree
		)
	
	val res2 =
			Cont(
				Cont(
					Done(1),
					plusOne
				),
				(x: Int) => Cont(plusTwo(x), plusThree)
			)

	val res3 =
		Cont(
			Done(1),
			(x: Int) => Cont(
				plusOne(x),
				(x: Int) => Cont(
					plusTwo(x),
					plusThree
				)
			)
		)
	
	assert(List(run(res1), run(res2), run(res3)) == List(7, 7, 7))
	
	def flatMap[A, B](t: Trampoline[A])(f: A => Trampoline[B]): Trampoline[B] = Cont(t, f)

	def andThenTrampolined2[A, B, C](f: A => Trampoline[B], g: B => Trampoline[C]): A => Trampoline[C] =
		(a: A) => More(() => {
			flatMap(f(a))(result => g(result))
		})

	run{
		List.fill(100000)(idTrampolined[Int](_)).foldLeft(idTrampolined[Int](_))(andThenTrampolined2)(1)
	}
}