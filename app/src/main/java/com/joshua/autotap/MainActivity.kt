package com.joshua.autotap

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.joshua.autotap.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }

        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Enable \"AutoTap Clicker\"", Toast.LENGTH_LONG).show()
        }

        binding.btnShow.setOnClickListener {
            when {
                !Settings.canDrawOverlays(this) ->
                    Toast.makeText(this, "Grant overlay permission first", Toast.LENGTH_SHORT).show()
                !ClickAccessibilityService.isEnabled(this) -> {
                    Toast.makeText(this, "Enable AutoTap in Accessibility first", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                else -> {
                    val svc = ClickAccessibilityService.instance
                    if (svc != null) {
                        svc.showOverlay()
                        moveTaskToBack(true)
                    } else {
                        Toast.makeText(
                            this,
                            "Service starting — toggle AutoTap off/on in Accessibility",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.statusOverlay.text =
            "${getString(R.string.overlay_perm)}: " +
                if (Settings.canDrawOverlays(this)) getString(R.string.granted)
                else getString(R.string.required)
        binding.statusAccessibility.text =
            "${getString(R.string.accessibility_perm)}: " +
                if (ClickAccessibilityService.isEnabled(this)) getString(R.string.granted)
                else getString(R.string.required)
    }
}
