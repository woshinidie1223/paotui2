package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.db.AppRepository
import com.example.db.UserEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: AppRepository) : ViewModel() {

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _nickname = MutableStateFlow("")
    val nickname: StateFlow<String> = _nickname.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _otpCode = MutableStateFlow("")
    val otpCode: StateFlow<String> = _otpCode.asStateFlow()

    private val _isRegisterMode = MutableStateFlow(false)
    val isRegisterMode: StateFlow<Boolean> = _isRegisterMode.asStateFlow()

    private val _countdown = MutableStateFlow(0)
    val countdown: StateFlow<Int> = _countdown.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private var countdownJob: Job? = null

    fun updateEmail(value: String) {
        _email.value = value
        _errorMessage.value = null
    }

    fun updateNickname(value: String) {
        _nickname.value = value
        _errorMessage.value = null
    }

    fun updatePassword(value: String) {
        _password.value = value
        _errorMessage.value = null
    }

    fun updateOtp(value: String) {
        _otpCode.value = value
        _errorMessage.value = null
    }

    fun toggleMode() {
        _isRegisterMode.value = !_isRegisterMode.value
        _errorMessage.value = null
        _successMessage.value = null
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    fun sendVerificationCode() {
        val currentEmail = _email.value.trim()
        if (currentEmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(currentEmail).matches()) {
            _errorMessage.value = "请输入有效的邮箱地址！"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _successMessage.value = null
            
            val response = repository.sendVerificationCode(currentEmail)
            _isLoading.value = false
            
            if (response.success) {
                _successMessage.value = response.message
                startCountdown()
            } else {
                _errorMessage.value = response.message
            }
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        _countdown.value = 60
        countdownJob = viewModelScope.launch {
            while (_countdown.value > 0) {
                delay(1000)
                _countdown.value -= 1
            }
        }
    }

    fun executeAuthAction(onSuccess: (UserEntity) -> Unit) {
        val currentEmail = _email.value.trim().lowercase()
        val currentPassword = _password.value
        val currentNickname = _nickname.value.trim()
        val currentOtp = _otpCode.value.trim()

        if (currentEmail.isBlank() || currentPassword.isBlank()) {
            _errorMessage.value = "邮箱或密码不能为空！"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _successMessage.value = null

            if (_isRegisterMode.value) {
                if (currentNickname.isBlank()) {
                    _errorMessage.value = "昵称不能为空！"
                    _isLoading.value = false
                    return@launch
                }
                if (currentOtp.isBlank()) {
                    _errorMessage.value = "验证码不能为空！"
                    _isLoading.value = false
                    return@launch
                }

                val response = repository.registerUser(
                    email = currentEmail,
                    nickname = currentNickname,
                    passwordHash = currentPassword,
                    otpCode = currentOtp
                )
                _isLoading.value = false
                if (response.success && response.data != null) {
                    _successMessage.value = "注册成功，正在为您自动登录..."
                    _currentUser.value = response.data
                    onSuccess(response.data)
                } else {
                    _errorMessage.value = response.message
                }
            } else {
                val response = repository.loginUser(currentEmail, currentPassword)
                _isLoading.value = false
                if (response.success && response.data != null) {
                    _successMessage.value = "登录成功！"
                    _currentUser.value = response.data
                    onSuccess(response.data)
                } else {
                    _errorMessage.value = response.message
                }
            }
        }
    }

    fun logout() {
        _currentUser.value = null
        _password.value = ""
        _otpCode.value = ""
        _successMessage.value = "已退出登录"
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }
}

class AuthViewModelFactory(private val repository: AppRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
