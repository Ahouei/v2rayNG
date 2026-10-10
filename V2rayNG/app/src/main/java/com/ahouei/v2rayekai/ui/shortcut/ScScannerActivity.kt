package com.ahouei.v2rayekai.ui.shortcut

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.ahouei.v2rayekai.R
import com.ahouei.v2rayekai.extension.toastError
import com.ahouei.v2rayekai.extension.toastSuccess
import com.ahouei.v2rayekai.handler.AngConfigManager
import com.ahouei.v2rayekai.ui.base.HelperBaseComponentActivity
import com.ahouei.v2rayekai.ui.main.MainActivity

class ScScannerActivity : HelperBaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        LaunchedEffect(Unit) {
            importQRcode()
        }
    }

    private fun importQRcode() {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                val (count, countSub) = AngConfigManager.importBatchConfig(scanResult, "", false)

                if (count + countSub > 0) {
                    toastSuccess(R.string.toast_success)
                } else {
                    toastError(R.string.toast_failure)
                }

                startActivity(Intent(this, MainActivity::class.java))
            }
            finish()
        }
    }
}