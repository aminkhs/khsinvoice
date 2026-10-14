package ir.hesab.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.content.res.ResourcesCompat
import ir.hesab.app.R
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import ir.hesab.core.*

@Composable fun AnalyticsScreen(state: AppState) {
    var customStart by rememberSaveable { mutableStateOf("") }; var customEnd by rememberSaveable { mutableStateOf("") }
    var period by rememberSaveable { mutableStateOf("month") }; var account by rememberSaveable { mutableStateOf("ALL") }; var bank by rememberSaveable { mutableStateOf("ALL") }; var card by rememberSaveable { mutableStateOf("ALL") }
    val range=reportRange(period,customStart,customEnd)
    val rows=state.transactions.filter { it.deletedAt==null && (range==null || it.timestamp in range) && (account=="ALL" || it.accountId==account) && (bank=="ALL" || it.bankId==bank) && (card=="ALL" || it.cardId==card) }
    val expenses=rows.filter { it.kind in setOf(Kind.EXPENSE,Kind.FEE) }
    val categories=expenses.groupBy { it.categoryId }.map { (id,values) -> (state.categories.find { it.id==id }?.title ?: "دسته‌بندی نشده") to Money.sum(values.map { it.amountIrr }) }.sortedByDescending { it.second }
    val deposits=rows.filter { it.kind==Kind.INCOME }.groupBy { it.categoryId }.map { (id,values) -> (state.categories.find { it.id==id }?.title ?: "دسته‌بندی نشده") to Money.sum(values.map { it.amountIrr }) }.sortedByDescending { it.second }
    Page("تصویر پول تو","گزارش واقعی از ثبت‌های تو؛ انتقال داخلی از هزینه حذف شده") {
        Select("بازه",periodChoices,period) { period=it }
        CustomRangeInputs(period,customStart,customEnd,{ customStart=it },{ customEnd=it })
        Select("بانک",listOf("ALL" to "همه")+Banks.all.map { it.id to it.title },bank) { bank=it }
        Select("حساب",listOf("ALL" to "همه")+state.accounts.map { it.id to it.nickname },account) { account=it }
        Select("کارت",listOf("ALL" to "همه")+state.cards.map { it.id to "کارت ${Money.persian(it.lastFour)}" },card) { card=it }
        if(rows.isEmpty()) EmptyState("هنوز تصویری نداریم","چند تراکنش ثبت کن تا الگوی هزینه‌ها اینجا شکل بگیرد.")
        else {
            val totals=Finance.totals(rows.map { it.financeItem() })
            Panel("واریز و هزینه خالص") { Bars(listOf("واریز" to totals.income,"هزینه خالص" to totals.spending),state); Text("بدون دسته: ${Money.format(totals.unclassified,state.preferences.toman)}"); Text("بازپرداخت‌ها از هزینه خالص کسر می‌شوند؛ توزیع دسته‌ها، خریدهای ناخالص را نشان می‌دهد.",style=MaterialTheme.typography.labelSmall) }
            if(categories.isNotEmpty()) Panel("سهم دسته‌ها · هزینه ناخالص") { Donut(categories); Bars(categories,state) }
            if(deposits.isNotEmpty()) Panel("سهم دسته‌ها · واریز") { Bars(deposits,state) }
            val days=expenses.groupBy { Jalali.at(it.timestamp).toString() }.map { it.key to Money.sum(it.value.map { t -> t.amountIrr }) }.reversed()
            if(days.isNotEmpty()) Panel("هزینه روزانه") { DailyChart(days); Text("محور عمودی: ریال · ترتیب روزها از قدیم به جدید",style=MaterialTheme.typography.labelSmall); Bars(days.takeLast(7),state) }
            val months=expenses.groupBy { Jalali.at(it.timestamp).let { d -> Jalali.shownMonth(d.year,d.month) } }.map { it.key to Money.sum(it.value.map { t -> t.amountIrr }) }
            Panel("روند ماهانه") { Bars(months.reversed(),state) }
            Panel("استفاده از حساب‌ها") { Bars(expenses.groupBy { it.accountId }.map { (id,v) -> (state.accounts.find { it.id==id }?.nickname ?: "نقدی / نامشخص") to Money.sum(v.map { it.amountIrr }) },state) }
            Panel("بانک‌ها") { Bars(expenses.groupBy { it.bankId }.map { (id,v) -> (Banks.all.find { it.id==id }?.title ?: "نقدی") to Money.sum(v.map { it.amountIrr }) },state) }
            Panel("کارت‌ها") { Bars(expenses.groupBy { it.cardId }.map { (id,v) -> (state.cards.find { it.id==id }?.let { "کارت ${Money.persian(it.lastFour)}" } ?: "کارت نامشخص / نقدی") to Money.sum(v.map { it.amountIrr }) },state) }
            Panel("پذیرنده‌های پرهزینه") { Bars(expenses.groupBy { it.merchant.ifBlank { "پذیرنده نامشخص" } }.map { it.key to Money.sum(it.value.map { t -> t.amountIrr }) }.sortedByDescending { it.second }.take(10),state) }
            Panel("شخصی و کاری") { Bars(expenses.groupBy { it.business }.map { (business,v) -> (if(business) "کاری" else "شخصی") to Money.sum(v.map { it.amountIrr }) },state) }
        }
    }
}
@Composable private fun Bars(values: List<Pair<String,Long>>,state: AppState) {
    val max=values.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    if(values.isEmpty()) Text("داده‌ای در این بازه نیست")
    values.forEach { (title,value) ->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(title,Modifier.weight(1f)); Text(Money.format(value,state.preferences.toman),style=MaterialTheme.typography.labelMedium) }
        LinearProgressIndicator(progress={ (value.toDouble()/max).toFloat().coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth(),color=LocalKhsSemantics.current.focus,trackColor=MaterialTheme.colorScheme.surfaceVariant)
    }
}
@Composable private fun Donut(values: List<Pair<String,Long>>) {
    val colors=if(MaterialTheme.colorScheme.onSurface.luminance()>.5f) listOf(Khs.Mint,Khs.Sky,Khs.Butter,Khs.Lilac,Color(0xFF98C9D1),Color(0xFFF3AFC4),Color(0xFFC0D195),Color.White) else Khs.ChartColors
    val total=Money.sum(values.map { it.second }).coerceAtLeast(1L)
    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        val diameter=size.minDimension-30.dp.toPx(); val origin=androidx.compose.ui.geometry.Offset((size.width-diameter)/2,(size.height-diameter)/2)
        var start=-90f
        values.forEachIndexed { index,(_,amount) -> val sweep=(amount.toDouble()/total*360).toFloat(); drawArc(colors[index%colors.size],start,sweep,false,origin,androidx.compose.ui.geometry.Size(diameter,diameter),style=Stroke(26.dp.toPx())); start+=sweep }
    }
}
private val dayLabels=ExtraStore.Key<List<String>>()
@Composable private fun DailyChart(values: List<Pair<String,Long>>) {
    val producer=remember { CartesianChartModelProducer() }
    // The labels travel with the data. After the range changes the chart still draws the previous
    // days for a moment, and a label looked up in the new list would be missing; the chart library
    // stops the app on an empty label.
    LaunchedEffect(values) { producer.runTransaction { columnSeries { series(values.map { it.second }) }; extras { it[dayLabels]=values.map { day -> day.first.takeLast(5) } } } }
    val theme=rememberM3VicoTheme().copy(columnCartesianLayerColors=listOf(LocalKhsSemantics.current.focus),lineCartesianLayerColors=listOf(LocalKhsSemantics.current.focus),textColor=MaterialTheme.colorScheme.onSurfaceVariant,lineColor=MaterialTheme.colorScheme.outlineVariant)
    val context=LocalContext.current
    val typeface=remember(context) { ResourcesCompat.getFont(context,R.font.estedad) ?: android.graphics.Typeface.DEFAULT }
    val amountFormatter=remember { CartesianValueFormatter { _,value,_ -> Money.persian(value.toLong().toString()) } }
    val dayFormatter=remember { CartesianValueFormatter { context,value,_ -> context.model.extraStore.getOrNull(dayLabels)?.getOrNull(value.toInt())?.takeIf { it.isNotEmpty() } ?: "·" } }
    ProvideVicoTheme(theme) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            val label=rememberAxisLabelComponent(typeface=typeface,textSize=12.sp)
            CartesianChartHost(chart=rememberCartesianChart(rememberColumnCartesianLayer(),startAxis=VerticalAxis.rememberStart(label=label,valueFormatter=amountFormatter),bottomAxis=HorizontalAxis.rememberBottom(label=label,valueFormatter=dayFormatter)),modelProducer=producer,modifier=Modifier.fillMaxWidth().height(210.dp))
        }
    }
}
