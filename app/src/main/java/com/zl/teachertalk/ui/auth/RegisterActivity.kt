package com.zl.teachertalk.ui.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.zl.teachertalk.R
import com.zl.teachertalk.appContainer
import com.zl.teachertalk.base.BaseActivity
import com.zl.teachertalk.bean.AuthError
import com.zl.teachertalk.bean.AuthUiState
import com.zl.teachertalk.databinding.ActivityRegisterBinding
import com.zl.teachertalk.model.auth.RegisterViewModel
import com.zl.teachertalk.ui.main.MainActivity

/**
 * 注册页：请求短信验证码、按身份选择结果注册账号并进入主页。
 */
class RegisterActivity : BaseActivity<ActivityRegisterBinding>() {

    private val selectedRole: String by lazy {
        intent.getStringExtra(EXTRA_ROLE).orEmpty()
    }

    private val viewModel: RegisterViewModel by lazy {
        ViewModelProvider(
            this,
            RegisterViewModel.factory(
                authRepository = appContainer.authRepository,
                sessionManager = appContainer.sessionManager
            )
        ).get(RegisterViewModel::class.java)
    }

    override fun onCreateBinding(inflater: LayoutInflater): ActivityRegisterBinding {
        return ActivityRegisterBinding.inflate(inflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.txtRequestCode.setOnClickListener {
            viewModel.sendSmsCode(binding.editPhone.text?.toString().orEmpty())
        }
        binding.txtRegisterSubmit.setOnClickListener {
            viewModel.register(
                phone = binding.editPhone.text?.toString().orEmpty(),
                code = binding.editVerificationCode.text?.toString().orEmpty(),
                role = selectedRole
            )
        }
        binding.txtBackToLogin.setOnClickListener { finish() }

        observeViewModel()
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
        viewModel.registeredEvents.observe(this) { event ->
            if (event.getContentIfNotHandled() != null) {
                openMainAndClearAuthTask()
            }
        }
    }

    private fun renderUiState(state: AuthUiState) {
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
            state.codeCooldownSeconds <= 0
        binding.txtRequestCode.isEnabled = canRequestCode
        binding.txtRequestCode.alpha = if (canRequestCode) 1f else DISABLED_ALPHA

        val canSubmit = !state.isRequestingCode && !state.isSubmitting
        binding.txtRegisterSubmit.isEnabled = canSubmit
        binding.txtRegisterSubmit.alpha = if (canSubmit) 1f else DISABLED_ALPHA
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

    companion object {
        private const val EXTRA_ROLE = "role"
        private const val DISABLED_ALPHA = 0.6f

        /**
         * 创建带注册身份参数的注册页 Intent。
         *
         * @param context 用于启动页面的上下文
         * @param role 身份选择页返回的角色
         * @return 可直接启动的 Intent
         */
        fun createIntent(context: Context, role: String): Intent {
            return Intent(context, RegisterActivity::class.java).putExtra(EXTRA_ROLE, role)
        }
    }
}
