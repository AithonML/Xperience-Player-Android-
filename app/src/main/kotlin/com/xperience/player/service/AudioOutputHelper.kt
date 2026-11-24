package com.xperience.player.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter2
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages audio output device detection and triggers the system Output Switcher dialog.
 */
class AudioOutputHelper(context: Context) {

    private val appContext: Context = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _currentOutputName = MutableStateFlow(resolveActiveOutputName())
    val currentOutputName: StateFlow<String> = _currentOutputName.asStateFlow()

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            _currentOutputName.value = resolveActiveOutputName()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            _currentOutputName.value = resolveActiveOutputName()
        }
    }

    init {
        try {
            audioManager?.registerAudioDeviceCallback(audioDeviceCallback, null)
        } catch (e: Exception) {
            Log.w("AudioOutputHelper", "Failed to register audio device callback", e)
        }
    }

    fun release() {
        try {
            audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (e: Exception) {
            Log.w("AudioOutputHelper", "Failed to unregister audio device callback", e)
        }
    }

    fun refresh() {
        _currentOutputName.value = resolveActiveOutputName()
    }

    private fun resolveActiveOutputName(): String {
        val manager = audioManager ?: return "Device Speaker"
        try {
            val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

            // Look for high-priority connected output devices (Bluetooth, USB, Wired)
            for (device in outputs) {
                when (device.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_HEADSET,
                    AudioDeviceInfo.TYPE_BLE_SPEAKER,
                    AudioDeviceInfo.TYPE_HEARING_AID -> {
                        val name = device.productName?.toString()
                        if (!name.isNullOrBlank()) return name
                        return "Bluetooth Headphones"
                    }
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> {
                        return "Wired Headphones"
                    }
                    AudioDeviceInfo.TYPE_USB_DEVICE,
                    AudioDeviceInfo.TYPE_USB_HEADSET -> {
                        val name = device.productName?.toString()
                        if (!name.isNullOrBlank()) return name
                        return "USB Audio"
                    }
                    AudioDeviceInfo.TYPE_REMOTE_SUBMIX,
                    AudioDeviceInfo.TYPE_LINE_ANALOG,
                    AudioDeviceInfo.TYPE_LINE_DIGITAL -> {
                        return "External Device"
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("AudioOutputHelper", "Error resolving active output device", e)
        }
        return "Phone Speaker"
    }

    /**
     * Programmatically triggers the native Android Output Switcher dialog.
     * Uses MediaRouter2 on API 34+ (Android 14+), and on API 33 opens the system volume panel or bluetooth settings.
     */
    fun showOutputSwitcher(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val mediaRouter2 = MediaRouter2.getInstance(activity)
                val shown = mediaRouter2.showSystemOutputSwitcher()
                if (shown) {
                    Log.d("AudioOutputHelper", "System output switcher shown successfully via MediaRouter2")
                    return true
                }
            } catch (e: Throwable) {
                Log.w("AudioOutputHelper", "MediaRouter2 showSystemOutputSwitcher failed", e)
            }
        }

        // Fallback for Android 13 or if MediaRouter2 didn't open
        return try {
            val volumePanelIntent = Intent(Settings.Panel.ACTION_VOLUME)
            activity.startActivity(volumePanelIntent)
            true
        } catch (e: Exception) {
            try {
                val btIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                activity.startActivity(btIntent)
                true
            } catch (e2: Exception) {
                Log.e("AudioOutputHelper", "Failed to launch fallback settings for output switcher", e2)
                false
            }
        }
    }
}
