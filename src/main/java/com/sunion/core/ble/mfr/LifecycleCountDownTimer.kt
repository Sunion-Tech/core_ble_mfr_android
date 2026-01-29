package com.sunion.core.ble.mfr

import android.os.CountDownTimer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class LifecycleCountDownTimer(
    millisUntilFinished: Long,
    interval: Long
) : CountDownTimer(millisUntilFinished, interval), DefaultLifecycleObserver {

    private var onFinishAction: (() -> Unit)? = null
    private var onTickAction: ((Long) -> Unit)? = null

    fun setOnFinishListener(action: () -> Unit) {
        onFinishAction = action
    }

    override fun onFinish() {
        onFinishAction?.invoke()
    }

    override fun onTick(millisUntilFinished: Long) {
        onTickAction?.invoke(millisUntilFinished)
    }

    fun stopAndClear() {
        onFinishAction = null
        onTickAction = null
        this.cancel()
    }

    override fun onStop(owner: LifecycleOwner) {
        stopAndClear()
        super.onStop(owner)
    }
}
