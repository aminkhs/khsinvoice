package ir.hesab.app.security

import android.content.Context
import android.security.keystore.*
import java.io.File
import java.security.*
import javax.crypto.*
import javax.crypto.spec.*

class QueueCipher(context: Context) {
    companion object { private val keyLock=Any() }
    private val alias="hesab_sms_queue_v1"
    private fun key(): SecretKey = synchronized(keyLock) {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias,null) as? SecretKey)?.let { return@synchronized it }
        KeyGenerator.getInstance("AES","AndroidKeyStore").apply { init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()) }.generateKey()
    }
    fun write(file: File,text: String) {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        val payload=cipher.iv+cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        val atomic=android.util.AtomicFile(file)
        val stream=atomic.startWrite()
        try { stream.write(payload); atomic.finishWrite(stream) } catch(e: Exception) { atomic.failWrite(stream); throw e }
    }
    fun read(file: File): String {
        val data=file.readBytes()
        require(data.size>=28)
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,data.copyOfRange(0,12))) }.doFinal(data.copyOfRange(12,data.size)).toString(Charsets.UTF_8)
    }
}
object BackupCrypto {
    private val header="HESAB01".toByteArray()
    private const val ITERATIONS=210000
    fun encrypt(data: ByteArray,password: CharArray): ByteArray {
        require(password.size>=12) { "گذرواژه باید حداقل ۱۲ نویسه داشته باشد" }
        val random=SecureRandom(); val salt=ByteArray(16).also(random::nextBytes); val nonce=ByteArray(12).also(random::nextBytes)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,derive(password,salt),GCMParameterSpec(128,nonce)); updateAAD(header) }
        return header+salt+nonce+cipher.doFinal(data)
    }
    fun decrypt(data: ByteArray,password: CharArray): ByteArray {
        require(data.size in 51..20_000_000 && data.copyOfRange(0,7).contentEquals(header)) { "فایل پشتیبان معتبر نیست" }
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,derive(password,data.copyOfRange(7,23)),GCMParameterSpec(128,data.copyOfRange(23,35))); updateAAD(header) }.doFinal(data.copyOfRange(35,data.size))
    }
    private fun derive(password: CharArray,salt: ByteArray): SecretKeySpec {
        val spec=PBEKeySpec(password,salt,ITERATIONS,256)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,"AES") } finally { spec.clearPassword() }
    }
}
