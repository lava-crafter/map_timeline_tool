package com.lavacrafter.maptimelinetool

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.lavacrafter.maptimelinetool.data.AppDatabase
import com.lavacrafter.maptimelinetool.data.PointRepository
import com.lavacrafter.maptimelinetool.domain.model.*
import com.lavacrafter.maptimelinetool.domain.port.*
import com.lavacrafter.maptimelinetool.domain.repository.PointRepositoryGateway
import com.lavacrafter.maptimelinetool.domain.usecase.*
import com.lavacrafter.maptimelinetool.ui.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/** Real MainActivity + retained ViewModel, with isolated Room and controllable photo preparation. */
@RunWith(AndroidJUnit4::class)
class Phase2WriteRecreationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val visibility = object : ExternalResource() {
        private val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, state: android.os.Bundle?) {
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            override fun onActivityStarted(activity: android.app.Activity) = Unit
            override fun onActivityResumed(activity: android.app.Activity) = Unit
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivityStopped(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        }
        override fun before() = app.registerActivityLifecycleCallbacks(callbacks)
        override fun after() = app.unregisterActivityLifecycleCallbacks(callbacks)
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)).around(visibility).around(compose)
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private fun label(id: Int) = app.getString(id)

    private inner class Fixture {
        val database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val realRepo = PointRepository(database, database.pointDao())
        var fail = false
        val repo = object : PointRepositoryGateway by realRepo {
            override suspend fun insert(point: Point): Long {
                check(!fail) { "Injected DB failure" }; return realRepo.insert(point)
            }
        }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var rolledBack = 0
        var preparedPath: String? = null
        val photos = object : PointOperationPhotos {
            override suspend fun prepare(path: String?, options: PhotoPersistOptions): PreparedPhoto {
                preparedPath = path; entered.complete(Unit); release.await(); return PreparedPhoto(path, null, null)
            }
            override suspend fun rollbackUnlessReferenced(photo: PreparedPhoto) { rolledBack++ }
            override suspend fun commitCleanup(photo: PreparedPhoto) = Unit
            override suspend fun deleteUnlessReferenced(path: String?) = Unit
        }
        val location = GeoPoint(10.0, 20.0, 5f, System.currentTimeMillis(), "test")
        val provider = object : LocationProvider {
            override fun getLastKnownLocation() = location
            override suspend fun getPreciseLocation(timeoutMs: Long) = location
            override suspend fun getFreshLocation(timeoutMs: Long) = location
            override suspend fun getBestEffortLocation(timeoutMs: Long) = location
        }
        val vm = AppViewModel(app, repo, PointWriteUseCase(repo, object : SensorSnapshotPort {
            override suspend fun readSnapshot() = PointSensorSnapshot()
        }, {}), TagManagementUseCase(repo), provider, SavedStateHandle(), photos,
            { LocationSaveDecision(LocationSaveQuality.PRECISE_FRESH, location, true, false) })
        init {
            compose.activityRule.scenario.onActivity { activity ->
                activity.viewModelStore.put("androidx.lifecycle.ViewModelProvider.DefaultKey:${AppViewModel::class.java.name}", vm)
            }
            recreate()
        }
        fun close() {
            release.complete(Unit)
            compose.activityRule.scenario.close()
            database.close()
        }
    }
    private fun recreate() {
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { it.window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        compose.waitForIdle()
    }
    private fun openDraft(title: String) {
        compose.onNodeWithText(label(R.string.action_add_point)).performClick()
        compose.onNodeWithText(label(R.string.dialog_title_label)).performTextInput(title)
    }

    @Test fun addWhilePreparingRecreatesWithoutDoubleInsert() {
        val f = Fixture(); val title = "recreated add ${UUID.randomUUID()}"
        try {
            openDraft(title)
            compose.onNodeWithText(label(R.string.action_save)).performClick()
            runBlocking { withTimeout(5000) { f.entered.await() } }
            val running = f.vm.pointWrites!!.state.value as PointWriteState.Running
            recreate()
            assertEquals(running.id, (f.vm.pointWrites!!.state.value as PointWriteState.Running).id)
            compose.onNodeWithText(label(R.string.action_save)).assertIsNotEnabled()
            f.release.complete(Unit)
            compose.waitUntil(10_000) { runBlocking { f.repo.getAll().size == 1 } }
            compose.waitUntil(10_000) { f.vm.pointWrites!!.state.value == PointWriteState.Idle }
            recreate()
            assertEquals(1, runBlocking { f.repo.getAll().size })
            compose.onNodeWithText(label(R.string.dialog_title_new_point)).assertDoesNotExist()
        } finally { f.close() }
    }

    @Test fun databaseFailureRecreatesDraftAndCanRetryOnce() {
        val f = Fixture(); val title = "retry draft ${UUID.randomUUID()}"
        try {
            f.fail = true; f.release.complete(Unit)
            openDraft(title)
            compose.onNodeWithText(label(R.string.dialog_note_label)).performTextInput("retained note")
            compose.onNodeWithText(label(R.string.action_save)).performClick()
            compose.waitUntil(10_000) { f.rolledBack == 1 && f.vm.pointWrites!!.state.value == PointWriteState.Idle }
            recreate()
            compose.onNodeWithText(title).assertIsDisplayed()
            compose.onNodeWithText("retained note").assertIsDisplayed()
            assertTrue(runBlocking { f.repo.getAll().isEmpty() })
            f.fail = false
            compose.onNodeWithText(label(R.string.action_save)).performClick()
            compose.waitUntil(10_000) { f.vm.pointWrites!!.state.value == PointWriteState.Idle && runBlocking { f.repo.getAll().size == 1 } }
            assertEquals(title, runBlocking { f.repo.getAll().single().title })
            assertEquals("retained note", runBlocking { f.repo.getAll().single().note })
        } finally { f.close() }
    }

    @Test fun committedResultWhileUiStoppedIsConsumedAfterRecreationWithoutRetry() {
        val f = Fixture()
        try {
            openDraft("committed while stopped")
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            f.release.complete(Unit)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                assertTrue(f.vm.pointWrites!!.submit(PointWriteRequest.Add("committed while stopped", "", 1234,
                    emptySet(), null, PhotoPersistOptions(true, PhotoCompressFormat.JPEG, 90))))
            }
            runBlocking { withTimeout(5000) {
                f.vm.pointWrites!!.state.first { it is PointWriteState.Success }
            } }
            assertEquals(1, runBlocking { f.repo.getAll().size })
            compose.activityRule.scenario.recreate()
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            compose.waitUntil(10_000) { f.vm.pointWrites!!.state.value == PointWriteState.Idle }
            compose.onNodeWithText(label(R.string.dialog_title_new_point)).assertDoesNotExist()
            assertEquals(1, runBlocking { f.repo.getAll().size })
        } finally { f.close() }
    }

    @Test fun failedSnapshotRetainsTagsAndCandidateAcrossActivityRecreation() {
        val f = Fixture()
        try {
            val tagId = runBlocking { f.repo.insertTag(Tag(name = "retry tag")) }
            val request = PointWriteRequest.Add("snapshot", "note", System.currentTimeMillis(), setOf(tagId),
                "candidate.jpg", PhotoPersistOptions(true, PhotoCompressFormat.JPEG, 90))
            f.fail = true
            compose.activityRule.scenario.onActivity { assertTrue(f.vm.pointWrites!!.submit(request)) }
            runBlocking { withTimeout(5000) { f.entered.await() } }
            recreate()
            assertEquals(request, (f.vm.pointWrites!!.state.value as PointWriteState.Running).request)
            f.release.complete(Unit)
            compose.waitUntil(10_000) { f.rolledBack == 1 && f.vm.pointWrites!!.state.value == PointWriteState.Idle }
            assertEquals(request.photoPath, f.preparedPath)
            f.fail = false
            compose.activityRule.scenario.onActivity { assertTrue(f.vm.pointWrites!!.submit(request)) }
            compose.waitUntil(10_000) { runBlocking { f.repo.getAll().size == 1 } && f.vm.pointWrites!!.state.value == PointWriteState.Idle }
            val point = runBlocking { f.repo.getAll().single() }
            assertEquals("candidate.jpg", point.photoPath)
            assertEquals(setOf(tagId), runBlocking { f.repo.getTagIdsForPoint(point.id).toSet() })
        } finally { f.close() }
    }
}
