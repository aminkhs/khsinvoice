package ir.hesab.app.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import ir.hesab.app.sms.SenderCandidate
import ir.hesab.app.sms.SmsInbox
import ir.hesab.app.sms.UnreadSms
import ir.hesab.core.*
import kotlinx.coroutines.launch

/** One place for everything the owner needs to connect bank messages: permission, senders and diagnosis. */
@Composable fun SmsSetupPanel(state: AppState,vm: HesabViewModel) {
    val context=LocalContext.current; val scope=rememberCoroutineScope()
    // Permissions change outside the app, so they are re-read whenever the screen comes back.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose { } }
    val canReceive=remember(refresh) { SmsInbox.canReceive(context) }; val canRead=remember(refresh) { SmsInbox.canRead(context) }
    var help by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<SenderCandidate>?>(null) }
    var unread by remember { mutableStateOf<List<UnreadSms>?>(null) }
    fun scan() { scope.launch { busy=true; try { found=vm.detectSenders() } catch(_: SecurityException) { help=true } catch(_: Exception) { vm.message.value="خواندن صندوق پیامک انجام نشد؛ دوباره تلاش کنید" } finally { busy=false } } }
    fun showUnread() { scope.launch { busy=true; try { unread=vm.unreadSms() } catch(_: SecurityException) { help=true } catch(_: Exception) { vm.message.value="خواندن صندوق پیامک انجام نشد؛ دوباره تلاش کنید" } finally { busy=false } } }
    val connect=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++; if(SmsInbox.canRead(context)) { vm.preference("sms",true); scan() } else help=true }
    val receive=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> refresh++; vm.preference("sms",granted); if(!granted) help=true }
    val read=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> refresh++; if(granted) showUnread() else help=true }
    Panel("اتصال پیامک بانکی") {
        Text("برنامه پیامک‌های بانک را روی همین گوشی می‌خواند و خرج و درآمد را خودش ثبت می‌کند. لازم نیست نام فرستنده را بدانی.",style=MaterialTheme.typography.bodySmall)
        Toggle("پردازش پیامک بانکی",state.preferences.smsEnabled) { enabled -> if(enabled) receive.launch(Manifest.permission.RECEIVE_SMS) else vm.preference("sms",false) }
        Text("دریافت پیامک جدید: ${if(canReceive) "مجاز" else "بدون مجوز"} · خواندن پیامک‌های قبلی: ${if(canRead) "مجاز" else "بدون مجوز"}",style=MaterialTheme.typography.labelSmall)
        if(state.preferences.smsEnabled && !canReceive) Text("پردازش روشن است ولی Android مجوز پیامک را به برنامه نداده؛ تا مجوز ندهی پیامک جدید ثبت نمی‌شود.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        KhsButton(enabled=!busy,onClick={ connect.launch(arrayOf(Manifest.permission.RECEIVE_SMS,Manifest.permission.READ_SMS)) }) { Text("پیدا کردن خودکار بانک‌ها از پیامک‌ها") }
        KhsTextButton(enabled=!busy,onClick={ if(state.preferences.senders.isEmpty()) vm.message.value="ابتدا فرستنده بانک را اضافه کنید" else if(canRead) showUnread() else read.launch(Manifest.permission.READ_SMS) }) { Text("پیامک‌های بانکی که ثبت نشده‌اند") }
        KhsTextButton(onClick={ help=true }) { Text("مجوز پیامک داده نمی‌شود؟") }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth(),color=LocalKhsSemantics.current.focus)
    }
    if(help) AlertDialog(onDismissRequest={ help=false },title={ Text("مجوز پیامک") },text={
        Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("۱. «تنظیمات برنامه» را باز کن ← مجوزها ← پیامک ← اجازه دادن.")
            Text("۲. اگر گزینه پیامک خاموش و غیرقابل لمس است یا پیام «تنظیمات محدودشده» می‌بینی: در همان صفحه اطلاعات برنامه، منوی ⋮ بالای صفحه را بزن و «اجازه به تنظیمات محدودشده» را انتخاب کن؛ بعد مجوز پیامک را بده. Android برای برنامه‌هایی که با فایل APK نصب می‌شوند این قفل را می‌گذارد.")
            Text("۳. در گوشی‌های شیائومی: مجوزها ← مجوزهای دیگر ← «خواندن پیامک‌های سرویس» را هم روشن کن؛ پیام بانک‌ها پیامک سرویس حساب می‌شود و بدون آن صندوق خالی دیده می‌شود.")
            Text("۴. برای ثبت پیامک جدید در پس‌زمینه، محدودیت باتری برنامه را بردار و برنامه را Force stop نکن.")
        }
    },confirmButton={ KhsTextButton(onClick={ help=false; runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",context.packageName,null))) } }) { Text("باز کردن تنظیمات برنامه") } },dismissButton={ KhsTextButton(onClick={ help=false }) { Text("بستن") } })
    found?.let { senders -> DetectedSenders(senders,state,{ found=null }) { chosen -> found=null; vm.setupSms(chosen) } }
    unread?.let { rows -> UnreadMessages(rows) { unread=null } }
}
@Composable private fun DetectedSenders(senders: List<SenderCandidate>,state: AppState,dismiss: () -> Unit,confirm: (List<Pair<String,String>>) -> Unit) {
    val checked=remember(senders) { mutableStateMapOf<String,Boolean>().apply { senders.forEach { put(it.address,it.likely) } } }
    // A sender the owner already trusts keeps the bank they chose for it.
    val banks=remember(senders) { mutableStateMapOf<String,String>().apply { senders.forEach { put(it.address,state.preferences.bankFor(it.address) ?: it.bank) } } }
    AlertDialog(onDismissRequest=dismiss,title={ Text(if(senders.isEmpty()) "پیامک بانکی پیدا نشد" else "فرستنده‌های بانکی پیدا شده") },text={
        Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(senders.isEmpty()) {
                Text("در صندوق پیامک، فرستنده‌ای با پیام برداشت، خرید یا واریز دیده نشد.")
                Text("اگر پیامک بانک روی این گوشی هست: در شیائومی مجوز «خواندن پیامک‌های سرویس» را روشن کن، یا فرستنده را از بخش «فرستنده‌های مجاز بانک» دستی اضافه کن.",style=MaterialTheme.typography.bodySmall)
            } else Text("فرستنده‌هایی که واقعاً بانک تو هستند را تیک بزن و بانک هر کدام را بررسی کن.",style=MaterialTheme.typography.bodySmall)
            senders.forEach { sender ->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked[sender.address]==true,onCheckedChange={ checked[sender.address]=it })
                    Column(Modifier.weight(1f)) {
                        Text(sender.address,style=MaterialTheme.typography.bodyLarge.copy(textDirection=TextDirection.Ltr))
                        Text("${Money.persian(sender.transactions.toString())} پیام تراکنش در ${Money.persian(sender.total.toString())} پیامک",style=MaterialTheme.typography.labelSmall)
                    }
                }
                if(checked[sender.address]==true) Select("بانک",Banks.all.map { it.id to it.title },banks[sender.address]) { banks[sender.address]=it }
                HorizontalDivider()
            }
        }
    },confirmButton={ if(senders.isNotEmpty()) KhsTextButton(enabled=checked.values.any { it },onClick={ confirm(senders.filter { checked[it.address]==true }.map { it.address to (banks[it.address] ?: "other") }) }) { Text("افزودن و خواندن پیامک‌ها") } },dismissButton={ KhsTextButton(onClick=dismiss) { Text(if(senders.isEmpty()) "بستن" else "انصراف") } })
}
@Composable private fun UnreadMessages(rows: List<UnreadSms>,dismiss: () -> Unit) {
    val context=LocalContext.current
    AlertDialog(onDismissRequest=dismiss,title={ Text("پیامک‌های ثبت‌نشده") },text={
        Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(rows.isEmpty()) Text("همه پیام‌های تراکنش فرستنده‌های مجاز خوانده شده‌اند. رمز پویا، تبلیغ و پیام فقط‌موجودی عمداً ثبت نمی‌شوند.")
            else Text("این پیام‌ها فقط همین‌جا نمایش داده می‌شوند و ذخیره نمی‌شوند. اگر پیام تراکنش واقعی اینجاست، متن را کپی کن تا قالب آن بانک اضافه شود.",style=MaterialTheme.typography.bodySmall)
            rows.forEach { row ->
                Text("${row.address} · ${Jalali.at(row.time)}",style=MaterialTheme.typography.labelMedium)
                Text(smsReason(row.reason),style=MaterialTheme.typography.labelSmall,color=LocalKhsSemantics.current.warning)
                SelectionContainer { Text(row.body,style=MaterialTheme.typography.bodySmall) }
                HorizontalDivider()
            }
        }
    },confirmButton={ if(rows.isNotEmpty()) KhsTextButton(onClick={
        val text=rows.joinToString("\n\n") { "[${it.address}] ${smsReason(it.reason)}\n${it.body}" }
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("sms",text))
    }) { Text("کپی همه") } },dismissButton={ KhsTextButton(onClick=dismiss) { Text("بستن") } })
}
