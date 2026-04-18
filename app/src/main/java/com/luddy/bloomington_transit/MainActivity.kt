package com.luddy.bloomington_transit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.luddy.bloomington_transit.ui.BtApp
import com.luddy.bloomington_transit.ui.theme.BloomingtonTransitTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BloomingtonTransitTheme {
                BtApp()
            }
        }
    }
}
