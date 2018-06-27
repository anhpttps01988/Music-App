package com.example.anhptt.playmusicexample.storage

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.example.anhptt.playmusicexample.music.Music
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

class StorageUtil constructor(private var context: Context) {

    companion object {
        const val STORAGE = "STORAGE_PREF"
        const val AUDIO_LIST_KEY = "AUDIO_LIST_KEY"
        const val AUDIO_INDEX_KEY = "AUDIO_INDEX_KEY"
    }

    private var preferences: SharedPreferences? = null

    @SuppressLint("ApplySharedPref")
    fun storeAudio(list: MutableList<Music>) {
        preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
        with(preferences!!.edit()) {
            val gson = Gson()
            val json = gson.toJson(list)
            putString(AUDIO_LIST_KEY, json)
            commit()
        }
    }

    @SuppressLint("ApplySharedPref")
    fun storeAudioIndex(audioIndex: Int) {
        preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
        with(preferences!!.edit()) {
            putInt(AUDIO_INDEX_KEY, audioIndex)
            commit()
        }
    }

    @SuppressLint("CommitPrefEdits")
    fun loadAudio(): MutableList<Music> {
        preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
        val gson = Gson()
        val json = preferences!!.getString(AUDIO_LIST_KEY, null)
        val type = object : TypeToken<MutableList<Music>>() {
        }.type
        return gson.fromJson(json, type)
    }

    fun loadAudioIndex(): Int {
        preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
        return preferences!!.getInt(AUDIO_INDEX_KEY, -1) // return -1 if data not found
    }

    @SuppressLint("CommitPrefEdits", "ApplySharedPref")
    fun clearCachedAudioPlayList() {
        preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
        with(preferences!!.edit()){
            clear()
            commit()
        }
    }
}