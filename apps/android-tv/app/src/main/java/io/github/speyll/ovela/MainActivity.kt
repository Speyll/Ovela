package io.github.speyll.ovela

import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import io.github.speyll.ovela.ui.OvelaApp
import io.github.speyll.ovela.ui.theme.BrandTokens

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Determine if this is a touch-first device (phone) or TV
        val isTouchUi = isTouchFirstUi(
            hasLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK),
            hasTelevision = (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
        )

        // Allow portrait/landscape on phones; lock to landscape on TVs
        requestedOrientation = if (isTouchUi) ActivityInfo.SCREEN_ORIENTATION_SENSOR
            else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // The branded intro is rendered by the app itself. Do not hold a second
        // compat splash over the launcher/app transition: on cold TV starts that
        // produced the duplicated/floating wordmark visible before the real intro.
        window.setBackgroundDrawable(ColorDrawable(BrandTokens.Background.toArgb()))
        enableEdgeToEdge()
        setContent {
            OvelaApp()
        }
    }

    private fun isTouchFirstUi(hasLeanback: Boolean, hasTelevision: Boolean): Boolean =
        !hasLeanback && !hasTelevision
}
