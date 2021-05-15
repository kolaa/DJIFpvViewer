package com.kolaa.djifpvviewer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log

class UsbDeviceBroadcastReceiver(private val listener: UsbDeviceListener) : BroadcastReceiver() {
    private val ACTION_USB_PERMISSION = "com.kolaa.djifpvviewer.USB_PERMISSION"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        Log.d("UsbDevice Broadcast", action.toString())
        if (ACTION_USB_PERMISSION == action) {
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                if (device != null) {
                    Log.d("UsbDeviceBroadcastReceiver", "Usb device approved")
                    listener.usbDeviceApproved(device)
                }
            }
        }
        if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
            listener.usbDeviceDetached()
        }
    }
}