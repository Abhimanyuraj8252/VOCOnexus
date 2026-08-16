package com.voconexus.app

import android.app.Application
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
        container = AppContainerImpl(this)

        CoroutineScope(Dispatchers.IO).launch {
            container.voiceRepository.seedDefaultCatalog()
        }
    }
}
