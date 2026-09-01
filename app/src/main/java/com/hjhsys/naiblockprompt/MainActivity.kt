package com.hjhsys.naiblockprompt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hjhsys.naiblockprompt.ui.NaiBlockPromptApp
import com.hjhsys.naiblockprompt.ui.theme.NaiBlockPromptTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as NaiBlockPromptApplication).container
        setContent {
            NaiBlockPromptTheme {
                NaiBlockPromptApp(container)
            }
        }
    }
}

