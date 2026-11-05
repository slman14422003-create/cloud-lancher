package com.cloud.launcher.utils

import android.content.Context
import android.content.Intent
import com.cloud.launcher.MainActivity

object RecentsIntent {
    const val EXTRA_OPEN_RECENTS = "com.cloud.launcher.OPEN_RECENTS"

    fun open(context: Context) {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            .putExtra(EXTRA_OPEN_RECENTS, true)
        PermissionUtils.startSafely(context, intent)
    }
}
