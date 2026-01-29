package com.sunion.core.ble.mfr

import com.sunion.core.ble.mfr.entity.DeviceToken
import com.sunion.core.ble.mfr.entity.LockConfig
import com.sunion.core.ble.mfr.exception.NotConnectedException
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

@Singleton
class BleSessionRepository @Inject constructor() {

    data class SessionData(
        val keyTwo: ByteArray,
        val ivTwo: ByteArray,
        val token: ByteArray,
        val permission: String = DeviceToken.PERMISSION_NONE,
        val lockConfig12: LockConfig.Twelve? = null
    ) {
        // 覆寫 equals/hashCode 因為包含 ByteArray
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as SessionData
            if (!keyTwo.contentEquals(other.keyTwo)) return false
            if (!ivTwo.contentEquals(other.ivTwo)) return false
            if (!token.contentEquals(other.token)) return false
            return true
        }
        override fun hashCode(): Int {
            var result = keyTwo.contentHashCode()
            result = 31 * result + ivTwo.contentHashCode()
            result = 31 * result + token.contentHashCode()
            return result
        }
    }

    private val _currentSession = AtomicReference<SessionData?>(null)

    // 讀取 Key/IV (保持不變)
    fun requireKeyTwo() = _currentSession.get()?.keyTwo ?: throw NotConnectedException()
    fun requireIvTwo() = _currentSession.get()?.ivTwo ?: throw NotConnectedException()
    fun requireToken() = _currentSession.get()?.token ?: throw NotConnectedException()
    fun requirePermission() = _currentSession.get()?.permission ?: throw NotConnectedException()


    // --- 新增：Config 相關操作 ---

    // 取得目前的 Config (可能為 null)
    val currentConfig: LockConfig.Twelve?
        get() = _currentSession.get()?.lockConfig12

    // 更新 Session 中的 Config
    fun updateConfig(newConfig: LockConfig.Twelve) {
        val oldSession = _currentSession.get()
        if (oldSession != null) {
            _currentSession.set(oldSession.copy(lockConfig12 = newConfig))
        } else {
            Timber.e("Cannot save config: Session not established.")
        }
    }

    // 儲存握手後的初始 Session (保持不變)
    fun saveHandshakeSession(keyTwo: ByteArray, ivTwo: ByteArray, token: ByteArray, permission: String) {
        _currentSession.set(SessionData(keyTwo, ivTwo, token, permission))
    }

    // 斷線清除 (一鍵清除所有狀態！)
    fun clearSession() {
        _currentSession.set(null)
    }
}