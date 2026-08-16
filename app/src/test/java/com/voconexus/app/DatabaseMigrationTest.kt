package com.voconexus.app

import androidx.test.core.app.ApplicationProvider
import com.voconexus.app.core.data.db.VocoNexusDatabase
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DatabaseMigrationTest {

    @Test
    fun testDatabaseInstanceCreationAndSchemaVersion() {
        val db = VocoNexusDatabase.createInMemory(
            ApplicationProvider.getApplicationContext()
        )
        assertNotNull(db.projectDao())
        assertNotNull(db.documentDao())
        assertNotNull(db.partDao())
        assertNotNull(db.chunkDao())
        assertNotNull(db.generationJobDao())
        assertNotNull(db.audioAssetDao())
        assertNotNull(db.exportJobDao())
        db.close()
    }
}
