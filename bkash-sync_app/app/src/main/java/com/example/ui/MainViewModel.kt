package com.example.ui

import android.app.Application
import android.util.Log
import android.os.PowerManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.RetrofitClient
import com.example.data.database.OutboxEntity
import com.example.data.database.SyncState
import com.example.data.database.TransactionEntity
import com.example.data.pref.PreferencesManager
import com.example.data.repository.TransactionRepository
import com.example.service.BackgroundSyncService
import com.example.util.HistoricalSmsScanner
import com.example.util.ScanResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class VerificationStatus {
    object IDLE : VerificationStatus()
    object LOADING : VerificationStatus()
    object SUCCESS : VerificationStatus()
    data class ERROR(val message: String) : VerificationStatus()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TransactionRepository(application)
    private val prefs = PreferencesManager(application)

    companion object {
        private const val TAG = "MainViewModel"
    }

    // --- Configurations Input Fields ---
    val apiEndpointInput = MutableStateFlow(prefs.apiEndpoint)
    val apiKeyInput = MutableStateFlow(prefs.apiKey)
    val userPhoneNumberInput = MutableStateFlow(prefs.userPhoneNumber)

    private val _isConfigured = MutableStateFlow(prefs.isConfigured)
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    private val _isDarkMode = MutableStateFlow(prefs.isDarkMode)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _isBatteryOptimizationIgnored = MutableStateFlow(false)
    val isBatteryOptimizationIgnored: StateFlow<Boolean> = _isBatteryOptimizationIgnored.asStateFlow()

    init {
        updateBatteryOptimizationStatus()
        if (prefs.isConfigured) {
            BackgroundSyncService.start(application)
        }
    }

    fun updateBatteryOptimizationStatus() {
        val pm = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as? PowerManager
        _isBatteryOptimizationIgnored.value = pm?.isIgnoringBatteryOptimizations(getApplication<Application>().packageName) ?: false
    }

    fun toggleDarkMode() {
        val newVal = !_isDarkMode.value
        _isDarkMode.value = newVal
        prefs.isDarkMode = newVal
    }

    private val _syncIntervalSeconds = MutableStateFlow(prefs.syncIntervalSeconds)
    val syncIntervalSeconds: StateFlow<Int> = _syncIntervalSeconds.asStateFlow()

    private val _autoReconcileOnSweep = MutableStateFlow(prefs.autoReconcileOnSweep)
    val autoReconcileOnSweep: StateFlow<Boolean> = _autoReconcileOnSweep.asStateFlow()

    fun updateAutoReconcileOnSweep(enabled: Boolean) {
        _autoReconcileOnSweep.value = enabled
        prefs.autoReconcileOnSweep = enabled
        if (prefs.isConfigured) {
            // Restart the service to immediately apply settings changes to the sweep loop
            BackgroundSyncService.stop(getApplication())
            BackgroundSyncService.start(getApplication())
        }
    }

    fun updateSyncInterval(seconds: Int) {
        if (seconds > 0) {
            _syncIntervalSeconds.value = seconds
            prefs.syncIntervalSeconds = seconds
            if (prefs.isConfigured) {
                BackgroundSyncService.stop(getApplication())
                BackgroundSyncService.start(getApplication())
            }
        }
    }

    // --- Async status stateholders ---
    private val _verificationStatus = MutableStateFlow<VerificationStatus>(VerificationStatus.IDLE)
    val verificationStatus: StateFlow<VerificationStatus> = _verificationStatus.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanResult = MutableStateFlow<ScanResult?>(null)
    val scanResult: StateFlow<ScanResult?> = _scanResult.asStateFlow()

    // --- Room Database Streams ---
    val transactions: StateFlow<List<TransactionEntity>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val outboxItems: StateFlow<List<OutboxEntity>> = repository.allOutbox
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalTransactionsCount: StateFlow<Int> = repository.transactionsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingCount: StateFlow<Int> = repository.pendingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val sentCount: StateFlow<Int> = repository.sentCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val failedCount: StateFlow<Int> = repository.failedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val syncState: StateFlow<SyncState?> = repository.syncStateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun updateEndpoint(url: String) {
        apiEndpointInput.value = url
    }

    fun updateApiKey(key: String) {
        apiKeyInput.value = key
    }

    fun updateUserPhone(phone: String) {
        userPhoneNumberInput.value = phone
    }

    /**
     * Attempts API verification. If successful: saves setup locally, enables real-time intercepts,
     * and triggers historical scans immediately.
     */
    fun verifyAndSaveConfiguration() {
        val endpoint = apiEndpointInput.value.trim()
        val key = apiKeyInput.value.trim()
        val phone = userPhoneNumberInput.value.trim()

        if (endpoint.isEmpty()) {
            _verificationStatus.value = VerificationStatus.ERROR("API Endpoint URL cannot be empty")
            return
        }
        if (key.isEmpty()) {
            _verificationStatus.value = VerificationStatus.ERROR("API Key cannot be empty")
            return
        }
        if (phone.isEmpty()) {
            _verificationStatus.value = VerificationStatus.ERROR("User phone number cannot be empty")
            return
        }

        viewModelScope.launch {
            _verificationStatus.value = VerificationStatus.LOADING
            try {
                val verifyUrl = com.example.data.pref.getUrlForPath(endpoint, "verify")
                val authHeader = "Bearer $key"

                Log.d(TAG, "Requesting verify check: URL: $verifyUrl")
                val response = RetrofitClient.apiService.verifyApiKey(verifyUrl, authHeader)

                if (response.isSuccessful && response.body()?.success == true) {
                    prefs.saveConfig(endpoint, key, phone)
                    _isConfigured.value = true
                    _verificationStatus.value = VerificationStatus.SUCCESS
                    Log.d(TAG, "Verification successful. System active.")

                    // Start background service to maintain active 24/7 background running
                    BackgroundSyncService.start(getApplication())

                    // Trigger Historical Scan automatically on configuration success
                    triggerHistoricalScan()
                } else {
                    val code = response.code()
                    val body = response.body()
                    val errorString = response.errorBody()?.string() ?: ""
                    Log.e(TAG, "Verify failed: Code: $code, Body: $body, ErrorBody: $errorString")
                    
                    val reason = if (body != null && !body.success) {
                        "Server returned success=false. Server rejected credentials."
                    } else if (code == 401 || code == 403) {
                        "Invalid API Key authorization (401/403)."
                    } else {
                        "Server did not return { \"success\": true } (Code $code)"
                    }
                    _verificationStatus.value = VerificationStatus.ERROR(reason)
                    deactivateConfiguration()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Verify catch connection failure", e)
                val msg = e.localizedMessage ?: "Unknown connectivity problem. Ensure domain is valid."
                _verificationStatus.value = VerificationStatus.ERROR("Connection failed: $msg")
                deactivateConfiguration()
            }
        }
    }

    fun deactivateConfiguration() {
        prefs.clearConfig()
        _isConfigured.value = false
        _verificationStatus.value = VerificationStatus.IDLE
        BackgroundSyncService.stop(getApplication())
    }

    fun clearVerificationStatus() {
        _verificationStatus.value = VerificationStatus.IDLE
    }

    /**
     * Scans matching SMS records historically in base inbox, stores unseen, and triggers synchronize worker.
     */
    fun triggerHistoricalScan() {
        if (_isScanning.value) return
        _isScanning.value = true
        _scanResult.value = null

        viewModelScope.launch {
            try {
                Log.d(TAG, "Scouting historical SMS messages in database.")
                val result = HistoricalSmsScanner.scanHistoricalSms(getApplication(), repository)
                _scanResult.value = result
                Log.d(TAG, "Historical scan completed: New: ${result.totalNewInserted}, Dupes: ${result.totalDuplicates}")
                
                // Immediately check if there are newly added items and trigger background sync uploads
                if (result.totalNewInserted > 0) {
                    repository.triggerSync()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Historical scanning throw error", e)
                _scanResult.value = ScanResult(false, 0, 0, 0, 0, e.localizedMessage ?: "Scanning failed")
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun triggerManualSync() {
        repository.triggerSync()
    }

    fun forceResyncAllTransactions() {
        viewModelScope.launch {
            repository.forceResyncAll()
        }
    }

    fun performDeltaSync() {
        viewModelScope.launch {
            repository.performSmartDeltaSync()
        }
    }

    fun clearLocalDatabase() {
        viewModelScope.launch {
            repository.clearDatabase()
        }
    }
}
