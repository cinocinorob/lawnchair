package it.robbie.prisma.island

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class IslandNotificationListener : NotificationListenerService() {

    private var msm: MediaSessionManager? = null
    private var current: MediaController? = null
    private val alerted = HashSet<String>()

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) { publish() }
        override fun onMetadataChanged(metadata: MediaMetadata?) { publish() }
        override fun onSessionDestroyed() { pickSession() }
    }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { pickSession() }

    private fun me() = ComponentName(this, IslandNotificationListener::class.java)

    override fun onListenerConnected() {
        msm = getSystemService(MediaSessionManager::class.java)
        try {
            msm?.addOnActiveSessionsChangedListener(sessionsListener, me())
        } catch (_: SecurityException) {
        }
        pickSession()
    }

    override fun onListenerDisconnected() {
        try { msm?.removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Exception) {}
        current?.unregisterCallback(callback)
        current = null
        IslandBus.controller = null
        IslandBus.postMedia(null)
    }

    private fun pickSession() {
        val list: List<MediaController> = try {
            msm?.getActiveSessions(me()) ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
        val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull()
        if (best?.sessionToken != current?.sessionToken) {
            current?.unregisterCallback(callback)
            current = best
            best?.registerCallback(callback)
        }
        IslandBus.controller = current
        publish()
    }

    private fun publish() {
        val c = current
        val md = c?.metadata
        if (c == null || md == null) {
            IslandBus.postMedia(null); return
        }
        val st = c.playbackState
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        IslandBus.postMedia(
            MediaInfo(
                title = title,
                artist = artist,
                art = art,
                playing = st?.state == PlaybackState.STATE_PLAYING,
                position = st?.position ?: 0L,
                duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
                positionTime = st?.lastPositionUpdateTime ?: 0L,
                speed = st?.playbackSpeed ?: 1f,
                pkg = c.packageName,
            )
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        if (!Prefs.get(this, Prefs.NOTIFS)) return
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if (sbn.isOngoing) return
        if (n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return
        if ((n.flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0 && sbn.key in alerted) return

        if (rankingMap != null) {
            val r = Ranking()
            if (rankingMap.getRanking(sbn.key, r)) {
                if (r.importance < NotificationManager.IMPORTANCE_DEFAULT) return
                if (r.isSuspended) return
            }
        }

        val title = (n.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: n.extras.getCharSequence(Notification.EXTRA_TITLE))?.toString() ?: return
        val text = (n.extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString() ?: ""
        val icon = try {
            packageManager.getApplicationIcon(sbn.packageName)
        } catch (_: Exception) {
            null
        }
        alerted.add(sbn.key)
        if (alerted.size > 500) alerted.clear()
        IslandBus.postNotification(NotifInfo(sbn.key, sbn.packageName, icon, title, text, n.contentIntent))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        alerted.remove(sbn.key)
    }
}
