package concurrency

object Streams extends App {

	sealed trait Process[I, O] {

		import Process._

		def apply(s: LazyList[I]): LazyList[O] = this match {
			case Next(feed) => s match {
				case LazyList.#::(h, t) => feed(Right(h))(t)
				case xs => feed(Left(End))(xs)
			}
			case Result(h, t) => h #:: t(s)
			case Halt(e) => e match {
				case Kill | End => LazyList.empty
				case _ => throw e
			}
		}

		def pipeTo[O2](that: Process[O, O2]): Process[I, O2] = Next {
			case Right(value) =>
				that(this (LazyList(value)))
					.headOption
					.map(Result(_, this.pipeTo(that)))
					.getOrElse(this.pipeTo(that))
			case Left(e) => Halt(e)
		}

		def map[O2](f: O => O2): Process[I, O2] = this pipeTo lift(f)

		def onHalt(f: Throwable => Process[I, O]): Process[I, O] = this match {
			case Next(recv) => Next(recv andThen (_.onHalt(f)))
			case Result(h, t) => Result(h, t.onHalt(f))
			case Halt(e) => Try(f(e))
		}

		def ++(p: => Process[I, O]): Process[I, O] =
			this.onHalt {
				case End => p
				case err => Halt(err)
			}

		def onComplete(p: => Process[I, O]): Process[I, O] =
			this.onHalt {
				case End => p.asFinalizer
				case err => p.asFinalizer ++ Halt(err)
			}

		def asFinalizer: Process[I, O] = this match {
			case Result(h, t) => Result(h, t.asFinalizer)
			case Halt(e) => Halt(e)
			case Next(recv) => Next {
				case Left(Kill) => this.asFinalizer
				case x => recv(x)
			}
		}


		def flatMap[O2](f: O => Process[I, O2]): Process[I, O2] = Next {
			case Right(value) =>
				this (LazyList(value))
					.headOption
					.map(x => Try(f(x)))
					.flatMap(proc => proc(LazyList(value)).headOption)
					.map(Result(_, this.flatMap(f)))
					.getOrElse(this.flatMap(f))
			case Left(e) => Halt(e)
		}

		def repeat: Process[I, O] = {
			def go(p: Process[I, O]): Process[I, O] = p match {
				case Next(recv) =>
					Next[I, O] {
						case i@Right(_) => go(recv(i))
						case e@Left(_) => recv(e)
					}
				case Result(h, t) => Result(h, go(t))
				case Halt(e) => go(this)
			}

			go(this)
		}
	}
	
	object Process {
		case class Next[I, O](feed: Either[Throwable, I] => Process[I, O]) extends Process[I, O]
		case class Result[I, O](head: O, tail: Process[I, O]) extends Process[I, O]
		case class Halt[I, O](e: Throwable) extends Process[I, O]
		case object End extends Exception
		case object Kill extends Exception

		def resource[R, I, O](acquire: => R)(use: R => Process[I, O])(release: R => Process[I, O]): Process[I, O] =
			((r: R) => use(r).onComplete(release(r)))(acquire)
		
		def Try[I, O](p: => Process[I, O]): Process[I, O] =
			try p
			catch { case e: Throwable => Halt(e) }

		def liftOne[I,O](f: I => O): Process[I,O] =
			Next {
				case Right(i) => Result(f(i), Halt(End))
				case Left(value) => Halt(value)
			}
		
		def lift[I,O](f: I => O): Process[I,O] = liftOne(f).repeat
		
		def filter[I](p: I => Boolean): Process[I,I] =
			Next[I,I] {
				case Right(i) if p(i) => Result(i, Halt(End))
				case Left(e) => Halt(e)
			}.repeat

		
		def take[I](n: Int): Process[I,I] = {
			def go(count: Int): Process[I, I] =
				Next {
					case Right(e) =>
						if (count < n)
							Result(e, go(count + 1))
						else 
							Halt(End)
					case Left(e) => Halt(e)
				}
			go(0)
		}

		def drop[I](n: Int): Process[I,I] = {
			def go(count: Int): Process[I, I] =
				Next {
					case Right(e) => 
						if (count >= n)
							Result(e, go(count + 1))
						else
							go(count + 1)
					case Left(e) => Halt(e)
				}
			go(0)
		}
		
		def takeWhile[I](f: I => Boolean): Process[I,I] = {
			def go: Process[I, I] =
				Next {
					case Right(e) =>
						if (f(e))
							Result(e, go)
						else
							Halt(End)
					case Left(e) => Halt(e)
				}
			go
		}
		
		def dropWhile[I](f: I => Boolean): Process[I,I] = {
			def go: Process[I, I] =
				Next {
					case Right(e) =>
						if (f(e))
							go
						else
							Result(e, go)
					case Left(e) => Halt(e)
				}
			go
		}

		def loop[S,I,O](z: S)(f: (I,S) => (O,S)): Process[I,O] =
			Next{
				case Right(i) => f(i,z) match {
					case (o,s2) => Result(o, loop(s2)(f))
				}
				case Left(e) => Halt(e)
			}
		
		def sum: Process[Double,Double] = {
			def go(acc: Double): Process[Double,Double] =
				Next {
					case Right(d) => Result(d+acc, go(d+acc))
					case Left(e) => Halt(e)
				}
			go(0.0)
		}

		def count[I]: Process[I,Int] = {
			def go(n: Int): Process[I, Int] = {
				Next {
					case Right(_) => go(n + 1)
					case Left(e) => Result(n, Halt(e))
				}
			}
			go(0)
		}
		
		def mean: Process[Double,Double] = {
			def go(n: Int, acc: Double): Process[Double, Double] = {
				Next {
					case Right(e) => go(n + 1, acc + e)
					case Left(e) => Result(acc / n, Halt(e))
				}
			}
			go(0, 0)
		}
	}

	import Process._
	val p = liftOne((x: Int) => x * 2)
//	println{
//		p(LazyList(1,2,3)).toList
//	}
//	println("----------------------------------------------")
	println{
//		(lift[Int, Int](_ * 2) pipeTo lift(_ + 1))(LazyList(1, 2, 3)).toList
//		(filter[Int](_ % 2 == 0) pipeTo lift(_ + 1))(LazyList(1, 2, 3)).toList
		resource[Int, Int, Int](5)(x => Result(x, Result(x, Halt(End))))(x => Result(x - 1, Halt(End))).flatMap(x => lift[Int, Int](y => x + y))(LazyList(1, 2, 3)).toList
	}
}
