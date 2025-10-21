package com.zipstats.app.ui.routes

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle

class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shareIntent = intent?.getParcelableExtra<Intent>(EXTRA_SHARE_INTENT)
        if (shareIntent == null) {
            finish()
            return
        }

        // Asegurar permisos de lectura en la Uri si existe
        val stream: Uri? = shareIntent.getParcelableExtra(Intent.EXTRA_STREAM)
        if (stream != null) {
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            grantUriPermission(shareIntent.`package`, stream, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, intent.getStringExtra(EXTRA_TITLE) ?: "Compartir"))
        finish()
    }

    companion object {
        const val EXTRA_SHARE_INTENT = "extra_share_intent"
        const val EXTRA_TITLE = "extra_title"
    }
}


