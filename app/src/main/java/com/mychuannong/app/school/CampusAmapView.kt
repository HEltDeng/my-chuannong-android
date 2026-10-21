package com.mychuannong.app.school

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.os.Bundle
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.amap.api.maps.*
import com.amap.api.maps.model.*
import com.amap.api.services.core.ServiceSettings
import com.mychuannong.app.BuildConfig
import kotlin.math.ln

internal object CampusAmapConsent {
    const val PRIVACY_URL = "https://lbs.amap.com/pages/privacy/"
    private fun prefs(context: Context) = context.getSharedPreferences("campus_amap_consent",Context.MODE_PRIVATE)
    fun agreed(context: Context) = prefs(context).getBoolean("agreed_v1",false)
    // Honor previous consent even if the old illustration mode was selected.
    fun hasChoice(context: Context) = agreed(context) || prefs(context).getBoolean("declined_v1",false) || prefs(context).contains("online")
    fun accept(context: Context) { prefs(context).edit().putBoolean("agreed_v1",true).remove("declined_v1").remove("online").apply() }
    fun decline(context: Context) { prefs(context).edit().putBoolean("declined_v1",true).apply() }
    fun initialize(context: Context) {
        check(agreed(context)) { "AMap requires explicit consent" }
        check(BuildConfig.AMAP_API_KEY.isNotBlank()) { "AMap key not configured" }
        MapsInitializer.updatePrivacyShow(context,true,true)
        MapsInitializer.updatePrivacyAgree(context,true)
        ServiceSettings.updatePrivacyShow(context,true,true)
        ServiceSettings.updatePrivacyAgree(context,true)
        MapsInitializer.setApiKey(BuildConfig.AMAP_API_KEY)
        MapsInitializer.setProtocol(MapsInitializer.HTTPS)
        ServiceSettings.getInstance().apply {
            setApiKey(BuildConfig.AMAP_API_KEY); protocol=ServiceSettings.HTTPS
            connectionTimeOut=12000; soTimeOut=12000
        }
    }
}

/** Native AMap rendering; the catalogue and Compose sheet remain school-owned. */
internal class CampusAmapView(context: Context, owner: LifecycleOwner, restoredCamera: FloatArray? = null) : FrameLayout(context), DefaultLifecycleObserver {
    private val mapView: TextureMapView
    val map: AMap
    private val lifecycle = owner.lifecycle
    private var disposed = false
    private var campus: CampusMap? = null
    private var data: CampusGeoData? = null
    private var places = emptyList<CampusPlace>()
    private var selectedId: String? = null
    private var visibleIds = emptySet<String>()
    private var pendingFocus: CampusPlace? = null
    private val markers = mutableMapOf<String,Marker>()
    private val icons = mutableMapOf<Triple<String,Boolean,Boolean>,BitmapDescriptor>()
    private var selectedCircle: Circle? = null
    private var lastStyle: Boolean? = null
    private var lastTextVisible: Boolean? = null
    private var lastMapHeight = -1
    private var restorePending = restoredCamera
    var onPlaceClick: (CampusPlace)->Unit = {}
    var onStatus: (String)->Unit = {}
    var onCameraChanged: (FloatArray)->Unit = {}
    var loaded = false
        private set
    var status = "正在加载高德地图…"
        private set

    init {
        CampusAmapConsent.initialize(context)
        val initial = restoredCamera?.takeIf { it.size==5 }?.let {
            CameraPosition(LatLng(it[0].toDouble(),it[1].toDouble()),it[2],it[3],it[4])
        } ?: CameraPosition(LatLng(30.67,104.06),10f,45f,0f)
        mapView = TextureMapView(context,AMapOptions().camera(initial).zoomControlsEnabled(false).compassEnabled(false))
        addView(mapView,LayoutParams(-1,-1)); mapView.onCreate(Bundle())
        map = mapView.map
        map.showBuildings(true)
        map.uiSettings.apply {
            isMyLocationButtonEnabled=false; isScaleControlsEnabled=true; isIndoorSwitchEnabled=false
            setAllGesturesEnabled(true)
        }
        map.setOnMapLoadedListener { loaded=true }
        map.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
            override fun onCameraChange(value: CameraPosition) { onCameraChanged(floatArrayOf(value.target.latitude.toFloat(),value.target.longitude.toFloat(),value.zoom,value.tilt,value.bearing)) }
            override fun onCameraChangeFinish(value: CameraPosition) = onCameraChange(value)
        })
        map.setOnMarkerClickListener { marker ->
            places.firstOrNull { it.id==marker.`object` }?.let(onPlaceClick); true
        }
        map.setOnPOIClickListener { poi ->
            places.firstOrNull { it.geo?.id == poi.poiId && it.id in visibleIds }?.let(onPlaceClick)
        }
        // Building surfaces without a POI label can select a nearby, verified campus marker.
        map.setOnMapClickListener { point ->
            val screen = map.projection.toScreenLocation(point)
            val nearest = markers.filterKeys { it in visibleIds }.entries.map { entry ->
                entry.key to map.projection.toScreenLocation(entry.value.position).let { p ->
                    kotlin.math.hypot((p.x-screen.x).toDouble(),(p.y-screen.y).toDouble())
                }
            }.minByOrNull { it.second }
            if(nearest != null && nearest.second < 28*resources.displayMetrics.density)
                places.firstOrNull { it.id==nearest.first }?.let(onPlaceClick)
        }
        lifecycle.addObserver(this)
        contentDescription="高德校园地图，可拖动、缩放、旋转和倾斜"
    }

    fun show(campus: CampusMap, source: CampusGeoData?, catalogue: List<CampusPlace>, selected: String?, visible: Set<String>, dark: Boolean, top: Float, bottom: Float) {
        if(disposed) return
        if(lastStyle != dark) { map.mapType=if(dark) AMap.MAP_TYPE_NIGHT else AMap.MAP_TYPE_NORMAL;lastStyle=dark }
        // Keep the camera's target and the mandatory AMap attribution above the moving sheet.
        val mapHeight=(top + bottom.coerceAtLeast(top+40)).toInt().coerceIn(1,height.coerceAtLeast(1))
        if(width>0 && mapHeight!=lastMapHeight) {
            // Resize the native viewport so its natural center stays in the clear area.
            // A custom SDK center displaced native POI hit targets from their rendered labels.
            mapView.layoutParams=LayoutParams(-1,mapHeight);lastMapHeight=mapHeight
            map.uiSettings.setLogoLeftMargin((12*resources.displayMetrics.density).toInt())
            map.uiSettings.setLogoBottomMargin((mapHeight-bottom+12*resources.displayMetrics.density).toInt().coerceAtLeast(0))
        }
        val changed = selectedId!=selected || visibleIds!=visible || places!=catalogue
        places=catalogue
        selectedId=selected;visibleIds=visible
        // During category/search filtering, vendor POI labels must not contradict the
        // filtered school markers. The base roads and real building geometry stay visible.
        val textVisible=places.all { it.id in visible }
        if(lastTextVisible!=textVisible) { map.showMapText(textVisible);lastTextVisible=textVisible }
        val campusChanged=this.campus!=campus
        if(campusChanged || data!=source) {
            this.campus=campus;data=source
            if(campusChanged) { pendingFocus=null;map.stopAnimation() }
            redrawMarkers()
            if(source==null) report("正在加载"+campus.title+"…")
            else {
                val restoring=restorePending!=null
                restorePending=null
                if(!restoring) resetCamera()
                report("高德地图 · "+places.size+" 个地点")
                (pendingFocus ?: if(!restoring) places.firstOrNull { it.id==selectedId } else null)?.let(::focus)
            }
        } else if(changed) redrawMarkers()
    }
    fun focus(place: CampusPlace) {
        pendingFocus=place
        if(place.campus!=campus || data==null || selectedId!=place.id) return
        val poi=place.geo ?: return
        redrawMarkers()
        val current=map.cameraPosition
        animate(CameraPosition(poi.position,current.zoom.coerceIn(18f,19.5f),current.tilt.coerceAtLeast(35f),current.bearing))
        report(place.name+" · 高德地点")
        pendingFocus=null
    }
    fun resetCamera() { data?.center?.let { animate(CameraPosition(it.position,16.6f,45f,map.cameraPosition.bearing)) } }
    fun togglePitch() { val c=map.cameraPosition;animate(CameraPosition(c.target,c.zoom,if(c.tilt>5) 0f else 45f,c.bearing)) }
    fun rotateBy(degrees: Float) { val c=map.cameraPosition;animate(CameraPosition(c.target,c.zoom,c.tilt,c.bearing+degrees)) }
    fun zoomBy(factor: Float) { val c=map.cameraPosition;animate(CameraPosition(c.target,(c.zoom+ln(factor)/ln(2f)).coerceIn(3f,20f),c.tilt,c.bearing)) }
    private fun animate(camera: CameraPosition) { map.stopAnimation();map.animateCamera(CameraUpdateFactory.newCameraPosition(camera),680,null) }
    private fun report(value: String) { status=value;onStatus(value) }
    private fun redrawMarkers() {
        if(disposed) return
        clearMarkers()
        places.filter { it.id in visibleIds }.forEach { place ->
            val poi=place.geo ?: return@forEach
            val selected=place.id==selectedId
            val marker=map.addMarker(MarkerOptions().position(poi.position).title(place.name)
                .icon(icon(place,selected,selected || places.count { it.id in visibleIds }<=6))
                .anchor(.5f,1f).zIndex(if(selected) 10f else 1f))
            marker.`object`=place.id;markers[place.id]=marker
            if(selected) {
                selectedCircle=map.addCircle(CircleOptions().center(poi.position).radius(18.0)
                    .strokeColor(Color.rgb(35,123,98)).strokeWidth(3f).fillColor(0x33237B62).zIndex(2f))
            }
        }
    }
    private fun icon(place: CampusPlace, selected: Boolean, labelled: Boolean): BitmapDescriptor = icons.getOrPut(Triple(place.id,selected,labelled)) {
        val scale=resources.displayMetrics.density
        // At campus overview scale, let AMap place its own labels and use compact category pins.
        if(!labelled) {
            val size=(18*scale).toInt()
            val bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(bitmap)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE }
            canvas.drawCircle(size/2f,size/2f,8*scale,paint)
            paint.color=place.category.color.toInt()
            canvas.drawCircle(size/2f,size/2f,5.5f*scale,paint)
            return@getOrPut BitmapDescriptorFactory.fromBitmap(bitmap)
        }
        val textPaint=android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize=11*scale;typeface=android.graphics.Typeface.DEFAULT_BOLD
            color=if(selected) Color.WHITE else place.category.color.toInt()
        }
        val textWidth=minOf(textPaint.measureText(place.name).toInt()+1,(174*scale).toInt())
        val layout=android.text.StaticLayout.Builder.obtain(place.name,0,place.name.length,textPaint,textWidth)
            .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).setIncludePad(false)
            .setMaxLines(if(selected) 4 else 2).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
        val w=textWidth+(20*scale).toInt();val h=layout.height+(22*scale).toInt()
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color=if(selected) Color.rgb(35,123,98) else Color.WHITE
        canvas.drawRoundRect(1f,1f,w-1f,h-7*scale,9*scale,9*scale,paint)
        paint.color=if(selected) Color.WHITE else place.category.color.toInt()
        paint.style=Paint.Style.STROKE;paint.strokeWidth=1.5f*scale
        canvas.drawRoundRect(1f,1f,w-1f,h-7*scale,9*scale,9*scale,paint)
        canvas.save();canvas.translate(10*scale,6*scale);layout.draw(canvas);canvas.restore()
        paint.style=Paint.Style.FILL
        paint.color=place.category.color.toInt();canvas.drawCircle(w/2f,h-4*scale,3*scale,paint)
        BitmapDescriptorFactory.fromBitmap(bitmap)
    }
    private fun clearMarkers() { markers.values.forEach { it.remove() };markers.clear();selectedCircle?.remove();selectedCircle=null }
    override fun onResume(owner: LifecycleOwner) { if(!disposed) mapView.onResume() }
    override fun onPause(owner: LifecycleOwner) { if(!disposed) mapView.onPause() }
    override fun onDestroy(owner: LifecycleOwner) = dispose()
    fun dispose() {
        if(disposed) return
        disposed=true;lifecycle.removeObserver(this);map.stopAnimation();clearMarkers()
        mapView.onPause();mapView.onDestroy();icons.values.forEach { it.recycle() };icons.clear()
    }
}
