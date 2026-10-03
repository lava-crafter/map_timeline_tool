package com.lavacrafter.maptimelinetool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lavacrafter.maptimelinetool.domain.model.GeoPoint
import com.lavacrafter.maptimelinetool.domain.usecase.*
import com.lavacrafter.maptimelinetool.ui.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingActionsRestorationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun visible() = compose.runOnUiThread { compose.activity.window.addFlags(
        android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }

    @Test fun everyPermissionActionRestoresMatchingMetadataAndDispatchesAtMostOnce() {
        val restoration = StateRestorationTester(compose)
        var action by mutableStateOf(LocationAction.NEW_POINT)
        var pending: LocationPermissionRequest? = null
        var activeId: String? = null
        val dispatched = mutableListOf<LocationPermissionRequest>()
        var requestCode = -1
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(code: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                requestCode = code // Hold the permission window's result until after restoration.
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        restoration.setContent {
            var savedRequest by rememberSaveable(stateSaver = LocationPermissionRequestSaver) { mutableStateOf<LocationPermissionRequest?>(null) }
            var savedId by rememberSaveable { mutableStateOf<String?>(null) }
            SideEffect { pending = savedRequest; activeId = savedId }
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
                val request = savedRequest; val id = savedId
                savedRequest = null; savedId = null
                matchLocationPermissionResult(request, id, grants[android.Manifest.permission.ACCESS_FINE_LOCATION] == true)?.let(dispatched::add)
            }
            Column {
            Button(onClick = {
                if (savedRequest == null) {
                    savedRequest = LocationPermissionRequest(action, "request-${action.name}", 1234)
                    savedId = savedRequest?.id
                    launcher.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION))
                }
            }) { Text("request") }
            Button(onClick = {
                registry.dispatchResult(requestCode, mapOf(android.Manifest.permission.ACCESS_FINE_LOCATION to true))
            }) { Text("result") }
            }
            }
        }
        for (next in LocationAction.entries) {
            compose.runOnIdle { action = next }
            compose.onNodeWithText("request").performClick()
            restoration.emulateSavedInstanceStateRestore()
            compose.runOnIdle {
                assertEquals(next, pending?.action); assertEquals(1234L, pending?.eventTimeMs)
                assertEquals(pending?.id, activeId)
            }
            compose.onNodeWithText("result").performClick()
            compose.onNodeWithText("result").performClick()
        }
        compose.runOnIdle { assertEquals(LocationAction.entries.toList(), dispatched.map { it.action }) }
    }

    @Test fun denialMismatchAndMissingMetadataCancelSafely() {
        val request = LocationPermissionRequest(LocationAction.NEW_POINT, "one", 1234)
        assertNull(matchLocationPermissionResult(request, "one", false))
        assertNull(matchLocationPermissionResult(request, "other", true))
        assertNull(matchLocationPermissionResult(request, null, true))
        assertNull(matchLocationPermissionResult(null, "one", true))
        assertNull(LocationPermissionRequestSaver.restore(Bundle()))
    }

    @Test fun confirmationBundleRetainsOnlyLightweightDraftAndFixMetadata() {
        val original = PointWriteState.ConfirmLocation("confirm", PointWriteRequest.Add("title", "note", 1000,
            setOf(4, 7), "candidate.jpg", PhotoPersistOptions(false, PhotoCompressFormat.WEBP, 85)),
            LocationSaveDecision(LocationSaveQuality.LAST_KNOWN_RECENT, GeoPoint(10.0, 20.0, 5f, 950, "gps"), true, true))
        val handle = SavedStateHandle(mapOf("point_confirmation" to original.toBundle()))
        assertEquals(original, confirmationFromBundle(handle["point_confirmation"]))
        assertNull(confirmationFromBundle(Bundle()))
        val missingFix = original.toBundle().apply { remove("fix") }
        val restored = requireNotNull(confirmationFromBundle(missingFix))
        assertFalse(LocationSavePolicy().evaluate(null, restored.decision.location, LocationSaveFlow.MANUAL_ADD, 2000).canSave)
    }
}
