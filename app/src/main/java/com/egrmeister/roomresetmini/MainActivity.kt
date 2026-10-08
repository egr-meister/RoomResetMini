package com.egrmeister.roomresetmini

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.egrmeister.roomresetmini.ui.RoomResetRoot
import com.egrmeister.roomresetmini.ui.theme.RoomResetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge (enforced on Android 15+); system bars stay visible, no immersive or keep-awake.
        enableEdgeToEdge()
        val container = (application as RoomResetApp).container
        setContent {
            RoomResetTheme {
                RoomResetRoot(container)
            }
        }
    }
}
