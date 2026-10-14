package ir.hesab.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import ir.hesab.app.data.Transaction
import ir.hesab.core.Direction
import ir.hesab.core.Kind
import kotlinx.coroutines.runBlocking
import org.junit.*

/** A phone with years of bank messages holds thousands of transactions; every screen must still open. */
class LongHistoryTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as HesabApplication
    private val marker="نمونه مصنوعی تاریخچه بلند"
    @Before fun prepare() = runBlocking {
        app.preferences.set("onboarded",true); app.preferences.set("lock",false)
        val now=System.currentTimeMillis()
        repeat(4000) { n -> app.db.dao().insert(Transaction(kind=if(n%5==0) Kind.INCOME else Kind.EXPENSE,accountId=null,bankId=null,amountIrr=1000L*(n%400+1),direction=if(n%5==0) Direction.CREDIT else Direction.DEBIT,timestamp=now-n*3_600_000L,source="SMS",note=marker)) }
    }
    @Test fun transactionsAndReportOpenWithThousandsOfRows() {
        compose.waitUntil(30000) { compose.onAllNodesWithText("تراکنش‌ها").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("تراکنش‌ها").performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("فهرست تراکنش‌ها",substring=true).fetchSemanticsNodes().isNotEmpty() }
        // The last row is reachable without building the ones in between.
        compose.onNode(hasScrollToNodeAction()).performScrollToIndex(4000)
        compose.onNodeWithText("گزارش").performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("هزینه روزانه").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("خانه").performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("هزینه این ماه").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun clean() = runBlocking {
        app.db.openHelper.writableDatabase.execSQL("DELETE FROM transactions WHERE note=?",arrayOf(marker))
        app.preferences.set("onboarded",false)
    }
}
