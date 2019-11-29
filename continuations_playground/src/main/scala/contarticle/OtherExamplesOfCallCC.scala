package contarticle

object OtherExamplesOfCallCC extends App {
	import CallCC._
	import CallCC.Continuation._

	// Here some other examples of callCC usage.
	def divExcpt[R](x: Int, y: Int, h: String => Continuation[R, Int]): Continuation[R, Int] =
		callCC[R, Int, String]{ (ok: Int => Continuation[R, String]) =>
			for {
				err <- callCC[R, String, Unit]{ (notOK: String => Continuation[R, Unit]) =>
					for {
						_ <- if (y == 0) notOK("Denominator 0") else Continuation.unit[R]
						r <- ok(x / y)
					} yield r
				}
				r <- h(err)
			} yield r
		}

	def divExcptDesugared[R](x: Int, y: Int, h: String => Continuation[R, Int]): Continuation[R, Int] =
		callCC[R, Int, String]{ (ok: Int => Continuation[R, String]) =>
			callCC[R, String, Unit]{ (notOK: String => Continuation[R, Unit]) =>
				(if (y == 0) notOK("Denominator 0") else Continuation.unit[R]).flatMap{ _ =>
					ok(x / y)
				}
			}.flatMap(error => h(error))
		}

	def handleError(error: String): Continuation[Either[String, Int], Int] = {
		Continuation(
			(k: (Int) => Either[String, Int]) => {
				Left(error)
			}
		)
	}

	assert(divExcpt(10, 2, handleError).run(x => Right(x)) == Right(5))
	assert(divExcpt(10, 0, handleError).run(x => Right(x)) == Left("Denominator 0"))

	def tryCont[R, T](code: () => T, errorCase: Throwable => Continuation[R, T]): Continuation[R, T] = {
		callCC[R, T, Throwable]{ success =>
			callCC[R, Throwable, Throwable]{ failure =>
				import scala.util._
				Try(code()) match {
					case Success(value) => success(value)
					case Failure(error) => failure(error)
				}
			}.flatMap{ errorMessage =>
				errorCase(errorMessage)
			}
		}
	}

	def tryCont2[R, T](code: () => T, errorCase: Throwable => Continuation[R, T]): Continuation[R, T] = {
		import scala.util._
		def proceed(v: T): Continuation[R, T] = Continuation(k => k(v))
		Try(code()) match {
			case Success(value) => proceed(value)
			case Failure(exception) =>
				Continuation.pure(exception).flatMap{ errorMessage =>
					errorCase(errorMessage)
				}
		}
	}

	def handleThrowable(error: Throwable): Continuation[Either[String, Int], Int] = {
		Continuation(_ => Left(error.getMessage))
	}

	assert(tryCont(() => throw new RuntimeException("test"), handleThrowable).run(x => Right(x)) == Left("test"))
	assert(tryCont(() => 10 / 2, handleThrowable).run(x => Right(x)) == Right(5))
	assert(tryCont2(() => throw new RuntimeException("test"), handleThrowable).run(x => Right(x)) == Left("test"))
	assert(tryCont2(() => 10 / 2, handleThrowable).run(x => Right(x)) == Right(5))
}