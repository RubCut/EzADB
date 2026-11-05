package dev.ezadb.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.flyfishxu.kadb.usb.KadbUsb

object UsbHelper {
    const val ACTION_USB_PERMISSION = "dev.ezadb.USB_PERMISSION"

    fun manager(context: Context): UsbManager = context.getSystemService(UsbManager::class.java)

    fun adbDevices(context: Context): List<UsbDevice> = KadbUsb.devices(manager(context))

    fun hasPermission(context: Context, device: UsbDevice): Boolean = manager(context).hasPermission(device)

    fun requestPermission(context: Context, device: UsbDevice) {
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pending = PendingIntent.getBroadcast(context, device.deviceId, intent, flags)
        manager(context).requestPermission(device, pending)
    }

    fun registerReceiver(
        context: Context,
        onPermission: (UsbDevice, Boolean) -> Unit,
        onAttached: (UsbDevice) -> Unit,
        onDetached: (UsbDevice) -> Unit,
    ): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE) ?: return
                when (intent.action) {
                    ACTION_USB_PERMISSION -> {
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        onPermission(device, granted)
                    }
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> if (KadbUsb.isAdbDevice(device)) onAttached(device)
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> onDetached(device)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        return receiver
    }
}
