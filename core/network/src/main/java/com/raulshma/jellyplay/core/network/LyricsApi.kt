package com.raulshma.jellyplay.core.network

import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.LyricsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

internal object LyricsApi {

    private val json = JellyfinApiClientImpl.sharedJson
    private val lyricsRegex = Regex("""\[(\d{1,2}):(\d{2}\.\d{2,3})](.+)""")

    private suspend fun executeAndReadBody(client: OkHttpClient, request: Request): String =
        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                response.body?.string() ?: ""
            }
        }

    suspend fun fetchLyrics(
        okHttpClient: OkHttpClient,
        serverAddress: String,
        itemId: String,
        accessToken: String,
    ): LyricsResult {
        val url = "$serverAddress/Items/$itemId/Lyrics?api_key=$accessToken"
        val request = Request.Builder()
            .url(url)
            .build()
        val body = executeAndReadBody(okHttpClient, request)

        val lines = try {
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
                val match = lyricsRegex.find(line.trim())
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
