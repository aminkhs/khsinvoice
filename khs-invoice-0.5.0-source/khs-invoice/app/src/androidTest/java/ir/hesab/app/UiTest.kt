package ir.hesab.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.test.platform.app.InstrumentationRegistry
import ir.hesab.core.Money
import kotlinx.coroutines.runBlocking
import org.junit.*

class UiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Before fun prepare() = runBlocking {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as HesabApplication
        app.preferences.set("onboarded",true); app.preferences.set("sms",false); app.preferences.set("lock",false); app.preferences.set("toman",true)
        app.preferences.theme("light"); app.preferences.set("dynamic",false)
    }
    @Test fun manualEntryWithSmsDisabledAndNavigation() {
        compose.waitUntil(15000) { compose.onAllNodesWithText("خانه").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("＋").performClick()
        compose.onNodeWithText("مبلغ به تومان").performTextInput("123")
        screenshot("editor-test.png")
        compose.onNodeWithText("نام پذیرنده (اختیاری)").performScrollTo().performTextInput("نمونه مصنوعی آزمون")
        compose.onNodeWithText("ذخیره").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("نمونه مصنوعی آزمون").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("تراکنش‌ها").performClick()
        // The list builds only the rows on screen, so the row is scrolled to first.
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("نمونه مصنوعی آزمون"))
        compose.onNodeWithText("نمونه مصنوعی آزمون").assertExists()
        compose.onNodeWithText("گزارش").performClick()
        compose.onNodeWithText("تصویر پول تو").assertExists()
        compose.onNodeWithText("خانه").performClick()
        screenshot("home-test.png")
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as HesabApplication
        runBlocking { app.preferences.theme("dark") }
        compose.waitUntil(15000) { compose.onRoot().captureToImage().toPixelMap()[0,0].red<0.3f }
        screenshot("home-dark-test.png")
    }
    @Test fun initialOnboardingAllowsPermissionDenial() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as HesabApplication
        runBlocking { app.preferences.set("onboarded",false) }
        compose.waitUntil(15000) { compose.onAllNodesWithText("به KHS Invoice خوش آمدی").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("ادامه").performScrollTo().performClick()
        compose.onNodeWithText("ادامه").performScrollTo().performClick()
        compose.onNodeWithText("ادامه بدون مجوز").performScrollTo().performClick()
        compose.onNodeWithText("ادامه بدون مجوز").performScrollTo().performClick()
        repeat(3) { compose.onNodeWithText("ادامه").performScrollTo().performClick() }
        compose.onNodeWithText("شروع کن").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("خانه").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun clean() = runBlocking {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as HesabApplication
        app.db.openHelper.writableDatabase.execSQL("DELETE FROM transactions WHERE merchant=?",arrayOf("نمونه مصنوعی آزمون"))
        app.preferences.set("onboarded",false); app.preferences.set("sms",false)
        app.preferences.theme("system")
    }
    private fun screenshot(name: String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=java.io.File(context.getExternalFilesDir(null),name)
        val bitmap=if(name=="editor-test.png") compose.onNode(isDialog()).captureToImage().asAndroidBitmap() else compose.onRoot().captureToImage().asAndroidBitmap()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }
}
