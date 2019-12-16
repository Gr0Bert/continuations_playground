package trampolines

object FunctionCompositionProblem extends App {
	sealed trait Trampoline[+A]
	final case class Done[A](result: A) extends Trampoline[A]
	final case class More[A](f: () => Trampoline[A]) extends Trampoline[A]

	@scala.annotation.tailrec
	def run[A](t: Trampoline[A]): A = {
		t match {
			case Done(result) => result
			case More(k) =>  run(k())
		}
	}

	/*
												 -------- f'' -------
									--------------- f' --------------
	   			----------------------- f --------------------
	andThen(andThen(andThen(andThen(f''', g'''), g''), g'), g)

	left-associated list
	------- result returned this way ------------>
	tail -> ((((f''', g'''), g''), g'), g) <- head
	<----------------- stack grows this way ------

		f_result = f(a)
			f'_result = f'(a)
				f''_result = f''(a)
					f'''_result = f'''(a)
					g'''_result = g'''(f'''_result)
				g''_result = g''(f''_result)
			g'_result = g'(f'_result)
		g_result = g(f_result)

	 */

	def andThen[A, B, C](f: A => B, g: B => C): A => C = (a: A) => {
		val result = f(a)
		g(result)
	}

	def id[A](a: A): A = a

	// will overflow the stack
	List.fill(10000)(id[Int](_)).foldLeft(id[Int](_))(andThen)(1)


	/*
			f_result_more = f(a)
			f_result = run(f_result_more)
			+-----------------------------------------------
			|	f'_result_more = f'(a)
			|	f'_result = run(f'_result_more)
			|	+---------------------------------------------
			|	|	f''_result_more = f''(a)
			|	|	f''_result = run(f''_result_more)
			|	|	+-------------------------------------------
			|	|	|	f'''_result_done = f'''(a)
			|	|	|	f'''_result = run(f'''_result_more)
			|	|	|	g'''_result = g'''(f'''_result)
			|	|	+-------------------------------------------
			|	|	g''_result = g''(f''_result)
			|	+---------------------------------------------
			|  g'_result = g'(f'_result)
			+-----------------------------------------------
		  g_result = g(f_result)
	 */
	def andThenTrampolined[A, B, C](f: A => Trampoline[B], g: B => Trampoline[C]): A => Trampoline[C] =
		(a: A) => More(() => {
			val resultTrampoline = f(a) // returns Trampolined call
			val result = run(resultTrampoline) // run trampoline to obtain results
			g(result)
		})

	def idTrampolined[A](a: A): Trampoline[A] = Done(a)

	//  will overflow the stack
	run{
		List.fill(10000)(idTrampolined[Int](_)).foldLeft(idTrampolined[Int](_))(andThenTrampolined)(1)
	}
}