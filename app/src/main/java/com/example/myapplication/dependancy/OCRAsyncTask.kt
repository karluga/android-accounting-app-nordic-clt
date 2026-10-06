package com.example.myapplication.dependancy

import android.os.AsyncTask
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

/**
 * Async Task to invoke OCR.space API endpoint and parse tabular document data.
 * Reference: https://ocr.space/ocrapi#java
 */
class OCRAsyncTask(
    private val mApiKey: String?,
    private val isOverlayRequired: Boolean,
    private val mImageDataOrUrl: String?,
    private val mLanguage: String?,
    private val mIOCRCallBack: IOCRCallBack
) : AsyncTask<Any?, Any?, String?>() {

    private val apiUrl: String = "https://api.ocr.space/parse/image"

    override fun doInBackground(params: Array<Any?>?): String? {
        try {
            return sendPost(
                apiKey = if (!mApiKey.isNullOrBlank()) mApiKey else com.example.myapplication.BuildConfig.OCR_API_KEY,
                isOverlayRequired = isOverlayRequired,
                imageDataOrUrl = mImageDataOrUrl,
                language = if (!mLanguage.isNullOrBlank()) mLanguage else "eng"
            )
        } catch (e: Exception) {
            Log.e(TAG, "OCR Request failed", e)
            return JSONObject().apply {
                put("IsErroredOnProcessing", true)
                put("ErrorMessage", listOf("Network or parsing error: ${e.localizedMessage}"))
            }.toString()
        }
    }

    @Throws(Exception::class)
    private fun sendPost(
        apiKey: String,
        isOverlayRequired: Boolean,
        imageDataOrUrl: String?,
        language: String
    ): String {
        val urlObj = URL(apiUrl)
        val con = urlObj.openConnection() as HttpsURLConnection

        con.requestMethod = "POST"
        con.setRequestProperty("User-Agent", "Mozilla/5.0")
        con.setRequestProperty("Accept-Language", "en-US,en;q=0.5")

        val postDataParams = JSONObject()
        postDataParams.put("apikey", apiKey)
        postDataParams.put("isOverlayRequired", isOverlayRequired)
        postDataParams.put("language", language)
        
        // Critical parameters for table reading
        postDataParams.put("isTable", true)
        postDataParams.put("OCREngine", "2")
        postDataParams.put("scale", true)

        if (!imageDataOrUrl.isNullOrBlank()) {
            if (imageDataOrUrl.startsWith("http://") || imageDataOrUrl.startsWith("https://")) {
                postDataParams.put("url", imageDataOrUrl)
            } else if (imageDataOrUrl.startsWith("data:")) {
                postDataParams.put("base64Image", imageDataOrUrl)
            } else {
                // Treat as base64 string
                postDataParams.put("base64Image", "data:image/jpeg;base64,$imageDataOrUrl")
            }
        }

        con.doOutput = true
        val wr = DataOutputStream(con.outputStream)
        wr.writeBytes(getPostDataString(postDataParams))
        wr.flush()
        wr.close()

        val responseCode = con.responseCode
        val inputStream = if (responseCode in 200..299) con.inputStream else con.errorStream
        val reader = BufferedReader(InputStreamReader(inputStream))
        val response = StringBuilder()
        var inputLine: String?

        while (reader.readLine().also { inputLine = it } != null) {
            response.append(inputLine)
        }
        reader.close()

        return response.toString()
    }

    override fun onPostExecute(result: String?) {
        super.onPostExecute(result)
        Log.d(TAG, "OCR API Result: $result")
        mIOCRCallBack.getOCRCallBackResult(result)
    }

    @Throws(Exception::class)
    private fun getPostDataString(params: JSONObject): String {
        val result = StringBuilder()
        var first = true

        val itr = params.keys()
        while (itr.hasNext()) {
            val key = itr.next()
            val value = params.get(key)

            if (first) first = false
            else result.append("&")

            result.append(URLEncoder.encode(key, "UTF-8"))
            result.append("=")
            result.append(URLEncoder.encode(value.toString(), "UTF-8"))
        }
        return result.toString()
    }

    companion object {
        private const val TAG: String = "OCRAsyncTask"
    }
}
