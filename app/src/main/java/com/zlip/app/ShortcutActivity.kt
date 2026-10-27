package com.zlip.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Where the launcher shortcuts ("Kapağı aç", "Ekranı yansıt", "Yeni yazı") land. Android opens a shortcut by
 * clearing its task, which would close a running Zlip, its cover session and any unsaved edit. This invisible
 * activity lives in a task of its own, hands the request to [MainActivity] the way the tile does, and is gone.
 */
class ShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(intent)
                .setClass(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
