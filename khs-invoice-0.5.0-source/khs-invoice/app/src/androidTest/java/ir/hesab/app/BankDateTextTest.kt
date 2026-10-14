package ir.hesab.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import ir.hesab.app.data.Transaction
import ir.hesab.core.Direction
import ir.hesab.core.Kind
import kotlinx.coroutines.runBlocking
import org.junit.*

/** The date quoted from the bank message is shown with the same year count as every other date. */
class BankDateTextTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as HesabApplication
    private val marker="نمونه مصنوعی تاریخ بانک"
    @Before fun prepare() = runBlocking {
        app.preferences.set("onboarded",true); app.preferences.set("lock",false)
        app.db.dao().insert(Transaction(kind=Kind.EXPENSE,accountId=null,bankId=null,amountIrr=5000,direction=Direction.DEBIT,timestamp=System.currentTimeMillis()+60000,merchant=marker,source="SMS",bankDateText="1404/07/12 13:45"))
        Unit
    }
    @Test fun editorShowsBankDateInShownYear() {
        compose.waitUntil(30000) { compose.onAllNodesWithText(marker).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText(marker).onFirst().performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("ویرایش تراکنش").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("۲۵۸۴/۰۷/۱۲ ۱۳:۴۵",substring=true).assertExists()
        compose.onAllNodesWithText("1404",substring=true).assertCountEquals(0)
    }
    @After fun clean() = runBlocking {
        app.db.openHelper.writableDatabase.execSQL("DELETE FROM transactions WHERE merchant=?",arrayOf(marker))
        app.preferences.set("onboarded",false)
    }
}
