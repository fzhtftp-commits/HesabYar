package ir.hesabyar.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class SupabaseSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String
)

object SupabaseApi {
    private const val BASE_URL = "https://cwuncmdfxvanflbunlxx.supabase.co"
    private const val API_KEY = "sb_publishable_mGd-RSSQeGsCMLrsFj5H5g_1xRESlGw"

    private fun errorMessage(code: Int, response: String, fallback: String): String {
        val detail = runCatching {
            val json = JSONObject(response)
            listOf(json.optString("message"), json.optString("msg"), json.optString("error_description"), json.optString("error"), json.optString("details"), json.optString("hint")).firstOrNull { it.isNotBlank() }
        }.getOrNull()
        return if (!detail.isNullOrBlank()) "Supabase HTTP $code: ${detail.take(240)}" else "Supabase HTTP $code: ${response.take(240).ifBlank { fallback }}"
    }

    private fun request(
        method: String,
        url: String,
        accessToken: String? = null,
        body: String? = null,
        prefer: String? = null
    ): Pair<Int, String> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("apikey", API_KEY)
            setRequestProperty("Content-Type", "application/json")
            if (accessToken != null) setRequestProperty("Authorization", "Bearer $accessToken")
            if (prefer != null) setRequestProperty("Prefer", prefer)
            doInput = true
            if (body != null) doOutput = true
        }
        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { reader -> reader.readText() }
            } ?: ""
            return code to text
        } finally {
            connection.disconnect()
        }
    }

    fun signIn(email: String, password: String): SupabaseSession {
        val body = JSONObject().apply {
            put("email", email)
            put("password", password)
        }.toString()
        val (code, response) = request(
            "POST",
            "$BASE_URL/auth/v1/token?grant_type=password",
            body = body
        )
        if (code !in 200..299) {
            val message = runCatching { JSONObject(response).optString("msg").ifBlank { JSONObject(response).optString("message") } }
                .getOrDefault("")
            throw IllegalStateException(errorMessage(code, response, "ورود ناموفق بود."))
        }
        val json = JSONObject(response)
        return SupabaseSession(
            json.getString("access_token"),
            json.getString("refresh_token"),
            json.getJSONObject("user").getString("id")
        )
    }

    fun signUp(email: String, password: String): Boolean {
        val body = JSONObject().apply {
            put("email", email)
            put("password", password)
        }.toString()
        val (code, response) = try {
            request("POST", "$BASE_URL/auth/v1/signup", body = body)
        } catch (e: Exception) {
            throw IllegalStateException("شبکه: ${e.message ?: "اتصال به سرور برقرار نشد."}")
        }
        if (code !in 200..299) {
            throw IllegalStateException(errorMessage(code, response, "ثبت‌نام ناموفق بود."))
        }
        return true
    }

    fun refresh(refreshToken: String): SupabaseSession {
        val body = JSONObject().put("refresh_token", refreshToken).toString()
        val (code, response) = request(
            "POST",
            "$BASE_URL/auth/v1/token?grant_type=refresh_token",
            body = body
        )
        if (code !in 200..299) throw IllegalStateException(errorMessage(code, response, "جلسه ورود منقضی شده است."))
        val json = JSONObject(response)
        return SupabaseSession(
            json.getString("access_token"),
            json.optString("refresh_token", refreshToken),
            json.getJSONObject("user").getString("id")
        )
    }

    fun getTransactions(accessToken: String, userId: String): List<RemoteTransaction> {
        val encodedUserId = java.net.URLEncoder.encode(userId, "UTF-8")
        val url = "$BASE_URL/rest/v1/transactions?select=id,title,amount,type,description,transaction_date&user_id=eq.$encodedUserId&order=created_at.desc"
        val (code, response) = request("GET", url, accessToken)
        if (code !in 200..299) throw IllegalStateException(errorMessage(code, response, "دریافت اطلاعات حسابداری ناموفق بود."))
        val arr = JSONArray(response)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            RemoteTransaction(
                id = o.getLong("id"),
                title = o.getString("title"),
                amount = o.getLong("amount"),
                income = o.getString("type") == "income",
                category = o.optString("description", "عمومی").ifBlank { "عمومی" },
                date = o.optString("transaction_date", "امروز")
            )
        }
    }

    fun insertTransaction(accessToken: String, transaction: Transaction, userId: String): Long {
        val body = JSONObject().apply {
            put("user_id", userId)
            put("type", if (transaction.income) "income" else "expense")
            put("amount", transaction.amount)
            put("title", transaction.title)
            put("description", transaction.category)
            put("transaction_date", transaction.date.take(10).ifBlank { "1970-01-01" })
        }.toString()
        val (code, response) = request(
            "POST",
            "$BASE_URL/rest/v1/transactions",
            accessToken,
            body,
            "return=representation"
        )
        if (code !in 200..299) throw IllegalStateException(errorMessage(code, response, "ذخیره ابری تراکنش ناموفق بود."))
        return JSONArray(response).getJSONObject(0).getLong("id")
    }

    fun updateTransaction(accessToken: String, transactionId: Long, transaction: Transaction) {
        val body = JSONObject().apply {
            put("type", if (transaction.income) "income" else "expense")
            put("amount", transaction.amount)
            put("title", transaction.title)
            put("description", transaction.category)
            put("transaction_date", transaction.date.take(10))
        }.toString()
        val (code, response) = request(
            "PATCH",
            "$BASE_URL/rest/v1/transactions?id=eq.$transactionId",
            accessToken,
            body,
            "return=minimal"
        )
        if (code !in 200..299) throw IllegalStateException(errorMessage(code, response, "ویرایش ابری تراکنش ناموفق بود."))
    }

    fun deleteTransaction(accessToken: String, transactionId: Long) {
        val (code, response) = request(
            "DELETE",
            "$BASE_URL/rest/v1/transactions?id=eq.$transactionId",
            accessToken
        )
        if (code !in 200..299) throw IllegalStateException(errorMessage(code, response, "حذف ابری تراکنش ناموفق بود."))
    }

    fun deleteAllTransactions(accessToken: String) {
        val (code, response) = request(
            "DELETE",
            "$BASE_URL/rest/v1/transactions?id=gt.0",
            accessToken
        )
        if (code !in 200..299) throw IllegalStateException(errorMessage(code, response, "پاک‌سازی ابری ناموفق بود."))
    }
}

data class RemoteTransaction(
    val id: Long,
    val title: String,
    val amount: Long,
    val income: Boolean,
    val category: String,
    val date: String
)