package com.kolaa.djifpvviewer

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.util.Log
import java.io.IOException

class UsbMaskConnection(private val activity: MainActivity) {

    // Constants.
    private val READ_TIMEOUT = 100
    private val WRITE_TIMEOUT = 2000
    private var working = false
    private var receiveThread: Thread? = null
    private val magicPacket = "RMVT".toByteArray()
    private var usbConnection: UsbDeviceConnection? = null
    private var sendEndPoint: UsbEndpoint? = null
    private var receiveEndPoint: UsbEndpoint? = null
    private var device: UsbDevice? = null
    private var usbInterface: UsbInterface? = null

    fun setUsbDevice(c: UsbDeviceConnection?, d: UsbDevice?) {
        usbConnection = c
        device = d
        usbInterface = device!!.getInterface(3)
        Log.d("GET_USB_INTERFACE", "Interface #3 (" + usbInterface!!.name + ")")
        usbConnection!!.claimInterface(usbInterface, true)

        sendEndPoint = usbInterface!!.getEndpoint(0)
        receiveEndPoint = usbInterface!!.getEndpoint(1)

    }

    fun start() {
        usbConnection!!.bulkTransfer(
            sendEndPoint,
            magicPacket,
            magicPacket.size,
            WRITE_TIMEOUT
        )

        startReadThread()
    }

    fun startReadThread() {
        if (!working) {
            working = true
            receiveThread = object : Thread() {
                override fun run() {
                    while (working) {
                        val buffer = ByteArray(1024)
                        val receivedBytes = usbConnection!!.bulkTransfer(
                            receiveEndPoint,
                            buffer,
                            buffer.size,
                            READ_TIMEOUT
                        )
                        if (receivedBytes > 0) {
                            val data = ByteArray(receivedBytes)
                            System.arraycopy(
                                buffer,
                                0,
                                data,
                                0,
                                receivedBytes
                            )
                            //Log.d("USBInputStream","Message received: " + data.toString());
                            activity.nativeReceiveVideoData(data);
                        }
                    }
                }
            }
            (receiveThread as Thread).start()
        }
    }

    fun stop() {
        try {
            working = false
            receiveThread?.interrupt()

        } catch (e: IOException) {
            e.printStackTrace()
        }
        if (usbConnection != null) {
            usbConnection!!.releaseInterface(usbInterface)
            usbConnection!!.close()
        }
    }
}