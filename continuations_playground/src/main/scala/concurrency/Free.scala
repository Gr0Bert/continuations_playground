package concurrency

import contarticle.MonadicOptional.Monad

object Free extends App {
	sealed trait Free[F[_], A] {
		def pure(v: A)(implicit F: Monad[F]): Free[F, A] = Suspend(F.pure(v))
		def map[B](f: A => B): Free[F, B] = FlatMap(this, (a: A) => Return(f(a)))
		def flatMap[B](f: A => Free[F, B]): Free[F, B] = FlatMap(this, f)
	}
	final case class Return[F[_], A](result: A) extends Free[F, A]
	final case class Suspend[F[_], A](f: F[A]) extends Free[F, A]
	final case class FlatMap[F[_], A, B](a: Free[F, A], f: A => Free[F, B]) extends Free[F, B]
	
	trait Translate[F[_], G[_]] { def apply[A](f: F[A]): G[A] }
	def identityTranslation[F[_], G[_]]: Translate[F, G] = new Translate[F, G] {
		override def apply[A](f: F[A]): G[A] = f.asInstanceOf[G[A]]
	}
	type ~>[F[_], G[_]] = Translate[F,G]
	
	final def run[F[_], G[_], A](t: Free[F, A])(nt: F ~> G)(implicit G: Monad[G]): G[A] = {
		t match {
			case Return(v) => G.pure(v)
			case Suspend(k) => nt(k)
			case FlatMap(Return(v), f) => run(f(v))(nt)
			case FlatMap(Suspend(k), f) => G.flatMap(nt(k))(a => run(f(a))(nt))
			case FlatMap(FlatMap(b, g), f) =>
				run{
					FlatMap(b, (x: Any) => FlatMap(g(x), f))
				}(nt)
		}
	}

	implicit def optionMonad = new Monad[Option] {
		override def pure[A](v: A): Option[A] = Some(v)
		override def map[A, B](fa: Option[A])(f: A => B): Option[B] = fa.map(f)
		override def flatMap[A, B](fa: Option[A])(f: A => Option[B]): Option[B] = fa.flatMap(f)
	}
	
	implicit def funcMonad: Monad[Function0] = new Monad[Function0] {
		override def pure[A](v: A): () => A = () => v
		override def map[A, B](fa: () => A)(f: A => B): () => B = () => f(fa())
		override def flatMap[A, B](fa: () => A)(f: A => () => B): () => B = () => f(fa())()
	}
	
	def freeMonadInstance[F[_] : Monad] = new Monad[({type f[a] = Free[F, a]})#f] {
		private val F = implicitly[Monad[F]]
		override def pure[A](v: A): Free[F, A] = Suspend(F.pure(v))
		override def map[A, B](fa: Free[F, A])(f: A => B): Free[F, B] = FlatMap(fa, (a: A) => Return(f(a)))
		override def flatMap[A, B](fa: Free[F, A])(f: A => Free[F, B]): Free[F, B] = FlatMap(fa, f)
	}
	
	type TT[T] = Free[Option, T]

}

object Example1 extends App {
	import Free._
	val f: Free[Option, Int] = freeMonadInstance[Option].flatMap(freeMonadInstance[Option].pure(10))((x: Int) => freeMonadInstance[Option].pure(x + 10))

	println{
		run(f)(identityTranslation[Option, Option])
	}
}

object Example2 extends App {
	sealed trait Console[A] {
		def toThunk: () => A
	}
	case object ReadLine extends Console[Option[String]] {
		def toThunk = () => run
		def run: Option[String] =
			try Some(System.console().readLine())
			catch { case e: Exception => None }
	}
	case class PrintLine(line: String) extends Console[Unit] {
		def toThunk = () => println(line)
	}

	import Free._
	object Console {
		type ConsoleIO[A] = Free[Console, A]
		def readLn: ConsoleIO[Option[String]] =
			Suspend(ReadLine)
		def printLn(line: String): ConsoleIO[Unit] =
			Suspend(PrintLine(line))
	}

	import Console._
	val f1: Free[Console, Option[String]] = for {
		_ <- printLn("I can only interact with the console.")
		ln <- readLn
	} yield ln

	val consoleToFunction0 =
		new (Console ~> Function0) { def apply[A](a: Console[A]) = a.toThunk }
	
	(run(f1)(consoleToFunction0)(funcMonad))()
}
