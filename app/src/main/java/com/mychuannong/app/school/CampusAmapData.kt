package com.mychuannong.app.school

import android.content.Context
import com.amap.api.maps.model.LatLng
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItemV2
import com.amap.api.services.poisearch.PoiSearchV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

internal data class CampusGeoPoi(val id: String, val name: String, val latitude: Double, val longitude: Double,
    val address: String = "", val typeCode: String = "") {
    val position get() = LatLng(latitude, longitude)
    fun distanceTo(other: CampusGeoPoi): Double {
        val y = Math.toRadians(latitude - other.latitude)
        val x = Math.toRadians(longitude - other.longitude) * cos(Math.toRadians((latitude + other.latitude) / 2))
        return sqrt(x*x + y*y) * 6371000
    }
}
internal data class CampusGeoData(val center: CampusGeoPoi, val pois: List<CampusGeoPoi>)
internal class CampusRootNotFound: IllegalStateException("Campus POI not found")

/** Public vendor POIs only. No school account, device location or typed search text is used. */
internal class CampusAmapData(context: Context) {
    private val context = context.applicationContext
    // Discard the old illustration-to-POI matching cache.
    private val prefs = context.getSharedPreferences("campus_amap_catalogue_v2", Context.MODE_PRIVATE)
    private val memory = java.util.concurrent.ConcurrentHashMap<CampusMap, CampusGeoData>()

    suspend fun load(campus: CampusMap, refresh: Boolean = false): CampusGeoData = withContext(Dispatchers.IO) {
        if(!refresh) {
            memory[campus]?.let { return@withContext it }
            (readCache(campus) ?: readBundledCache(campus))?.let { cached ->
                return@withContext withVerifiedBuildings(campus,cached).also { memory[campus]=it }
            }
        }
        val city = if(campus == CampusMap.YAAN) "雅安市" else "成都市"
        val campusName = "四川农业大学(${campus.title})"
        val rootPois = search(campusName, city)
        val allowedNames = (if(campus == CampusMap.YAAN) setOf(campusName, "四川农业大学(雅安本部)", "四川农业大学")
            else setOf(campusName)).map { it.replace(Regex("[()（）\\s]"), "") }
        val root = rootPois.firstOrNull { it.title.replace(Regex("[()（）\\s]"), "") in allowedNames }
            ?: throw CampusRootNotFound()
        val center = root.toGeo()
        val all = linkedMapOf<String, CampusGeoPoi>()
        fun collect(items: List<PoiItemV2>) {
            items.filter { it.latLonPoint != null }.forEach { item ->
                val poi = item.toGeo()
                if(CampusAmapCatalogue.belongs(poi, campus, center)) {
                    all[poi.id] = poi
                    item.subPois.orEmpty().forEach { sub ->
                        sub.latLonPoint?.let { point ->
                            val child = CampusGeoPoi(sub.poiId, sub.title, point.latitude, point.longitude, sub.snippet.orEmpty(), sub.typeCode.orEmpty())
                            if(CampusAmapCatalogue.belongs(child, campus, center, childOfCampus = true)) all.putIfAbsent(child.id, child)
                        }
                    }
                }
            }
        }
        collect(rootPois)
        // Also find short building titles whose address, rather than name, identifies the school.
        for(keyword in listOf("四川农业大学", "教学楼", "教学实验楼", "公寓", "宿舍", "食堂", "餐厅", "图书馆", "操场", "体育馆", "学院", "楼", "实验", "体育", "校门")) {
            val seen = mutableSetOf<String>()
            for(page in 1..8) {
                ensureActive()
                val items = search(keyword, city, center, page, if(campus == CampusMap.YAAN) 1800 else 1200)
                collect(items)
                val added = items.count { seen.add(it.poiId) }
                if(items.size < 25 || added == 0) break
            }
        }
        ensureActive()
        withVerifiedBuildings(campus,CampusGeoData(center, all.values.toList())).also { memory[campus] = it; saveCache(campus,it) }
    }

    private fun withVerifiedBuildings(campus: CampusMap, data: CampusGeoData): CampusGeoData {
        // Public SDK map labels can exist without being returned by the basic search index.
        // These IDs/names/coordinates were recorded from the native OnPOIClick callback.
        val snapshot=JSONObject(context.assets.open("campus-map/amap-verified-buildings.json").bufferedReader().use { it.readText() })
        val entries=snapshot.getJSONArray("places")
        val extra=(0 until entries.length()).map { entries.getJSONObject(it) }
            .filter { it.getString("campus")==campus.id }.map(::decode)
            .filter { CampusAmapCatalogue.valid(it) && it.distanceTo(data.center)<1800 }
        return data.copy(pois=(data.pois+extra).distinctBy { it.id })
    }

    private suspend fun search(keyword: String, city: String, center: CampusGeoPoi? = null, page: Int = 1, radius: Int = 1200): List<PoiItemV2> = requestGate.withLock {
        delay((1100L-(android.os.SystemClock.elapsedRealtime()-lastRequest)).coerceAtLeast(0))
        lastRequest=android.os.SystemClock.elapsedRealtime()
        val query = PoiSearchV2.Query(keyword,"",city).apply {
            pageSize=25; pageNum=page; cityLimit=true
            showFields=PoiSearchV2.ShowFields(PoiSearchV2.ShowFields.CHILDREN)
        }
        val search = PoiSearchV2(context,query)
        if(center != null) search.bound = PoiSearchV2.SearchBound(LatLonPoint(center.latitude,center.longitude),radius)
        try { search.searchPOI()?.pois.orEmpty() }
        catch(e: AMapException) {
            if(!e.errorMessage.orEmpty().contains("QPS")) throw e
            delay(2500)
            lastRequest=android.os.SystemClock.elapsedRealtime()
            search.searchPOI()?.pois.orEmpty()
        }
    }
    private fun PoiItemV2.toGeo() = CampusGeoPoi(poiId,title,latLonPoint.latitude,latLonPoint.longitude,snippet.orEmpty(),typeCode.orEmpty())
    private fun encode(poi: CampusGeoPoi) = JSONObject().put("id",poi.id).put("name",poi.name).put("lat",poi.latitude).put("lng",poi.longitude)
        .put("address",poi.address).put("typeCode",poi.typeCode)
    private fun decode(json: JSONObject) = CampusGeoPoi(json.getString("id"),json.getString("name"),json.getDouble("lat"),json.getDouble("lng"),json.optString("address"),json.optString("typeCode"))
    private fun saveCache(campus: CampusMap, data: CampusGeoData) {
        val json = JSONObject().put("time",System.currentTimeMillis()).put("center",encode(data.center))
            .put("pois",JSONArray().apply { data.pois.forEach { put(encode(it)) } })
        prefs.edit().putString(campus.id,json.toString()).apply()
    }
    private fun readCache(campus: CampusMap): CampusGeoData? = runCatching {
        val json = JSONObject(prefs.getString(campus.id,null) ?: return null)
        if(System.currentTimeMillis()-json.getLong("time") !in 0..7*24*60*60*1000L) return null
        CampusGeoData(decode(json.getJSONObject("center")),json.getJSONArray("pois").let { array ->
            (0 until array.length()).map { decode(array.getJSONObject(it)) }
        })
    }.getOrNull()

    private fun readBundledCache(campus: CampusMap): CampusGeoData? {
        val snapshot=JSONObject(context.assets.open("campus-map/amap-campus-snapshot.json").bufferedReader().use { it.readText() })
        // A dated, verified public snapshot avoids a burst of SDK queries on every new install.
        if(System.currentTimeMillis()-snapshot.getLong("verifiedAtEpochMs") !in 0..7*24*60*60*1000L) return null
        val data=snapshot.getJSONObject("campuses").getJSONObject(campus.id)
        val pois=data.getJSONArray("pois")
        return CampusGeoData(decode(data.getJSONObject("center")),(0 until pois.length()).map { decode(pois.getJSONObject(it)) })
    }

    companion object {
        private val requestGate=Mutex()
        private var lastRequest=0L
        fun errorText(error: Exception) = when((error as? AMapException)?.errorCode) {
            1001,1002,1005,1006,1007,1008,1009,1012,1013 -> "高德授权未通过，请核对 Android Key、包名与 SHA-1"
            else -> "高德地点加载失败，点击重试"
        }
    }
}
