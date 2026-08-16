package com.voconexus.app

import com.voconexus.app.core.tts.catalog.DefaultModelProvider
import com.voconexus.app.core.tts.catalog.ModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ModelCatalogTest {

    private val catalog = ModelCatalog()

    @Test
    fun testDefaultModelProvider() {
        val defaultModel = DefaultModelProvider.defaultModel

        assertNotNull(defaultModel)
        assertEquals("kokoro-82m-v1.0", defaultModel.modelId)
        assertEquals("Kokoro 82M", defaultModel.displayName)
        assertEquals("Apache-2.0", defaultModel.license.licenseName)
    }

    @Test
    fun testCatalogModelLookup() {
        val models = catalog.getAvailableModels()
        assertNotNull(models)
        assertEquals(true, models.isNotEmpty())

        val retrieved = catalog.getModelById("kokoro-82m-v1.0")
        assertNotNull(retrieved)
        assertEquals("Kokoro 82M", retrieved?.displayName)
    }
}
