package com.zl.teachertalk.ui.auth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.zl.teachertalk.R
import com.zl.teachertalk.appContainer
import com.zl.teachertalk.base.BaseActivity
import com.zl.teachertalk.bean.AuthError
import com.zl.teachertalk.bean.AuthUiState
import com.zl.teachertalk.databinding.ActivityLoginBinding
import com.zl.teachertalk.model.auth.LoginViewModel
import com.zl.teachertalk.ui.main.MainActivity

/**
 * 登录页：请求短信验证码、登录并进入主页。
 */
class LoginActivity : BaseActivity<ActivityLoginBinding>() {

    private val roleSelectLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val role = result.data
            ?.getStringExtra(RoleSelectActivity.EXTRA_SELECTED_ROLE)
            .orEmpty()
        if (role.isNotBlank()) {
            startActivity(RegisterActivity.createIntent(this, role))
        }
    }

    private val viewModel: LoginViewModel by lazy {
        ViewModelProvider(
            this,
            LoginViewModel.factory(
                authRepository = appContainer.authRepository,
                sessionManager = appContainer.sessionManager
            )
        ).get(LoginViewModel::class.java)
    }

    override fun onCreateBinding(inflater: LayoutInflater): ActivityLoginBinding {
        return ActivityLoginBinding.inflate(inflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.txtRequestCode.setOnClickListener {
            viewModel.sendSmsCode(binding.editPhone.text?.toString().orEmpty())
        }
        binding.txtLogin.setOnClickListener {
            viewModel.login(
                phone = binding.editPhone.text?.toString().orEmpty(),
                code = binding.editVerificationCode.text?.toString().orEmpty()
            )
        }
        binding.txtRegister.setOnClickListener {
            roleSelectLauncher.launch(RoleSelectActivity.createIntent(this))
        }

        observeViewModel()
        viewModel.checkSavedSession()
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state -> renderUiState(state) }
        viewModel.errorEvents.observe(this) { event ->
            event.getContentIfNotHandled()?.let { error -> showError(error) }
        }
        viewModel.codeSentEvents.observe(this) { event ->
            if (event.getContentIfNotHandled() != null) {
                Toast.makeText(this, R.string.auth_code_sent, Toast.LENGTH_SHORT).show()
            }
        }
        viewModel.authenticatedEvents.observe(this) { event ->
            if (event.getContentIfNotHandled() != null) {
                openMainAndClearAuthTask()
            }
        }
    }

    private fun renderUiState(state: AuthUiState) {
        binding.scrollAuth.isVisible = !state.isCheckingSession
        binding.layoutSessionCheck.isVisible = state.isCheckingSession

        binding.txtRequestCode.text = when {
            state.isRequestingCode -> getText(R.string.auth_sending_code)
            state.codeCooldownSeconds > 0 -> getString(
                R.string.auth_resend_countdown,
                state.codeCooldownSeconds
            )

            else -> getText(R.string.auth_request_code)
        }

        val canRequestCode = !state.isRequestingCode &&
            !state.isSubmitting &&
            !state.isCheckingSession &&
            state.codeCooldownSeconds <= 0
        binding.txtRequestCode.isEnabled = canRequestCode
        binding.txtRequestCode.alpha = if (canRequestCode) 1f else DISABLED_ALPHA

        val canSubmit = !state.isRequestingCode &&
            !state.isSubmitting &&
            !state.isCheckingSession
        binding.txtLogin.isEnabled = canSubmit
        binding.txtLogin.alpha = if (canSubmit) 1f else DISABLED_ALPHA
    }

    private fun showError(error: AuthError) {
        val messageRes = when (error) {
            AuthError.INVALID_PHONE -> R.string.auth_error_invalid_phone
            AuthError.INVALID_CODE -> R.string.auth_error_invalid_code
            AuthError.INVALID_INPUT -> R.string.auth_error_invalid_input
            AuthError.CODE_INVALID_OR_EXPIRED -> R.string.auth_error_code_expired
            AuthError.PHONE_NOT_REGISTERED -> R.string.auth_error_phone_not_registered
            AuthError.PHONE_ALREADY_REGISTERED -> R.string.auth_error_phone_registered
            AuthError.SESSION_EXPIRED -> R.string.auth_error_session_expired
            AuthError.NETWORK -> R.string.auth_error_network
            AuthError.SERVER -> R.string.auth_error_server
            AuthError.UNKNOWN -> R.string.auth_error_unknown
        }
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun openMainAndClearAuthTask() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    private companion object {
        const val DISABLED_ALPHA = 0.6f
    }
}
