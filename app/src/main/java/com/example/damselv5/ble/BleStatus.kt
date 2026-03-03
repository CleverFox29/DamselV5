package com.example.damselv5.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BleStatus {
    private val _connectionState = MutableStateFlow("Disconnected")
    val connectionState: StateFlow<String> = _connectionState.asStateFlow()

    private val _deviceName = MutableStateFlow("None")
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    fun updateState(state: String) {
        _connectionState.value = state
    }

    fun updateDeviceName(name: String) {
        _deviceName.value = name
    }
}