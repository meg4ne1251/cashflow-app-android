package com.kakeibo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kakeibo.android.ui.shell.AppShell
import com.kakeibo.android.ui.theme.CashflowTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CashflowTheme {
                AppShell()
            }
        }
    }
}
