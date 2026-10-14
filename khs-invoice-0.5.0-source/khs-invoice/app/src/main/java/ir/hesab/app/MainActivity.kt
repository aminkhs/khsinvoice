package ir.hesab.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.*
import ir.hesab.app.ui.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity: FragmentActivity() {
    private lateinit var vm: HesabViewModel
    private var unlocked by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm=ViewModelProvider(this,object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T = HesabViewModel(application as HesabApplication) as T
        })[HesabViewModel::class.java]
        intent.getStringExtra("transaction")?.let { vm.editId.value=it }
        setContent { HesabApp(vm,unlocked,::authenticate) }
    }
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { unlocked=!(application as HesabApplication).preferences.flow.first().appLock; if(!unlocked) authenticate() }
    }
    override fun onStop() { super.onStop(); unlocked=false }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); intent.getStringExtra("transaction")?.let { vm.editId.value=it } }
    private fun authenticate() {
        val authenticators=BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if(BiometricManager.from(this).canAuthenticate(authenticators)!=BiometricManager.BIOMETRIC_SUCCESS) { vm.message.value="برای قفل برنامه، ابتدا قفل امن دستگاه را فعال کنید"; return }
        BiometricPrompt(this,ContextCompat.getMainExecutor(this),object: BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlocked=true }
        }).authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(getString(R.string.app_name)).setSubtitle("تأیید هویت با قفل امن دستگاه").setAllowedAuthenticators(authenticators).build())
    }
}
