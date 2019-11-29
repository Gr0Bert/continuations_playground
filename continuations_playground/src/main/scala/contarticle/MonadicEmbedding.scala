package contarticle

object MonadicEmbedding extends App {
	import Domain._
	import MonadicOptional._
	import OptionalEmbedding.Continuation

	import scala.language.higherKinds

	// Can any Monad be embedded into Continuation? Yes! Lets do it!
	def embedM[R, T, M[_]](x: M[T])(implicit M: Monad[M]): Continuation[M[R], T] = Continuation[M[R], T](k => M.flatMap(x)(k))
	def runM[T, M[_]](m: Continuation[M[T], T])(implicit M: Monad[M]): M[T] = m.run(x => M.pure(x))

	def programOptional[R](id: Long)(implicit M: Monad[Optional]): Continuation[Optional[R], Info] =
		embedM[R, User, Optional](Optional.pure(getUser(id))).flatMap{ user =>
			embedM[R, Info, Optional](Optional.pure(getInfo(user)))
		}

	assert(runM(programOptional[Info](123)) == Optional.pure(Info("Tom")))
	assert(runM(programOptional[Info](1234)) == Nothing)
}