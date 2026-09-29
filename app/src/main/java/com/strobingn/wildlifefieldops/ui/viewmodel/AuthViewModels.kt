package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.data.auth.AuthActionResult
import com.strobingn.wildlifefieldops.data.auth.AuthSessionRepository
import com.strobingn.wildlifefieldops.data.auth.AuthUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppAuthViewModel @Inject constructor(
    private val auth: AuthSessionRepository
) : ViewModel() {
    val uiState: StateFlow<AuthUiState> = auth.uiState
    val continueOffline: StateFlow<Boolean?> = auth.continueOffline
}

data class SignInFormState(
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val passwordVisible: Boolean = false
)

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val auth: AuthSessionRepository
) : ViewModel() {
    val uiState: StateFlow<AuthUiState> = auth.uiState

    private val _form = MutableStateFlow(SignInFormState())
    val form: StateFlow<SignInFormState> = _form.asStateFlow()

    fun setEmail(value: String) {
        _form.value = _form.value.copy(email = value, error = null)
    }

    fun setPassword(value: String) {
        _form.value = _form.value.copy(password = value, error = null)
    }

    fun togglePasswordVisible() {
        _form.value = _form.value.copy(passwordVisible = !_form.value.passwordVisible)
    }

    fun signIn(onSuccess: () -> Unit) {
        val current = _form.value
        if (current.busy) return
        viewModelScope.launch {
            _form.value = current.copy(busy = true, error = null)
            when (val result = auth.signIn(current.email, current.password)) {
                AuthActionResult.Success -> {
                    _form.value = _form.value.copy(busy = false, password = "")
                    onSuccess()
                }
                is AuthActionResult.Failure -> {
                    _form.value = _form.value.copy(busy = false, error = result.message)
                }
            }
        }
    }

    fun continueOffline(onDone: () -> Unit) {
        viewModelScope.launch {
            auth.setContinueOffline(true)
            onDone()
        }
    }
}
