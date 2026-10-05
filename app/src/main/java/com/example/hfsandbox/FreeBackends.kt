package com.example.hfsandbox

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

enum class Backend(val label: String, val keyHelp: String) {
    HF("Hugging Face (free serverless)", "Free HF token (huggingface.co/settings/tokens), 'Inference Providers' permission"),
    OPENROUTER("OpenRouter (free models)", "Free key from openrouter.ai/keys")
}

class FreeBackends(private val backend: Backend, private val key: String) {
    private val http = OkHttpClient.Builder().readTimeout(120, TimeUnit.SECONDS).build()
    private val json = "application/json".toMediaType()

    private fun get(url: String, auth: Boolean = true): String {
        val b = Request.Builder().url(url)
        if (auth) b.header("Authorization", "Bearer $key")
        http.newCall(b.build()).execute().use {
            val t = it.body!!.string()
            if (!it.isSuccessful) error("HTTP ${it.code}: $t")
            return t
        }
    }

    fun search(query: String): List<String> = when (backend) {
        Backend.HF -> searchHf(query)
        Backend.OPENROUTER -> searchOpenRouter(query)
    }

    private fun searchHf(query: String): List<String> {
        val q = URLEncoder.encode(query, "UTF-8")
        val base = "https://huggingface.co/api/models?search=$q&pipeline_tag=text-generation&sort=downloads&limit=20"
        val ids = runCatching { ids(get("$base&inference_provider=all", false)) }.getOrDefault(emptyList())
        return ids.ifEmpty { ids(get(base, false)) }
    }

    private fun ids(body: String): List<String> {
        val a = JSONArray(body)
        return List(a.length()) { a.getJSONObject(it).getString("id") }
    }

    private fun searchOpenRouter(query: String): List<String> {
        val data = JSONObject(get("https://openrouter.ai/api/v1/models", false)).getJSONArray("data")
        val out = mutableListOf<String>()
        for (i in 0 until data.length()) {
            val m = data.getJSONObject(i)
            val p = m.optJSONObject("pricing")
            val free = m.getString("id").endsWith(":free") ||
                (p?.optString("prompt") == "0" && p.optString("completion") == "0")
            if (free && m.getString("id").contains(query, ignoreCase = true)) out.add(m.getString("id"))
        }
        return out
    }

    fun chat(model: String, history: JSONArray): String {
        val url = when (backend) {
            Backend.HF -> "https://router.huggingface.co/v1/chat/completions"
            Backend.OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions"
        }
        val body = JSONObject().put("model", model).put("messages", history).put("max_tokens", 512)
        val req = Request.Builder().url(url)
            .header("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody(json)).build()
        http.newCall(req).execute().use {
            val t = it.body!!.string()
            if (!it.isSuccessful) error(explain(it.code, t))
            return JSONObject(t).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
        }
    }

    private fun explain(code: Int, body: String) = when (code) {
        401, 403 -> "Key rejected or missing permission ($code). Check your key."
        402 -> "Free credits used up for now. Try another model, the other backend, or wait for reset."
        404, 400 -> "This model isn't served free right now. Pick another. ($code) $body"
        429 -> "Rate limited. Wait a minute and retry."
        else -> "HTTP $code: $body"
    }
}
