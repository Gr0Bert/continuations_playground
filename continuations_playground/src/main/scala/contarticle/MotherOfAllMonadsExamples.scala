package contarticle

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