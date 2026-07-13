package com.santi.dbdmeta

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

class DbdHttpClient(
    private val baseUrl: String = "https://dbd.tricky.lol/"
) {
    suspend fun getJson(path: String): AppResult<JsonElement> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(baseUrl + path.removePrefix("/"))
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 18_000
                setRequestProperty("Accept", "application/json")
            }

            try {
                val status = connection.responseCode
                val stream = if (status in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val body = stream.bufferedReader().use { it.readText() }
                if (status !in 200..299) {
                    error("HTTP $status: $body")
                }
                Json.parseToJsonElement(body)
            } finally {
                connection.disconnect()
            }
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Error("No se pudo consultar dbd.tricky.lol", it) }
        )
    }
}
