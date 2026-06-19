package com.example.system

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object OSGeminiClient {

    private const val TAG = "OSGeminiClient"
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Determines if a real Gemini API Key is available on the client workspace or configuration.
     */
    fun isRealKeyConfigured(): Boolean {
        val apiKey = BuildConfig.GEMINI_API_KEY
        return apiKey.isNotEmpty() && 
               apiKey != "MY_GEMINI_API_KEY" && 
               apiKey != "placeholder" && 
               !apiKey.startsWith("YOUR_")
    }

    /**
     * Queries the modern gemini-3.5-flash endpoint using raw JSON parsing to prevent library serialization mismatches.
     * Respects Android threading rules by operating on Dispatchers.IO.
     */
    suspend fun queryGemini(prompt: String, systemInstruction: String? = null): String = withContext(Dispatchers.IO) {
        if (!isRealKeyConfigured()) {
            return@withContext "SIMULATION_MODE"
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        try {
            // Build raw JSON manually for perfect parsing robustly
            val rootObj = JSONObject()
            
            // Contents payload
            val contentsArr = JSONArray()
            val contentObj = JSONObject()
            val partsArr = JSONArray()
            val partObj = JSONObject()
            partObj.put("text", prompt)
            partsArr.put(partObj)
            contentObj.put("parts", partsArr)
            contentsArr.put(contentObj)
            rootObj.put("contents", contentsArr)

            // Optional System instruction
            if (systemInstruction != null) {
                val sysInstObj = JSONObject()
                val sysPartsArr = JSONArray()
                val sysPartObj = JSONObject()
                sysPartObj.put("text", systemInstruction)
                sysPartsArr.put(sysPartObj)
                sysInstObj.put("parts", sysPartsArr)
                rootObj.put("systemInstruction", sysInstObj)
            }

            // Adjust generation config
            val genConfigObj = JSONObject()
            genConfigObj.put("temperature", 0.7)
            rootObj.put("generationConfig", genConfigObj)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = rootObj.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Unsuccessful response: Code ${response.code}, Body: $bodyStr")
                    return@withContext "Error: Received HTTP ${response.code} from Gemini gateway."
                }

                // Extract text from standard Gemini JSON response structure
                val jsonResponse = JSONObject(bodyStr)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.optJSONObject("content")
                    if (content != null) {
                        val parts = content.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            return@withContext parts.getJSONObject(0).optString("text", "No readable text stream.")
                        }
                    }
                }
                return@withContext "Response processed, but no content blocks were resolved."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini network stack failure: ${e.message}", e)
            return@withContext "Connection Refused: Unable to reach Gemini core neural synchronizer. Details: ${e.localizedMessage}"
        }
    }
}
