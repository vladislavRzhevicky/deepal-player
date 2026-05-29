package com.deepal.videocast

import org.json.JSONArray
import org.json.JSONObject

/**
 * Wire-протокол между user-инстансами приложения.
 *
 * Транспорт: abstract Unix socket "videocast.user<id>" (LocalSocket /
 * LocalServerSocket с Namespace.ABSTRACT). Abstract namespace — глобальный
 * на устройстве, не зависит от ФС → виден между user'ами.
 *
 * Каждая команда — одна строка JSON + '\n'. Ответ тоже строка JSON + '\n'.
 * Никакого длинно-живущего state'а, каждый коннект — одна операция.
 */
object CastProtocol {

    const val SOCKET_PREFIX = "videocast.user"

    /** Минимальный таймаут на коннект — сервиса либо нет, либо ответ < 100ms. */
    const val CONNECT_TIMEOUT_MS = 150
    const val READ_TIMEOUT_MS = 500

    fun socketName(userId: Int) = "$SOCKET_PREFIX$userId"

    // --- requests ---------------------------------------------------------

    fun pingRequest(): String = JSONObject().apply {
        put("op", "PING")
    }.toString()

    fun castRequest(path: String, displayId: Int): String = JSONObject().apply {
        put("op", "CAST")
        put("path", path)
        put("displayId", displayId)
    }.toString()

    // --- responses --------------------------------------------------------

    fun pongResponse(userId: Int, displays: List<DisplayInfo>): String =
        JSONObject().apply {
            put("ok", true)
            put("userId", userId)
            put("displays", JSONArray().apply {
                displays.forEach { d ->
                    put(JSONObject().apply {
                        put("id", d.id)
                        put("name", d.name)
                        put("state", d.state)
                        put("w", d.widthPx)
                        put("h", d.heightPx)
                        put("dpi", d.densityDpi)
                    })
                }
            })
        }.toString()

    fun okResponse(): String = JSONObject().apply { put("ok", true) }.toString()
    fun errResponse(msg: String): String = JSONObject().apply {
        put("ok", false); put("err", msg)
    }.toString()

    // --- parse ------------------------------------------------------------

    data class Request(val op: String, val raw: JSONObject) {
        fun path(): String = raw.getString("path")
        fun displayId(): Int = raw.getInt("displayId")
    }

    fun parseRequest(line: String): Request {
        val j = JSONObject(line)
        return Request(j.getString("op"), j)
    }

    data class Pong(val userId: Int, val displays: List<DisplayInfo>)

    fun parsePong(line: String): Pong {
        val j = JSONObject(line)
        if (!j.optBoolean("ok", false)) error("not ok: $line")
        val arr = j.getJSONArray("displays")
        val displays = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            DisplayInfo(
                id = o.getInt("id"),
                name = o.getString("name"),
                state = o.optInt("state", 0),
                widthPx = o.optInt("w", 0),
                heightPx = o.optInt("h", 0),
                densityDpi = o.optInt("dpi", 0),
            )
        }
        return Pong(j.getInt("userId"), displays)
    }

    data class DisplayInfo(
        val id: Int,
        val name: String,
        val state: Int,
        val widthPx: Int = 0,
        val heightPx: Int = 0,
        val densityDpi: Int = 0,
    )
}
