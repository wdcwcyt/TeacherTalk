package com.zl.teachertalk.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zl.teachertalk.R
import com.zl.teachertalk.appContainer
import com.zl.teachertalk.base.BaseFragment
import com.zl.teachertalk.bean.ProfileAvatarError
import com.zl.teachertalk.bean.ProfileUiState
import com.zl.teachertalk.databinding.FragmentProfileBinding
import com.zl.teachertalk.model.profile.ProfileViewModel
import com.zl.teachertalk.net.NetworkConfig
import com.zl.teachertalk.ui.auth.LoginActivity

/**
 * 我的内容页：展示当前登录用户，并提供退出登录和后续功能占位入口。
 */
class ProfileFragment : BaseFragment<FragmentProfileBinding>() {

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
        setPendingFeatureClickListener(
            currentBinding.btnEditProfile,
            currentBinding.rowMyPosts,
            currentBinding.rowMyFavorites,
            currentBinding.rowBrowsingHistory,
            currentBinding.rowMessageNotification,
            currentBinding.rowAccountSecurity,
            currentBinding.rowSettings
        )
        currentBinding.btnLogout.setOnClickListener {
            showLogoutConfirmation()
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
        viewModel.loadProfile()
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
        currentBinding.avatarContainer.isEnabled = !state.isUpdatingAvatar
        currentBinding.ivAvatar.alpha = if (state.isUpdatingAvatar) {
            UPLOADING_AVATAR_ALPHA
        } else {
            1f
        }

        val userInfo = state.userInfo ?: return

        currentBinding.tvNickname.text = userInfo.nickname
        currentBinding.tvUserId.text = getString(
            R.string.profile_user_id_format,
            userInfo.id
        )
        bindAvatar(userInfo.avatarUrl)
    }

    private fun openAvatarPicker() {
        if (viewModel.uiState.value?.isUpdatingAvatar == true) return
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
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_logout_confirm_title)
            .setMessage(R.string.profile_logout_confirm_message)
            .setNegativeButton(R.string.profile_logout_cancel, null)
            .setPositiveButton(R.string.profile_logout_confirm) { _, _ ->
                viewModel.logout()
            }
            .show()
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
    }
}
