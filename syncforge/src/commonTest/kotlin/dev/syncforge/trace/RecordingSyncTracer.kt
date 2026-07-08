package dev.syncforge.trace

data class RecordedSpan(
    val name: SyncSpanName,
    val attributes: MutableMap<String, String>,
    var status: SyncSpanStatus = SyncSpanStatus.OK,
    var exception: Throwable? = null,
)

/** Test double that records started spans. */
class RecordingSyncTracer : SyncTracer {
    override val isEnabled: Boolean = true
    val spans = mutableListOf<RecordedSpan>()

    override fun startSpan(name: SyncSpanName, attributes: Map<String, String>): SyncSpan {
        val recorded = RecordedSpan(name = name, attributes = attributes.toMutableMap())
        spans += recorded
        return object : SyncSpan {
            override fun setAttribute(key: String, value: String) {
                recorded.attributes[key] = value
            }

            override fun setAttribute(key: String, value: Long) {
                setAttribute(key, value.toString())
            }

            override fun recordException(error: Throwable) {
                recorded.exception = error
            }

            override fun end(status: SyncSpanStatus) {
                recorded.status = status
            }
        }
    }

    fun clear() = spans.clear()
}