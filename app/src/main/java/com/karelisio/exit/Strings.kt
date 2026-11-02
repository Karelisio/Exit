package com.karelisio.exit

import android.content.res.Resources
import androidx.annotation.StringRes

/** Accès aux textes (strings.xml) hors de Compose : notifications, widget, dépôt, tuiles. */
object AppStrings {
    lateinit var resources: Resources
}

fun str(@StringRes id: Int, vararg args: Any?): String = AppStrings.resources.getString(id, *args)
