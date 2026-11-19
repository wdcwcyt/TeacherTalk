package com.zl.teachertalk.ui.profile

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.zl.teachertalk.R
import com.zl.teachertalk.appContainer
import com.zl.teachertalk.base.BaseFragment
import com.zl.teachertalk.bean.CancelAccountError
import com.zl.teachertalk.bean.ForumPostSource
import com.zl.teachertalk.bean.ProfileAvatarError
import com.zl.teachertalk.bean.ProfileUiState
import com.zl.teachertalk.databinding.DialogCancelAccountBinding
import com.zl.teachertalk.databinding.DialogLogoutBinding
import com.zl.teachertalk.databinding.FragmentProfileBinding
import com.zl.teachertalk.model.profile.ProfileViewModel
import com.zl.teachertalk.net.NetworkConfig
import com.zl.teachertalk.ui.auth.LoginActivity
import com.zl.teachertalk.ui.forum.PostCollectionActivity

/**
 * 我的内容页：展示当前登录用户，并提供退出登录和后续功能占位入口。
 */
class ProfileFragment : BaseFragment<FragmentProfileBinding>() {

    private var logoutDialog: Dialog? = null
    private var cancelAccountDialog: Dialog? = null
    private var cancelAccountDialogBinding: DialogCancelAccountBinding? = null

    private val viewModel: ProfileViewModel by lazy {
        ViewModelProvider(
            this,
            ProfileViewModel.factory(
                sessionManager = requireContext().appContainer.sessionManager,
                authRepository = requireContext().appContainer.authRepository,
                imageCompressor = requireContext().appContainer.postImageCompressor
            )
        )[ProfileViewModel::class.java]
    }

    private val avatarPicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.updateAvatar(uri)
        }
    }

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentProfileBinding {
        return FragmentProfileBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentBinding = binding ?: return
        currentBinding.tvUserBio.isVisible = false
        currentBinding.cardUserStats.isVisible = false

        currentBinding.avatarContainer.setOnClickListener {
            openAvatarPicker()
        }
        currentBinding.rowMyPosts.setOnClickListener {
            openPostCollection(ForumPostSource.MY_POSTS)
        }
        currentBinding.rowMyFavorites.setOnClickListener {
            openPostCollection(ForumPostSource.FAVORITES)
        }
        setPendingFeatureClickListener(
            currentBinding.btnEditProfile,
            currentBinding.rowBrowsingHistory,
            currentBinding.rowMessageNotification,
            currentBinding.rowAccountSecurity,
            currentBinding.rowSettings
        )
        currentBinding.btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }
        currentBinding.btnCancelAccount.setOnClickListener {
            showCancelAccountDialog()
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            render(state)
        }
        viewModel.loginRequiredEvents.observe(viewLifecycleOwner) { event ->
            if (event.getContentIfNotHandled() != null) {
                openLoginAndClearTask()
            }
        }
        viewModel.avatarErrorEvents.observe(viewLifecycleOwner) { event ->
            val error = event.getContentIfNotHandled() ?: return@observe
            showAvatarError(error)
        }
        viewModel.avatarUpdatedEvents.observe(viewLifecycleOwner) { event ->
            if (event.getContentIfNotHandled() != null) {
                Toast.makeText(
                    requireContext(),
                    R.string.profile_avatar_update_succeeded,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        viewModel.cancelCodeSentEvents.observe(viewLifecycleOwner) { event ->
            if (event.getContentIfNotHandled() != null) {
                Toast.makeText(
                    requireContext(),
                    R.string.auth_code_sent,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        viewModel.cancelAccountErrorEvents.observe(viewLifecycleOwner) { event ->
            val error = event.getContentIfNotHandled() ?: return@observe
            showCancelAccountError(error)
        }
        viewModel.accountCancelledEvents.observe(viewLifecycleOwner) { event ->
            if (event.getContentIfNotHandled() != null) {
                cancelAccountDialog?.dismiss()
                Toast.makeText(
                    requireContext(),
                    R.string.profile_cancel_account_succeeded,
                    Toast.LENGTH_SHORT
                ).show()
                openLoginAndClearTask()
            }
        }
        viewModel.loadProfile()
    }

    private fun openPostCollection(source: ForumPostSource) {
        startActivity(
            PostCollectionActivity.createIntent(
                context = requireContext(),
                source = source
            )
        )
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && view != null) {
            viewModel.loadProfile()
        }
    }

    private fun render(state: ProfileUiState) {
        val currentBinding = binding ?: return
        currentBinding.progressAvatarUpload.isVisible = state.isUpdatingAvatar
        currentBinding.avatarContainer.isEnabled =
            !state.isUpdatingAvatar && !state.isCancellingAccount
        currentBinding.btnLogout.isEnabled = !state.isCancellingAccount
        currentBinding.btnCancelAccount.isEnabled =
            !state.isCancellingAccount && !state.isUpdatingAvatar
        currentBinding.ivAvatar.alpha = if (state.isUpdatingAvatar) {
            UPLOADING_AVATAR_ALPHA
        } else {
            1f
        }
        renderCancelAccountDialog(state)

        val userInfo = state.userInfo ?: return

        currentBinding.tvNickname.text = userInfo.nickname
        currentBinding.tvUserId.text = getString(
            R.string.profile_user_id_format,
            userInfo.id
        )
        bindAvatar(userInfo.avatarUrl)
    }

    private fun openAvatarPicker() {
        val state = viewModel.uiState.value ?: return
        if (state.isUpdatingAvatar || state.isCancellingAccount) return
        avatarPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun bindAvatar(avatarUrl: String?) {
        val currentBinding = binding ?: return
        val normalizedUrl = avatarUrl?.trim().orEmpty()
        if (normalizedUrl.isEmpty()) {
            Glide.with(currentBinding.ivAvatar).clear(currentBinding.ivAvatar)
            currentBinding.ivAvatar.setImageResource(R.drawable.icon_user_avatar)
            return
        }

        Glide.with(currentBinding.ivAvatar)
            .load(NetworkConfig.resolveMediaUrl(normalizedUrl))
            .placeholder(R.drawable.icon_user_avatar)
            .error(R.drawable.icon_user_avatar)
            .centerCrop()
            .into(currentBinding.ivAvatar)
    }

    private fun setPendingFeatureClickListener(vararg views: View) {
        views.forEach { view ->
            view.setOnClickListener {
                Toast.makeText(
                    requireContext(),
                    R.string.profile_feature_pending,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showAvatarError(error: ProfileAvatarError) {
        val messageRes = when (error) {
            ProfileAvatarError.INVALID_IMAGE -> R.string.profile_avatar_invalid
            ProfileAvatarError.IMAGE_TOO_LARGE -> R.string.profile_avatar_too_large
            ProfileAvatarError.UPLOAD_FAILED -> R.string.profile_avatar_upload_failed
            ProfileAvatarError.NETWORK -> R.string.auth_error_network
            ProfileAvatarError.SERVER -> R.string.auth_error_server
            ProfileAvatarError.SESSION_EXPIRED -> R.string.auth_error_session_expired
        }
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun showLogoutConfirmation() {
        if (logoutDialog?.isShowing == true) return

        val dialogBinding = DialogLogoutBinding.inflate(layoutInflater)
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(dialogBinding.root)

        logoutDialog = dialog
        dialogBinding.btnDismissLogout.setOnClickListener {
            dialog.dismiss()
        }
        dialogBinding.btnConfirmLogout.setOnClickListener {
            dialog.dismiss()
            viewModel.logout()
        }
        dialog.setOnDismissListener {
            if (logoutDialog === dialog) {
                logoutDialog = null
            }
        }

        dialog.show()
        configureProfileDialogWindow(dialog)
    }

    private fun showCancelAccountDialog() {
        if (cancelAccountDialog?.isShowing == true) return
        val state = viewModel.uiState.value ?: ProfileUiState()
        if (state.isCancellingAccount || state.isUpdatingAvatar) return
        val userInfo = state.userInfo ?: return

        val dialogBinding = DialogCancelAccountBinding.inflate(layoutInflater)
        dialogBinding.tvCancelAccountPhone.text = getString(
            R.string.profile_cancel_account_phone_format,
            maskPhone(userInfo.phone)
        )
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(dialogBinding.root)
        dialog.setCanceledOnTouchOutside(false)

        cancelAccountDialog = dialog
        cancelAccountDialogBinding = dialogBinding
        dialogBinding.btnDismissCancelAccount.setOnClickListener {
            if (viewModel.uiState.value?.isCancellingAccount != true) {
                dialog.dismiss()
            }
        }
        dialogBinding.btnConfirmCancelAccount.setOnClickListener {
            val code = dialogBinding.etCancelAccountCode.text?.toString().orEmpty()
            if (code.length == ACCOUNT_CODE_LENGTH) {
                viewModel.cancelAccount(code)
            }
        }
        dialogBinding.btnRequestCancelAccountCode.setOnClickListener {
            viewModel.requestCancelAccountCode()
        }
        dialogBinding.etCancelAccountCode.doAfterTextChanged {
            dialogBinding.tilCancelAccountCode.error = null
            renderCancelAccountDialog(viewModel.uiState.value ?: ProfileUiState())
        }
        dialog.setOnDismissListener {
            if (cancelAccountDialog === dialog) {
                cancelAccountDialog = null
                cancelAccountDialogBinding = null
            }
        }
        dialog.show()
        configureProfileDialogWindow(dialog)
        renderCancelAccountDialog(viewModel.uiState.value ?: ProfileUiState())
    }

    private fun configureProfileDialogWindow(dialog: Dialog) {
        val window = dialog.window ?: return
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setGravity(Gravity.CENTER)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val horizontalMargin = resources.getDimensionPixelSize(
            R.dimen.profile_dialog_horizontal_margin
        )
        val maxWidth = resources.getDimensionPixelSize(
            R.dimen.profile_dialog_max_width
        )
        val availableWidth = (resources.displayMetrics.widthPixels - horizontalMargin * 2)
            .coerceAtLeast(1)
        window.setLayout(
            availableWidth.coerceAtMost(maxWidth),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun renderCancelAccountDialog(state: ProfileUiState) {
        val dialog = cancelAccountDialog ?: return
        val dialogBinding = cancelAccountDialogBinding ?: return
        if (!dialog.isShowing) return

        val isCancelling = state.isCancellingAccount
        dialogBinding.etCancelAccountCode.isEnabled = !isCancelling
        dialogBinding.btnRequestCancelAccountCode.isEnabled =
            !isCancelling &&
            !state.isRequestingCancelCode &&
            state.cancelCodeCooldownSeconds <= 0
        dialogBinding.btnRequestCancelAccountCode.text = when {
            state.isRequestingCancelCode -> getString(R.string.profile_cancel_code_sending)
            state.cancelCodeCooldownSeconds > 0 -> getString(
                R.string.auth_resend_countdown,
                state.cancelCodeCooldownSeconds
            )
            else -> getString(R.string.profile_cancel_get_code)
        }

        val hasCompleteCode = dialogBinding.etCancelAccountCode.text?.length == ACCOUNT_CODE_LENGTH
        val canSubmit = hasCompleteCode &&
            !isCancelling &&
            !state.isRequestingCancelCode
        dialogBinding.btnConfirmCancelAccount.isEnabled = canSubmit
        dialogBinding.btnConfirmCancelAccount.alpha =
            if (canSubmit) 1f else DISABLED_BUTTON_ALPHA
        dialogBinding.btnConfirmCancelAccount.text = getString(
            if (isCancelling) {
                R.string.profile_cancel_account_submitting
            } else {
                R.string.profile_cancel_account_confirm
            }
        )
        dialogBinding.btnDismissCancelAccount.isEnabled = !isCancelling
        dialog.setCancelable(!isCancelling)
        dialog.setCanceledOnTouchOutside(false)
    }

    private fun showCancelAccountError(error: CancelAccountError) {
        when (error) {
            CancelAccountError.INVALID_CODE -> {
                val dialogBinding = cancelAccountDialogBinding
                if (cancelAccountDialog?.isShowing == true && dialogBinding != null) {
                    dialogBinding.tilCancelAccountCode.error =
                        getString(R.string.profile_cancel_invalid_code)
                } else {
                    showToast(R.string.profile_cancel_invalid_code)
                }
            }

            CancelAccountError.INVALID_PHONE ->
                showToast(R.string.auth_error_invalid_phone)
            CancelAccountError.NETWORK -> showToast(R.string.auth_error_network)
            CancelAccountError.SERVER -> showToast(R.string.auth_error_server)
            CancelAccountError.SESSION_EXPIRED -> showToast(R.string.auth_error_session_expired)
            CancelAccountError.STATE_CONFLICT -> showToast(R.string.profile_cancel_state_conflict)
            CancelAccountError.UNKNOWN -> showToast(R.string.auth_error_unknown)
        }
    }

    private fun showToast(messageRes: Int) {
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun maskPhone(phone: String): String {
        if (phone.length != PHONE_NUMBER_LENGTH) return phone
        return phone.substring(0, 3) + "****" + phone.substring(7)
    }

    override fun onDestroyView() {
        logoutDialog?.dismiss()
        logoutDialog = null
        cancelAccountDialog?.dismiss()
        cancelAccountDialog = null
        cancelAccountDialogBinding = null
        super.onDestroyView()
    }

    private fun openLoginAndClearTask() {
        startActivity(
            Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        requireActivity().finish()
    }

    private companion object {
        const val UPLOADING_AVATAR_ALPHA = 0.55f
        const val DISABLED_BUTTON_ALPHA = 0.45f
        const val ACCOUNT_CODE_LENGTH = 6
        const val PHONE_NUMBER_LENGTH = 11
    }
}
