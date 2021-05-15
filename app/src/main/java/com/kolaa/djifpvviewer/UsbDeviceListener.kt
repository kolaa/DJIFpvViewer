package com.kolaa.djifpvviewer

import android.hardware.usb.UsbDevice

interface UsbDeviceListener {
    fun usbDeviceApproved(device: UsbDevice?)
    fun usbDeviceDetached()
}