package com.deepal.videocast

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import kotlin.concurrent.thread

/**
 * Foreground service в каждом user-инстансе приложения. Поднимает
 * abstract Unix socket "videocast.user<myUserId>" и слушает:
 *   - PING → отдаёт свой userId + список своих displays
 *   - CAST → стартует PlayerActivity на указанном displayId в своём user-контексте
 *
 * Запускается:
 *   - BootReceiver на BOOT_COMPLETED / USER_UNLOCKED / MY_PACKAGE_REPLACED
 *   - MainActivity при открытии (на случай если receiver не сработал)
 */
class CastListenerService : Service() {

    private val tag = "VideoCast.Listener"
    private var serverSocket: LocalServerSocket? = null
    private var acceptThread: Thread? = null
    @Volatile private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        if (!running) {
            running = true
            startAcceptLoop()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        runCatching { serverSocket?.close() }
        super.onDestroy()
    }

    // --- FG plumbing ------------------------------------------------------

    private fun startInForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Video Cast listener",
                    NotificationManager.IMPORTANCE_MIN
                )
            )
        }
        val notif = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Video Cast")
            .setContentText("user ${Process.myUserHandle().hashCode()} — listener")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIF_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    // --- accept loop ------------------------------------------------------

    private fun startAcceptLoop() {
        acceptThread = thread(name = "VideoCast-accept", isDaemon = true) {
            val userId = currentUserId()
            val name = CastProtocol.socketName(userId)

            try {
                // На случай если предыдущий процесс не отпустил — abstract сокеты
                // удерживаются процессом до его смерти, новый bind вернёт IOException.
                // Делаем 3 попытки с паузой.
                var attempt = 0
                while (running && serverSocket == null && attempt < 3) {
                    try {
                        serverSocket = LocalServerSocket(name)
                    } catch (e: Exception) {
                        Log.w(tag, "bind $name attempt $attempt failed: ${e.message}")
                        attempt++
                        Thread.sleep(300)
                    }
                }
                val s = serverSocket ?: run {
                    Log.e(tag, "could not bind $name, giving up")
                    return@thread
                }
                Log.i(tag, "listening on abstract:$name (user=$userId)")

                while (running) {
                    val client = try {
                        s.accept()
                    } catch (e: Exception) {
                        if (running) Log.w(tag, "accept: ${e.message}")
                        break
                    }
                    handleClient(client, userId)
                }
            } catch (e: Exception) {
                Log.e(tag, "accept loop crashed", e)
            }
        }
    }

    private fun handleClient(client: LocalSocket, userId: Int) {
        try {
            client.soTimeout = CastProtocol.READ_TIMEOUT_MS
            val reader = BufferedReader(InputStreamReader(client.inputStream))
            val out: OutputStream = client.outputStream
            val line = reader.readLine() ?: return
            val req = CastProtocol.parseRequest(line)
            when (req.op) {
                "PING" -> {
                    val displays = collectDisplays()
                    write(out, CastProtocol.pongResponse(userId, displays))
                }
                "CAST" -> {
                    val path = req.path()
                    val displayId = req.displayId()
                    try {
                        launchPlayer(path, displayId)
                        write(out, CastProtocol.okResponse())
                    } catch (e: Exception) {
                        Log.w(tag, "cast failed", e)
                        write(out, CastProtocol.errResponse(e.message ?: "?"))
                    }
                }
                else -> write(out, CastProtocol.errResponse("unknown op ${req.op}"))
            }
        } catch (e: Exception) {
            Log.w(tag, "client error: ${e.message}")
        } finally {
            runCatching { client.close() }
        }
    }

    private fun write(out: OutputStream, line: String) {
        out.write(line.toByteArray())
        out.write("\n".toByteArray())
        out.flush()
    }

    // --- ops --------------------------------------------------------------

    private fun collectDisplays(): List<CastProtocol.DisplayInfo> {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return dm.displays.map { d ->
            val metrics = android.util.DisplayMetrics()
            // getRealMetrics включает status/nav-bar — нам нужен полный размер
            // панели, не usable-area.
            d.getRealMetrics(metrics)
            CastProtocol.DisplayInfo(
                id = d.displayId,
                name = d.name ?: "",
                state = d.state,
                widthPx = metrics.widthPixels,
                heightPx = metrics.heightPixels,
                densityDpi = metrics.densityDpi,
            )
        }
    }

    private fun launchPlayer(path: String, displayId: Int) {
        // PlayerActivity должна стартануть в нашем (получателя) user-контексте,
        // чтобы launchDisplayId сработал. Делаем это из main looper, иначе
        // некоторые сборки AOSP плюются "calling startActivity outside of an
        // Activity context requires FLAG_ACTIVITY_NEW_TASK" даже когда флаг
        // выставлен — баг framework'а решается postом в main.
        Handler(Looper.getMainLooper()).post {
            val intent = Intent(this, PlayerActivity::class.java).apply {
                data = Uri.parse("file://$path")
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            val options = ActivityOptions.makeBasic().apply {
                launchDisplayId = displayId
            }
            startActivity(intent, options.toBundle())
        }
    }

    private fun currentUserId(): Int {
        // UserHandle.myUserId() — hidden public. Достаём reflection'ом,
        // на S09 frameworks их не урезали.
        return runCatching {
            val cls = Class.forName("android.os.UserHandle")
            (cls.getMethod("myUserId").invoke(null) as Int)
        }.getOrElse {
            // fallback: парсим uid → uid / 100000
            Process.myUid() / 100000
        }
    }

    companion object {
        const val CHANNEL_ID = "videocast.listener"
        const val NOTIF_ID = 0x7C57

        fun start(ctx: Context) {
            val i = Intent(ctx, CastListenerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i)
            } else {
                ctx.startService(i)
            }
        }
    }
}
