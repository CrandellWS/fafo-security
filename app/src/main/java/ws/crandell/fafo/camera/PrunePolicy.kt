package ws.crandell.fafo.camera

import kotlin.math.abs

data class PhotoInfo(
    val incidentId: String,
    val name: String,
    val sizeBytes: Long,
    val takenAt: Long,
    val bigChange: Boolean = false,
)

data class IncidentInfo(
    val id: String,
    val startedAt: Long,
    val closed: Boolean,
    val photos: List<PhotoInfo>,
)

data class PrunePlan(val deletes: List<PhotoInfo>, val fits: Boolean)

/**
 * Spec section 5 photo cap. Never deletes the protected set (first, latest, and up to 20 "big change"
 * frames per incident). Prunes the oldest unprotected photos of the oldest closed incident first, then
 * thins the current incident by deleting every other unprotected frame, oldest first.
 */
object PrunePolicy {
    const val MAX_BIG_CHANGE = 20

    fun protectedNames(incident: IncidentInfo): Set<String> {
        val sorted = incident.photos.sortedBy { it.takenAt }
        if (sorted.isEmpty()) return emptySet()
        val out = mutableSetOf(sorted.first().name, sorted.last().name)
        sorted.filter { it.bigChange }.take(MAX_BIG_CHANGE).forEach { out += it.name }
        return out
    }

    fun plan(incidents: List<IncidentInfo>, capBytes: Long, incomingBytes: Long): PrunePlan {
        var need = incidents.sumOf { inc -> inc.photos.sumOf { it.sizeBytes } } + incomingBytes - capBytes
        if (need <= 0) return PrunePlan(emptyList(), true)
        val deletes = mutableListOf<PhotoInfo>()

        // 1. Closed incidents, oldest first, their oldest unprotected photos first.
        for (inc in incidents.filter { it.closed }.sortedBy { it.startedAt }) {
            val keep = protectedNames(inc)
            for (p in inc.photos.sortedBy { it.takenAt }.filter { it.name !in keep }) {
                if (need <= 0) break
                deletes += p
                need -= p.sizeBytes
            }
            if (need <= 0) return PrunePlan(deletes, true)
        }

        // 2. Open (current) incidents: thin every other unprotected frame, repeatedly.
        for (inc in incidents.filter { !it.closed }.sortedBy { it.startedAt }) {
            val keep = protectedNames(inc)
            var candidates = inc.photos.sortedBy { it.takenAt }.filter { it.name !in keep }
            while (need > 0 && candidates.isNotEmpty()) {
                val round = candidates.filterIndexed { i, _ -> i % 2 == 0 }
                for (p in round) {
                    if (need <= 0) break
                    deletes += p
                    need -= p.sizeBytes
                }
                candidates = candidates - deletes.toSet()
            }
            if (need <= 0) return PrunePlan(deletes, true)
        }
        return PrunePlan(deletes, need <= 0)
    }
}

/** 32x32 grayscale thumbnail comparison for "big change" frames. */
object BigChange {
    const val SIZE = 32
    /** Mean absolute grayscale difference (0..255) above which a frame counts as a big change. */
    const val THRESHOLD = 25.0

    fun diff(a: IntArray, b: IntArray): Double {
        require(a.size == b.size && a.isNotEmpty())
        var sum = 0L
        for (i in a.indices) sum += abs(a[i] - b[i])
        return sum.toDouble() / a.size
    }

    fun isBigChange(previousKept: IntArray?, current: IntArray?): Boolean =
        previousKept != null && current != null && diff(previousKept, current) > THRESHOLD
}
