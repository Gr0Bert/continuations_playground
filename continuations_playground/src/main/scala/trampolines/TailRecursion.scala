package trampolines

object TailRecursion extends App {
	@scala.annotation.tailrec
	def callee(n: Int): Int =
		if (n == 0) n else callee(n - 1)

	def caller(n: Int): Int =
		callee(n)

	caller(1000000)
}