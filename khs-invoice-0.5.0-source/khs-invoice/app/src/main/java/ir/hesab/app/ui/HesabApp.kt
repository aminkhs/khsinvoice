package ir.hesab.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import ir.hesab.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import ir.hesab.app.data.*
import ir.hesab.core.*

val kindNames=mapOf(Kind.EXPENSE to "هزینه",Kind.INCOME to "واریز",Kind.TRANSFER to "انتقال",Kind.FEE to "کارمزد",Kind.REFUND to "بازپرداخت خرید",Kind.REVERSAL to "برگشت تراکنش",Kind.BORROW to "دریافت قرض",Kind.LEND to "پرداخت قرض",Kind.DEBT_REPAYMENT to "بازپرداخت بدهی")
@Composable fun HesabApp(vm: HesabViewModel,unlocked: Boolean,unlock: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val edit by vm.editId.collectAsStateWithLifecycle()
    HesabTheme(state.preferences) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                when {
                    state.loading -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator(color=LocalKhsSemantics.current.focus) }
                    state.preferences.appLock && !unlocked -> Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) { Text("${stringResource(R.string.app_name)} قفل است",style=MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(24.dp)); KhsButton(onClick=unlock) { Text("باز کردن برنامه") } }
                    !state.preferences.onboarded -> Onboarding(state,vm)
                    else -> MainScreens(state,vm)
                }
                if(message!=null) AlertDialog(onDismissRequest={ vm.message.value=null },title={ Text(stringResource(R.string.app_name)) },text={ Text(message!!) },confirmButton={ KhsTextButton(onClick={ vm.message.value=null }) { Text("باشه") } })
                if(edit!=null && state.preferences.onboarded && (!state.preferences.appLock || unlocked)) TransactionEditor(state,vm,state.transactions.find { it.id==edit })
            }
        }
    }
}
@Composable private fun MainScreens(state: AppState,vm: HesabViewModel) {
    val nav=rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current=entry?.destination?.route ?: "home"
    val screens=listOf("home" to "خانه","transactions" to "تراکنش‌ها","analytics" to "گزارش","budgets" to "بودجه","settings" to "تنظیمات")
    Scaffold(containerColor=MaterialTheme.colorScheme.background,bottomBar={
        Surface(Modifier.padding(horizontal=8.dp,vertical=4.dp),shape=Khs.Card,color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
            NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp) {
                screens.forEach { (route,label) -> NavigationBarItem(selected=current==route,onClick={ nav.navigate(route) { popUpTo(nav.graph.startDestinationId) { saveState=true }; launchSingleTop=true; restoreState=true } },
                    colors=NavigationBarItemDefaults.colors(indicatorColor=MaterialTheme.colorScheme.primaryContainer,selectedIconColor=MaterialTheme.colorScheme.onPrimaryContainer,selectedTextColor=MaterialTheme.colorScheme.onSurface),
                    icon={ KhsNavigationIcon(route,current==route) },label={ Text(label,style=MaterialTheme.typography.labelMedium,maxLines=1) }) }
            }
        }
    },floatingActionButton={ if(current!="settings") FloatingActionButton(onClick={ vm.editId.value="new" },shape=Khs.Pill,containerColor=MaterialTheme.colorScheme.primaryContainer,contentColor=MaterialTheme.colorScheme.onPrimaryContainer) { Text("＋",fontSize=28.sp) } }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) { NavHost(nav,startDestination="home") {
            composable("home") { HomeScreen(state,vm) }
            composable("transactions") { TransactionsScreen(state,vm) }
            composable("analytics") { AnalyticsScreen(state) }
            composable("budgets") { BudgetsScreen(state,vm) }
            composable("settings") { SettingsScreen(state,vm) }
        } }
    }
}
@Composable fun Page(title: String,subtitle: String,content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gutter=if(maxWidth<600.dp) 16.dp else 24.dp
        Column(Modifier.widthIn(max=840.dp).fillMaxWidth().align(Alignment.TopCenter).verticalScroll(rememberScrollState()).padding(horizontal=gutter,vertical=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(title,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
            Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)
            content()
            Spacer(Modifier.height(72.dp))
        }
    }
}
@Composable fun Panel(title: String,content: @Composable ColumnScope.() -> Unit) {
    Card(shape=Khs.Card,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) { Text(title,style=MaterialTheme.typography.titleMedium); content() } }
}
@Composable fun EmptyState(title: String,body: String) { Panel(title) { Text(body,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable fun Select(label: String,choices: List<Pair<String,String>>,selected: String?,onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick={ expanded=true },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).khsFocusRing(Khs.Field),shape=Khs.Field,colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.onSurface)) { Text("$label: ${choices.find { it.first==selected }?.second ?: "انتخاب کنید"}") }
        DropdownMenu(expanded=expanded,onDismissRequest={ expanded=false }) { choices.forEach { (id,title) -> DropdownMenuItem(text={ Text(title) },onClick={ onSelect(id); expanded=false }) } }
    }
}
@Composable fun Toggle(title: String,checked: Boolean,onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(title,Modifier.weight(1f)); Switch(checked,onCheckedChange=onChange) } }
@Composable fun TransactionRow(t: Transaction,state: AppState,onClick: () -> Unit) {
    val category=state.categories.find { it.id==t.categoryId }
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Surface(shape=Khs.Field,color=MaterialTheme.colorScheme.secondaryContainer) { Text(category?.emoji ?: "؟",Modifier.padding(12.dp),fontSize=20.sp) }
        Column(Modifier.weight(1f)) { Text(t.merchant.ifBlank { category?.title ?: t.note.takeIf { it.isNotBlank() && it.length<=24 } ?: kindNames[t.kind] ?: "تراکنش" },fontWeight=FontWeight.Medium); Text("${Jalali.at(t.timestamp)} · ${state.accounts.find { it.id==t.accountId }?.nickname ?: if(t.source=="SMS") "حساب نیازمند بررسی" else "نقدی"}",style=MaterialTheme.typography.labelSmall); if(t.status!=Status.CONFIRMED) Text(if(t.status==Status.REVIEW) "نیازمند بررسی" else "دسته‌بندی نشده",color=LocalKhsSemantics.current.warning,style=MaterialTheme.typography.labelSmall) }
        Text(Money.format(t.amountIrr,state.preferences.toman),style=MaterialTheme.typography.labelMedium,color=if(t.direction==Direction.CREDIT) LocalKhsSemantics.current.success else MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold)
    }
}
@Composable private fun HomeScreen(state: AppState,vm: HesabViewModel) {
    val now=System.currentTimeMillis(); val date=Jalali.at(now); val range=Jalali.monthRange(now)
    val active=state.transactions.filter { it.deletedAt==null }
    val month=active.filter { it.timestamp in range }
    val totals=runCatching { Finance.totals(month.map { it.financeItem() }) }.getOrNull()
    Page(stringResource(R.string.app_name), "پول‌هات رو بهتر بشناس · ${Jalali.shownMonth(date.year,date.month)}") {
        Card(shape=Khs.Feature,colors=CardDefaults.cardColors(containerColor=Khs.Ink,contentColor=Color.White)) {
            Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("هزینه این ماه",style=MaterialTheme.typography.titleMedium)
                Text(totals?.let { Money.format(it.spending,state.preferences.toman) } ?: "مجموع خارج از محدوده",style=MaterialTheme.typography.headlineLarge,color=Khs.Lime)
                HorizontalDivider(color=Color.White.copy(alpha=.2f))
                Text("واریز  ${Money.format(totals?.income ?: 0,state.preferences.toman)}")
                Text("جریان نقدی  ${Money.format(totals?.cashFlow ?: 0,state.preferences.toman)}")
                Text("انتقال داخلی در هزینه و درآمد محاسبه نمی‌شود",style=MaterialTheme.typography.labelSmall)
            }
        }
        val budget=state.budgets.find { it.period=="${date.year}-${date.month}" && it.scope=="ALL" }
        if(budget!=null) BudgetPanel(budget,month,state)
        val top=month.filter { it.kind in setOf(Kind.EXPENSE,Kind.FEE) }.groupBy { it.categoryId }.maxByOrNull { Money.sum(it.value.map { row -> row.amountIrr }) }
        if(top!=null) Panel("بیشترین هزینه") { Text(state.categories.find { it.id==top.key }?.title ?: "دسته‌بندی نشده"); Text(Money.format(Money.sum(top.value.map { it.amountIrr }),state.preferences.toman)) }
        val pending=active.filter { it.status!=Status.CONFIRMED }
        if(pending.isNotEmpty()) Panel("منتظر دسته‌بندی · ${Money.persian(pending.size.toString())}") { pending.take(5).forEach { TransactionRow(it,state) { vm.editId.value=it.id } } }
        if(active.isEmpty()) EmptyState("شروع یک عادت خوب", "اولین هزینه یا درآمدت را با دکمه + ثبت کن. اتصال پیامک اختیاری است؛ اطلاعاتت روی همین دستگاه می‌ماند.")
        else Panel("تراکنش‌های اخیر") { active.take(6).forEach { TransactionRow(it,state) { vm.editId.value=it.id } } }
    }
}
@Composable private fun TransactionsScreen(state: AppState,vm: HesabViewModel) {
    var customStart by rememberSaveable { mutableStateOf("") }; var customEnd by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }; var account by rememberSaveable { mutableStateOf("ALL") }; var category by rememberSaveable { mutableStateOf("ALL") }; var kind by rememberSaveable { mutableStateOf("ALL") }; var deleted by rememberSaveable { mutableStateOf(false) }; var period by rememberSaveable { mutableStateOf("all") }
    val range=reportRange(period,customStart,customEnd)
    val rows=remember(state.transactions,state.categories,deleted,account,category,kind,range?.first,range?.last,search) {
        val titles=state.categories.associate { it.id to it.title }
        state.transactions.filter { (it.deletedAt!=null)==deleted && (account=="ALL" || it.accountId==account) && (category=="ALL" || it.categoryId==category || category=="pending" && it.categoryId==null) && (kind=="ALL" || it.kind.name==kind) && (range==null || it.timestamp in range) && (it.merchant+it.note+(titles[it.categoryId] ?: "")).contains(search,true) }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gutter=if(maxWidth<600.dp) 16.dp else 24.dp
        // Only the rows on screen are built. A bank history is thousands of rows; building them all at once ran the app out of memory.
        LazyColumn(Modifier.widthIn(max=840.dp).fillMaxWidth().align(Alignment.TopCenter),contentPadding=PaddingValues(start=gutter,end=gutter,top=24.dp,bottom=96.dp)) {
            item(key="filters") {
                Column(Modifier.padding(bottom=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text("تراکنش‌ها",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
                    Text("هر ثبت، یک تصویر روشن‌تر از عادت‌های مالی تو",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)
                    KhsTextField(search,{ search=it },label={ Text("جستجوی پذیرنده و یادداشت") },modifier=Modifier.fillMaxWidth())
                    Select("حساب",listOf("ALL" to "همه")+state.accounts.map { it.id to it.nickname },account) { account=it }
                    Select("دسته",listOf("ALL" to "همه","pending" to "دسته‌بندی نشده")+categoryChoices(state.categories),category) { category=it }
                    Select("نوع",listOf("ALL" to "همه")+kindNames.map { it.key.name to it.value },kind) { kind=it }
                    Select("بازه",periodChoices,period) { period=it }
                    CustomRangeInputs(period,customStart,customEnd,{ customStart=it },{ customEnd=it })
                    TransferSuggestions(state,vm)
                    Toggle("نمایش سطل بازیافت",deleted) { deleted=it }
                    if(rows.isEmpty()) EmptyState("تراکنشی پیدا نشد","فیلترها را تغییر بده یا اولین تراکنش را ثبت کن.")
                    else Text("${if(deleted) "تراکنش‌های حذف‌شده" else "فهرست تراکنش‌ها"} · ${Money.persian(rows.size.toString())}",style=MaterialTheme.typography.titleMedium)
                }
            }
            itemsIndexed(rows,key={ _,t -> t.id }) { index,t ->
                val top=if(index==0) 24.dp else 0.dp; val bottom=if(index==rows.lastIndex) 24.dp else 0.dp
                Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(topStart=top,topEnd=top,bottomStart=bottom,bottomEnd=bottom),color=MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(horizontal=20.dp)) {
                        TransactionRow(t,state) { vm.editId.value=t.id }
                        if(deleted) KhsTextButton(onClick={ vm.delete(t.id,true) }) { Text("بازیابی") }
                        if(index<rows.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
    }
}
@Composable private fun TransferSuggestions(state: AppState,vm: HesabViewModel) {
    var pair by remember { mutableStateOf<Pair<Transaction,Transaction>?>(null) }
    // Bank messages arrive as plain expense and income; a matching pair across two own accounts is probably one transfer.
    fun candidate(t: Transaction,direction: Direction) = t.direction==direction && (t.kind==Kind.TRANSFER || t.source=="SMS" && t.kind==(if(direction==Direction.DEBIT) Kind.EXPENSE else Kind.INCOME))
    // Credits are looked up by amount, so a long history is scanned once instead of once per debit.
    val suggestions=remember(state.transactions) {
        val rows=state.transactions.filter { it.deletedAt==null && it.transferId==null && it.accountId!=null }
        val credits=rows.filter { candidate(it,Direction.CREDIT) }.groupBy { it.amountIrr }
        rows.asSequence().filter { candidate(it,Direction.DEBIT) }.mapNotNull { debit -> credits[debit.amountIrr]?.firstOrNull { it.accountId!=debit.accountId && java.math.BigInteger.valueOf(it.timestamp).subtract(java.math.BigInteger.valueOf(debit.timestamp)).abs()<=java.math.BigInteger.valueOf(600000L) }?.let { debit to it } }.take(4).toList()
    }
    if(suggestions.isNotEmpty()) Panel("انتقال‌های احتمالی بین حساب‌های تو") {
        Text("زمان و مبلغ فقط پیشنهاد می‌دهند؛ ارتباط با تأیید تو ذخیره می‌شود.",style=MaterialTheme.typography.bodySmall)
        suggestions.forEach { (debit,credit) -> KhsTextButton(onClick={ pair=debit to credit }) { Text("${state.accounts.find { it.id==debit.accountId }?.nickname} ← ${state.accounts.find { it.id==credit.accountId }?.nickname} · ${Money.format(debit.amountIrr,state.preferences.toman)}") } }
    }
    pair?.let { (debit,credit) -> AlertDialog(onDismissRequest={ pair=null },title={ Text("تأیید انتقال داخلی") },text={ Text("این دو پیامک، دو سمت یک انتقال بین حساب‌های خودت هستند؟") },confirmButton={ KhsTextButton(onClick={ vm.matchTransfer(debit.id,credit.id); pair=null }) { Text("بله، مرتبط کن") } },dismissButton={ KhsTextButton(onClick={ pair=null }) { Text("انصراف") } }) }
}
@Composable private fun TransactionEditor(state: AppState,vm: HesabViewModel,old: Transaction?) {
    var amount by remember(old?.id) { mutableStateOf(old?.let { if(state.preferences.toman) (it.amountIrr/10).toString() else it.amountIrr.toString() } ?: "") }
    var exactIrr by remember(old?.id) { mutableStateOf(old?.amountIrr) }
    var kind by remember(old?.id) { mutableStateOf(old?.kind ?: Kind.EXPENSE) }
    var direction by remember(old?.id) { mutableStateOf(old?.direction ?: Direction.DEBIT) }
    var account by remember(old?.id) { mutableStateOf(old?.accountId ?: "cash") }
    var destination by remember { mutableStateOf("") }
    var category by remember(old?.id) { mutableStateOf(old?.categoryId ?: "pending") }
    var merchant by remember(old?.id) { mutableStateOf(old?.merchant ?: "") }
    var note by remember(old?.id) { mutableStateOf(old?.note ?: "") }
    var business by remember(old?.id) { mutableStateOf(old?.business ?: false) }
    var rememberRule by remember { mutableStateOf(false) }
    var refundOf by remember { mutableStateOf(old?.refundOf ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    val date=Jalali.at(old?.timestamp ?: System.currentTimeMillis())
    var dateText by remember { mutableStateOf("${Jalali.shownYear(date.year)}/${date.month}/${date.day}") }
    // Spending and deposits have separate categories; a choice from the other side does not carry over when the type changes.
    val choices=state.categories.filter { it.deposit==takesDepositCategory(kind,direction) }
    val chosen=category.takeIf { id -> id=="pending" || choices.any { it.id==id } } ?: "pending"
    AlertDialog(onDismissRequest={ vm.editId.value=null },title={ Text(if(old==null) "ثبت تراکنش" else "ویرایش تراکنش") },text={
        Column(Modifier.heightIn(max=550.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            KhsTextField(amount,{ amount=it; exactIrr=null },label={ Text(if(state.preferences.toman) "مبلغ به تومان" else "مبلغ به ریال") },singleLine=true)
            if(old!=null && state.preferences.toman && old.amountIrr%10!=0L) Text("مبلغ دقیق: ${Money.format(old.amountIrr)}؛ برای حفظ کسر ریال، مبلغ را تغییر ندهید",style=MaterialTheme.typography.labelSmall)
            Select("نوع",kindNames.map { it.key.name to it.value },kind.name) { kind=Kind.valueOf(it); direction=if(kind in setOf(Kind.INCOME,Kind.REFUND,Kind.REVERSAL,Kind.BORROW)) Direction.CREDIT else Direction.DEBIT }
            Select("حساب",listOf("cash" to "نقدی")+state.accounts.filter { it.active }.map { it.id to it.nickname },account) { account=it }
            if(kind==Kind.TRANSFER && old==null) Select("حساب مقصد",state.accounts.filter { it.active && it.id!=account }.map { it.id to it.nickname },destination) { destination=it }
            if(kind in setOf(Kind.TRANSFER,Kind.DEBT_REPAYMENT)) Select("جهت",listOf("DEBIT" to "پرداخت","CREDIT" to "دریافت"),direction.name) { direction=Direction.valueOf(it) }
            Select("دسته",listOf("pending" to "دسته‌بندی نشده")+choices.map { it.id to "${it.emoji} ${it.title}" },chosen) { category=it }
            KhsTextField(merchant,{ merchant=it },label={ Text("نام پذیرنده (اختیاری)") })
            KhsTextField(note,{ note=it },label={ Text("یادداشت") })
            KhsTextField(dateText,{ dateText=it },label={ Text("تاریخ خورشیدی: سال/ماه/روز") })
            if(old?.bankDateText!=null) Text("تاریخ متن بانک: ${Jalali.shownDateText(old.bankDateText)} · تاریخ گزارش را بررسی کنید",style=MaterialTheme.typography.labelSmall)
            if(kind in setOf(Kind.REFUND,Kind.REVERSAL)) Select("خرید مرتبط",listOf("" to "بدون ارتباط")+state.transactions.asSequence().filter { it.kind==Kind.EXPENSE && it.deletedAt==null }.take(100).toList().map { it.id to "${it.merchant.ifBlank { "خرید" }} · ${Money.format(it.amountIrr)}" },refundOf) { refundOf=it }
            Toggle("کاری",business) { business=it }
            if(merchant.isNotBlank() && chosen!="pending") Toggle("دسته را برای این پذیرنده به خاطر بسپار",rememberRule) { rememberRule=it }
            if(old?.transferId!=null) Text("این تراکنش به انتقال داخلی متصل است. مبلغ هر دو سمت را یکسان نگه دارید.")
            if(error!=null) Text(error!!,color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={ KhsTextButton(onClick={
        val value=exactIrr ?: Money.parse(amount,state.preferences.toman)
        if(value==null) { error="مبلغ معتبر وارد کنید"; return@KhsTextButton }
        val timestamp=runCatching { val parts=Money.normalize(dateText).split('/').map(String::toInt); require(parts.size==3); Jalali.toGregorian(PersianDate(Jalali.storedYear(parts[0]),parts[1],parts[2])).atTime(java.time.Instant.ofEpochMilli(old?.timestamp ?: System.currentTimeMillis()).atZone(Jalali.zone).toLocalTime()).atZone(Jalali.zone).toInstant().toEpochMilli() }.getOrNull()
        if(timestamp==null) { error="تاریخ خورشیدی معتبر وارد کنید"; return@KhsTextButton }
        if(kind==Kind.TRANSFER && old==null && destination.isNotEmpty()) { if(account=="cash" || account==destination) { error="دو حساب متفاوت انتخاب کنید"; return@KhsTextButton }; vm.transfer(account,destination,value); vm.editId.value=null }
        else { val base=old ?: Transaction(kind=kind,accountId=null,bankId=null,amountIrr=value,direction=direction,timestamp=timestamp); vm.save(base.copy(kind=kind,accountId=account.takeUnless { it=="cash" },bankId=state.accounts.find { it.id==account }?.bankId,amountIrr=value,direction=direction,timestamp=timestamp,categoryId=chosen.takeUnless { it=="pending" },merchant=merchant.trim(),note=note,business=business,refundOf=refundOf.takeIf { it.isNotEmpty() },status=if(chosen=="pending" || kind==Kind.TRANSFER && old?.transferId==null) Status.PENDING else Status.CONFIRMED),rememberRule) }
    }) { Text("ذخیره") } },dismissButton={ Row { KhsTextButton(onClick={ vm.editId.value=null }) { Text("انصراف") }; if(old!=null && old.deletedAt==null) KhsTextButton(onClick={ vm.delete(old.id); vm.editId.value=null }) { Text("حذف") } } })
}
@Composable private fun BudgetPanel(b: Budget,rows: List<Transaction>,state: AppState) {
    val spent=Finance.totals(rows.filter { b.scope=="ALL" || it.categoryId==b.scope }.map { it.financeItem() }).spending
    val remaining=java.math.BigInteger.valueOf(b.limitIrr).subtract(java.math.BigInteger.valueOf(spent))
    Panel(if(b.scope=="ALL") "بودجه کل ماه" else state.categories.find { it.id==b.scope }?.title ?: "بودجه دسته") {
        Text("${Money.format(spent,state.preferences.toman)} از ${Money.format(b.limitIrr,state.preferences.toman)}")
        LinearProgressIndicator(progress={ (spent.toDouble()/b.limitIrr).toFloat().coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth(),color=if(spent>b.limitIrr) MaterialTheme.colorScheme.error else LocalKhsSemantics.current.focus,trackColor=MaterialTheme.colorScheme.surfaceVariant)
        Text(if(remaining.signum()<0) "بیش از بودجه هزینه کردی" else "باقی‌مانده: ${Money.format(remaining.toLong(),state.preferences.toman)}",color=if(spent>b.limitIrr) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}
@Composable private fun BudgetsScreen(state: AppState,vm: HesabViewModel) {
    var rollover by rememberSaveable { mutableStateOf(false) }
    var amount by rememberSaveable { mutableStateOf("") }; var scope by rememberSaveable { mutableStateOf("ALL") }
    val now=System.currentTimeMillis(); val date=Jalali.at(now); val month=state.transactions.filter { it.deletedAt==null && it.timestamp in Jalali.monthRange(now) }
    Page("بودجه","برای چیزهایی که برات مهم‌اند، جا باز کن") {
        Panel("بودجه این ماه") {
            Select("دسته",listOf("ALL" to "کل ماه")+state.categories.filter { !it.deposit }.map { it.id to it.title },scope) { scope=it }
            KhsTextField(amount,{ amount=it },label={ Text(if(state.preferences.toman) "سقف بودجه به تومان" else "سقف بودجه به ریال") })
            Toggle("انتقال مانده مثبت به ماه بعد",rollover) { rollover=it }
            KhsButton(onClick={ val value=Money.parse(amount,state.preferences.toman); if(value==null) vm.message.value="مبلغ معتبر وارد کنید" else { vm.budget(value,scope.takeUnless { it=="ALL" },rollover); amount="" } }) { Text("ذخیره بودجه") }
            Text("هشدار در ۷۰٪، ۹۰٪ و ۱۰۰٪؛ هر آستانه یک‌بار در ماه",style=MaterialTheme.typography.labelSmall)
        }
        val current=state.budgets.filter { it.period=="${date.year}-${date.month}" }
        if(current.isEmpty()) EmptyState("بودجه‌ای تعیین نشده","یک سقف ماهانه برای کل هزینه‌ها یا یک دسته انتخاب کن.")
        current.forEach { BudgetPanel(it,month,state) }
        val historical=state.budgets.filter { it.period!="${date.year}-${date.month}" }
        if(historical.isNotEmpty()) Panel("بودجه‌های گذشته") { historical.forEach { Text("${shownPeriod(it.period)} · ${Money.format(it.limitIrr,state.preferences.toman)}") } }
    }
}
/** Budget periods are stored as «1405-7»; the owner sees the imperial year. */
private fun shownPeriod(period: String) = period.split('-').mapNotNull(String::toIntOrNull).takeIf { it.size==2 }?.let { Jalali.shownMonth(it[0],it[1]) } ?: Money.persian(period)
/** One list for filters: spending categories first, deposit ones marked so the two «سایر» can be told apart. */
fun categoryChoices(categories: List<Category>) = categories.sortedBy { it.deposit }.map { it.id to if(it.deposit) "${it.title} · واریز" else it.title }
val periodChoices=listOf("all" to "همه زمان‌ها","today" to "امروز","yesterday" to "دیروز","week" to "این هفته","month" to "این ماه خورشیدی","previous" to "ماه خورشیدی قبل","year" to "این سال خورشیدی","custom" to "بازه دلخواه")
@Composable fun CustomRangeInputs(period: String,start: String,end: String,onStart: (String) -> Unit,onEnd: (String) -> Unit) {
    if(period=="custom") {
        KhsTextField(start,onStart,label={ Text("از تاریخ خورشیدی: سال/ماه/روز") },modifier=Modifier.fillMaxWidth())
        KhsTextField(end,onEnd,label={ Text("تا تاریخ خورشیدی: سال/ماه/روز") },modifier=Modifier.fillMaxWidth())
        if(reportRange(period,start,end)?.isEmpty()==true) Text("دو تاریخ معتبر به ترتیب وارد کنید",color=MaterialTheme.colorScheme.error)
    }
}
fun reportRange(period: String,customStart: String="",customEnd: String=""): LongRange? {
    val now=java.time.ZonedDateTime.now(Jalali.zone); val today=now.toLocalDate(); val date=Jalali.fromGregorian(today)
    fun range(start: java.time.LocalDate,end: java.time.LocalDate) = start.atStartOfDay(Jalali.zone).toInstant().toEpochMilli() until end.atStartOfDay(Jalali.zone).toInstant().toEpochMilli()
    return when(period) {
        "today" -> range(today,today.plusDays(1)); "yesterday" -> range(today.minusDays(1),today)
        "week" -> range(today.minusDays(((today.dayOfWeek.value+1)%7).toLong()),today.plusDays(1))
        "month" -> Jalali.monthRange(System.currentTimeMillis())
        "previous" -> { val start=Jalali.toGregorian(PersianDate(date.year,date.month,1)); Jalali.monthRange(start.minusDays(1).atStartOfDay(Jalali.zone).toInstant().toEpochMilli()) }
        "year" -> range(Jalali.startYear(date.year),Jalali.startYear(date.year+1))
        "custom" -> runCatching { fun parse(text: String): java.time.LocalDate { val p=Money.normalize(text).split('/').map(String::toInt); require(p.size==3); return Jalali.toGregorian(PersianDate(Jalali.storedYear(p[0]),p[1],p[2])) }; val start=parse(customStart); val end=parse(customEnd); require(end>=start); range(start,end.plusDays(1)) }.getOrElse { LongRange.EMPTY }
        else -> null
    }
}
@Composable private fun Onboarding(state: AppState,vm: HesabViewModel) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val context=LocalContext.current
    val sms=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.preference("sms",granted); step=3 }
    val notification=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { step=4 }
    val titles=listOf("به ${stringResource(R.string.app_name)} خوش آمدی","اطلاعاتت پیش خودت می‌ماند","پیامک‌های بانکی","یادآوری دسته‌بندی","واحد نمایش پول","فرستنده‌های مورد اعتماد","وارد کردن پیامک‌های قدیمی","حساب‌های تو")
    Page(titles[step],"${Money.persian((step+1).toString())} از ۸ · هر وقت خواستی در تنظیمات تغییر بده") {
        when(step) {
            0 -> { Surface(shape=Khs.Feature,color=Khs.Ink) { Text(stringResource(R.string.app_name),Modifier.fillMaxWidth().padding(24.dp),style=MaterialTheme.typography.displayMedium,color=Khs.Lime) }; Text("بدون اینترنت، خرج‌ها و درآمدهات را بشناس. دستی ثبت کن یا پیامک‌های بانکی را روی همین گوشی به تراکنش تبدیل کن.",style=MaterialTheme.typography.titleLarge) }
            1 -> { Panel("خصوصی، روی همین دستگاه") { Text("پیامک بانکی محلی پردازش می‌شود. رمز یک‌بارمصرف رد می‌شود؛ متن پیامک به گزارش‌ها و پشتیبان راه پیدا نمی‌کند. حساب کاربری و سرویس ابری نداریم."); Text("پایگاه Room رمزگذاری نشده و از فضای خصوصی Android محافظت می‌گیرد. فایل پشتیبان با گذرواژه رمزگذاری می‌شود.") } }
            2 -> { Text("مجوز دریافت پیامک برای ثبت خودکار هزینه‌هاست. بدون این مجوز هم ثبت دستی کار می‌کند."); KhsButton(onClick={ sms.launch(Manifest.permission.RECEIVE_SMS) }) { Text("اجازه دریافت پیامک") } }
            3 -> { Text("برای هزینه جدید ازت می‌پرسیم: برای چی بود؟ جزئیات روی صفحه قفل پنهان می‌ماند."); KhsButton(onClick={ if(Build.VERSION.SDK_INT>=33) notification.launch(Manifest.permission.POST_NOTIFICATIONS) else step=4 }) { Text("فعال کردن اعلان‌ها") } }
            4 -> Toggle("نمایش به تومان (۱۰ ریال = ۱ تومان)",state.preferences.toman) { vm.preference("toman",it) }
            5 -> { SmsSetupPanel(state,vm); SenderPanel(state,vm) }
            6 -> { Text("اگر در مرحله قبل بانک‌ها را خودکار پیدا کردی، خواندن پیامک‌های قبلی شروع شده است. این دکمه همان کار را دوباره انجام می‌دهد و تراکنش تکراری نمی‌سازد."); HistoryImport(state,vm) }
            7 -> { AccountPanel(state,vm); Text("برای هر بانکی که پیامک دارد، حساب خودکار ساخته می‌شود. می‌توانی با پول نقد شروع کنی و حساب‌ها را بعداً اضافه کنی.") }
        }
        KhsButton(onClick={ if(step<7) step++ else vm.preference("onboarded",true) },modifier=Modifier.fillMaxWidth()) { Text(if(step==7) "شروع کن" else if(step in 2..3) "ادامه بدون مجوز" else "ادامه") }
    }
}



