package dev.ezadb

import android.content.BroadcastReceiver
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ezadb.data.UsbHelper
import dev.ezadb.ui.EzApp
import dev.ezadb.ui.theme.EzTheme
import dev.ezadb.ui.viewmodel.EzViewModel
import dev.ezadb.util.toLocaleList

class MainActivity : AppCompatActivity() {
    private var usbReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val vm: EzViewModel = viewModel()
            val prefs by vm.prefs.collectAsStateWithLifecycle()
            LaunchedEffect(prefs.language) {
                AppCompatDelegate.setApplicationLocales(prefs.language.toLocaleList())
            }
            LaunchedEffect(prefs.keepScreenOn) {
                if (prefs.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            EzTheme(prefs) {
                EzApp(vm)
            }
        }
        handleUsbIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUsbIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        val vm = androidx.lifecycle.ViewModelProvider(this)[EzViewModel::class.java]
        usbReceiver = UsbHelper.registerReceiver(
            context = this,
            onPermission = { device, granted -> vm.onUsbPermission(device, granted) },
            onAttached = { device -> vm.onUsbAttached(device) },
            onDetached = { device -> vm.onUsbDetached(device) },
        )
        (application as EzAdbApp).discovery.start()
    }

    override fun onStop() {
        usbReceiver?.let { runCatching { unregisterReceiver(it) } }
        usbReceiver = null
        (application as EzAdbApp).discovery.stop()
        super.onStop()
    }

    private fun handleUsbIntent(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            val device = intent.getParcelableExtra<android.hardware.usb.UsbDevice>(UsbManager.EXTRA_DEVICE) ?: return
            val vm = androidx.lifecycle.ViewModelProvider(this)[EzViewModel::class.java]
            vm.onUsbAttached(device)
        }
    }
}
