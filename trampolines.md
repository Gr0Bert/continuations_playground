Another concept I found particularly interesting is 'trampoline'.
Those are reference materials I used while writing it:
1. `Tail Call Optimization in the Java HotSpot™ VM` paper by Arnold Schwaighofer
2. `Stackless Scala With Free Monads` paper by Runar Oli Bjarnason
3.  `Tail Call Elimination and Data Representation for
     Functional Languages on the Java Virtual Machine` paper by Magnus Madsen, Ramin Zarif, Ondřej Lhoták
4.  [Tail Call Elimination in Scala Monads](https://apocalisp.wordpress.com/2011/10/26/tail-call-elimination-in-scala-monads/) by Runar Oli Bjarnason
5. `The Hardware/Software Interface Class` by Luis Ceze and Gaetano Borriello. It's available on YouTube.  

Before discussion on tail calls and various optimizations one need some context for understanding the problem.
Let's start with memory layout for programs.  
As you can see form the picture below, stack starts from highest memory address and grows down,
while dynamic data grows up. When those two are colliding - bad things starting to happen,
e.g. program will crash. What one wants to achieve is to remove the possibility of such things happening.

Memory layout:
```
+-------------------+	2^n-1
|       stack       |
+-------------------+
|~~~~~~~~~~~~~~~~~~~|
+-------------------+
|dynamic data (heap)|
+-------------------+
|   static data     |
+-------------------+
|   literals        |
+-------------------+
|   instructions    |
+-------------------+	0
```
To understand how such a collision could happen one need to know what causes stack and heap to grow and to shrink.
I'm talking only about them because instructions, literals and static data are being initialized
on a process start-up, so they are not dynamic.
As for the heap - it's size increasing if something is allocated in it.
In context of java or c - it is variables allocated with 'new' or malloc calls.
Stack holds the procedure context.
What is that context and why do we need it?
Let's clear this up with a low level example on how functions call is working in stack-based languages.
Consider two functions:  
1. `caller`  
2. `callee`  
Now `caller` calls the `callee`, here are the steps which should be done:  
```
`caller`
<save registers> (3)
<set up args>
call `callee`
	-------
	`callee`
	<save registers> (3)
	<create local vars>
	...
	<setup return val>
	<destroy local vars>
	<restore registers> (3)
	<return> (1)
	-------
<clean up args>
<restore registers> (3)
<find return val> (2)
```
Assuming `caller` and `callee` running on the same CPU and uses the same register set:  
1. `callee` procedure must know where to find it arguments and return address.  
2. `caller` must know where to find returned value.  
3. `caller`'s registers needs to be saved:  
	a. by `caller`, before calling the `callee`  
	b. by `callee`, before using them  
	c. by some combination of both.  
The convention of where to find\leave things is called the `procedure call linkage`.  
As you can see calling the procedure involving plenty of a machinery.
Also each procedure instantiation should keep it's state, consisting of arguments, local variables and return address, somewhere,
and it's turns out that this place is a stack.
From now and on we consider that stack is allocated in frames,
which contains an information needed to call the procedure, including the procedures state.
Two implications immediately following from above:  
1. each procedure call increase the stack size by adding it's stack frame  
2. each procedure retun decrease the stack size by removing it's stack frame  
Let's see how recursive calls would affect the stack given two fuctions `caller` and `callee`:  
Here is a program:  
```
caller() {
	...
	result = callee(3)
	<do something with result>
	return result
}

callee(n) {
	...
	result = callee(n - 1)
	<do something with result>
	return result
}
```
stack during program execution:
```
+------------------+
|     caller       |
+------------------+
|     callee(3)    |
+------------------+
|     callee(2)    |
+------------------+
|     callee(1)    |
+------------------+
```
control flow:
```
`caller`
...
call `callee` (3)
	-------
	`callee`
	...
	call `callee` (2)
		-------
		`callee`
		...
		call `callee` (1)
			-------
			`callee`
			...
			<do something with result>
			<return>
			-------
		<do something with result>
		<return>
		-------
	<do something with result>
	<return>
	-------
<do something with result>
<return>
```
As you can see - stack is growing with each function call. But is there a way to avoid it?
Yes, there is, and it's name `tail call optimization`.
One could notice that if there is nothing to do with return value of callee, or, in other words,
the call to callee is the final action of it's caller -
then there is no purpose in storing the state of a caller, it means that it's frame
could be replaced with callee frame thus avoiding the stack growth.
If the caller and callee are the same function and call is the last action then such function
is 'tail-recursive', which is a special case of recursion.
The example above could be rewritten in the following way:
```
caller() {
	...
	result = callee(3)
	<do something with result>
	return result
}

callee(n) {
	...
	result = callee(n - 1) // notice the subsequent call to `callee` is the last action
	return result
}
```
stack during program execution:  
```
+------------------+
|     caller       |
+------------------+
| callee(3)(2)(1)  |
+------------------+
```
control flow:
```
`caller`
...
call `callee` (3)(2)(1)
	-------
	`callee`
	...
	<subsequent `callee` calls>
	<return>
	-------
<do something with result>
<return>
```
###The situation with tail-calls on JVM.  

In Java each active method has a stack frame associated with it and contains:
1. Local data where local variables being stored.
2. Dynamic link - reference to the frame of the caller method
3. Return address
4. Parameters of the current method
There are several problems associated with general tail-call optimization:
1. State of each thread should be stored on the thread's stack.
2. Exception handling actively using stack and exposes it to a programmer.
3. Security model which is looking at permissions of each stack frame.
And more.

So, is there a hope for people who wants general tail-call optimizaiton and stack-safety?
Yes - implementing a thing, called trampoline.
The idea is to make each function to return a continuation that represents next tail call or the result
of an entire computation. This functions being executed in a loop, called `trampoline` until result is available.
The disadvantage of trampolines is that each tail call requires an object which holds a continuation and object allocation is
usually more expensive than a method called, not speaking about later garbage collection.

Let's follow few examples and see how one can implement trampolining and what difficulties will be hidden on that path.
All examples will be written in Scala which supports tail-recursion. And from tail recursion we will start:

Consider this function:
[WhileLoop.scala](continuations_playground/src/main/scala/trampolines/WhileLoop.scala)
```scala
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
// res0: Int = 0
```
It's just a simple while loop which decrements the variable.  
As one can see it is verbose and could be rewritten more elegantly with recursion:  
[TailRecursion.scala](continuations_playground/src/main/scala/trampolines/TailRecursion.scala)
```scala
@scala.annotation.tailrec
final def callee(n: Int): Int =
    if (n == 0) n else callee(n - 1)

def caller(n: Int): Int =
    callee(n)

caller(1000000)
// res2: Int = 0
```
Result will be the same, but code is much more concise. That is the power of optimizing
tail-recursive calls. Notice the `@tailrec` annotation - it's notifies the compiler
to throw a warning if function stops being tail-recursive.

What if one wants a stack-safe mutual recursion? Like in the following example:  
[MutualRecursion.scala](continuations_playground/src/main/scala/trampolines/MutualRecursion.scala)
```scala
def calleeA(n: Int): Int =
    if (n == 0) n else calleeB(n - 1)

def calleeB(n: Int): Int =
    if (n == 0) n else calleeA(n - 1)

def callerAB(n: Int) =
    calleeA(n)
``` 
It turns out that this code will blows the stack because general tail call optimization
currently is not supported in Scala compiler. But there is a good thing - it could be fixed in
user code with trampoline. As you remember the main idea of trampoline is to make a
function return on each step. This can be done if, instead of calling next function, it will return 
a thunk - function without arguments which contains the rest of the computation.
But there should be something to evaluate this function. And it is a trampoline itself - 
a loop which evaluates each thunk.  
Let's see how it could be done:  
[SimpleTrampoline.scala](continuations_playground/src/main/scala/trampolines/SimpleTrampoline.scala)
```scala
// ADT for holding either thunk or result
sealed trait Trampoline[+A]
// structure which signalise that computation is over with the value `a` of type `A`
final case class Done[A](result: A) extends Trampoline[A]
// structure which holding a thunk, signalise that this thunk should be executed
final case class More[A](f: () => Trampoline[A]) extends Trampoline[A]

// trampoline itself, written as a while loop
def run[A](t: Trampoline[A]): A = {
    // current structure, updated on each cycle
    var curr: Trampoline[A] = t
    // result which will be returned when the computation will be finished
    var res: Option[A] = None
    // while computation is not finished
    while (res.isEmpty) {
        // check what trampoline structure we have
        curr match {
            // if Done - computation is finished, assign the result
            case Done(result) =>
                res = Some(result)
            // if more, execute thunk and go on the other cycle
            case More(k) =>
                curr = k()
        }
    }
    // return the actual result
    res.get
}

// notice - every subsequent call is wrapped in `More`
// this makes the function not to grow the stack,
// but return with a thunk
def calleeA(n: Int): Trampoline[Int] =
    if (n == 0) Done(n) else More(() => calleeB(n - 1))

def calleeB(n: Int): Trampoline[Int] =
    if (n == 0) Done(n) else More(() => calleeA(n - 1))

def callerAB(n: Int): Trampoline[Int] =
    calleeA(n)

run(callerAB(1000000))
// res5: Int = 0
```
This code will be safely executed without blowing the stack. Could the run function be less verbose?
Yes! Lets try to use tail-recursion instead of while loop:  
[SimpleTrampolineTailRecursive.scala](continuations_playground/src/main/scala/trampolines/SimpleTrampolineTailRecursive.scala)
```scala
@scala.annotation.tailrec
final def run[A](t: Trampoline[A]): A = {
    t match {
        case Done(result) => result
        case More(k) => run(k())
    }
}
```
This version I like much more.
There is still a case where such approach will not work. What if one wants to to something after the value will be returned?
Consider the following example:
[FunctionCompositionProblem.scala](continuations_playground/src/main/scala/trampolines/FunctionCompositionProblem.scala)
```scala
def andThen[A, B, C](f: A => B, g: B => C): A => C = (a: A) => {
    val result = f(a)
    g(result)
}

def id[A](a: A): A = a
```
Let's take a closer look on what happens here. Imagine the following call sequence:
```
                       -------- f'' -------
               --------------- f' --------------
        ----------------------- f --------------------
andThen(andThen(andThen(andThen(f''', g'''), g''), g'), g)
```
It is a composition of such `andThen` functions. The things above is `f` functions from `andThen` perspective, e.g.:
`andThen(andThen(f''', g'''), g'')`  
for outer andThen `f` call will lead to inner `andThen(f''', g''')`, thus I called it `f''`.
If one omit `andThen` he will come up to the following observations:  
1. The call sequence reminds left-associated list which we want to traverse from tail to head.  
2. While going to tail the stack will grow.  
3. The result will be returned from tail to head.  
I depicted that below:  
```
left-associated list
------- result returned this way ------------>
tail -> ((((f''', g'''), g''), g'), g) <- head
<----------------- stack grows this way ------
``` 
Let's check the stack during this calls:    
```
    f_result = f(a)
        f'_result = f'(a)
            f''_result = f''(a)
                f'''_result = f'''(a)
                g'''_result = g'''(f'''_result)
            g''_result = g''(f''_result)
        g'_result = g'(f'_result)
    g_result = g(f_result)
```
In the picture above inner function returns the value to the first variable above it, e.g.:  
`g'''_result` will be assigned to `f'''_result` and only then `g''(f''_result)` will be executed. 
As one can notice there are a lot of nested calls which are leading to stack overflow.  
This happens because call to `f` is not in a tail position because one needs the resulting value to be fed into `g`
and thus call to `f`, can not be optimized.
Maybe this issue could be solved with our trampoline? Let's try it out!  
```scala
def andThenTrampolined[A, B, C](f: A => Trampoline[B], g: B => Trampoline[C]): A => Trampoline[C] =
    (a: A) => More(() => {
        val resultTrampoline = f(a) // returns Trampolined call
        val result = run(resultTrampoline) // run trampoline to obtain results
        g(result)
    })

def idTrampolined[A](a: A): Trampoline[A] = Done(a)
```

Unfortunately the stack overflow still happening, the reason is the same but now it's a little bit more indirect.
Here is a stack during execution of `andThenTrampolined`:  
```
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
```
The legend is same - inner function returns the value to the first variable above.  
As one can see executing `run` inside of `andThenTrampolined` leads to subsequent `run` and `f` calls 
which overflows the stack.
So, the general problem is more or less clear - stack overflow happens when there is to many instances
of a functions (or function calls) on the stack.
The solution is also more or less clear - let's use the function as a constructors of
the structures on heap, which is potentially much bigger than stack, holding `delayed` function calls.
The main question now - can we apply this solution to a problem with `andThen` and trade somehow stack for heap?

Let's start solving this puzzle with defining a structure which could handle the situation when one should act
on a returning value:
[StackBasedTrampoline.scala](continuations_playground/src/main/scala/trampolines/StackBasedTrampoline.scala)
```scala
sealed trait Trampoline[+A]
final case class Done[A](result: A) extends Trampoline[A]
final case class More[A](f: () => Trampoline[A]) extends Trampoline[A]
// Cont for continuation
// constructor for applying function on the right to value on the left
// the point is to emulate the stack in the heap
final case class Cont[A, B](a: Trampoline[A], f: A => Trampoline[B]) extends Trampoline[B]
```
Okay, now the `andThenTrampolined` could be rewritten in the following way:
```scala
// return after every call
def andThenTrampolined[A, B, C](f: A => Trampoline[B], g: B => Trampoline[C]): A => Trampoline[C] =
    (a: A) => More(() => {
        Cont(f(a), result => g(result))
    })
```
Now what one get is the structure with nested `Cont`:  
```
Cont(Cont(Cont(Cont(Cont(a, f'''), g'''), g''), g'), g)
```
But it's again reminds the left-associated list:  
```
    left-associated list
------- result returned this way ------------>
tail -> (((((a, f'''), g'''), g''), g'), g) <- head
<----------------- stack grows this way ------
```
Could such a trampoline be written to allow one to reassociate the deeply nested `Call`'s on the go like this:   
```
    right-associated list
------- result returned this way ------------>
tail -> (a, (f''', (g''', (g'', (g', g))))) <- head
```
Use right associated structure - List:  
```scala
def run[A](t: Trampoline[A]): A = {
    var curr: Trampoline[Any] = t
    var res: Option[A] = None
    // keep track of operations by appending them to List which serves as a stack
    // scala List is right associated, so by appending operations to it we
    // effectively reassociate computations from left to right
    var stack: List[Any => Trampoline[A]] = List()
    while (res.isEmpty) {
        curr match {
            case Done(result) =>
                stack match {
                    case Nil =>
                        res = Some(result.asInstanceOf[A])
                    case f :: rest =>
                        stack = rest
                        curr = f(result)
                }
            case More(k) =>
                curr = k()
            case Cont(a, f) =>
                curr = a
                stack = f.asInstanceOf[Any => Trampoline[A]] :: stack
        }
    }
    res.get
}
```

```scala
def idTrampolined[A](a: A): Trampoline[A] = Done(a)

run{
    List.fill(100000)(idTrampolined[Int](_)).foldLeft(idTrampolined[Int](_))(andThenTrampolined)(1)
}
// res9: Int = 1
```

Could it be written in a more functional way with use of tail recursion?
[PureTrampoline.scala](continuations_playground/src/main/scala/trampolines/PureTrampoline.scala)

```scala
@scala.annotation.tailrec
final def run[A](t: Trampoline[A]): A = {
    t match {
        case Done(v) => v
        case More(k) => run(k())
        case Cont(Done(v), f) => run(f(v))
        case Cont(More(k), f) => run(Cont(k(), f))
        case Cont(Cont(b, g), f) => // if there are two subsequent Cont
        // reassociate on the go by producing new Cont which first apply `g` and then `f` to the `b`
            run{
                Cont(b, (x: Any) => Cont(g(x), f))
            }
    }
}
```
To understand how Cont's structure being rewritten consider the following example:
```scala
def plusOne(x: Int): Trampoline[Int] = Done(x + 1)
def plusTwo(x: Int): Trampoline[Int] = Done(x + 2)
def plusThree(x: Int): Trampoline[Int] = Done(x + 3)

// Here one have a deeply nested Cont's structure
val res1 = 
    Cont(
        Cont(
            Cont(
                Done(1),
                plusOne
            ),
            plusTwo
        ),
        plusThree
    )
// res1: Cont[Int, Int] = Cont(
//   Cont(Cont(Done(1), <function1>), <function1>),
//   <function1>
// )

// First step started with processing the outer Cont and Cont directly nested to it
// `plusTwo` become `g` in run loop
// `plusThree` become `f` in run loop
// new continuation being created with first applying plusTwo and then plusThree
val res2 =
    Cont(
        Cont(
            Done(1),
            plusOne
        ),
        (x: Int) => Cont(plusTwo(x), plusThree)
    )
// res2: Cont[Int, Int] = Cont(Cont(Done(1), <function1>), <function1>)

// Second step started with processing the outer Cont and Cont directly nested to it
// `plusOne` become `g` in run loop
// `plusTwo` become `f` in run loop
// new continuation being created with first applying plusOne and then plusTwo and then plusThree
val res3 =
    Cont(
        Done(1),
        (x: Int) => Cont(
            plusOne(x),
            (x: Int) => Cont(
                plusTwo(x),
                plusThree
            )
        )
    )
// res3: Cont[Int, Int] = Cont(Done(1), <function1>)
```
In the end run loop gradually rewrites all the nested Cont's to functions, which returns Cont's and
applying them in the right order.

The last thing one could notice is that we explored the rewriting rules using continuations and associativity law,
and it is a strong clue that one can implement a monad.
And it is particularly true in this case:
```scala
def flatMap[A, B](t: Trampoline[A])(f: A => Trampoline[B]): Trampoline[B] = Cont(t, f)
```
With shiny new flatMap `andThenTrampolined` could be rewritten in the following way:
```scala
def andThenTrampolined2[A, B, C](f: A => Trampoline[B], g: B => Trampoline[C]): A => Trampoline[C] =
    (a: A) => More(() => {
        flatMap(f(a))(result => g(result))
    })
```
One last thing to notice - one needs to wrap all calls into `More` constructor, in other case
calls to `f` will not be trampolined and the stack will be blown.