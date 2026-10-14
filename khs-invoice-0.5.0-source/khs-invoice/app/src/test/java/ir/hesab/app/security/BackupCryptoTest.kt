package ir.hesab.app.security

import org.junit.Test
import org.junit.Assert.*

class BackupCryptoTest {
    @Test fun authenticatedRoundTrip() { val secret="synthetic password 123".toCharArray(); val data="synthetic finance data".toByteArray(); val encrypted=BackupCrypto.encrypt(data,secret); assertArrayEquals(data,BackupCrypto.decrypt(encrypted,secret)); assertFalse(encrypted.toString(Charsets.UTF_8).contains("synthetic finance data")) }
    @Test fun wrongPasswordRejected() { val encrypted=BackupCrypto.encrypt(byteArrayOf(1,2,3),"synthetic password 123".toCharArray()); assertThrows(Exception::class.java) { BackupCrypto.decrypt(encrypted,"wrong password 456".toCharArray()) } }
    @Test fun tamperingRejected() { val encrypted=BackupCrypto.encrypt(byteArrayOf(1,2,3),"synthetic password 123".toCharArray()); encrypted[encrypted.lastIndex]=(encrypted.last().toInt() xor 1).toByte(); assertThrows(Exception::class.java) { BackupCrypto.decrypt(encrypted,"synthetic password 123".toCharArray()) } }
}
