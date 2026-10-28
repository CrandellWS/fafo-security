package ws.crandell.fafo.circles

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Circles in credential-protected storage (`filesDir/trust/circles.json`). Home exists from first launch. */
class CircleStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Circle.serializer())
    private val _circles = MutableStateFlow(load())
    val circles: StateFlow<List<Circle>> = _circles

    private fun load(): List<Circle> {
        val stored = if (file.exists()) runCatching { json.decodeFromString(serializer, file.readText()) }.getOrNull() else null
        if (stored != null && stored.isNotEmpty()) return stored
        val initial = listOf(CirclePolicy.home())
        write(initial)
        return initial
    }

    private fun write(list: List<Circle>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(serializer, list))
        tmp.renameTo(file)
    }

    fun all(): List<Circle> = _circles.value
    fun get(id: String): Circle? = _circles.value.firstOrNull { it.id == id }

    @Synchronized
    fun update(transform: (List<Circle>) -> List<Circle>) {
        val n = transform(_circles.value)
        write(n)
        _circles.value = n
    }

    @Synchronized
    fun put(c: Circle) = update { list -> if (list.any { it.id == c.id }) list.map { if (it.id == c.id) c else it } else list + c }
}
