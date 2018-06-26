package com.example.anhptt.playmusicexample

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import com.example.anhptt.playmusicexample.adapter.MusicAdapter
import com.example.anhptt.playmusicexample.music.Music
import com.example.anhptt.playmusicexample.service.MusicService
import kotlinx.android.synthetic.main.activity_main.*
import android.media.MediaMetadataRetriever
import android.widget.LinearLayout
import android.R.attr.data
import android.graphics.BitmapFactory
import android.graphics.Bitmap


class MainActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_READ_AND_WRITE_EXTERNAL_CODE: Int = 1111
    }

    private lateinit var mListMusic: MutableList<Music>
    private lateinit var mAdapter: MusicAdapter
    private var musicService: MusicService? = null
    private var serviceBound = false
    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(p0: ComponentName?, service: IBinder?) {
            val binder: MusicService.LocalBinder = service as MusicService.LocalBinder
            musicService = binder.service
            serviceBound = true
            Toast.makeText(this@MainActivity, "Service Bound", Toast.LENGTH_SHORT).show()
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            serviceBound = false
        }
    }
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {

        }
    }

    private fun playMedia(media: String) {
        if (!serviceBound) {
            val intent = Intent(this@MainActivity, MusicService::class.java)
            intent.putExtra("media", media)
            startService(intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } else {
            val intent = Intent("SWITCH_TO_NEXT_SONG_ACTION")
            intent.putExtra("media", media)
            sendBroadcast(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mListMusic = mutableListOf()
        mAdapter = MusicAdapter(this@MainActivity, mListMusic)
        listMusic.adapter = mAdapter
        requestPermission()
        listMusic.setOnItemClickListener { _, _, position, _ ->
            run {
                playMedia(mListMusic[position].fullPath!!)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        outState!!.putBoolean("ServiceState", serviceBound)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        serviceBound = savedInstanceState!!.getBoolean("ServiceState")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            musicService?.stopSelf()
        }
    }

    private fun getListMusicFromMedia() {
        val cursor: Cursor
        val allSongUri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection: String = MediaStore.Audio.Media.IS_MUSIC + " != 0"
        if (isSdPresent()) {
            cursor = contentResolver.query(allSongUri, arrayOf("*"), selection, null, null)
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    do {
                        val music = Music()
                        music.id = cursor.getInt(cursor
                                .getColumnIndex(MediaStore.Audio.Media._ID))
                        music.name = cursor
                                .getString(cursor
                                        .getColumnIndex(MediaStore.Audio.Media.TITLE))
                        music.fullPath = cursor.getString(cursor
                                .getColumnIndex(MediaStore.Audio.Media.DATA))
                        music.album = cursor.getString(cursor
                                .getColumnIndex(MediaStore.Audio.Media.ALBUM))
                        music.artist = cursor.getString(cursor
                                .getColumnIndex(MediaStore.Audio.Media.ARTIST))
                        val mmr = MediaMetadataRetriever()
                        mmr.setDataSource(music.fullPath)
                        val dataPicture = mmr.embeddedPicture
                        val bitmap = BitmapFactory.decodeByteArray(dataPicture, 0, dataPicture.size)
                        music.avatar = bitmap
                        mListMusic.add(music)
                    } while (cursor.moveToNext())
                }
            }
            cursor.close()
        }
        mAdapter.notifyDataSetChanged()
    }

    private fun isSdPresent(): Boolean {
        return android.os.Environment.getExternalStorageState() == android.os.Environment.MEDIA_MOUNTED
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            ActivityCompat.requestPermissions(this@MainActivity,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_READ_AND_WRITE_EXTERNAL_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_READ_AND_WRITE_EXTERNAL_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("Permissions", "permission was granted, yay!")
                getListMusicFromMedia()
            } else {
                Log.d("Permissions", "Permission denied to read your External storage")
            }
        }
    }

}
