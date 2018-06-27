package com.example.anhptt.playmusicexample.music

import android.graphics.Bitmap
import java.io.Serializable

class Music : Serializable {
    var id: Int? = null
    var name: String? = null
    var fullPath: String? = null
    var album: String? = null
    var artist: String? = null
    var avatar: Bitmap? = null
}