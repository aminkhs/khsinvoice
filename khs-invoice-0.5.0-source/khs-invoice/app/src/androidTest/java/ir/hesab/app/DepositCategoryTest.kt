package ir.hesab.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import ir.hesab.core.Kind
import ir.hesab.core.Status
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** A deposit is sorted into deposit categories, a payment into spending ones. */
class DepositCategoryTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as HesabApplication
    private val marker="نمونه مصنوعی واریز"
    @Before fun prepare() = runBlocking { app.preferences.set("onboarded",true); app.preferences.set("lock",false); app.preferences.set("toman",true) }
    private fun open(button: String,choice: String) { compose.onNodeWithText(button).performScrollTo().performClick(); compose.onNodeWithText(choice).performClick(); compose.waitForIdle() }
    @Test fun editorOffersCategoriesOfTheChosenSide() {
        compose.waitUntil(30000) { compose.onAllNodesWithText("＋").fetchSemanticsNodes().isNotEmpty() }
        // Categories are seeded on first start; wait for them.
        compose.waitUntil(30000) { runBlocking { app.db.dao().categories().any { it.id=="in-dong" } } }
        compose.onNodeWithText("＋").performClick()
        compose.onNodeWithText("مبلغ به تومان").performTextInput("250")
        // A payment offers spending categories only.
        compose.onNodeWithText("دسته: دسته‌بندی نشده").performScrollTo().performClick()
        compose.onNodeWithText("☕ کافه").assertExists(); compose.onNodeWithText("🤝 دنگ").assertDoesNotExist()
        compose.onNodeWithText("☕ کافه").performClick()
        compose.onNodeWithText("دسته: ☕ کافه").assertExists()
        // Switching to a deposit drops the spending choice and offers the deposit list.
        open("نوع: هزینه","واریز")
        compose.onNodeWithText("دسته: دسته‌بندی نشده").assertExists()
        compose.onNodeWithText("دسته: دسته‌بندی نشده").performScrollTo().performClick()
        listOf("💰 درآمد","🤝 دنگ","🤲 کمک","🎁 هدیه","📝 سایر").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("☕ کافه").assertDoesNotExist()
        compose.onNodeWithText("🤝 دنگ").performClick()
        compose.onNodeWithText("نام پذیرنده (اختیاری)").performScrollTo().performTextInput(marker)
        compose.onNodeWithText("ذخیره").performClick()
        compose.waitUntil(15000) { runBlocking { app.db.dao().transactions().any { it.merchant==marker } } }
        val saved=runBlocking { app.db.dao().transactions().single { it.merchant==marker } }
        assertEquals(Kind.INCOME,saved.kind); assertEquals("in-dong",saved.categoryId); assertEquals(Status.CONFIRMED,saved.status); assertEquals(2500L,saved.amountIrr)
    }
    @After fun clean() = runBlocking {
        app.db.openHelper.writableDatabase.execSQL("DELETE FROM transactions WHERE merchant=?",arrayOf(marker))
        app.preferences.set("onboarded",false)
    }
}
