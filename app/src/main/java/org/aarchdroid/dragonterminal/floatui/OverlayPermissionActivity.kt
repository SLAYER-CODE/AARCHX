package org.aarchdroid.dragonterminal.floatui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.aarchdroid.R

class OverlayPermissionActivity : AppCompatActivity() {

    companion object {
        private const val SYSTEM_OVERLAY_REQUEST = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.permission_screen)

        findViewById<Button>(R.id.btn_open_settings).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, SYSTEM_OVERLAY_REQUEST)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SYSTEM_OVERLAY_REQUEST) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                setResult(RESULT_OK)
                finish()
            } else {
                Toast.makeText(this, R.string.float_overlay_permission_title, Toast.LENGTH_LONG).show()
            }
        }
    }
}
