/* These tests show various mechanisms available for implicit prioritization.
 */

class E[T](val str: String)  // The type for which we infer terms below

class Arg[T]  // An argument that we use as a given for some implied instances below

/* First, two schemes that require a pre-planned architecture for how and
 * where implied instances are defined.
 *
 * Traditional scheme: prioritize with location in class hierarchy
 */
class LowPriorityImplicits {
  implied t1[T] for E[T]("low")
}

object NormalImplicits extends LowPriorityImplicits {
  implied t2[T]for E[T]("norm") given Arg[T]
}

def test1 = {
  import implied NormalImplicits._
  assert(the[E[String]].str == "low") // No Arg available, so only t1 applies

  { implied for Arg[String]
    assert(the[E[String]].str == "norm")  // Arg available, t2 takes priority
  }
}

/* New scheme: dummy implicit arguments that indicate priorities
 */
object Priority {
  class Low
  object Low { implied for Low }

  class High extends Low
  object High { implied for High }
}

object Impl2 {
  implied t1[T] for E[T]("low") given Priority.Low
  implied t2[T] for E[T]("norm") given Priority.High given Arg[T]
}

def test2 = {
  import implied Impl2._
  assert(the[E[String]].str == "low") // No Arg available, so only t1 applies

  { implied for Arg[String]
    assert(the[E[String]].str == "norm") // Arg available, t2 takes priority
  }
}

/* The remaining tests show how we can add an override of highest priority or
 * a fallback of lowest priority to a group of existing implied instances, without
 * needing to change the location or definition of those instances.
 *
 * First, consider the problem how to define an override of highest priority.
 * If all of the alternatives in the existing hierarchy take implicit arguments,
 * an alternative without implicit arguments would override all of them.
 */
object Impl2a {
  implied t3[T] for E[T]("hi")
}

def test2a = {
  import implied Impl2._
  import implied Impl2a._

  implied for Arg[String]
  assert(the[E[String]].str == "hi")
}

/* If that solution is not applicable, we can define an override by refining the
 * result type of the implied instance, e.g. like this:
 */
object Impl3 {
  implied t1[T] for E[T]("low")
}

object Override {
  trait HighestPriority  // A marker trait to indicate a higher priority

  implied over[T] for E[T]("hi"), HighestPriority
}

def test3 = {
  import implied Impl3._
  assert(the[E[String]].str == "low")  // only t1 is available

  { import implied Override._
    import implied Impl3._
    assert(the[E[String]].str == "hi") // `over` takes priority since its result type is a subtype of t1's.
  }
}

/* Now consider the dual problem: How to install a fallback with lower priority than existing
 * implied instances that kicks in when none of the other instances are applicable.
 * We get there in two stages. The first stage is by defining an explicit `withFallback` method
 * that takes the right implicit and returns it. This can be achieved using an implicit parameter
 * with a default argument.
 */
object Impl4 {
  implied t1 for E[String]("string")
  implied t2[T] for E[T]("generic") given Arg[T]
}

object fallback4 {
  def withFallback[T] given (ev: E[T] = new E[T]("fallback")): E[T] = ev
}

def test4 = {
  import implied Impl4._
  import fallback4._
  assert(withFallback[String].str == "string")  // t1 is applicable
  assert(withFallback[Int].str == "fallback")   // No applicable instances, pick the default

  { implied for Arg[Int]
    assert(withFallback[Int].str == "generic")  // t2 is applicable
  }
}

/* The final setup considers the problem how to define a fallback with lower priority than existing
 * implicits that exists as an implicit instance alongside the others. This can be achieved
 * by combining the implicit parameter with default technique for getting an existing impplicit
 * or a fallback with the result refinement technique for overriding all existing implicit instances.
 *
 * It employs a more re-usable version of the result refinement trick.
 */
opaque type HigherPriority = Any
object HigherPriority {
  def inject[T](x: T): T & HigherPriority = x
}

object fallback5 {
  implied [T] for (E[T] & HigherPriority) given (ev: E[T] = new E[T]("fallback")) = HigherPriority.inject(ev)
}

def test5 = {
  import implied Impl4._
  import implied fallback5._

  // All inferred terms go through the implied instance in fallback5.
  // They differ in what implicit argument is synthesized for that instance.
  assert(the[E[String]].str == "string")  // t1 is applicable
  assert(the[E[Int]].str == "fallback")   // No applicable instances, pick the default

  { implied for Arg[Int]
    assert(the[E[Int]].str == "generic")  // t2 is applicable
  }
}

object Test extends App {
  test1
  test2
  test2a
  test3
  test4
  test5
}

