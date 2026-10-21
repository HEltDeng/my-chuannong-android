package com.mychuannong.app.core.web

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mychuannong.app.core.theme.AppTheme

/** Shared by every native web header. Reads only URL, never form values or cookies. */
@Composable
fun WebMoreMenu(currentWeb: () -> WebView?, onClose: () -> Unit, onRefresh: (() -> Unit)? = null) {
    var confirmRefresh by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf<String?>(null) }
    var popup by remember { mutableStateOf<AnchoredWebMenu?>(null) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if(event == androidx.lifecycle.Lifecycle.Event.ON_STOP) popup?.dismiss()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); popup?.dismiss() }
    }
    AndroidView(modifier = Modifier.width(64.dp).height(48.dp), factory = { context ->
        TextView(context).apply {
            text = "更多"; contentDescription = "更多"; textSize = 16f
            setTextColor(Color.rgb(35,123,98)); gravity = Gravity.CENTER; isFocusable = true
            background = RippleDrawable(ColorStateList.valueOf(0x19237B62), null, android.graphics.drawable.ColorDrawable(Color.WHITE))
            setOnClickListener { anchor ->
                if(popup?.showing == true) { popup?.dismiss(); return@setOnClickListener }
                popup = AnchoredWebMenu(anchor, listOf("刷新", "在浏览器打开", "页面来源", "关闭页面")) { index ->
                    when(index) {
                        0 -> if(onRefresh != null) onRefresh() else if(currentWeb() != null) confirmRefresh = true else Toast.makeText(context, "页面尚未加载", Toast.LENGTH_SHORT).show()
                        1 -> openCurrentWebInBrowser(anchor, currentWeb()?.url)
                        2 -> source = runCatching { Uri.parse(currentWeb()?.url.orEmpty()).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: "页面尚未加载"
                        3 -> onClose()
                    }
                }.also { it.show() }
            }
        }
    })
    if(source != null) AlertDialog(onDismissRequest = { source = null }, containerColor = AppTheme.colors.bgCardColor,
        title = { Text("页面来源") }, text = { Text(source.orEmpty()) },
        confirmButton = { TextButton(onClick = { source = null }) { Text("知道了", color = AppTheme.colors.commonColor) } })
    if(confirmRefresh) AlertDialog(onDismissRequest = { confirmRefresh = false }, containerColor = AppTheme.colors.bgCardColor,
        title = { Text("刷新当前页面？") }, text = { Text("未提交的内容可能丢失。") },
        confirmButton = { TextButton(onClick = { confirmRefresh = false; currentWeb()?.reload() }) { Text("刷新", color = AppTheme.colors.commonColor) } },
        dismissButton = { TextButton(onClick = { confirmRefresh = false }) { Text("取消", color = AppTheme.colors.commonColor) } })
}

internal fun openCurrentWebInBrowser(anchor: View, url: String?) {
    if(url == null || !com.mychuannong.app.SafeUrl.https(url)) {
        Toast.makeText(anchor.context, "当前地址无法在浏览器打开", Toast.LENGTH_SHORT).show(); return
    }
    runCatching { anchor.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)) }
        .onFailure { Toast.makeText(anchor.context, "暂时无法打开浏览器", Toast.LENGTH_SHORT).show() }
}

/** Native school landing pages share the same measured anchor and popup geometry. */
@Composable
fun PageActionsMenu(actions: List<Pair<String, () -> Unit>>) {
    var popup by remember { mutableStateOf<AnchoredWebMenu?>(null) }
    val currentActions by rememberUpdatedState(actions)
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if(event == androidx.lifecycle.Lifecycle.Event.ON_STOP) popup?.dismiss()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); popup?.dismiss() }
    }
    AndroidView(modifier = Modifier.width(64.dp).height(48.dp), factory = { context ->
        TextView(context).apply {
            text="更多";contentDescription="更多";textSize=16f
            setTextColor(Color.rgb(35,123,98));gravity=Gravity.CENTER;isFocusable=true
            setOnClickListener { anchor ->
                if(popup?.showing==true) popup?.dismiss()
                else popup=AnchoredWebMenu(anchor,currentActions.map{it.first}) { index ->
                    currentActions.getOrNull(index)?.second?.invoke()
                }.also{it.show()}
            }
        }
    })
}

/** A focusable, touch-modal window consumes outside taps and Back before the web page. */
private class AnchoredWebMenu(private val anchor: View, labels: List<String>, action: (Int) -> Unit) {
    private val context = anchor.context
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private val window = PopupWindow(context)
    private val origin = IntArray(2)
    private var shownBounds = Rect()
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        val next = Rect(); anchor.getGlobalVisibleRect(next)
        if(next != shownBounds || !anchor.isAttachedToWindow) dismiss()
    }
    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) = dismiss()
    }
    val showing get() = window.isShowing
    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val content = ScrollView(context).apply { isFillViewport = false; addView(list); setPadding(dp(1), dp(16), dp(1), dp(8)); clipToPadding = true }
    private var consumed = false
    init {
        labels.forEachIndexed { index, label ->
            list.addView(TextView(context).apply {
                text = label; textSize = 16f; setTextColor(0xFF363C49.toInt()); gravity = Gravity.CENTER_VERTICAL
                minHeight = dp(50); setPadding(dp(16),dp(10),dp(16),dp(10)); isFocusable = true
                background = RippleDrawable(ColorStateList.valueOf(0x1A237B62), android.graphics.drawable.ColorDrawable(Color.WHITE), null)
                setOnClickListener { if(!consumed) { consumed = true; dismiss(); action(index) } }
            }, LinearLayout.LayoutParams(-1,-2))
            if(index < labels.lastIndex) list.addView(View(context).apply { setBackgroundColor(0xFFF0F3F8.toInt()) }, LinearLayout.LayoutParams(-1,dp(1).coerceAtLeast(1)))
        }
        window.contentView = content
        window.isFocusable = true
        window.isOutsideTouchable = true
        window.isTouchable = true
        window.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        window.elevation = dp(3).toFloat()
        window.setOnDismissListener {
            if(anchor.viewTreeObserver.isAlive) anchor.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
            anchor.removeOnAttachStateChangeListener(attachListener)
            if(anchor.isAttachedToWindow) anchor.requestFocus()
        }
    }
    fun show() {
        if(!anchor.isAttachedToWindow) return
        val frame = Rect(); anchor.getWindowVisibleDisplayFrame(frame)
        anchor.getLocationOnScreen(origin)
        anchor.getGlobalVisibleRect(shownBounds)
        val paint = Paint().apply { textSize = 16f * context.resources.displayMetrics.scaledDensity }
        val width = (paint.measureText("在浏览器打开").toInt() + dp(40)).coerceAtLeast(dp(156)).coerceAtMost((frame.width()-dp(24)).coerceAtLeast(1))
        content.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(frame.height(),View.MeasureSpec.AT_MOST))
        val height = content.measuredHeight.coerceAtMost((frame.height()-dp(24)).coerceAtLeast(1))
        val x = (origin[0]+anchor.width-width).coerceIn(frame.left+dp(12),(frame.right-dp(12)-width).coerceAtLeast(frame.left+dp(12)))
        val y = (origin[1]+anchor.height).coerceAtMost(frame.bottom-dp(12)-height).coerceAtLeast(frame.top+dp(12))
        val tip = (origin[0]+anchor.width/2-x).toFloat().coerceIn(dp(18).toFloat(),(width-dp(18)).toFloat())
        window.width = width; window.height = height
        window.setBackgroundDrawable(MenuOutline(tip,density))
        anchor.viewTreeObserver.addOnGlobalLayoutListener(layoutListener); anchor.addOnAttachStateChangeListener(attachListener)
        window.showAtLocation(anchor,Gravity.TOP or Gravity.LEFT,x,y)
    }
    fun dismiss() { if(window.isShowing) window.dismiss() }
}

/** One continuous contour for body and pointer; no seam under the pointer. */
private class MenuOutline(private val tip: Float, private val d: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val w=bounds.width().toFloat(); val h=bounds.height().toFloat(); val t=8*d; val r=8*d; val edge=d/2
        val p=Path().apply {
            moveTo(r,t); lineTo(tip-8*d,t); lineTo(tip,edge); lineTo(tip+8*d,t); lineTo(w-r,t)
            quadTo(w-edge,t,w-edge,t+r); lineTo(w-edge,h-r); quadTo(w-edge,h-edge,w-r,h-edge)
            lineTo(r,h-edge); quadTo(edge,h-edge,edge,h-r); lineTo(edge,t+r); quadTo(edge,t,r,t); close()
        }
        paint.color=Color.WHITE; paint.style=Paint.Style.FILL; canvas.drawPath(p,paint)
        paint.color=0xFFCEE8DE.toInt(); paint.style=Paint.Style.STROKE; paint.strokeWidth=d; canvas.drawPath(p,paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha=alpha }
    override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter=filter }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
