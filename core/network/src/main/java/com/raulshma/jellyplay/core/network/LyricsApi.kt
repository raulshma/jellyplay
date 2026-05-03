package com.raulshma.jellyplay.core.network

import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.LyricsSource
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

internal object LyricsApi {

    fun fetchLyrics(serverAddress: String, itemId: String, accessToken: String): LyricsResult {
        val url = URL("$serverAddress/Items/$itemId/Lyrics?api_key=$accessToken")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        val body = try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }

        val lines = try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val array = json.parseToJsonElement(body).jsonArray
            array.map { element ->
                val obj = element.jsonObject
                LyricsLine(
                    timeMs = obj["start"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                    text = obj["text"]?.jsonPrimitive?.content ?: "",
                )
            }
        } catch (e: Exception) {
            body.lineSequence().mapNotNull { line: String ->
                val match = Regex("""\[(\d{1,2}):(\d{2}\.\d{2,3})](.+)""").find(line.trim())
                match?.let { m ->
                    val min = m.groupValues[1].toLong()
                    val sec = m.groupValues[2].toDouble()
                    val text = m.groupValues[3]
                    LyricsLine(
                        timeMs = min * 60_000 + (sec * 1000).toLong(),
                        text = text,
                    )
                }
            }.toList()
        }

        return LyricsResult(
            lines = lines,
            source = if (lines.isNotEmpty()) LyricsSource.EXTERNAL else LyricsSource.UNKNOWN,
        )
    }
}
