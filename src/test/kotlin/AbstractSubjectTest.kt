import observer.AbstractSubject
import observer.Observer

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Minimal concrete subject so we can instantiate the abstract class under test. */
private class TestSubject<T> : AbstractSubject<T>()

class AbstractSubjectTest {

    private lateinit var subject: TestSubject<String>

    @BeforeTest
    fun setUp() {
        subject = TestSubject()
    }

    // --- basic subscribe / notify --------------------------------------

    @Test
    fun `notifyObservers with no subscribers does nothing and does not throw`() {
        subject.notifyObservers("hello") // should not throw
    }

    @Test
    fun `a subscribed observer receives the notified value`() {
        val received = mutableListOf<String>()
        subject.subscribe(Observer { received.add(it) })

        subject.notifyObservers("first")

        assertEquals(listOf("first"), received)
    }

    @Test
    fun `multiple distinct observers all receive the same notification`() {
        val a = mutableListOf<String>()
        val b = mutableListOf<String>()
        val c = mutableListOf<String>()
        subject.subscribe(Observer { a.add(it) })
        subject.subscribe(Observer { b.add(it) })
        subject.subscribe(Observer { c.add(it) })

        subject.notifyObservers("ping")

        assertEquals(listOf("ping"), a)
        assertEquals(listOf("ping"), b)
        assertEquals(listOf("ping"), c)
    }

    @Test
    fun `observers are notified in subscription order`() {
        // LinkedHashSet preserves insertion order; this locks that in as a
        // guarantee rather than an implementation detail that could silently
        // change if the backing collection is ever swapped out.
        val callOrder = mutableListOf<String>()
        subject.subscribe(Observer { callOrder.add("first") })
        subject.subscribe(Observer { callOrder.add("second") })
        subject.subscribe(Observer { callOrder.add("third") })

        subject.notifyObservers("x")

        assertEquals(listOf("first", "second", "third"), callOrder)
    }

    @Test
    fun `repeated notifications each deliver independently to all observers`() {
        val received = mutableListOf<String>()
        subject.subscribe(Observer { received.add(it) })

        subject.notifyObservers("one")
        subject.notifyObservers("two")
        subject.notifyObservers("three")

        assertEquals(listOf("one", "two", "three"), received)
    }

    // --- duplicate subscriptions ---------------------------------------------

    @Test
    fun `subscribing the exact same observer instance twice only notifies it once`() {
        var callCount = 0
        val observer = Observer<String> { callCount++ }

        subject.subscribe(observer)
        subject.subscribe(observer) // duplicate reference

        subject.notifyObservers("x")

        assertEquals(1, callCount, "the backing Set should de-duplicate the same observer reference")
    }

    @Test
    fun `two separate observer instances with identical bodies are treated as distinct`() {
        // Observer is a fun interface (SAM), so each lambda literal creates its
        // own object with reference/identity-based equality -- there's no
        // structural equality that would collapse "equivalent" lambdas together.
        var countA = 0
        var countB = 0
        subject.subscribe(Observer<String> { countA++ })
        subject.subscribe(Observer<String> { countB++ })

        subject.notifyObservers("x")

        assertEquals(1, countA)
        assertEquals(1, countB)
    }

    // --- unsubscribe -----------------------------------------------------

    @Test
    fun `unsubscribing stops further notifications`() {
        val received = mutableListOf<String>()
        val observer = Observer<String> { received.add(it) }
        subject.subscribe(observer)
        subject.notifyObservers("before")

        subject.unsubscribe(observer)
        subject.notifyObservers("after")

        assertEquals(listOf("before"), received)
    }

    @Test
    fun `unsubscribing an observer that was never subscribed is a safe no-op`() {
        val observer = Observer<String> { }

        subject.unsubscribe(observer) // should not throw

        subject.notifyObservers("x") // still fine afterward
    }

    @Test
    fun `unsubscribing twice is a safe no-op the second time`() {
        val received = mutableListOf<String>()
        val observer = Observer<String> { received.add(it) }
        subject.subscribe(observer)

        subject.unsubscribe(observer)
        subject.unsubscribe(observer) // second removal of an already-removed observer

        subject.notifyObservers("x")

        assertTrue(received.isEmpty())
    }

    @Test
    fun `unsubscribing one observer leaves the others intact`() {
        val a = mutableListOf<String>()
        val b = mutableListOf<String>()
        val observerA = Observer<String> { a.add(it) }
        val observerB = Observer<String> { b.add(it) }
        subject.subscribe(observerA)
        subject.subscribe(observerB)

        subject.unsubscribe(observerA)
        subject.notifyObservers("x")

        assertTrue(a.isEmpty())
        assertEquals(listOf("x"), b)
    }

    @Test
    fun `re-subscribing after unsubscribing works normally`() {
        val received = mutableListOf<String>()
        val observer = Observer<String> { received.add(it) }
        subject.subscribe(observer)
        subject.unsubscribe(observer)

        subject.subscribe(observer)
        subject.notifyObservers("x")

        assertEquals(listOf("x"), received)
    }

    // --- generic type parameter sanity ---------------------------------------

    @Test
    fun `works with non-String type parameters too`() {
        val boolSubject = TestSubject<Boolean>()
        val received = mutableListOf<Boolean>()
        boolSubject.subscribe(Observer { received.add(it) })

        boolSubject.notifyObservers(true)
        boolSubject.notifyObservers(false)

        assertEquals(listOf(true, false), received)
    }

    // --- documents current fragility: mutating subscriptions during notify ---

    @Test
    fun `an observer unsubscribing itself during notifyObservers throws ConcurrentModificationException`() {
        // This documents CURRENT behavior rather than asserting it's desirable.
        // notifyObservers iterates the live backing Set directly; if a callback
        // mutates that same Set mid-iteration (even unsubscribing itself), the
        // fail-fast LinkedHashSet iterator throws. A more defensive implementation
        // might iterate over a snapshot (e.g. observers.toList()) instead.
        lateinit var selfUnsubscribingObserver: Observer<String>
        selfUnsubscribingObserver = Observer { subject.unsubscribe(selfUnsubscribingObserver) }
        subject.subscribe(selfUnsubscribingObserver)
        subject.subscribe(Observer { }) // a second observer so the set has >1 entry during iteration

        assertFailsWith<ConcurrentModificationException> {
            subject.notifyObservers("x")
        }
    }
}