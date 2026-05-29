package com.deepal.videocast

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Тонкий клиент к чужому user-инстансу. Pure-IO, не привязан к Context.
 *
 * Все методы блокирующие, вызывать из background-потока.
 */
object CastClient {

    private const val TAG = "VideoCast.Client"

    /**
     * Прощупывает диапазон возможных user-id. На S09 driver обычно 10 или 13,
     * passenger — соседний номер. Диапазон 0..30 покрывает realistic layout.
     */
    private val USER_CANDIDATES: IntRange = 0..30

    /**
     * Пингует все возможные user'ы, возвращает только ответившие.
     */
    fun discoverUsers(): List<CastProtocol.Pong> {
        val out = mutableListOf<CastProtocol.Pong>()
        for (uid in USER_CANDIDATES) {
            val pong = pingOne(uid) ?: continue
            out += pong
        }
        return out
    }

    fun pingOne(userId: Int): CastProtocol.Pong? = withConnection(userId) { sock ->
        sock.outputStream.write((CastProtocol.pingRequest() + "\n").toByteArray())
        sock.outputStream.flush()
        val line = BufferedReader(InputStreamReader(sock.inputStream)).readLine()
            ?: return@withConnection null
        runCatching { CastProtocol.parsePong(line) }.getOrNull()
    }

    /**
     * Шлёт CAST-команду в указанный user. Возвращает null при успехе или
     * текст ошибки.
     */
    fun cast(userId: Int, path: String, displayId: Int): String? =
        withConnection(userId) { sock ->
            val req = CastProtocol.castRequest(path, displayId) + "\n"
            sock.outputStream.write(req.toByteArray())
            sock.outputStream.flush()
            val line = BufferedReader(InputStreamReader(sock.inputStream)).readLine()
                ?: return@withConnection "no response"
            val j = org.json.JSONObject(line)
            if (j.optBoolean("ok", false)) null else j.optString("err", "unknown error")
        } ?: "connection failed (user $userId offline?)"

    private inline fun <T> withConnection(
        userId: Int,
        block: (LocalSocket) -> T?
    ): T? {
        val sock = LocalSocket()
        return try {
            sock.connect(
                LocalSocketAddress(
                    CastProtocol.socketName(userId),
                    LocalSocketAddress.Namespace.ABSTRACT
                )
            )
            sock.soTimeout = CastProtocol.READ_TIMEOUT_MS
            block(sock)
        } catch (e: Exception) {
            // Молча — для discovery большинство user'ов offline это норма.
            null
        } finally {
            runCatching { sock.close() }
        }
    }
}
