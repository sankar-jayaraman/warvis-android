package com.warvis.android.taskratchet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

internal class TaskRatchetClient {
    suspend fun validate(token: String): TaskRatchetAccountResult = withContext(Dispatchers.IO) {
        if (!TaskRatchetConfigurationPolicy.isTokenCandidateAllowed(token)) {
            return@withContext TaskRatchetAccountResult.Failure(
                TaskRatchetFailureKind.Authentication,
            )
        }
        execute(
            path = "/api2/me",
            method = "GET",
            token = token,
            body = null,
        ) { statusCode, responseBody ->
            val account = if (statusCode == HttpURLConnection.HTTP_OK) {
                parseAccount(responseBody)
            } else {
                null
            }
            TaskRatchetResultMapper.account(statusCode, account)
        }
    }

    suspend fun charge(
        token: String,
        requestId: String,
        note: String,
        dryRun: Boolean,
    ): TaskRatchetChargeResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("cents", TASK_RATCHET_CHARGE_CENTS)
            .put("note", note.take(MAX_NOTE_LENGTH))
            .put("requestid", requestId.take(MAX_REQUEST_ID_LENGTH))
            .put("dryrun", dryRun)
            .toString()

        execute(
            path = "/api2/me/charges",
            method = "POST",
            token = token,
            body = body,
        ) { statusCode, responseBody ->
            val response = if (statusCode == HttpURLConnection.HTTP_OK) {
                parseCharge(responseBody)
            } else {
                null
            }
            TaskRatchetResultMapper.charge(
                statusCode = statusCode,
                chargeId = response?.id,
                remoteStatus = response?.status,
                remoteCents = response?.cents,
                dryRun = dryRun,
            )
        }
    }

    private fun <T> execute(
        path: String,
        method: String,
        token: String,
        body: String?,
        map: (Int, String) -> T,
    ): T {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(BASE_URL + path)
            check(url.protocol == "https")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                instanceFollowRedirects = false
                setRequestProperty("Authorization", "ApiKey-v2 $token")
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
                }
            }
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                val result = StringBuilder()
                val buffer = CharArray(4_096)
                while (result.length <= MAX_RESPONSE_LENGTH) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    result.append(buffer, 0, count)
                }
                if (result.length > MAX_RESPONSE_LENGTH) {
                    throw IllegalStateException("TaskRatchet response exceeded limit")
                }
                result.toString()
            }.orEmpty()
            map(statusCode, responseBody)
        } catch (_: SocketTimeoutException) {
            @Suppress("UNCHECKED_CAST")
            networkFailureFor(path) as T
        } catch (_: IOException) {
            @Suppress("UNCHECKED_CAST")
            networkFailureFor(path) as T
        } catch (_: RuntimeException) {
            @Suppress("UNCHECKED_CAST")
            malformedFailureFor(path) as T
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseAccount(body: String): TaskRatchetAccount? {
        return try {
            val json = JSONObject(body)
            if (!json.has("has_stripe_customer")) return null
            TaskRatchetAccount(
                userId = json.getString("id").takeIf { it.isNotBlank() } ?: return null,
                hasPaymentMethod = json.getBoolean("has_stripe_customer"),
                monthlyChargeLimitCents = if (
                    json.has("monthly_charge_limit_cents") &&
                    !json.isNull("monthly_charge_limit_cents")
                ) {
                    json.getInt("monthly_charge_limit_cents")
                } else {
                    null
                },
            )
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun parseCharge(body: String): ParsedCharge? = try {
        val json = JSONObject(body)
        ParsedCharge(
            id = json.getString("id"),
            status = json.getString("status"),
            cents = json.getInt("cents"),
        )
    } catch (_: RuntimeException) {
        null
    }

    private fun networkFailureFor(path: String): Any =
        if (path.endsWith("/charges")) {
            TaskRatchetChargeResult.Failure(TaskRatchetResultMapper.networkFailure())
        } else {
            TaskRatchetAccountResult.Failure(TaskRatchetResultMapper.networkFailure())
        }

    private fun malformedFailureFor(path: String): Any =
        if (path.endsWith("/charges")) {
            TaskRatchetChargeResult.Failure(TaskRatchetFailureKind.MalformedResponse)
        } else {
            TaskRatchetAccountResult.Failure(TaskRatchetFailureKind.MalformedResponse)
        }

    private companion object {
        const val BASE_URL = "https://api.taskratchet.com"
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 15_000
        const val MAX_RESPONSE_LENGTH = 65_536
        const val MAX_NOTE_LENGTH = 160
        const val MAX_REQUEST_ID_LENGTH = 255
    }

    private data class ParsedCharge(
        val id: String,
        val status: String,
        val cents: Int,
    )
}
