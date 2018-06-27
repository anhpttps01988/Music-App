package com.example.anhptt.playmusicexample.service

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import com.example.anhptt.playmusicexample.MainActivity
import com.example.anhptt.playmusicexample.R
import com.example.anhptt.playmusicexample.music.Music
import com.example.anhptt.playmusicexample.music.PlaybackStatus
import com.example.anhptt.playmusicexample.storage.StorageUtil


class MusicService : Service(), MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener,
        MediaPlayer.OnInfoListener, MediaPlayer.OnBufferingUpdateListener,

        AudioManager.OnAudioFocusChangeListener {
    override fun onCompletion(p0: MediaPlayer?) {
        stopMedia()

        stopSelf()
    }

    override fun onPrepared(p0: MediaPlayer?) {
        //invoked when the media source is ready for playback
        playMedia()
    }

    override fun onError(p0: MediaPlayer?, p1: Int, p2: Int): Boolean {
        when (p1) {
            MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK -> {
                Log.d("MediaPlayer Error", "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK $p2")
            }
            MediaPlayer.MEDIA_ERROR_SERVER_DIED -> {
                Log.d("MediaPlayer Error", "MEDIA ERROR SERVER DIED $p2")
            }
            MediaPlayer.MEDIA_ERROR_UNKNOWN -> {
                Log.d("MediaPlayer Error", "MEDIA ERROR UNKNOWN $p2")
            }
        }
        return false
    }

    override fun onSeekComplete(p0: MediaPlayer?) {
    }

    override fun onInfo(p0: MediaPlayer?, p1: Int, p2: Int): Boolean {
        return false
    }

    override fun onBufferingUpdate(p0: MediaPlayer?, p1: Int) {
    }

    override fun onAudioFocusChange(p0: Int) {
        when (p0) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (mediaPlayer!!.isPlaying) mediaPlayer!!.start()
                mediaPlayer!!.setVolume(1.0f, 1.0f)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                if (mediaPlayer!!.isPlaying) {
                    mediaPlayer!!.stop()
                }
                mediaPlayer!!.release()
                mediaPlayer = null
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (mediaPlayer!!.isPlaying) mediaPlayer!!.setVolume(0.1f, 0.1f)
            }
        }
    }

    private lateinit var audioManager: AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var mediaFile: String? = null
    private var resumePosition: Int? = null
    private var binder = LocalBinder()
    private var ongoingCall = false
    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyManager: TelephonyManager? = null
    private var musicList: MutableList<Music>? = null
    private var activeMusic: Music? = null
    private var audioIndex: Int? = -1
    private var switchToNextSongReceiver: BroadcastReceiver? = null
    private var becomingNoisyReceiver: BroadcastReceiver? = null
    private var playNewAudioReceiver: BroadcastReceiver? = null

    companion object {
        const val ACTION_PLAY = "ACTION_PLAY"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_PREVIOUS = "ACTION_PREVIOUS"
        const val ACTION_NEXT = "ACTION_NEXT"
        const val ACTION_STOP = "ACTION_STOP"
        const val NOTIFICATION_ID = 101
    }

    private var mediaSessionManager: MediaSessionManager? = null
    private var mediaSession: MediaSession? = null
    private var transportControls: MediaController.TransportControls? = null


    inner class LocalBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    @SuppressLint("NewApi")
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Throws(RemoteException::class)
    private fun initMediaSession() {
        if (mediaSessionManager != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            mediaSession = MediaSession(applicationContext, "MusicPlayer")
            transportControls = mediaSession!!.controller.transportControls
            mediaSession!!.isActive = true
            mediaSession!!.setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            updateMetaData()
            mediaSession!!.setCallback(object : MediaSession.Callback() {
                @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
                override fun onPlay() {
                    super.onPlay()
                    resumeMedia()
                    buildNotification(PlaybackStatus.PLAYING)
                }

                override fun onPause() {
                    super.onPause()
                    pauseMedia()
                    buildNotification(PlaybackStatus.PAUSE)
                }

                override fun onSkipToNext() {
                    super.onSkipToNext()
                    skipToNext()
                    updateMetaData()
                    buildNotification(PlaybackStatus.PLAYING)
                }

                override fun onSkipToPrevious() {
                    super.onSkipToPrevious()
                    skipToPrevious()
                    updateMetaData()
                    buildNotification(PlaybackStatus.PLAYING)
                }

                override fun onStop() {
                    super.onStop()
                    removeNotification()
                    stopSelf()
                }

                override fun onSeekTo(pos: Long) {
                    super.onSeekTo(pos)
                }
            })
        }
    }

    private fun updateMetaData() {
        val albumArt = activeMusic!!.avatar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaSession!!.setMetadata(MediaMetadata.Builder()
                    .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, albumArt)
                    .putString(MediaMetadata.METADATA_KEY_TITLE, activeMusic!!.name)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, activeMusic!!.artist).build())
        }
    }

    private fun skipToNext() {
        if (audioIndex == musicList!!.size - 1) {
            audioIndex = 0
            activeMusic = musicList!![audioIndex!!]
        } else {
            audioIndex = audioIndex!! + 1
            activeMusic = musicList!![audioIndex!!] // ++audioIndex
        }

        StorageUtil(applicationContext).storeAudioIndex(audioIndex!!)

        stopMedia()
        //reset mediaplayer
        mediaPlayer!!.reset()
        initMediaPlayer()
    }

    @SuppressLint("NewApi")
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun skipToPrevious() {
        if (audioIndex == 0) {
            audioIndex = musicList!!.size - 1
            activeMusic = musicList!![audioIndex!!]
        } else {
            audioIndex = audioIndex!! - 1
            activeMusic = musicList!![audioIndex!!] // --audioIndex
        }

        StorageUtil(applicationContext).storeAudioIndex(audioIndex!!)

        stopMedia()
        mediaPlayer!!.reset()
        initMediaPlayer()
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun buildNotification(playbackStatus: PlaybackStatus) {
        var notificationAction = android.R.drawable.ic_media_pause

        var play_pauseAction: PendingIntent? = null
        if (playbackStatus == PlaybackStatus.PLAYING) {
            notificationAction = android.R.drawable.ic_media_pause
            play_pauseAction = playBackAction(1)
        } else {
            notificationAction = android.R.drawable.ic_media_play
            play_pauseAction = playBackAction(0)
        }

        val largeIcon = activeMusic!!.avatar
        val notificationBuilder = Notification.Builder(this)
                .setShowWhen(true)
                .setStyle(Notification.MediaStyle().setMediaSession(mediaSession!!.sessionToken).setShowActionsInCompactView(0, 1, 2))
                .setColor(resources.getColor(R.color.colorPrimary))
                .setLargeIcon(largeIcon)
                .setSmallIcon(android.R.drawable.stat_sys_headset)
                .setContentText(activeMusic!!.artist)
                .setContentTitle(activeMusic!!.album)
                .setContentInfo(activeMusic!!.name)
                .addAction(android.R.drawable.ic_media_previous, "previous", playBackAction(3))
                .addAction(notificationAction, "pause", play_pauseAction)
                .addAction(android.R.drawable.ic_media_next, "next", playBackAction(2))

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun removeNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun playBackAction(actionNumber: Int): PendingIntent? {
        val playBackAction = Intent(this, MusicService::class.java)
        when (actionNumber) {
            0 -> {
                playBackAction.action = ACTION_PLAY
                return PendingIntent.getService(this, actionNumber, playBackAction, 0)
            }
            1 -> {
                playBackAction.action = ACTION_PAUSE
                return PendingIntent.getService(this, actionNumber, playBackAction, 0)
            }
            2 -> {
                playBackAction.action = ACTION_NEXT
                return PendingIntent.getService(this, actionNumber, playBackAction, 0)
            }
            3 -> {
                playBackAction.action = ACTION_PREVIOUS
                return PendingIntent.getService(this, actionNumber, playBackAction, 0)
            }
            else -> {
                return null
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun handlerIncomingActions(playbackAction: Intent) {
        if (playbackAction?.action == null) return
        val actionString = playbackAction.action
        when (actionString) {
            ACTION_PLAY -> {
                transportControls!!.play()
            }
            ACTION_PAUSE -> {
                transportControls!!.pause()
            }
            ACTION_NEXT -> {
                transportControls!!.skipToNext()
            }
            ACTION_PREVIOUS -> {
                transportControls!!.skipToPrevious()
            }
        }
    }

    private fun callStateListener() {
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, incomingNumber: String?) {
                super.onCallStateChanged(state, incomingNumber)
                when (state) {
                    TelephonyManager.CALL_STATE_OFFHOOK,
                    TelephonyManager.CALL_STATE_RINGING -> {
                        if (mediaPlayer != null) {
                            pauseMedia()
                            ongoingCall = true
                        }
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        if (mediaPlayer != null) {
                            if (ongoingCall) {
                                ongoingCall = false
                                resumeMedia()
                            }
                        }
                    }
                }
            }
        }
        telephonyManager!!.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private fun initMediaPlayer() {
        mediaPlayer = MediaPlayer()
        mediaPlayer?.setOnCompletionListener(this)
        mediaPlayer?.setOnErrorListener(this)
        mediaPlayer?.setOnPreparedListener(this)
        mediaPlayer?.setOnBufferingUpdateListener(this)
        mediaPlayer?.setOnSeekCompleteListener(this)
        mediaPlayer?.setOnInfoListener(this)
        mediaPlayer?.reset()
        mediaPlayer?.setAudioStreamType(AudioManager.STREAM_MUSIC)

        try {
            mediaPlayer?.setDataSource(this, Uri.parse(activeMusic!!.fullPath))
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }

        mediaPlayer?.prepareAsync()
    }

    private fun requestAudioFocus(): Boolean {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return true
        }
        return false
    }

    private fun removeAudioFocus(): Boolean {
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED ==
                audioManager.abandonAudioFocus(this)
    }

    private fun playMedia() {
        if (!mediaPlayer!!.isPlaying) {
            mediaPlayer?.start()
        }
    }

    private fun stopMedia() {
        if (mediaPlayer!!.isPlaying) {
            mediaPlayer?.stop()
        }
    }

    private fun pauseMedia() {
        if (mediaPlayer!!.isPlaying) {
            mediaPlayer!!.pause()
            resumePosition = mediaPlayer?.currentPosition
        }
    }

    private fun resumeMedia() {
        if (!mediaPlayer!!.isPlaying) {
            mediaPlayer?.seekTo(resumePosition!!)
            mediaPlayer?.start()
        }
    }

    @SuppressLint("NewApi")
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    override fun onCreate() {
        super.onCreate()
        musicList = StorageUtil(this).loadAudio()
        callStateListener()
        becomingNoisy()
        playNewAudio()
    }

    @SuppressLint("NewApi")
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val storage = StorageUtil(this)
            audioIndex = storage.loadAudioIndex()
            if (audioIndex != null && audioIndex!! < musicList!!.size) {
                activeMusic = musicList!![audioIndex!!]
                Log.d("PLAY_MUSIC", activeMusic!!.fullPath)
            } else {
                stopSelf()
            }
        } catch (e: Exception) {
            stopSelf()
        }
        if (!requestAudioFocus()) {
            stopSelf()
        }
        if (mediaSessionManager == null) {
            try {
                initMediaSession()
                initMediaPlayer()
            } catch (e: Exception) {
                e.printStackTrace()
                stopSelf()
            }
            buildNotification(PlaybackStatus.PLAYING)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            handlerIncomingActions(intent!!)
        }
        return Service.START_STICKY
    }

    @SuppressLint("NewApi")
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun playNewAudio() {
        playNewAudioReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                audioIndex = StorageUtil(context!!).loadAudioIndex()
                if (audioIndex != null && audioIndex!! < musicList!!.size) {
                    activeMusic = musicList!![audioIndex!!]
                    Log.d("PLAY_MUSIC", activeMusic!!.fullPath)
                } else {
                    stopSelf()
                }
                //reset media
                stopMedia()
                mediaPlayer!!.reset()
                initMediaPlayer()
                updateMetaData()
                buildNotification(PlaybackStatus.PLAYING)
            }
        }
        registerReceiver(playNewAudioReceiver, IntentFilter(MainActivity.BROADCAST_PLAY_NEW_AUDIO))
    }

    @SuppressLint("NewApi")
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun becomingNoisy() {
        becomingNoisyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                pauseMedia()
                buildNotification(PlaybackStatus.PAUSE)
            }
        }
        registerReceiver(becomingNoisyReceiver!!, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(playNewAudioReceiver)
        unregisterReceiver(becomingNoisyReceiver)
        unregisterReceiver(switchToNextSongReceiver)
        if (mediaPlayer != null) {
            stopMedia()
            mediaPlayer!!.release()
        }
        removeAudioFocus()
        if (phoneStateListener != null) {
            telephonyManager?.listen(phoneStateListener,
                    PhoneStateListener.LISTEN_NONE)
        }
        removeNotification()
        //clear cache
        StorageUtil(this).clearCachedAudioPlayList()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return binder
    }

}