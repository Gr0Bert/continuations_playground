package trampolines

object SimpleTrampolineTailRecursive extends App {
	sealed trait Trampoline[+A]
	final case class Done[A](result: A) extends Trampoline[A]
	final case class More[A](f: () => Trampoline[A]) extends Trampoline[A]

	@scala.annotation.tailrec
	def run[A](t: Trampoline[A]): A = {
		t match {
			case Done(result) => result
			case More(k) => run(k())
		}
	}

	def calleeA(n: Int): Trampoline[Int] =
		if (n == 0) Done(n) else More(() => calleeB(n - 1))

	def calleeB(n: Int): Trampoline[Int] =
		if (n == 0) Done(n) else More(() => calleeA(n - 1))

	def callerAB(n: Int): Trampoline[Int] =
		calleeA(n)

	run(callerAB(1000000))
}