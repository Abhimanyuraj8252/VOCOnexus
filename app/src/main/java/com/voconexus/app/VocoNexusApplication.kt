package com.voconexus.app

import android.app.Application
import android.util.Log
import com.voconexus.app.di.AppContainer
import com.voconexus.app.di.AppContainerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VocoNexusApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        try {
            System.loadLibrary("onnxruntime")
            System.loadLibrary("sherpa-onnx-cxx-api")
            System.loadLibrary("sherpa-onnx-c-api")
            System.loadLibrary("sherpa-onnx-jni")
            Log.i("VocoNexusApplication", "Successfully preloaded sherpa-onnx native libraries")
        } catch (e: Throwable) {
            Log.e("VocoNexusApplication", "Native library preload warning: ${e.message}")
        }

        container = AppContainerImpl(this)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.voiceRepository.seedDefaultCatalog()
            } catch (e: Exception) {
                Log.e("VocoNexusApplication", "Failed to seed default catalog", e)
            }
        }
    }
}

