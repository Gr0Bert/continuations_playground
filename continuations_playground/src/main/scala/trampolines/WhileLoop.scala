package trampolines

object WhileLoop extends App {
	def callee(n: Int): Int = {
		var nLoop = n
		do {
			nLoop -= 1
		} while (nLoop != 0)
		nLoop
	}

	def caller(n: Int): Int =
		callee(n)

	caller(1000000)
}