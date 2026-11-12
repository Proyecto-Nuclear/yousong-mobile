package cue.edu.co.yousong_movile

import org.junit.Assert.*
import org.junit.Test

class EntryFlowTest {
    @Test fun rapidTapsCannotStartDuplicateRequests() {
        val flow = EntryFlow()
        val first = requireNotNull(flow.begin())
        assertNull(flow.begin())
        assertTrue(flow.owns(first))
    }

    @Test fun staleCompletionCannotUnlockNewScanAfterLifecycleReset() {
        val flow = EntryFlow()
        val stale = requireNotNull(flow.begin())
        flow.reset()
        assertFalse(flow.owns(stale))
        val current = requireNotNull(flow.begin())
        assertFalse(flow.finish(stale))
        assertTrue(flow.owns(current))
        assertNull(flow.begin())
    }

    @Test fun completedRequestAllowsExplicitNextAction() {
        val flow = EntryFlow()
        val lookup = requireNotNull(flow.begin())
        assertTrue(flow.finish(lookup))
        assertFalse(flow.owns(lookup))
        val join = requireNotNull(flow.begin())
        assertTrue(flow.owns(join))
        assertTrue(flow.finish(join))
    }
}
