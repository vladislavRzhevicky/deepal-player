package com.deepal.videocast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Поднимает CastListenerService на старте каждого user'а.
 *
 * Регистрируется в Manifest на BOOT_COMPLETED + LOCKED_BOOT_COMPLETED +
 * MY_PACKAGE_REPLACED. Все три broadcast'а доставляются per-user, так что
 * сервис будет запущен в каждом user-инстансе автоматически.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        CastListenerService.start(ctx)
    }
}
