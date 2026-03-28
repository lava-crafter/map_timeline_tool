package test
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.views.MapView
import android.content.Context

fun test(map: MapView) {
    val cm = CacheManager(map)
    cm.cancelAllJobs()
}
