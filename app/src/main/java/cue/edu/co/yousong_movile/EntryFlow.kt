package cue.edu.co.yousong_movile

/** Main-thread request ownership, independent of canceled network I/O completion. */
internal class EntryFlow {
    private var generation = 0L
    private var pending: Long? = null

    fun begin(): Long? {
        if (pending != null) return null
        val ticket = ++generation
        pending = ticket
        return ticket
    }

    fun owns(ticket: Long): Boolean = pending == ticket

    fun finish(ticket: Long): Boolean {
        if (!owns(ticket)) return false
        pending = null
        return true
    }

    fun reset() {
        pending = null
    }
}
