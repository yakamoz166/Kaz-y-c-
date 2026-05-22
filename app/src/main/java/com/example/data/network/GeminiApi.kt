package com.example.data.network

import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class Part(val text: String?)

@JsonClass(generateAdapter = true)
data class Content(val parts: List<Part>)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class PartResponse(val text: String?)

@JsonClass(generateAdapter = true)
data class ContentResponse(val parts: List<PartResponse>?)

@JsonClass(generateAdapter = true)
data class Candidate(val content: ContentResponse?)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(val candidates: List<Candidate>?)

object GeminiApiClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generateContent(prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Error: Gemini API Anahtarı eksik veya geçersiz. Lütfen Secrets panelinden ayarlayın."
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val requestBodyData = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            )
        )

        val adapter = moshi.adapter(GenerateContentRequest::class.java)
        val jsonString = adapter.toJson(requestBodyData)

        val request = Request.Builder()
            .url(url)
            .post(jsonString.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    return@withContext "Error: API hatası codes: ${response.code}. Detay: $errorBody"
                }
                val responseBodyStr = response.body?.string() ?: ""
                val responseAdapter = moshi.adapter(GenerateContentResponse::class.java)
                val responseObj = responseAdapter.fromJson(responseBodyStr)
                val extractedText = responseObj?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                extractedText ?: "Error: Gemini'den boş yanıt geldi veya yanıt yapısı çözümlenemedi."
            }
        } catch (e: Exception) {
            "Error: Bağlantı hatası: ${e.message}"
        }
    }
}
