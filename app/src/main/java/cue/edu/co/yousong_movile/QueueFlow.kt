package cue.edu.co.yousong_movile

/** Main-thread ownership; a mutation is never repeated before authoritative reconciliation. */
internal class QueueFlow {
    private val requests = EntryFlow()
    private var active = true
    private var reconciled = false
    var snapshot: QueueSnapshot? = null
        private set

    fun accept(value: QueueSnapshot): Boolean {
        if (!active || value.version < (snapshot?.version ?: -1)) return false
        snapshot = value
        reconciled = true
        return true
    }

    fun beginMutation(): Long? {
        if (!active || !reconciled || snapshot?.state != ServiceState.ABIERTO) return null
        return requests.begin()?.also { reconciled = false }
    }

    fun owns(ticket: Long): Boolean = active && requests.owns(ticket)
    fun finishMutation(ticket: Long) {
        if (requests.finish(ticket)) reconciled = false
    }
    fun invalidate() { active = false; reconciled = false; requests.reset(); snapshot = null }
}

internal fun canVote(state: ServiceState?, voted: Boolean?, pending: Boolean): Boolean =
    state == ServiceState.ABIERTO && voted == false && !pending
