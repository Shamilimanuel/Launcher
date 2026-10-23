package com.shamil.launcher.notify

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Seelie's notification listener (his locked feature #6, 23 Oct): Android hands it the phone's notifications and lets
 * Seelie see what's playing (media sessions), once he switches on "Notification access" for Seelie.
 * Everything stays on the phone. The music card, the notification rows and the Quiet toggle all read [NotifyHub].
 */
class SeelieNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        NotifyHub.attach(this)
        NotifyHub.update(safeActive())
    }

    override fun onListenerDisconnected() = NotifyHub.detach(this)

    override fun onNotificationPosted(sbn: StatusBarNotification?) = NotifyHub.update(safeActive())

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = NotifyHub.update(safeActive())

    /** Android can throw here while the listener is (re)connecting; then there's simply nothing yet. */
    private fun safeActive(): List<StatusBarNotification> = runCatching { activeNotifications?.toList() }.getOrNull() ?: emptyList()
}

/** The shared place where the rest of Seelie reads notifications and talks to the listener. */
object NotifyHub {
    private val _notifications = MutableStateFlow<List<StatusBarNotification>>(emptyList())
    /** Every notification on the phone right now (newest Android order). Empty when access is off. */
    val notifications: StateFlow<List<StatusBarNotification>> = _notifications.asStateFlow()

    private val _connected = MutableStateFlow(false)
    /** True while Android has the listener connected (access on and the service running). */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** The running listener, for things only it may do (e.g. Quiet mode via requestInterruptionFilter, dismissing). */
    @Volatile var listener: SeelieNotificationListener? = null
        private set

    internal fun attach(service: SeelieNotificationListener) {
        listener = service
        _connected.value = true
    }

    internal fun detach(service: SeelieNotificationListener) {
        if (listener === service) listener = null
        _connected.value = false
        _notifications.value = emptyList()
    }

    internal fun update(list: List<StatusBarNotification>) {
        _notifications.value = list
    }

    /** The listener's name, needed by MediaSessionManager.getActiveSessions to see what's playing. */
    fun component(context: Context) = ComponentName(context, SeelieNotificationListener::class.java)

    /** Has he switched on Notification access for Seelie? */
    fun isEnabled(context: Context): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    /** Opens Android's page where he switches Notification access on for Seelie (straight to Seelie on Android 11+). */
    fun accessIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(context).flattenToString())
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
}
