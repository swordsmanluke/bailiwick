package com.perfectlunacy.bailiwick.models

import android.graphics.drawable.Drawable
import java.io.File

data class Identity(val name: String, val cid: String, val profilePicFile: File?) {
    val drawablePic: Drawable? = when(profilePicFile) {
        null -> null
        else -> Drawable.createFromPath(profilePicFile.path)
    }
}