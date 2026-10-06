package com.mychuannong.app.school

import android.graphics.Bitmap
import android.graphics.Color
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Bundled on-device recognition. No upload, diagnostic text log, or persistent source image. */
object TimetableImageReader {
    data class Result(val term: String, val drafts: List<TimetableImportRules.Draft>)
    fun read(bitmap: Bitmap, progress: (Int) -> Unit = {}): Result {
        require(bitmap.width in 700..6000 && bitmap.height in 300..6000) { "请选用清晰、完整的横版学期课表原图" }
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        fun recognize(image: Bitmap) = Tasks.await(recognizer.process(InputImage.fromBitmap(image, 0)), 40, TimeUnit.SECONDS)
        try {
            val overview = recognize(bitmap)
            val lines = overview.textBlocks.flatMap { it.lines }
            val headers = lines.filter { it.text.replace(" ", "").contains(Regex("星期[一二三四五六日天]")) }
            require(headers.size >= 5) { "没有找到完整星期栏，请选用包含星期一至星期日的原图" }
            val headerY = headers.mapNotNull { it.boundingBox?.centerY() }.sorted().let { it[it.size / 2] }
            val aligned = headers.filter { abs((it.boundingBox?.centerY() ?: 0) - headerY) < bitmap.height / 50 }
            val anchors = aligned.mapNotNull { line ->
                val day = listOf("一", "二", "三", "四", "五", "六", "日").indexOfFirst { line.text.contains("星期$it") }
                if (day < 0) null else day to (line.boundingBox?.centerX()?.toDouble() ?: 0.0)
            }.distinctBy { it.first }.sortedBy { it.first }
            require(anchors.size >= 5) { "星期栏识别不完整，请更换原图" }
            val widths = anchors.zipWithNext { a, b -> (b.second - a.second) / (b.first - a.first) }.sorted()
            val width = widths[widths.size / 2]
            val firstCenter = anchors.map { it.second - it.first * width }.average()
            require(width > 60 && anchors.all { abs(it.second - firstCenter - it.first * width) < width * .12 }) { "课表列位置不明确，请使用完整横版原图" }
            val left = (firstCenter - width / 2).toInt()
            val right = (left + 7 * width).toInt()
            require(left >= 0 && right <= bitmap.width) { "图片缺少课表左右边缘" }
            val boundaries = mutableListOf<Int>()
            val pixels = IntArray(bitmap.width)
            for (y in headerY + 1 until bitmap.height) {
                bitmap.getPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, 1)
                val dark = (left until right).count { x ->
                    val c = pixels[x]; Color.red(c) < 165 && Color.green(c) < 165 && Color.blue(c) < 165
                }
                if (dark > (right - left) * .90 && (boundaries.isEmpty() || y - boundaries.last() > 4)) boundaries.add(y)
                if (boundaries.size == 6) break
            }
            require(boundaries.size == 6 && boundaries.zipWithNext().all { (a,b) -> b-a > 25 }) {
                "没有找到完整的五行节次，请保留课表边框及第 1 至 10 节"
            }
            val drafts = mutableListOf<TimetableImportRules.Draft>()
            for (row in 0..4) for (day in 0..6) {
                val x = (left + day * width + 4).toInt()
                val y = boundaries[row] + 4
                val crop = Bitmap.createBitmap(bitmap, x, y, width.toInt() - 8, boundaries[row + 1] - y - 4)
                val enlarged = Bitmap.createScaledBitmap(crop, crop.width * 3, crop.height * 3, false)
                try {
                    val cellLines = recognize(enlarged).textBlocks.flatMap { it.lines }.sortedBy { it.boundingBox?.centerY() ?: 0 }
                    val rows = mutableListOf<MutableList<com.google.mlkit.vision.text.Text.Line>>()
                    cellLines.forEach { line ->
                        val last = rows.lastOrNull()
                        if (last != null && abs((last.first().boundingBox?.centerY() ?: 0) - (line.boundingBox?.centerY() ?: 0)) < (line.boundingBox?.height() ?: 10) / 2)
                            last.add(line) else rows.add(mutableListOf(line))
                    }
                    val text = rows.joinToString("\n") { group -> group.sortedBy { it.boundingBox?.left ?: 0 }.joinToString(" ") { it.text } }
                    drafts.addAll(TimetableImportRules.cell(text, day + 1, row * 2 + 1))
                } finally { enlarged.recycle(); crop.recycle() }
                progress((row * 7 + day + 1) * 100 / 35)
            }
            require(drafts.isNotEmpty()) { "没有识别到课程，请换用清晰原图；原有课表已保留" }
            val term = Regex("20\\d{2}[-－]20\\d{2}[-－][12]").find(overview.text)?.value?.replace('－','-').orEmpty()
            return Result(term, drafts)
        } finally { recognizer.close() }
    }
}
