Recently I explored the concept of “continuation” and gathered lots of code samples along the way.
I decided to make this repository to allow users to check it out and play with those samples and hope such a format will be a positive experience.
This work is heavily inspired by:
Philip Wadler "Comprehending Monads",
Andrzej Filinski "Representing Monads",
and ["Mother of all monads"](http://blog.sigfpe.com/2008/12/mother-of-all-monads.html) blog post.
Lets start with domain which will be used throughout the article.
[Domain.scala](continuations_playground/src/main/scala/contarticle/Domain.scala)
```scala mdoc:silent
// Domain models
case class User(id: Long)
case class Info(name: String)

// Data storage:
val users = Map(123L -> User(123))
val info = Map(123L -> Info("Tom"))

// Access functions
def getUser(id: Long): User = {
    users.getOrElse(id, null)
}

def getInfo(user: User): Info = {
    info.getOrElse(user.id, null)
}
```
[SimpleNullChecks.scala](continuations_playground/src/main/scala/contarticle/SimpleNullChecks.scala)
Let's say we have a program which may fail if nonexistent user will be passed in.
Notice that this program is written in a so called "direct style", which means that runtime
takes care to determine what line of code should be executed next.
One could say that control flow defined "implicitly" by runtime internals.
```scala mdoc
def programMayFail(id: Long): Info = {
    val user = getUser(id)
    val info = getInfo(user)
    info
}
programMayFail(123)
// This case throws NullPointerException.
scala.util.Try(programMayFail(1234)).isFailure
```
How can we protect ourselves? The easiest way is to add null checks.
But this way has it's own drawbacks: such checkings are not composable.
```scala mdoc
def programNullChecked(id: Long): Info = {
    val user = getUser(id)
    if (user != null) {
        val info = getInfo(user)
        info
    } else {
        null
    }
}
programNullChecked(123)
programNullChecked(1234)
```
Clearly the program now is safe. But can we do better? Can we make such checkings composable?
Let's take another look on our problem: clearly all those null checks are a duplicating code.
Could all those checkings be abstracted somehow?
[OptionalCPSExample.scala](continuations_playground/src/main/scala/contarticle/OptionalCPSExample.scala)
It turns out that yes, but to do so one should switch a point of view on the problem:
Having functions, each executing with the return value of the previous one, can they be short-circuited
in a way, that if previous returns null next one will not be ever called?
It reminds exceptions - they also allows to short-circuit execution by throwing an exception.
To implement an idea of stopping execution at some point we need a reified notion of execution itself.
But what is an execution? How could it be captured? What is an execution unit?
Seems like we do not have much choice but use functions as reified execution.
Let's try to abstract over the null check keeping an ideas above in mind:
`v: T` - is a called-by-name code block of type T which could return null
`k: T => R` - is a rest of a program, captured as a function.
```scala mdoc
def optional[R, T](v: => T)(k: T => R): R = {
    if (v != null) {
        k(v)
    } else {
        null.asInstanceOf[R]
    }
}
```
Now the program is safe, but let's take a closer look on the control flow:
```scala mdoc
def programOptional(id: Long): Info = {
    optional(getUser(id)) { user: User => // this function is our "k: T => R" where T - User and R - Info
        optional(getInfo(user))(identity) // one need to call identity as the rest of computation to acquire the value from the previous step
    }
}
programOptional(123)
programOptional(1234)
```
Seems like each next step of computation now is handled explicitly as a function call.
This approach gave more control over execution and opened a way to a composition of a succeeding calls.
[ContinuationExample.scala](continuations_playground/src/main/scala/contarticle/ContinuationExample.scala)
The notion of the "rest of the program" has a name on it's own - "continuation"
Let's try to extract a continuation signature from example with "optional":
`(A => R)` - continuation or representation of the "rest of the program."
this function will be called with a value of type A and returns a value of type R,
which, in turn, will be returned to a caller.
Lets sum it up:
`R` - is the return type of ALL computation.
`A` - is the type of a value passed to continuation.
```scala mdoc:reset:invisible
case class User(id: Long)
case class Info(name: String)
val users = Map(123L -> User(123))
val info = Map(123L -> Info("Tom"))
def getUser(id: Long): User = users.getOrElse(id, null)
def getInfo(user: User): Info = info.getOrElse(user.id, null)
```
```scala mdoc
type Continuation[R, A] = (A => R) => R
```
Lets try to use newly defined Continuation type:
```scala mdoc
def optional[R, A](v: A): Continuation[R, A] =
    (k: A => R) => {
        if (v != null) {
            k(v)
        } else {
            null.asInstanceOf[R]
        }
    }
def programOptional(id: Long): Info =
    optional(getUser(id)) { user =>
        optional(getInfo(user))(identity)
    }

programOptional(123)
programOptional(1234)
```
[ContinuationComposition.scala](continuations_playground/src/main/scala/contarticle/ContinuationComposition.scala)
All this continuation stuff closely reminds me a stack operations:
Each computation have a superpower to "go to" continuation with some value and this value is placed on an imaginary stack.
Next computation have an access to that value and can call its continuation with it or some other value.
When the last value being computed it will be returned to a caller.
Seems like small runtime with it's own control flow rules.
Lets try to capture them:
`(run: (A => R) => R)` - continuation
`changeValue` - changes value on "stack" before passing it to next computation
`continueWith` - continue execution with another continuation
```scala mdoc:reset:invisible
case class User(id: Long)
case class Info(name: String)
val users = Map(123L -> User(123))
val info = Map(123L -> Info("Tom"))
def getUser(id: Long): User = users.getOrElse(id, null)
def getInfo(user: User): Info = info.getOrElse(user.id, null)
```
```scala mdoc
case class Continuation[R, +A](run: (A => R) => R) {
    // Notice f type - it takes A as a parameter. This is because it modifies the value passed to the continuation.
    def changeValue[B](f: A => B): Continuation[R, B] = {
        this.continueWith((a: A) => Continuation(k => k(f(a)))) // You can clearly see this here - f called first, then it's result passed next to a continuation.
    }

    def continueWith[B](f: A => Continuation[R, B]): Continuation[R, B] = {
        Continuation(k => run(a => f(a).run(k)))
    }
}
```
Lets redefine optional in terms of Continuation
```scala mdoc
def optional[R, T](v: T): Continuation[R, T] =
    Continuation((k: T => R) =>
        if (v != null) {
            k(v)
        } else {
            null.asInstanceOf[R]
        }
    )
```
Now the program could be defined in terms of composition operators.
```scala mdoc
def programOptional[R](id: Long): Continuation[R, Info] =
    optional(getUser(id)).continueWith{ user =>
        optional(getInfo(user))
    }

programOptional(123).run(identity)
programOptional(1234).run(identity)
```
[MonadicOptional.scala](continuations_playground/src/main/scala/contarticle/MonadicOptional.scala)
Is there another way to conquer the problem with nulls?
Turned out it is - monads. Without additional theory let's represent them as an interface:
```scala mdoc:reset:invisible
case class User(id: Long)
case class Info(name: String)
val users = Map(123L -> User(123))
val info = Map(123L -> Info("Tom"))
def getUser(id: Long): User = users.getOrElse(id, null)
def getInfo(user: User): Info = info.getOrElse(user.id, null)
```
```scala mdoc
trait Monad[F[_]] {
    def pure[A](v: A): F[A] // the way to create instance of a Monad
    def map[A, B](fa: F[A])(f: A => B): F[B] // the way to change value "inside" the monad
    def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B] // the way to compose two monads
}
```
Lets define algebraic data type Optional with two children: Just and Nothing.
```scala mdoc
sealed trait Optional[+T] {
    // those functions just convinient wrappers on top of monad implementation
    def map[B](f: T => B)(implicit M: Monad[Optional]): Optional[B] = M.map(this)(f)
    def flatMap[B](f: T => Optional[B])(implicit M: Monad[Optional]): Optional[B] = M.flatMap(this)(f)
}
// Represents existence of a value.
final case class Just[+A](value: A) extends Optional[A]
// Represents absence of a value.
final case object Nothing extends Optional[Nothing]
```
And provide companion object with implementation of Monad typeclass and Monad constructor:
```scala mdoc
object Optional {
    // also a wrapper around Monad implementation
    def pure[A](v: A)(implicit M: Monad[Optional]): Optional[A] = M.pure(v)

    // Implementation of a monad
    implicit val monadOptional: Monad[Optional] = new Monad[Optional] {
        override def pure[A](v: A): Optional[A] = if (v == null) Nothing else Just(v)
        override def map[A, B](fa: Optional[A])(f: A => B): Optional[B] = flatMap(fa)((v: A) => pure(f(v)))
        // Pay attention how close is this code to "optional" function definde earlier.
        override def flatMap[A, B](fa: Optional[A])(f: A => Optional[B]): Optional[B] =
            fa match {
                case Just(value) => f(value)
                case Nothing => Nothing
            }
    }
}
```
This is how the program can be rewritten with newly defined Optional monad.
```scala mdoc
def programOptional(id: Long): Optional[Info] =
    Optional.pure(getUser(id)).flatMap{ user =>
        Optional.pure(getInfo(user))
    }

programOptional(123)
programOptional(1234)
```
There is also a few laws each monad implementation should follow.
In reality some implementations just pretending to be monads and not following the laws, but it is not the case with Optional.
```scala mdoc
	val a = 1
val f = (x: Int) => Optional.pure(x + 1)
val g = (x: Int) => Optional.pure(x * 2)
Optional.pure(a).flatMap(f) == f(a) // left identity
Optional.pure(a).flatMap(Optional.pure) == Optional.pure(a) // right identity
Optional.pure(a).flatMap(f).flatMap(g) == Optional.pure(a).flatMap(x => f(x).flatMap(g)) // associativity
```
Clearly Optional follows all three laws and can be called a monad.
[OptionalEmbedding.scala](continuations_playground/src/main/scala/contarticle/OptionalEmbedding.scala)
```scala mdoc:reset:invisible
case class User(id: Long)
case class Info(name: String)
val users = Map(123L -> User(123))
val info = Map(123L -> Info("Tom"))
def getUser(id: Long): User = users.getOrElse(id, null)
def getInfo(user: User): Info = info.getOrElse(user.id, null)
trait Monad[F[_]] {
    def pure[A](v: A): F[A]
    def map[A, B](fa: F[A])(f: A => B): F[B]
    def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]
}
sealed trait Optional[+T] {
    def map[B](f: T => B)(implicit M: Monad[Optional]): Optional[B] = M.map(this)(f)
    def flatMap[B](f: T => Optional[B])(implicit M: Monad[Optional]): Optional[B] = M.flatMap(this)(f)
}
final case class Just[+A](value: A) extends Optional[A]
final case object Nothing extends Optional[Nothing]

object Optional {
    def pure[A](v: A)(implicit M: Monad[Optional]): Optional[A] = M.pure(v)
    implicit val monadOptional: Monad[Optional] = new Monad[Optional] {
        override def pure[A](v: A): Optional[A] = if (v == null) Nothing else Just(v)
        override def map[A, B](fa: Optional[A])(f: A => B): Optional[B] = flatMap(fa)((v: A) => pure(f(v)))
        override def flatMap[A, B](fa: Optional[A])(f: A => Optional[B]): Optional[B] =
            fa match {
                case Just(value) => f(value)
                case Nothing => Nothing
            }
    }
}
```
The close resemblance between monads and continuations should lead to some discoveries.
Lets try to pretend that Continuation is a monad:
```scala mdoc
case class Continuation[R, +A](run: (A => R) => R) {
    def map[B](f: A => B): Continuation[R, B] = {
        this.flatMap((a: A) => Continuation(k => k(f(a))))
    }

    def flatMap[B](f: A => Continuation[R, B]): Continuation[R, B] = {
        Continuation(k => run(a => f(a).run(k)))
    }
}

object Continuation {
    def pure[R, T](v: T): Continuation[R, T] = Continuation[R, T](k => k(v))
}
```
Close enough, lets check if the laws are satisfied.
```scala mdoc
	val a = 1
	val f = (x: Int) => Continuation.pure[Int, Int](x + 1)
	val g = (x: Int) => Continuation.pure[Int, Int](x * 2)
	Continuation.pure(a).flatMap(f).run(identity) == f(a).run(identity) // left identity
	Continuation.pure(a).flatMap(Continuation.pure[Int, Int]).run(identity) == Continuation.pure(a).run(identity) // right identity
	Continuation.pure(a).flatMap(f).flatMap(g).run(identity) == Continuation.pure(a).flatMap(x => f(x).flatMap(g)).run(identity) // associativity
```
Turns out that the laws are satisfied and Continuations could be represented as monads.
But what kind of monad is a Continuation? I mean - what is it actually doing?
It turns out that Continuation is a monad which is doing nothing except passing the values.
Maybe there is a way to compose Optional and Continuation?
This way there will be more evidence to their close relations and, perhaps, Continuation will mimic Optional?
```scala mdoc
// embed - creates a Continuation out of Optional.
def embed[R, T](x: Optional[T]): Continuation[Optional[R], T] = Continuation[Optional[R], T](k => x.flatMap(k))
// run - run the Continuation.
def run[T](m: Continuation[Optional[T], T]): Optional[T] = m.run(x => Optional.pure(x))
```
Seems like a safe version of a program.
```scala mdoc
def programOptional[R](id: Long): Continuation[Optional[R], Info] =
    embed(Optional.pure(getUser(id))).flatMap{ user =>
        embed(Optional.pure(getInfo(user)))
    }

run(programOptional[Info](123))
run(programOptional[Info](1234))
```
[MonadicEmbedding.scala](continuations_playground/src/main/scala/contarticle/MonadicEmbedding.scala)
Can any Monad be embedded into Continuation? Yes! Lets do it!
```scala mdoc
def embedM[R, T, M[_]](x: M[T])(implicit M: Monad[M]): Continuation[M[R], T] = Continuation[M[R], T](k => M.flatMap(x)(k))
def runM[T, M[_]](m: Continuation[M[T], T])(implicit M: Monad[M]): M[T] = m.run(x => M.pure(x))

def programOptionalM[R](id: Long)(implicit M: Monad[Optional]): Continuation[Optional[R], Info] =
    embedM[R, User, Optional](Optional.pure(getUser(id))).flatMap{ user =>
        embedM[R, Info, Optional](Optional.pure(getInfo(user)))
    }

runM(programOptionalM[Info](123))
runM(programOptionalM[Info](1234))
```
[ContinuationCompositionWithSimpleChoice.scala](continuations_playground/src/main/scala/contarticle/ContinuationCompositionWithSimpleChoice.scala)
Lets explore an opportunity to make a choice - return a different value, say, specific error.
All we need - just a function which return the value of the `R` (result) type.
```scala mdoc
def optionalSimpleChoise[R, T](v: T, ifNull: () => R): Continuation[R, T] =
    Continuation((k: T => R) =>
        if (v != null) {
            k(v)
        } else {
            ifNull()
        }
    )

def programOptionalSimpleChoise[R](id: Long)(ifNull: () => R): Continuation[R, Info] =
    optionalSimpleChoise(getUser(id), ifNull).flatMap{ user =>
        optionalSimpleChoise(getInfo(user), ifNull)
    }

val ifNull = () => "Error: null"
programOptionalSimpleChoise(123)(ifNull).map(_.name).run(identity)
programOptionalSimpleChoise(1234)(ifNull).map(_.name).run(identity)
```
[ContinuationCompositionWithContinuationChoice.scala](continuations_playground/src/main/scala/contarticle/ContinuationCompositionWithContinuationChoice.scala)
What if one want to pass a continuation as another path of execution?
Easy - just run it inside!
```scala mdoc
def optionalContinuationChoise[R, T](v: T, ifNull: () => Continuation[R, T]): Continuation[R, T] =
    Continuation((k: T => R) =>
        if (v != null) {
            k(v)
        } else {
            ifNull().run(k)
        }
    )
```
Notice that ifNull return type is `Continuation[R, Null]`
Null - because it is a subtype of every AnyRef type and we can pass any AnyRef value to continuation.
In this example it could be values of either User or Info.
```scala mdoc
def programOptionalContinuationChoise[R, T](id: Long)(ifNull: () => Continuation[R, Null]): Continuation[R, Info] =
    optionalContinuationChoise[R, User](getUser(id), ifNull).flatMap{ user =>
        optionalContinuationChoise[R, Info](getInfo(user), ifNull)
    }

val ifNullCont = () => Continuation[String, Null](_ => "Error: null")
programOptionalContinuationChoise(123)(ifNullCont).map(_.name).run(identity)
programOptionalContinuationChoise(1234)(ifNullCont).map(_.name).run(identity)
```
[CallCC.scala](continuations_playground/src/main/scala/contarticle/CallCC.scala)
If you made it here then some hardcore content awaits: callCC function
which is a common abbreviation for call with current continuation.
Check it out at wikipedia: https://www.wikiwand.com/en/Call-with-current-continuation.
What this function does - gives an ability to jump to passed continuation (this, given by f)
The interesting thing is that continuation returned from f call - is the continuation returned by callCC itself.
```scala mdoc
def callCC[R, A, B](f: (A => Continuation[R, B]) => Continuation[R, A]): Continuation[R, A] = {
    Continuation((k: A => R) => {           // continuation from following map and flatMap
        f{(a: A) => 				        // argument to pass to continuation
            Continuation((_: B => R) => {   // wrapper to make compiler happy on nested callCC, notice (B => R) continuation is ignored
                k(a)	                    // actual call to following continuation in case continuation of this callCC will be used
            })
        }.run(k)                            // run in case some other continuation will be returned from call to f
                                            // all in all both cases should provide same continuation types
    })
}
```
This how optional can be implemented in term of callCC.
```scala mdoc
import Continuation._
def optionalCallCC[R, T](v: T, ifNull: () => Continuation[R, T]): Continuation[R, T] =
    callCC[R, T, T]((k: T => Continuation[R, T]) =>         // here "k" would be binded to (2) and following flatMap/map calls - see the optional implementation
        callCC[R, T, T]((exit: T => Continuation[R, T]) =>  // here "exit" would be binded to (1)
            if (v != null) {
                k(v)
            } else {
                exit(v)
            }
        ).flatMap(_ => ifNull())                             // (1) ignore because it is always null
    )

def programOptionalCallCC[R, T](id: Long)(ifNull: () => Continuation[R, Null]): Continuation[R, Info] =
    optionalCallCC[R, User](getUser(id), ifNull).flatMap{
        user => // (2)
            optionalCallCC[R, Info](getInfo(user), ifNull)  // (2)
    }

val ifNullCallCC = () => Continuation[String, Null](_ => "Error: null")
programOptionalCallCC(123)(ifNullCallCC).map(_.name).run(identity)
programOptionalCallCC(1234)(ifNullCallCC).map(_.name).run(identity)
```
The following content is mostly about code samples. I recommend to check it out!
[OtherExamplesOfCallCC.scala](continuations_playground/src/main/scala/contarticle/OtherExamplesOfCallCC.scala)
Translation of Mothers of all monads article source code into Scala.
http://blog.sigfpe.com/2008/12/mother-of-all-monads.html
[MotherOfAllMonadsExamples.scala](continuations_playground/src/main/scala/contarticle/MotherOfAllMonadsExamples.scala)
Translation of same Hackage examples from documentation of ConT onto Scala.
http://hackage.haskell.org/package/mtl-2.2.2/docs/Control-Monad-Cont.html
[HackageContExamples.scala](continuations_playground/src/main/scala/contarticle/HackageContExamples.scala)
Special thanks to https://github.com/Odomontois for providing this nice Continuation monad implementation!
[ContinuationMonadTrait.scala](continuations_playground/src/main/scala/contarticle/ContinuationMonadTrait.scala)