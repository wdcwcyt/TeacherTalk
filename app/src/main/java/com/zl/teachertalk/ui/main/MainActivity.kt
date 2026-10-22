package com.zl.teachertalk.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.zl.teachertalk.R
import com.zl.teachertalk.base.BaseActivity
import com.zl.teachertalk.databinding.ActivityMainBinding
import com.zl.teachertalk.ui.forum.ForumFragment
import com.zl.teachertalk.ui.message.MessageFragment
import com.zl.teachertalk.ui.order.OrderFragment
import com.zl.teachertalk.ui.profile.ProfileFragment

/**
 * 主页容器：管理四个内容 Fragment，并记录当前显示的内容页。
 *
 * 当前加号只保留操作入口，发帖和发布需求的页面选择流程后续接入。
 */
class MainActivity : BaseActivity<ActivityMainBinding>() {

    private var selectedTabId = R.id.tabForum

    override fun onCreateBinding(inflater: LayoutInflater): ActivityMainBinding {
        return ActivityMainBinding.inflate(inflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.tabForum.setOnClickListener { selectTab(R.id.tabForum) }
        binding.tabOrder.setOnClickListener { selectTab(R.id.tabOrder) }
        binding.tabMessage.setOnClickListener { selectTab(R.id.tabMessage) }
        binding.tabProfile.setOnClickListener { selectTab(R.id.tabProfile) }
        binding.tabAdd.setOnClickListener {
            Toast.makeText(this, R.string.main_add_pending, Toast.LENGTH_SHORT).show()
        }

        val restoredTabId = savedInstanceState?.getInt(KEY_SELECTED_TAB, DEFAULT_TAB_ID)
            ?: DEFAULT_TAB_ID
        if (savedInstanceState == null ||
            supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null
        ) {
            selectTab(restoredTabId)
        } else {
            selectedTabId = restoredTabId
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_SELECTED_TAB, selectedTabId)
        super.onSaveInstanceState(outState)
    }

    private fun selectTab(tabId: Int) {
        val tab = when (tabId) {
            R.id.tabForum -> MainTab(TAG_FORUM, ::ForumFragment)
            R.id.tabOrder -> MainTab(TAG_ORDER, ::OrderFragment)
            R.id.tabMessage -> MainTab(TAG_MESSAGE, ::MessageFragment)
            R.id.tabProfile -> MainTab(TAG_PROFILE, ::ProfileFragment)
            else -> return
        }

        if (selectedTabId != tabId || supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
            val targetFragment = supportFragmentManager.findFragmentByTag(tab.tag)
                ?: tab.createFragment()
            val transaction = supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)

            supportFragmentManager.fragments
                .filter { it.tag in MAIN_FRAGMENT_TAGS && it.tag != tab.tag }
                .forEach { transaction.hide(it) }

            if (targetFragment.isAdded) {
                transaction.show(targetFragment)
            } else {
                transaction.add(R.id.fragmentContainer, targetFragment, tab.tag)
            }
            transaction.commitNow()
        }

        selectedTabId = tabId
    }

    private data class MainTab(
        val tag: String,
        val createFragment: () -> Fragment,
    )

    private companion object {
        const val KEY_SELECTED_TAB = "selected_tab"
        val DEFAULT_TAB_ID = R.id.tabForum
        const val TAG_FORUM = "main_forum"
        const val TAG_ORDER = "main_order"
        const val TAG_MESSAGE = "main_message"
        const val TAG_PROFILE = "main_profile"
        val MAIN_FRAGMENT_TAGS = setOf(TAG_FORUM, TAG_ORDER, TAG_MESSAGE, TAG_PROFILE)
    }
}
