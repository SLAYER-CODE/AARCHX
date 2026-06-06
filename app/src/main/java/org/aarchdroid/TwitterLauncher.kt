package org.aarchdroid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class TwitterLauncher : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://twitter.com/ANDRAXPENTEST"))
        startActivity(intent)

    }

    override fun onPause() {
        super.onPause()

        finish();

    }
}
