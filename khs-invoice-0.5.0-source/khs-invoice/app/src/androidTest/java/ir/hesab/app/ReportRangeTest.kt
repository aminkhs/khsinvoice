package ir.hesab.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import ir.hesab.app.data.Transaction
import ir.hesab.core.Direction
import ir.hesab.core.Kind
import kotlinx.coroutines.runBlocking
import org.junit.*

/** Smoke test: every report range can follow any other, including shorter ones after longer ones. */
class ReportRangeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as HesabApplication
    private val marker="نمونه مصنوعی گزارش"
    @Before fun prepare() = runBlocking {
        app.preferences.set("onboarded",true); app.preferences.set("lock",false)
        val now=System.currentTimeMillis()
        repeat(12) { day -> app.repository.save(Transaction(kind=Kind.EXPENSE,accountId=null,bankId=null,amountIrr=10000L*(day+1),direction=Direction.DEBIT,timestamp=now-day*86_400_000L,merchant=marker)) }
    }
    private fun choose(from: String,to: String) {
        compose.onNodeWithText("بازه: $from").performScrollTo().performClick()
        compose.onNodeWithText(to).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("بازه: $to").assertExists()
    }
    @Test fun everyRangeCanBeSelectedAfterAnother() {
        compose.waitUntil(15000) { compose.onAllNodesWithText("گزارش").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("گزارش").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("هزینه روزانه").fetchSemanticsNodes().isNotEmpty() }
        val order=listOf("این ماه خورشیدی","امروز","این هفته","دیروز","همه زمان‌ها","دیروز","این سال خورشیدی","امروز","ماه خورشیدی قبل","این هفته","این ماه خورشیدی")
        order.zipWithNext().forEach { (from,to) ->
            choose(from,to)
            // Give the chart time to draw with the new data.
            compose.mainClock.advanceTimeBy(600)
            compose.waitForIdle()
        }
        compose.onNodeWithText("هزینه روزانه").assertExists()
    }
    @After fun clean() = runBlocking {
        app.db.openHelper.writableDatabase.execSQL("DELETE FROM transactions WHERE merchant=?",arrayOf(marker))
        app.preferences.set("onboarded",false)
    }
}
