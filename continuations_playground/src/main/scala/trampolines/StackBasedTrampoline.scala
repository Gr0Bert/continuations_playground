package trampolines

object StackBasedTrampoline extends App {
	/*
		left-associated list
	------- result returned this way ------------>
	tail -> ((((f''', g'''), g''), g'), g) <- head
	<----------------- stack grows this way ------

		right-associated list
	------- result returned this way ------------>
	head -> (f''', (g''', (g'', (g', g)))) <- tail

	Make stack operations explicit with continuations.
	 */


	sealed trait Trampoline[+A]
	final case class Done[A](result: A) extends Trampoline[A]
	final case class More[A](f: () => Trampoline[A]) extends Trampoline[A]
	// constructor for applying function on the right to value on the left
	// the point is to emulate the stack in the heap to allow reassociation
	final case class Cont[A, B](a: Trampoline[A], f: A => Trampoline[B]) extends Trampoline[B]

	def run[A](t: Trampoline[A]): A = {
		var curr: Trampoline[Any] = t
		var res: Option[A] = None
		// keep track of operations by appending them to List which serves as a stack
		// scala List is right associated, so by appending operations to it we
		// effectively reassociate computations from left to right
		var stack: List[Any => Trampoline[A]] = List()
		while (res.isEmpty) {
			println(s"Stack size: ${stack.size}")
			curr match {
				case Done(result) =>
					println(s"Done($result)")
					stack match {
						case Nil =>
							res = Some(result.asInstanceOf[A])
						case f :: rest =>
							stack = rest
							curr = f(result)
					}
				case More(k) =>
					println("More(k)")
					curr = k()
				case Cont(a, f) =>
					println(s"Cont($a, f)")
					curr = a
					stack = f.asInstanceOf[Any => Trampoline[A]] :: stack
			}
		}
		res.get
	}

	def andThenTrampolined[A, B, C](f: A => Trampoline[B], g: B => Trampoline[C]): A => Trampoline[C] =
		(a: A) => More(() => {
			Cont(f(a), result => g(result))
		})

	def idTrampolined[A](a: A): Trampoline[A] = Done(a)

	run{
		List.fill(10)(idTrampolined[Int](_)).foldLeft(idTrampolined[Int](_))(andThenTrampolined)(1)
	}
}
