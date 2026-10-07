package com.xtreamlytv.androidtv

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xtreamlytv.androidtv.ui.XtreamlyTvApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The branded intro is rendered by the app itself. Do not hold a second
        // compat splash over the launcher/app transition: on cold TV starts that
        // produced the duplicated/floating wordmark visible before the real intro.
        window.setBackgroundDrawable(ColorDrawable(Color.rgb(2, 5, 10)))
        enableEdgeToEdge()
        setContent {
            XtreamlyTvApp()
        }
    }
}
