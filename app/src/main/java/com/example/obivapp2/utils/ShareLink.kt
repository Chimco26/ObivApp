package com.example.obivapp2.utils

import android.content.Intent
import android.content.Context

fun shareLink(context: Context, link: String) {
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, link)
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share via"))
}