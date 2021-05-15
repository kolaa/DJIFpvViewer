package com.kolaa.djifpvviewer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.kolaa.djifpvviewer.databinding.ActivityMainBinding
import org.freedesktop.gstreamer.GStreamer


class MainActivity : AppCompatActivity(), SurfaceHolder.Callback, UsbDeviceListener {

    private val ACTION_USB_PERMISSION = "com.kolaa.djifpvviewer.USB_PERMISSION"
    private val VENDOR_ID = 11427
    private val PRODUCT_ID = 31
    var permissionIntent: PendingIntent? = null
    lateinit var usbDeviceBroadcastReceiver: UsbDeviceBroadcastReceiver
    lateinit var usbManager: UsbManager
    lateinit var mUsbMaskConnection: UsbMaskConnection
    var usbDevice: UsbDevice? = null

    var usbConnected = false

    private external fun nativeInit() // Initialize native code, build pipeline, etc
    private external fun nativeFinalize() // Destroy pipeline and shutdown native code
    private external fun nativePlay() // Set pipeline to PLAYING
    private external fun nativePause() // Set pipeline to PAUSED
    private external fun nativeClassInit(): Boolean // Initialize native class: cache Method IDs for callbacks
    private external fun nativeSurfaceInit(surface: Any)
    private external fun nativeSurfaceFinalize()
    external fun nativeReceiveVideoData(buffer: ByteArray)
    private val native_custom_data: Long = 0 // Native code will use this to keep private data

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        nativeClassInit()

        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)

        // Prevent screen from sleeping
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        supportActionBar?.hide()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), 0)
        usbDeviceBroadcastReceiver = UsbDeviceBroadcastReceiver(this)

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        registerReceiver(usbDeviceBroadcastReceiver, filter)
        val filterDetached = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
        registerReceiver(usbDeviceBroadcastReceiver, filterDetached)

        mUsbMaskConnection = UsbMaskConnection(this)


        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars())
        } else {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }

        try {
            GStreamer.init(this)
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding.surfaceVideo.holder.addCallback(this)

        nativeInit()

//        Toast.makeText(applicationContext, "waiting for usb connection...", Toast.LENGTH_SHORT)
//            .show()

        if (searchDevice() && !usbConnected) {
            connect()
        }
    }

    // Called from native code. Native code calls this once it has created its pipeline and
    // the main loop is running, so it is ready to accept commands.
    private fun onGStreamerInitialized() {
        Log.i("GStreamer", "Gst initialized.")
    }

    // Called from native code. This sets the content of the TextView from the UI thread.
    private fun setMessage(message: String) {

        runOnUiThread { binding.message.text = message }
    }

    companion object {
        // Used to load the 'native-lib' library on application startup.

        init {
            System.loadLibrary("gstreamer_android")
            System.loadLibrary("gs-view")
        }
    }

    override fun onDestroy() {
        mUsbMaskConnection.stop()
        usbConnected = false
        nativeFinalize()
        super.onDestroy()

    }

    override fun usbDeviceApproved(device: UsbDevice?) {
        Log.d("USB", "usbDevice approved")
        usbDevice = device
        Toast.makeText(applicationContext, "usb attached", Toast.LENGTH_SHORT).show()
        connect()

    }

    override fun usbDeviceDetached() {
        Log.d("USB", "usbDevice detached")
        Toast.makeText(applicationContext, "usb detached", Toast.LENGTH_SHORT).show()
        onStop()
    }

    private fun searchDevice(): Boolean {
        val deviceList = usbManager.deviceList
        if (deviceList.size <= 0) {
            usbDevice = null
            return false
        }
        for (device in deviceList.values) {
            if (device.vendorId == VENDOR_ID && device.productId == PRODUCT_ID) {
                Toast.makeText(applicationContext, "device found", Toast.LENGTH_SHORT).show()

                if (usbManager.hasPermission(device)) {
                    usbDevice = device
                    return true
                }

                Toast.makeText(applicationContext, "requesting permission...", Toast.LENGTH_SHORT).show()

                usbManager.requestPermission(device, permissionIntent)
            }
        }
        return false
    }

    private fun connect() {
        Toast.makeText(applicationContext, "Connected!!!", Toast.LENGTH_SHORT).show()

        usbConnected = true
        mUsbMaskConnection.setUsbDevice(usbManager!!.openDevice(usbDevice), usbDevice)
        mUsbMaskConnection.start()
        nativePlay()
    }

    override fun onResume() {
        super.onResume()
        if (searchDevice() && !usbConnected) {
            Log.d("RESUME_USB_CONNECTED", "not connected")
            connect()
        }
    }

    override fun onStop() {
        super.onStop()
        mUsbMaskConnection.stop()
        nativePause()
        usbConnected = false
    }


    override fun surfaceChanged(
        holder: SurfaceHolder, format: Int, width: Int,
        height: Int
    ) {
        Log.d(
            "GStreamer", "Surface changed to format " + format + " width "
                    + width + " height " + height
        )
        nativeSurfaceInit(holder.surface)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d("GStreamer", "Surface destroyed")
        nativeSurfaceFinalize()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d("GStreamer", "Surface created: " + holder.surface)
    }
}