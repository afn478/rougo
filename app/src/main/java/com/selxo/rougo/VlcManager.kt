package com.selxo.rougo

import android.content.Context
import org.videolan.libvlc.LibVLC

object VLCManager {
    @Volatile
    private var libVLC: LibVLC? = null

    fun getLibVLC(context: Context): LibVLC {
        return libVLC ?: synchronized(this) {
            libVLC ?: try {
                val options = arrayListOf(
                    "--verbose=0",
                    "--network-caching=800",
                    "--clock-jitter=0",
                    "--clock-synchro=0",
                    "--file-caching=300"
                )
                LibVLC(context.applicationContext, options).also { libVLC = it }
            } catch (e: Exception) {
                LibVLC(context.applicationContext).also { libVLC = it }
            }
        }
    }
}
