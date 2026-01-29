package com.sunion.core.ble.mfr.entity

enum class OTATarget(val value: Int) {
    MCU(0),
    WIRELESS(1),
}

enum class OTAState(val value: Int) {
    START(0),
    FINISH(1),
    CANCEL(2)
}
enum class Direction(val value: Int) {
    RIGHT(0xA0),
    LEFT(0xA1),
    UNKNOWN(0xA2),
    NOT_SUPPORT(0xFF)
}

enum class VacationMode(val value: Int) {
    CLOSE(0),
    OPEN(1),
    NOT_SUPPORT(0xFF)
}

enum class DeadBolt(val value: Int) {
    NOT_PROTRUDE(0),
    PROTRUDE(1),
    NOT_SUPPORT(0xFF)
}

enum class DoorState(val value: Int) {
    OPEN(0),
    CLOSE(1),
    NOT_SUPPORT(0xFF)
}

enum class LockState(val value: Int) {
    UNLOCKED(0),
    LOCKED(1),
    UNKNOWN(2)
}

enum class SecurityBolt(val value: Int) {
    NOT_PROTRUDE(0),
    PROTRUDE(1),
    NOT_SUPPORT(0xFF)
}

enum class BatteryState(val value: Int) {
    NORMAL(0),
    WEAK_CURRENT(1),
    DANGEROUS(2)
}

enum class LockStateAction(val value: Int) {
    LOCK_STATE(0x01),
    SECURITY_BOLT(0x02)
}

enum class Password(val value: Int) {
    CLOSE(0),
    OPEN(1),
    NOT_SUPPORT(0xFF)
}

enum class RemotePinCode(val value: Int) {
    CLOSE(0),
    OPEN(1),
    NOT_SUPPORT(0xFF)
}

enum class GuidingCode(val value: Int) {
    CLOSE(0),
    OPEN(1),
    NOT_SUPPORT(0xFF)
}

enum class TwoFA(val value: Int) {
    CLOSE(0),
    OPEN(1),
    NOT_SUPPORT(0xFF)
}

enum class AutoLock(val value: Int) {
    CLOSE(0),
    OPEN(1),
    NOT_SUPPORT(0xFF)
}

enum class AutoLockTime(val value: Int) {
    NOT_SUPPORT(0xFFFF)
}

enum class AutoLockTimeUpperLimit(val value: Int) {
    NOT_SUPPORT(0xFFFF)
}

enum class AutoLockTimeLowerLimit(val value: Int) {
    NOT_SUPPORT(0xFFFF)
}

enum class OperatingSound(val value: Int) {
    CLOSE(0),
    OPEN(1),
    NOT_SUPPORT(0xFF)
}

enum class SoundType(val value: Int) {
    ON_OFF(0x01),
    LEVEL(0x02),
    PERCENTAGE(0x03),
    NOT_SUPPORT(0xFF)
}

enum class SoundValue(val value: Int) {
    CLOSE(0),
    OPEN(100),
    LOW_VOICE(50),
    HIGH_VOICE(100),
    NOT_SUPPORT(0xFF)
}

enum class ShowFastTrackMode(val value: Int) {
    CLOSE(0x00),
    OPEN(0x01),
    NOT_SUPPORT(0xFF)
}

enum class SabbathMode(val value: Int) {
    CLOSE(0x00),
    OPEN(0x01),
    NOT_SUPPORT(0xFF)
}

enum class PhoneticLanguage(val value: Int) {
    ENGLISH(0x00),
    SPANISH(0x01),
    FRENCH(0x02),
    CHINESE(0x03),
    NOT_SUPPORT(0xFF)
}

enum class SupportPhoneticLanguage(val value: Int) {
    NOT_SUPPORT(0)
}

enum class VersionType(val value: Int) {
    MCU(0),
    RF(1),
    NOT_SUPPORT(0xFFFF)
}

enum class UserType(val value: Int) {
    UNRESTRICTED(0x00), // 永久性密碼 All
    YEAR_DAY_SCHEDULE(0x01), // 時間區段密碼 ValidTimeRange
    WEEK_DAY_SCHEDULE(0x02), // 週期性密碼 ScheduleEntry
    PROGRAMMING(0x03), // app不顯示此選項
    NON_ACCESS(0x04),
    FORCED(0x05), // 永久性密碼但有警報
    DISPOSABLE(0x06), // 一次性密碼 SingleEntry
    EXPIRING(0x07), // 不支援
    SCHEDULE_RESTRICTED(0x08),
    REMOTE_ONLY(0x09),
    UNKNOWN(10)
}

enum class CredentialRule(val value: Int) {
    SINGLE(0x00), // 驗證1個Credential
    DUAL(0x01), // 驗證2個Credential
    TRI(0x02), // 驗證3個Credential
    UNKNOWN(3)
}

enum class CredentialType(val value: Int) {
    PROGRAMMING_PIN(0x00), // 不支援
    PIN(0x01),
    RFID(0x02),
    FINGERPRINT(0x03),
    FINGER_VEIN(0x04),
    FACE(0x05),
    UNKNOWN(6)
}

enum class CredentialState(val value: Int) {
    EXIT(0),
    START(1),
    UPDATE(2)
}

enum class CredentialStatus(val value: Int) {
    AVAILABLE(0x00), // 未使用可放資料
    OCCUPIED_ENABLED(0x01), // 已使用, 目前啟用
    OCCUPIED_DISABLED(0x03), // 已使用, 目前停用
    UNKNOWN(2)
}

enum class HashFormat(val value: Int) {
    CREDENTIAL(0),
    BLE_USER(1),
    BLACKLIST(1)
}

enum class ScheduleStatus(val value: Int) {
    AVAILABLE(0x00), // 可用
    OCCUPIED_ENABLED(0x01), // 已使用, 目前啟用
    OCCUPIED_DISABLED(0x03), // 已使用, 目前停用
    UNKNOWN(2)
}

enum class DaysMaskMap(val value: Int) {
    SUNDAY(0x01),
    MONDAY(0x02),
    TUESDAY(0x04),
    WEDNESDAY(0x08),
    THURSDAY(0x10),
    FRIDAY(0x20),
    SATURDAY(0x40),
}

enum class ResetMaskMap(val value: Int) {
    SETTING(0x01),
    BLACKLIST(0x02),
    PINCODE(0x04),
    ALL(0xFF),
}

enum class AlertType(val value: Int) {
    ERROR_ACCESS_CODE(0),
    CURRENT_ACCESS_CODE_AT_WRONG_TIME(1),
    CURRENT_ACCESS_CODE_BUT_AT_VACATION_MODE(2),
    ACTIVELY_PRESS_THE_CLEAR_KEY(3),
    MANY_ERROR_KEY_LOCKED(20),
    LOCK_BREAK_ALERT(40),
    NONE(0xFF),
    UNKNOWN_ALERT_TYPE(-1),
}

enum class DoorType(val value: Int) {
    Public(0),
    Private(1)
}