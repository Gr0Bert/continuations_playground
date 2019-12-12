package trampolines

object MutualRecursion extends App {
	def calleeA(n: Int): Int =
		if (n == 0) n else calleeB(n - 1)

	def calleeB(n: Int): Int =
		if (n == 0) n else calleeA(n - 1)

	def callerAB(n: Int) =
		calleeA(n)

	callerAB(1000000)
}