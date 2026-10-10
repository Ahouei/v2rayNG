package com.ahouei.v2rayekai.ui.shortcut

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.ahouei.v2rayekai.core.CoreServiceManager
import com.ahouei.v2rayekai.core.LauncherManager
import com.ahouei.v2rayekai.ui.base.BaseComponentActivity

class ScStartActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        LaunchedEffect(Unit) {
            moveTaskToBack(true)
            if (!CoreServiceManager.isRunning()) {
                LauncherManager.startServiceFromToggle(this@ScStartActivity)
            }
            finish()
        }
    }
}
