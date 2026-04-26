package com.kakeibo.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import com.kakeibo.android.feature.root.RootApp
import com.kakeibo.android.ui.theme.CashflowTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CashflowTheme {
                RootApp()
            }
        }
    }
}
