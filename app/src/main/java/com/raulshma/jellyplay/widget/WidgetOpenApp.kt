package com.raulshma.jellyplay.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.raulshma.jellyplay.MainActivity

/**
 * The widgets' plain open-app tap target: [MainActivity] with
 * NEW_TASK|CLEAR_TOP and no deep link, so the app resumes wherever it was.
 * The shared PendingIntent table is keyed by request code, so callers pass
 * their own [requestCode] — the per-widget constants must stay disjoint.
 */
internal fun openAppPendingIntent(context: Context, requestCode: Int): PendingIntent =
    PendingIntent.getActivity(
        context,
        requestCode,
        openAppIntent(context),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

internal fun openAppIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
