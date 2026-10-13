import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

@Serializable
data class State(val type: String)

fun main() {
    val jsonString = "{\"type\":\"state\"}" + " ".repeat(4096)
    println(Json { ignoreUnknownKeys = true }.decodeFromString<State>(jsonString))
}
