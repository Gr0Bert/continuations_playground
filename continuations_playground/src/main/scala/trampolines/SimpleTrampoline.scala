package trampolines

object SimpleTrampoline extends App {
	sealed trait Trampoline[+A]
	final case class Done[A](result: A) extends Trampoline[A]
	final case class More[A](f: () => Trampoline[A]) extends Trampoline[A]

	def run[A](t: Trampoline[A]): A = {
		var curr: Trampoline[A] = t
		var res: Option[A] = None
		while (res.isEmpty) {
			curr match {
				case Done(result) =>
					res = Some(result)
				case More(k) =>
					curr = k()
			}
		}
		res.get
	}

	def calleeA(n: Int): Trampoline[Int] =
		if (n == 0) Done(n) else More(() => calleeB(n - 1))

	def calleeB(n: Int): Trampoline[Int] =
		if (n == 0) Done(n) else More(() => calleeA(n - 1))

	def callerAB(n: Int): Trampoline[Int] =
		calleeA(n)

	run(callerAB(1000000))
}