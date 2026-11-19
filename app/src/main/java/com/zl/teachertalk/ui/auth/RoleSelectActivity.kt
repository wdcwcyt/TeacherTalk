package com.zl.teachertalk.ui.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import com.zl.teachertalk.base.BaseActivity
import com.zl.teachertalk.databinding.ActivityRoleSelectBinding

/**
 * 身份选择页：让注册用户选择家长或老师，并通过 Activity Result 返回角色。
 *
 * 当前后端仍使用 `STUDENT` 表示老师/大学生角色，因此 [ROLE_TEACHER] 映射为 `STUDENT`。
 */
class RoleSelectActivity : BaseActivity<ActivityRoleSelectBinding>() {

    override fun onCreateBinding(inflater: LayoutInflater): ActivityRoleSelectBinding {
        return ActivityRoleSelectBinding.inflate(inflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.roleParent.setOnClickListener {
            finishWithRole(ROLE_PARENT)
        }
        binding.roleTeacher.setOnClickListener {
            finishWithRole(ROLE_TEACHER)
        }
    }

    private fun finishWithRole(role: String) {
        setResult(
            RESULT_OK,
            Intent().putExtra(EXTRA_SELECTED_ROLE, role)
        )
        finish()
    }

    companion object {
        /**
         * Activity Result 中返回身份角色的 key。
         */
        const val EXTRA_SELECTED_ROLE = "selected_role"

        /**
         * 家长身份。
         */
        const val ROLE_PARENT = "PARENT"

        /**
         * 老师身份；当前后端角色枚举使用 `STUDENT` 表示老师/大学生。
         */
        const val ROLE_TEACHER = "STUDENT"

        /**
         * 创建身份选择页 Intent。
         *
         * @param context 用于启动页面的上下文
         * @return 可直接启动的 Intent
         */
        fun createIntent(context: Context): Intent {
            return Intent(context, RoleSelectActivity::class.java)
        }
    }
}
