package ir.hesab.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.work.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.hesab.app.data.*
import ir.hesab.app.sms.HistoryWorker
import ir.hesab.core.*
import kotlinx.coroutines.*

@Composable fun SettingsScreen(state: AppState,vm: HesabViewModel) {
    val reviews by vm.reviewCount.collectAsStateWithLifecycle()
    val lastImport by vm.lastImport.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val historyFlow=remember(context) { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow("history") }
    val historyJobs by historyFlow.collectAsStateWithLifecycle(initialValue=emptyList())
    val historyJob=historyJobs.maxByOrNull { job -> job.tags.firstOrNull { it.startsWith("history-run-") } ?: "" }
    var category by rememberSaveable { mutableStateOf("") }; var categorySide by rememberSaveable { mutableStateOf("spending") }
    Page("تنظیمات","همه‌چیز به روش خودت") {
        SmsSetupPanel(state,vm)
        SenderPanel(state,vm)
        HistoryImport(state,vm)
        Panel("وضعیت پردازش") {
            Text("پیام‌های بانکی مبهم: ${Money.persian(reviews.toString())}")
            if(reviews>0) Text("برای این پیام‌ها مبلغ نامطمئن ساخته نشده است. متن آن‌ها را با دکمه «پیامک‌های بانکی که ثبت نشده‌اند» ببین و در صورت نیاز دستی ثبت کن.",style=MaterialTheme.typography.bodySmall)
            if(historyJob==null) lastImport?.let { Text(if(it.completedAt==null) "وارد کردن قبلی کامل نشده؛ دوباره تلاش کنید" else "آخرین وارد کردن: ${Money.persian(it.count.toString())} تراکنش جدید · ${Jalali.at(it.completedAt)}") }
            historyJob?.let { job ->
                when(job.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> Text("خواندن تاریخچه در صف است")
                    WorkInfo.State.RUNNING -> Text("در حال خواندن تاریخچه: ${Money.persian(job.progress.getInt("matched",0).toString())} پیامک از فرستنده‌های مجاز")
                    WorkInfo.State.FAILED -> Text(job.outputData.getString("error") ?: "خواندن تاریخچه انجام نشد؛ دوباره تلاش کنید",color=MaterialTheme.colorScheme.error)
                    WorkInfo.State.CANCELLED -> Text("خواندن تاریخچه متوقف شد")
                    WorkInfo.State.SUCCEEDED -> {
                        val data=job.outputData
                        Text("پیامک پیدا شده: ${Money.persian(data.getInt("matched",0).toString())} · تراکنش جدید: ${Money.persian(data.getInt("count",0).toString())}")
                        Text("نیازمند بررسی: ${Money.persian(data.getInt("review",0).toString())} · نادیده گرفته شده: ${Money.persian(data.getInt("ignored",0).toString())} · قبلاً ثبت شده: ${Money.persian(data.getInt("duplicate",0).toString())}",style=MaterialTheme.typography.bodySmall)
                        data.getString("reason")?.takeIf { it.isNotEmpty() }?.let { Text("علت یک پیام ثبت‌نشده: ${smsReason(it)}",style=MaterialTheme.typography.bodySmall) }
                        if(data.getInt("matched",0)==0) Text("پیامکی با این فرستنده‌ها در صندوق SMS پیدا نشد. از «پیدا کردن خودکار بانک‌ها» استفاده کن؛ در شیائومی مجوز «خواندن پیامک‌های سرویس» هم لازم است.")
                        else if(data.getInt("count",0)==0 && data.getInt("duplicate",0)==0) Text("پیامک پیدا شد ولی تراکنشی تشخیص داده نشد. متن‌ها را با دکمه «پیامک‌های بانکی که ثبت نشده‌اند» ببین.")
                    }
                }
            }
        }
        AccountPanel(state,vm)
        CardPanel(state,vm)
        Panel("دسته‌ها") { Text("خرج: "+state.categories.filter { !it.deposit }.joinToString("  ·  ") { "${it.emoji} ${it.title}" }); Text("واریز: "+state.categories.filter { it.deposit }.joinToString("  ·  ") { "${it.emoji} ${it.title}" }); KhsTextField(category,{ category=it },label={ Text("دسته جدید") }); Select("برای",listOf("spending" to "خرج","deposit" to "واریز"),categorySide) { categorySide=it }; KhsTextButton(onClick={ if(category.isNotBlank()) { vm.category(category.trim(),categorySide=="deposit"); category="" } }) { Text("افزودن دسته") } }
        Panel("ظاهر و اعلان") {
            Select("پوسته",listOf("system" to "همراه دستگاه","light" to "روشن","dark" to "تیره"),state.preferences.theme) { vm.theme(it) }
            Toggle("رنگ‌های پویا",state.preferences.dynamicColor) { vm.preference("dynamic",it) }
            Toggle("نمایش به تومان",state.preferences.toman) { vm.preference("toman",it) }
            Toggle("پنهان کردن مبلغ در اعلان",state.preferences.hideDetails) { vm.preference("hide",it) }
            KhsTextButton(onClick={ context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName)) }) { Text("تنظیم اعلان‌های Android") }
        }
        Panel("حریم خصوصی") {
            Toggle("قفل برنامه با تأیید هویت دستگاه",state.preferences.appLock) { enabled ->
                if(!enabled || BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)==BiometricManager.BIOMETRIC_SUCCESS) vm.preference("lock",enabled) else vm.message.value="ابتدا قفل امن دستگاه را در تنظیمات Android فعال کنید"
            }
            Text("پایگاه داده Room رمزگذاری نشده است. پیامک در صف موقت با کلید Android Keystore رمزگذاری می‌شود و پس از پردازش حذف می‌شود. پشتیبان ابری Android غیرفعال است.",style=MaterialTheme.typography.bodySmall)
            Text("برای حذف همه اطلاعات، از تنظیمات Android داده‌های برنامه را پاک کنید. حذف امن فیزیکی حافظه فلش تضمین نمی‌شود.",style=MaterialTheme.typography.bodySmall)
        }
        ExportPanel(state,vm)
        ParserPlayground()
    }
}
@Composable fun SenderPanel(state: AppState,vm: HesabViewModel) {
    var sender by rememberSaveable { mutableStateOf("") }; var bank by rememberSaveable { mutableStateOf("mellat") }
    Panel("فرستنده‌های مجاز بانک") {
        Text("فقط پیام این فرستنده‌ها خوانده می‌شود. برای افزودن دستی، نام یا شماره فرستنده را از بالای پیامک بانک وارد کن.",style=MaterialTheme.typography.bodySmall)
        Select("بانک",Banks.all.map { it.id to it.title },bank) { bank=it }
        KhsTextField(sender,{ sender=it },label={ Text("نام یا شماره فرستنده") },singleLine=true,textStyle=MaterialTheme.typography.bodyLarge.copy(textDirection=TextDirection.Ltr))
        Text("شماره با پیش‌شماره 98، با علامت + یا با صفر اول یکی حساب می‌شود؛ فاصله و بزرگی و کوچکی حروف هم مهم نیست.",style=MaterialTheme.typography.bodySmall)
        KhsTextButton(onClick={ if(sender.isNotBlank()) { vm.sender(sender,bank); sender="" } }) { Text("اعتماد به این فرستنده") }
        state.preferences.senders.forEach { (name,id) -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(name,style=MaterialTheme.typography.bodyLarge.copy(textDirection=TextDirection.Ltr)); Text(Banks.all.find { it.id==id }?.title ?: id,style=MaterialTheme.typography.bodySmall) }; KhsTextButton(onClick={ vm.removeSender(name) }) { Text("حذف") } } }
    }
}
@Composable fun AccountPanel(state: AppState,vm: HesabViewModel) {
    var nickname by rememberSaveable { mutableStateOf("") }; var suffix by rememberSaveable { mutableStateOf("") }; var bank by rememberSaveable { mutableStateOf("mellat") }; var business by rememberSaveable { mutableStateOf(false) }
    var reconcile by remember { mutableStateOf<FinancialAccount?>(null) }; var balance by remember { mutableStateOf("") }
    Panel("بانک‌ها و حساب‌ها") {
        state.accounts.forEach { account ->
            Text("${account.nickname} · ${Banks.all.find { it.id==account.bankId }?.title} · ${Money.persian(account.suffix)}")
            if(account.reconciledBalance!=null) Text("مانده ثبت‌شده دستی: ${Money.format(account.reconciledBalance,state.preferences.toman)} · ${Jalali.at(account.reconciledAt ?: 0)}",style=MaterialTheme.typography.labelSmall)
            Row { KhsTextButton(onClick={ reconcile=account; balance="" }) { Text("ثبت مانده دستی") }; KhsTextButton(onClick={ vm.account(account.copy(active=!account.active)) }) { Text(if(account.active) "غیرفعال کردن" else "فعال کردن") } }
        }
        Select("بانک",Banks.all.map { it.id to it.title },bank) { bank=it }
        KhsTextField(nickname,{ nickname=it },label={ Text("نام دلخواه حساب") })
        KhsTextField(suffix,{ suffix=Money.normalize(it).take(4) },label={ Text("۴ رقم آخر حساب (اختیاری)") })
        Toggle("حساب کاری",business) { business=it }
        KhsButton(onClick={ if(nickname.isNotBlank()) { vm.account(FinancialAccount(bankId=bank,nickname=nickname.trim(),suffix=suffix,business=business)); nickname=""; suffix="" } }) { Text("افزودن حساب") }
        Text("مانده فعلی از تاریخچه ناقص پیامک حدس زده نمی‌شود.",style=MaterialTheme.typography.labelSmall)
    }
    reconcile?.let { account -> AlertDialog(onDismissRequest={ reconcile=null },title={ Text("ثبت مانده دستی") },text={ KhsTextField(balance,{ balance=it },label={ Text(if(state.preferences.toman) "مانده به تومان" else "مانده به ریال") }) },confirmButton={ KhsTextButton(onClick={ val value=if(Money.normalize(balance)=="0") 0L else Money.parse(balance,state.preferences.toman); if(value!=null) { vm.account(account.copy(reconciledBalance=value,reconciledAt=System.currentTimeMillis())); reconcile=null } else vm.message.value="مبلغ معتبر وارد کنید" }) { Text("ذخیره") } },dismissButton={ KhsTextButton(onClick={ reconcile=null }) { Text("انصراف") } }) }
}
@Composable private fun CardPanel(state: AppState,vm: HesabViewModel) {
    var account by rememberSaveable { mutableStateOf("") }; var lastFour by rememberSaveable { mutableStateOf("") }
    Panel("کارت‌ها") { Text("فقط ۴ رقم آخر کارت ذخیره می‌شود."); state.cards.forEach { card -> Text("${Money.persian(card.lastFour)} · ${state.accounts.find { it.id==card.accountId }?.nickname}") }; Select("حساب",state.accounts.map { it.id to it.nickname },account) { account=it }; KhsTextField(lastFour,{ lastFour=Money.normalize(it).take(4) },label={ Text("۴ رقم آخر کارت") }); KhsTextButton(onClick={ if(account.isNotEmpty()) { vm.card(account,lastFour); lastFour="" } }) { Text("افزودن کارت") } }
}
@Composable fun HistoryImport(state: AppState,vm: HesabViewModel) {
    val context=LocalContext.current
    var consent by remember { mutableStateOf(false) }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) { HistoryWorker.enqueue(context); vm.message.value="خواندن تاریخچه شروع شد؛ نتیجه در بخش وضعیت پردازش نمایش داده می‌شود" } else vm.message.value="مجوز خواندن پیامک داده نشد. راهنما را از «مجوز پیامک داده نمی‌شود؟» ببین؛ ثبت دستی همچنان در دسترس است."
    }
    KhsTextButton(onClick={ if(!state.preferences.smsEnabled || state.preferences.senders.isEmpty()) vm.message.value="ابتدا دریافت پیامک و فرستنده‌های مجاز را تنظیم کنید" else consent=true }) { Text("وارد کردن تاریخچه پیامک با رضایت من") }
    if(consent) AlertDialog(onDismissRequest={ consent=false },title={ Text("خواندن تاریخچه بانکی") },text={ Text("برنامه تنها پیامک‌های فرستنده‌های مجاز را از صندوق ورودی می‌خواند. متن اصلی ذخیره نمی‌شود. اجازه می‌دهید؟") },confirmButton={ KhsTextButton(onClick={ consent=false; permission.launch(Manifest.permission.READ_SMS) }) { Text("اجازه و شروع") } },dismissButton={ KhsTextButton(onClick={ consent=false }) { Text("انصراف") } })
}
@Composable private fun ExportPanel(state: AppState,vm: HesabViewModel) {
    var customStart by rememberSaveable { mutableStateOf("") }; var customEnd by rememberSaveable { mutableStateOf("") }
    val context=LocalContext.current; val scope=rememberCoroutineScope()
    var password by remember { mutableStateOf("") }; var account by rememberSaveable { mutableStateOf("ALL") }; var category by rememberSaveable { mutableStateOf("ALL") }; var period by rememberSaveable { mutableStateOf("all") }; var busy by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf<ByteArray?>(null) }
    val saveCsv=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val data=output; output=null
        if(uri!=null && data!=null) scope.launch { try { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri,"wt")!!.use { it.write(data) } }; vm.message.value="فایل ذخیره شد" } catch(_: Exception) { vm.message.value="ذخیره فایل انجام نشد" } }
    }
    val saveBackup=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val data=output; output=null
        if(uri!=null && data!=null) scope.launch { try { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri,"wt")!!.use { it.write(data) } }; vm.message.value="پشتیبان رمزگذاری‌شده ذخیره شد" } catch(_: Exception) { vm.message.value="ذخیره پشتیبان انجام نشد" } }
    }
    val saveXlsx=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        val data=output; output=null
        if(uri!=null && data!=null) scope.launch { try { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri,"wt")!!.use { it.write(data) } }; vm.message.value="فایل Excel ذخیره شد" } catch(_: Exception) { vm.message.value="ذخیره فایل انجام نشد" } }
    }
    val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) { val secret=password.toCharArray(); password=""; scope.launch { busy=true; try { val data=withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { stream -> val buffer=java.io.ByteArrayOutputStream(); val chunk=ByteArray(8192); while(true) { val n=stream.read(chunk); if(n<0) break; require(buffer.size()+n<=20_000_000); buffer.write(chunk,0,n) }; buffer.toByteArray() } }; val count=vm.restore(data,secret); vm.message.value="بازیابی انجام شد؛ ${Money.persian(count.toString())} ردیف بررسی شد. موارد تکراری اضافه نمی‌شوند." } catch(_: Exception) { vm.message.value="بازیابی انجام نشد؛ فایل یا گذرواژه را بررسی کنید" } finally { secret.fill('\u0000'); busy=false } } }
    }
    Panel("خروجی و پشتیبان") {
        Select("حساب خروجی",listOf("ALL" to "همه")+state.accounts.map { it.id to it.nickname },account) { account=it }
        Select("دسته خروجی",listOf("ALL" to "همه")+categoryChoices(state.categories),category) { category=it }
        Select("بازه خروجی",periodChoices,period) { period=it }
        CustomRangeInputs(period,customStart,customEnd,{ customStart=it },{ customEnd=it })
        KhsTextButton(enabled=!busy,onClick={ scope.launch { busy=true; try { output=vm.export(account.takeUnless { it=="ALL" },category.takeUnless { it=="ALL" },reportRange(period,customStart,customEnd)); saveCsv.launch("khs-invoice.csv") } catch(_: Exception) { vm.message.value="خروجی انجام نشد" } finally { busy=false } } }) { Text("خروجی CSV برای صفحه‌گسترده") }
        KhsTextButton(enabled=!busy,onClick={ scope.launch { busy=true; try { output=vm.export(account.takeUnless { it=="ALL" },category.takeUnless { it=="ALL" },reportRange(period,customStart,customEnd),true); saveXlsx.launch("khs-invoice.xlsx") } catch(_: Exception) { vm.message.value="خروجی انجام نشد" } finally { busy=false } } }) { Text("خروجی Excel (XLSX)") }
        Text("خروجی متنی محرمانه و رمزگذاری‌نشده است. تزریق فرمول در سلول‌ها خنثی می‌شود.",style=MaterialTheme.typography.labelSmall)
        KhsTextField(password,{ password=it },label={ Text("گذرواژه پشتیبان؛ حداقل ۱۲ نویسه") },visualTransformation=PasswordVisualTransformation())
        KhsTextButton(enabled=!busy && password.length>=12,onClick={ val secret=password.toCharArray(); password=""; scope.launch { busy=true; try { output=vm.backup(secret); saveBackup.launch("khs-invoice.hsb") } catch(_: Exception) { vm.message.value="پشتیبان انجام نشد" } finally { secret.fill('\u0000'); busy=false } } }) { Text("ساخت پشتیبان رمزگذاری‌شده") }
        KhsTextButton(enabled=!busy && password.length>=12,onClick={ restore.launch(arrayOf("application/octet-stream","*/*")) }) { Text("بازیابی پشتیبان (ادغام بدون تکرار)") }
        Text("گذرواژه را نگه دار؛ بدون آن بازیابی ممکن نیست. پشتیبان همه حساب‌ها را دارد. مجوزها و رضایت پیامک روی دستگاه جدید دوباره تنظیم می‌شوند.",style=MaterialTheme.typography.labelSmall)
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth(),color=LocalKhsSemantics.current.focus)
    }
}
@Composable private fun ParserPlayground() {
    var bank by remember { mutableStateOf("mellat") }; var body by remember { mutableStateOf("خرید: ۴۵۰٬۰۰۰ تومان\nکارت: ****1234\nمانده: ۹۰۰٬۰۰۰ تومان") }; var result by remember { mutableStateOf("") }
    Panel("آزمایش تشخیص پیامک") {
        Text("متن واردشده فقط برای آزمایش است و به تراکنش تبدیل نمی‌شود.",style=MaterialTheme.typography.bodySmall)
        Select("بانک",Banks.all.map { it.id to it.title },bank) { bank=it }
        KhsTextField(body,{ body=it },label={ Text("نمونه ساختگی یا متن انتخابی شما") },modifier=Modifier.fillMaxWidth())
        KhsTextButton(onClick={ result=when(val parsed=BankParser().parse(body,bank)) { is ParseResult.Transaction -> listOfNotNull("${kindNames[parsed.value.kind]} · ${Money.format(parsed.value.amountIrr)}",parsed.value.label.takeIf { it.isNotEmpty() },parsed.value.feeIrr?.let { "کارمزد ${Money.format(it)}" },"۴ رقم حساب: ${parsed.value.accountSuffix ?: "نامشخص"}",parsed.value.cardSuffix?.let { "کارت: $it" },if(parsed.value.review) "نیازمند بررسی" else null).joinToString(" · "); is ParseResult.Ignored -> "پیام نادیده گرفته شد: ${smsReason(parsed.reason)}"; is ParseResult.Review -> "نیازمند بررسی: ${smsReason(parsed.reason)}" } }) { Text("آزمایش") }
        Text(result)
    }
}

internal fun smsReason(reason: String) = when(reason) {
    "authentication" -> "پیام حاوی رمز یا کد تأیید است"
    "unknown_currency" -> "واحد پول پشتیبانی نمی‌شود"
    "balance_only" -> "پیام فقط مانده یا موجودی دارد"
    "unsupported" -> "قالب پیام یا نوع تراکنش شناخته نشد"
    "ambiguous_amount" -> "مبلغ تراکنش در پیام پیدا نشد"
    "unknown_direction" -> "مشخص نیست پیام برداشت است یا واریز"
    "promotional" -> "پیام تبلیغاتی است"
    "reminder" -> "پیام یادآوری است، نه تراکنش"
    "failed" -> "تراکنش ناموفق بوده است"
    "invalid_amount" -> "مبلغ معتبر نیست"
    "aggregate_overflow" -> "مجموع مبلغ‌ها از حد مجاز بیشتر می‌شود"
    else -> "پیام نیاز به بررسی دارد"
}


