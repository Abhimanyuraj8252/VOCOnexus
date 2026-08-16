package com.voconexus.app

import androidx.test.core.app.ApplicationProvider
import com.voconexus.app.core.data.db.VocoNexusDatabase
import com.voconexus.app.ui.screens.speech.PronunciationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PronunciationViewModelTest {

    private lateinit var database: VocoNexusDatabase
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = VocoNexusDatabase.createInMemory(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun testAddAndDeletePronunciationRule() = runTest(testDispatcher) {
        val viewModel = PronunciationViewModel(
            projectId = "p1",
            database = database,
            ioDispatcher = testDispatcher
        )

        viewModel.onMatchTextChanged("AI")
        viewModel.onReplacementChanged("A I")
        viewModel.addRule()

        val rulesAfterAdd = database.pronunciationRuleDao().getRulesForProject("p1")
        assertEquals(1, rulesAfterAdd.size)
        assertEquals("AI", rulesAfterAdd[0].matchText)
        assertEquals("A I", rulesAfterAdd[0].replacement)

        viewModel.deleteRule(rulesAfterAdd[0].id)

        val rulesAfterDelete = database.pronunciationRuleDao().getRulesForProject("p1")
        assertEquals(0, rulesAfterDelete.size)
    }
}
