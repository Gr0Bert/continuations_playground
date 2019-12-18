package concurrency

import java.util.concurrent.{Callable, CountDownLatch, ExecutorService, ThreadPoolExecutor, TimeUnit}
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.ExecutionContext

object ParCont extends App {
	trait Continuation[R, +A] extends ((A => R) => R) {
		def map[B](f: A => B): Continuation[R, B] =
			flatMap(a => _(f(a)))

		def flatMap[B](f: A => Continuation[R, B]): Continuation[R, B] =
			k => this(a => f(a)(k))
	}

	object Continuation {
		def pure[R, A](a: A): Continuation[R, A] = _(a)
	}
	
	sealed trait Par[A] extends (ExecutionContext => Continuation[Unit, A]) { self =>
		def map[B](f: A => B): Par[B] = new Par[B] {
			override def apply(ec: ExecutionContext): Continuation[Unit, B] = self(ec).map(f)
		}
		def flatMap[B](f: A => Par[B]): Par[B] = new Par[B] {
			override def apply(ec: ExecutionContext): Continuation[Unit, B] = self(ec).flatMap{ a =>
				f(a)(ec)
			}
		}
	}
	
	object Par {
		def pure[A](a: A): Par[A] = new Par[A] {
			override def apply(v1: ExecutionContext): Continuation[Unit, A] = Continuation.pure(a)
		}
		
		def fork[A](p: => Par[A]): Par[A] = {
			new Par[A] {
				override def apply(ec: ExecutionContext): Continuation[Unit, A] =
					(k: A => Unit) => {
						val r = () => p(ec)(k)
						ec.execute(() => r())
					}
			}
		}
	}

	def map2[A,B,C](a: Par[A], b: Par[B])(f: (A,B) => C): Par[C] = {
		new Par[C] {
			override def apply(ec: ExecutionContext): Continuation[Unit, C] = {
				val refA = new AtomicReference[A]()
				val refB = new AtomicReference[B]()
				val latch = new CountDownLatch(2)
				val aF = Par.fork{
					println(s"a started at: ${Thread.currentThread().getName}")
					Thread.sleep(5000)
					a
				}.map{ a =>
					refA.set(a)
					latch.countDown()
				}
				val bF = Par.fork{
					println(s"b started at: ${Thread.currentThread().getName}")
					b
				}.map{ b =>
					refB.set(b)
					latch.countDown()
				}
				aF(ec)(identity)
				bF(ec)(identity)
				latch.await()
				Continuation.pure(f(refA.get, refB.get()))
			}
		}
	}
	
	def run[A](es: ExecutionContext)(p: Par[A]): A = {
		val ref = new AtomicReference[A]
		val latch = new CountDownLatch(1)
		p(es) { a => ref.set(a); latch.countDown() }
		latch.await()
		ref.get
	}
	
	println{
		run(ExecutionContext.global) {
			map2(Par.pure(1), Par.pure(2))(_ + _)
		}
	}
}
