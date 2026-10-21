package com.mychuannong.app.school
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mychuannong.app.core.account.AccountAvatarStore
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class AccountAvatarIsolationTest {
    @Test fun persistsOnlyForOwnerAndInvalidReplacementPreservesImage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val owner = "avatar-test-" + java.util.UUID.randomUUID()
        val other = "avatar-test-" + java.util.UUID.randomUUID()
        val source = File(context.cacheDir, owner + ".png")
        val bitmap = Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        try {
            AccountAvatarStore.save(context,owner,Uri.fromFile(source))
            val saved = requireNotNull(AccountAvatarStore.read(context,owner))
            val before = saved.readBytes()
            assertTrue(before.isNotEmpty())
            assertNull(AccountAvatarStore.read(context,other))
            assertNull(AccountAvatarStore.read(context,null))
            source.writeText("not an image")
            assertTrue(runCatching { AccountAvatarStore.save(context,owner,Uri.fromFile(source)) }.isFailure)
            assertArrayEquals(before,requireNotNull(AccountAvatarStore.read(context,owner)).readBytes())
        } finally {
            source.delete()
            AccountAvatarStore.read(context,owner)?.delete()
        }
    }
}
