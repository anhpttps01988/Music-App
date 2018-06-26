package com.example.anhptt.playmusicexample.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.util.Log


class MusicService constructor() : Service(), MediaPlayer.OnCompletionListener,
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

    private var mediaPlayer: MediaPlayer? = null
    private var mediaFile: String? = null
    lateinit var mUri: Uri
    lateinit var audioManager: AudioManager
    private var resumePosition: Int? = null
    private var binder = LocalBinder()
    private var switchToNextSongBroadCash: BroadcastReceiver? = null

    inner class LocalBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
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
            mediaPlayer?.setDataSource(this, Uri.parse(mediaFile))
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
        if (mediaPlayer!!.isPlaying) {
            mediaPlayer?.seekTo(resumePosition!!)
            mediaPlayer?.start()
        }
    }


    override fun onCreate() {
        super.onCreate()
    }

    @SuppressLint("WrongConstant")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            mediaFile = intent!!.extras.getString("media")
        } catch (e: Exception) {
            stopSelf()
        }
        if (!requestAudioFocus()) {
            stopSelf()
        }
        if (mediaFile != null) {
            initMediaPlayer()
        }
        switchToNextSongBroadCash = object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, intent: Intent?) {
                if (intent!!.action == "SWITCH_TO_NEXT_SONG_ACTION") {
                    mediaFile = intent.extras.getString("media")
                    mediaPlayer!!.reset()
                    mediaPlayer!!.setDataSource(p0, Uri.parse(mediaFile))
                    mediaPlayer!!.prepareAsync()
                }
            }

        }
        registerReceiver(switchToNextSongBroadCash!!, IntentFilter("SWITCH_TO_NEXT_SONG_ACTION"))
        return Service.START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy();
        unregisterReceiver(switchToNextSongBroadCash!!)
        if (mediaPlayer != null) {
            stopMedia()
            mediaPlayer!!.release()
        }
        removeAudioFocus()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return binder
    }

}