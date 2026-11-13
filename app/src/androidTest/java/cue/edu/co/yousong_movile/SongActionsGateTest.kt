package cue.edu.co.yousong_movile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch

/** Drives the real coordinator through two polls that return the same queue version. */
@RunWith(AndroidJUnit4::class)
class SongActionsGateTest {
    private lateinit var backend: FakeBackend
    private lateinit var scope: CoroutineScope
    private lateinit var actions: SongActions
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun <T> main(block: () -> T): T { var result: Any? = null; instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST") return result as T }

    private fun waitFor(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!main(condition)) {
            assertTrue("Timed out waiting: $message", System.currentTimeMillis() < deadline)
            Thread.sleep(50)
        }
    }

    @Before fun setUp() {
        backend = FakeBackend()
        main {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            actions = SongActions(ParticipantApi(FakeBackend.BASE), FakeBackend.session(), scope) {}
            scope.launch { actions.poll() }
        }
    }

    @After fun tearDown() { main { actions.dispose(); scope.cancel() }; backend.close() }

    @Test fun aTapDuringAPollIsRefusedWithoutSendingAndTheNextSettledPollAcceptsIt() {
        waitFor("first poll") { actions.ready && actions.canSupport(false) }
        val firstVersion = main { actions.snapshot?.version }

        // Second poll with the queue read held open: the gate must refuse and send nothing.
        val hold = CountDownLatch(1)
        backend.holdQueue = hold
        main { actions.refresh() }
        waitFor("second poll in flight") { !actions.ready }
        assertFalse(main { actions.vote(FakeBackend.REQUEST_ID, voted = false) })
        assertFalse(main { actions.pending })
        assertEquals(0, backend.count("POST"))

        backend.holdQueue = null
        hold.countDown()
        waitFor("second poll settled") { actions.ready && actions.canSupport(false) }
        assertEquals(firstVersion, main { actions.snapshot?.version })

        assertTrue(main { actions.vote(FakeBackend.REQUEST_ID, voted = false) })
        waitFor("vote outcome") { actions.outcome != null && !actions.pending }
        val outcome = main { actions.outcome!! }
        assertEquals(true, outcome.result?.voted)
        assertEquals(false, outcome.result?.created)
        assertEquals(1, backend.count("POST /api/participante/solicitudes/${FakeBackend.REQUEST_ID}/votos"))
    }

    @Test fun aSecondTapWhileTheFirstIsPendingIsRefused() {
        waitFor("first poll") { actions.ready && actions.canSupport(false) }
        val hold = CountDownLatch(1)
        backend.holdQueue = hold // the reconcile after the vote stays open
        assertTrue(main { actions.vote(FakeBackend.REQUEST_ID, voted = false) })
        assertFalse(main { actions.vote(FakeBackend.REQUEST_ID, voted = false) })
        backend.holdQueue = null
        hold.countDown()
        waitFor("reconciled") { !actions.pending }
        assertEquals(1, backend.count("POST"))
    }
}
