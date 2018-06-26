package com.example.anhptt.playmusicexample.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.example.anhptt.playmusicexample.R
import com.example.anhptt.playmusicexample.music.Music

class MusicAdapter constructor(private var context: Context, private var listMusic: MutableList<Music>) : BaseAdapter() {


    @SuppressLint("ViewHolder")
    override fun getView(position: Int, converView: View?, p2: ViewGroup?): View {
        var viewHolder = ViewHolder()
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        var view: View? = null
        if (converView == null) {
            view = inflater.inflate(R.layout.item_music, p2, false)
            viewHolder.itemName = view.findViewById(R.id.item_music_name)
            viewHolder.itemAlbum = view.findViewById(R.id.item_music_album)
            viewHolder.itemIcon = view.findViewById(R.id.item_music_icon)
            viewHolder.itemArTist = view.findViewById(R.id.item_music_artist)
            view.tag = viewHolder
        } else {
            view = converView
            viewHolder = converView.tag as ViewHolder
        }
        val music = listMusic[position]
        viewHolder.itemName.text = music.name
        viewHolder.itemAlbum.text = music.album
        viewHolder.itemArTist.text = music.artist
        viewHolder.itemIcon.setImageBitmap(music.avatar)
        return view!!
    }

    override fun getItem(p0: Int): Any {
        return listMusic[p0]
    }

    override fun getItemId(p0: Int): Long {
        return p0.toLong()
    }

    override fun getCount(): Int {
        return listMusic.size
    }

    inner class ViewHolder {
        lateinit var itemName: TextView
        lateinit var itemIcon: ImageView
        lateinit var itemAlbum: TextView
        lateinit var itemArTist: TextView
    }

}