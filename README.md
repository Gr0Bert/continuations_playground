# Continuations playground

Recently I explored the concept of “continuation” and gathered lots of code samples along the way. I decided to make this repository to allow users to check it out and play with those samples. I hope such a format will be a positive experience.  
Lets start with domain which will be used throughout the article.  
[Domain.scala](../contunuations_playground/contarticle/Domain.scala)
```scala
// Domain models
case class User(id: Long)

case class Info(name: String)


// Data storage:
val users = Map(123L -> User(123))
// users: Map[Long, User] = Map(123L -> User(123L))
val info = Map(123L -> Info("Tom"))
// info: Map[Long, Info] = Map(123L -> Info("Tom"))

// Access functions
def getUser(id: Long): User = {
    users.getOrElse(id, null)
}


def getInfo(user: User): Info = {
    info.getOrElse(user.id, null)
}
```
[SimpleNullChecks.scala](../contunuations_playground/contarticle/SimpleNullChecks.scala)
Let's say we have a program which may fail if nonexistent user will be passed in.
Notice that this program is written in a so called "direct style", which means that runtime
takes care to determine what line of code should be executed next.
One could say that control flow defined "implicitly" by runtime internals.
```scala
def programMayFail(id: Long): Info = {
    val user = getUser(id)
    val info = getInfo(user)
    info
}

programMayFail(123)
// res0: Info = Info("Tom")
// This case throws NullPointerException.
scala.util.Try(programMayFail(1234)).isFailure
// res1: Boolean = true
```
How can we protect ourselves? The easiest way is to add null checks.
But this way has it's own drawbacks: such checkings are not composable.
```scala
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
// res2: Info = Info("Tom")
programNullChecked(1234)
// res3: Info = null
```
Clearly the program now is safe. But can we do better? Can we make such checkings composable?  
Let's take another look on our problem: clearly all those null checks are a duplicating code.
Could all those checkings be abstracted somehow?  
[OptionalCPSExample.scala](../contunuations_playground/contarticle/OptionalCPSExample.scala)
It turns out that yes, but to do so one should switch a point of view on the problem:  
Having functions, each executing with the return value of the previous one, can they be short-circuited
in a way, that if previous returns null next one will not be ever called?  
It reminds exceptions - they also allows to short-circuit execution by throwing an exception.  
To implement an idea of stopping execution at some point we need a reified notion of execution itself.
But what is an execution? How could it be captured? What is an execution unit?
Seems like we do not have much choice but use functions as reified execution.
Let's try to abstract over the null check keeping an ideas above in mind:  
v: T - is a called-by-name code block of type T which could return null  
k: T => R - is a rest of a program, captured as a function.  
```scala
def optional[R, T](v: => T)(k: T => R): R = {
    if (v != null) {
        k(v)
    } else {
        null.asInstanceOf[R]
    }
}
```
Now the program is safe, but let's take a closer look on the control flow:  
```scala
def programOptional(id: Long): Info = {
    optional(getUser(id)) { user: User => // this function is our "k: T => R" where T - User and R - Info
        optional(getInfo(user))(identity) // one need to call identity as the rest of computation to acquire the value from the previous step
    }
}

programOptional(123)
// res4: Info = Info("Tom")
programOptional(1234)
// res5: Info = null
```
Seems like each next step of computation now is handled explicitly as a function call.
This approach gave more control over execution and opened a way to a composition of a succeeding calls.  
