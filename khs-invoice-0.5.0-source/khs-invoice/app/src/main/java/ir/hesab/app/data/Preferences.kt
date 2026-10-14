package ir.hesab.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import ir.hesab.core.SmsSender

private val Context.store by preferencesDataStore("hesab_preferences")
data class UserPreferences(val onboarded: Boolean = false, val smsEnabled: Boolean = false, val toman: Boolean = true, val hideDetails: Boolean = true, val theme: String = "system", val dynamicColor: Boolean = false, val appLock: Boolean = false, val senders: Map<String,String> = emptyMap()) {
    private val senderKeys by lazy { senders.entries.associate { SmsSender.key(it.key) to it.value } }
    /** Bank of a trusted sender, however the phone spells its number (+98…, 98…, 0…). */
    fun bankFor(address: String): String? = senderKeys[SmsSender.key(address)]
}
class Preferences(private val context: Context) {
    val flow = context.store.data.map { p -> UserPreferences(
        p[booleanPreferencesKey("onboarded")] ?: false,
        p[booleanPreferencesKey("sms")] ?: false,
        p[booleanPreferencesKey("toman")] ?: true,
        p[booleanPreferencesKey("hide")] ?: true,
        p[stringPreferencesKey("theme")] ?: "system",
        p[booleanPreferencesKey("dynamic")] ?: false,
        p[booleanPreferencesKey("lock")] ?: false,
        (p[stringSetPreferencesKey("senders")] ?: emptySet()).mapNotNull { item -> val parts=item.split('|',limit=2); if(parts.size==2) SmsSender.normalize(parts[0]) to parts[1] else null }.groupBy { it.first }.mapNotNull { (name,entries) -> entries.map { it.second }.distinct().singleOrNull()?.let { name to it } }.toMap()
    ) }
    suspend fun set(key: String,value: Boolean) { context.store.edit { it[booleanPreferencesKey(key)] = value } }
    suspend fun theme(value: String) { context.store.edit { it[stringPreferencesKey("theme")] = value } }
    suspend fun sender(sender: String,bank: String) {
        require(sender.isNotBlank() && !sender.contains('|') && sender.length <= 64)
        val key=SmsSender.normalize(sender)
        require(key.isNotBlank())
        // One entry per sender, whichever spelling (+98…, 0…) it was added with.
        context.store.edit { p -> val set=p[stringSetPreferencesKey("senders")] ?: emptySet(); p[stringSetPreferencesKey("senders")] = set.filterNot { SmsSender.key(it.substringBefore('|'))==SmsSender.key(key) }.toSet() + "$key|$bank" }
    }
    suspend fun removeSender(sender: String) { context.store.edit { p -> p[stringSetPreferencesKey("senders")] = (p[stringSetPreferencesKey("senders")] ?: emptySet()).filterNot { SmsSender.key(it.substringBefore('|'))==SmsSender.key(sender) }.toSet() } }
}
