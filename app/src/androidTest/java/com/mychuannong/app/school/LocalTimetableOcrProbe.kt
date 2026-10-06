package com.mychuannong.app.school

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** Opt-in local diagnostic. Input/output stay in validation app private files, never fixtures/logs. */
@RunWith(AndroidJUnit4::class)
class LocalTimetableOcrProbe {
    @Test fun productionGridReaderOnLocalImage() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("localTimetableProbe") == "true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".validation"))
        val dir=File(context.filesDir,"timetable-probe")
        val bitmap=requireNotNull(BitmapFactory.decodeFile(File(dir,"input.jpg").path))
        try {
            val result=TimetableImageReader.read(bitmap)
            File(dir,"grid-result.json").writeText(com.google.gson.Gson().toJson(result))
            assertTrue(result.drafts.isNotEmpty())
            assertTrue(result.drafts.all { !it.reviewed })
        } finally { bitmap.recycle() }
    }
    @Test fun recognizeLocalImageWithoutImportingCourses() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("localTimetableProbe") == "true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".validation"))
        val dir=File(context.filesDir,"timetable-probe")
        val original=requireNotNull(BitmapFactory.decodeFile(File(dir,"input.jpg").path))
        // Two sizes allow evaluating consistency before choosing a production import strategy.
        val readings=JSONArray()
        val recognizer=TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        try {
            for(scale in listOf(1,2)) {
                val bitmap=if(scale==1) original else Bitmap.createScaledBitmap(original,original.width*scale,original.height*scale,false)
                try {
                    val text=Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap,0)),60,TimeUnit.SECONDS)
                    val lines=JSONArray()
                    text.textBlocks.flatMap { it.lines }.forEach { line ->
                        val rect=line.boundingBox ?: return@forEach
                        lines.put(JSONObject().put("text",line.text).put("left",rect.left/scale.toFloat())
                            .put("top",rect.top/scale.toFloat()).put("right",rect.right/scale.toFloat())
                            .put("bottom",rect.bottom/scale.toFloat()))
                    }
                    readings.put(JSONObject().put("scale",scale).put("lines",lines))
                } finally { if(bitmap!==original) bitmap.recycle() }
            }
            File(dir,"result.json").writeText(JSONObject().put("width",original.width).put("height",original.height).put("readings",readings).toString())
            assertTrue(readings.length()==2)
        } finally { recognizer.close();original.recycle() }
    }
}
