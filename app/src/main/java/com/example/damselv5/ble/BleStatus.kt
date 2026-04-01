package com.example.damselv5.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BleStatus {
    private val _cs = MutableStateFlow("Disconnected")
    val connectionState: StateFlow<String> = _cs.asStateFlow()

    private val _dn = MutableStateFlow("None")
    val deviceName: StateFlow<String> = _dn.asStateFlow()

    private val _cd = MutableStateFlow(-1)
    val countdown: StateFlow<Int> = _cd.asStateFlow()

    fun updateState(s: String) {
        _cs.value = s
    }

    fun updateDeviceName(n: String) {
        _dn.value = n
    }

    fun updateCountdown(i: Int) {
        _cd.value = i
    }
}