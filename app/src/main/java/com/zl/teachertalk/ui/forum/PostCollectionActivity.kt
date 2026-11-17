package com.zl.teachertalk.ui.forum

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zl.teachertalk.R
import com.zl.teachertalk.adapter.ForumPostAdapter
import com.zl.teachertalk.appContainer
import com.zl.teachertalk.base.BaseActivity
import com.zl.teachertalk.bean.ForumLoadError
import com.zl.teachertalk.bean.ForumPostSource
import com.zl.teachertalk.bean.ForumUiState
import com.zl.teachertalk.databinding.ActivityPostCollectionBinding
import com.zl.teachertalk.model.forum.ForumViewModel
import com.zl.teachertalk.ui.auth.LoginActivity

/**
 * 我的帖子 / 我的收藏列表页，复用论坛列表的分页和刷新流程。
 */
class PostCollectionActivity : BaseActivity<ActivityPostCollectionBinding>() {

    private val postSource: ForumPostSource by lazy {
        when (
            intent.getStringExtra(EXTRA_POST_SOURCE)
        ) {
            ForumPostSource.FAVORITES.name -> ForumPostSource.FAVORITES
            else -> ForumPostSource.MY_POSTS
        }
    }

    private var isOpeningDetail = false

    private val postDetailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isOpeningDetail = false
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.refreshManually()
        }
    }

    private val postAdapter = ForumPostAdapter { postId ->
        if (!isOpeningDetail) {
            isOpeningDetail = true
            postDetailLauncher.launch(
                ForumPostDetailActivity.createIntent(
                    context = this,
                    postId = postId
                )
            )
        }
    }

    private val viewModel: ForumViewModel by lazy {
        ViewModelProvider(
            this,
            ForumViewModel.factory(
                forumRepository = appContainer.forumRepository,
                sessionManager = appContainer.sessionManager,
                postSource = postSource
            )
        )[ForumViewModel::class.java]
    }

    override fun onCreateBinding(inflater: LayoutInflater): ActivityPostCollectionBinding {
        return ActivityPostCollectionBinding.inflate(inflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.tvPageTitle.setText(titleRes())
        binding.btnBack.setOnClickListener { finish() }
        binding.swipeRefresh.setColorSchemeResources(R.color.main_primary)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshManually()
        }
        binding.rvPosts.adapter = postAdapter
        binding.rvPosts.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) {
                    loadNextPageIfNeeded()
                }
            }
        })
        binding.btnRetry.setOnClickListener {
            viewModel.refreshManually()
        }

        viewModel.uiState.observe(this) { state ->
            render(state)
        }
        viewModel.loadErrors.observe(this) { event ->
            val error = event.getContentIfNotHandled() ?: return@observe
            val messageRes = when (error) {
                ForumLoadError.REFRESH_FAILED -> R.string.forum_refresh_failed
                ForumLoadError.LOAD_MORE_FAILED -> R.string.forum_load_more_failed
            }
            Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
        }
        viewModel.loginRequiredEvents.observe(this) { event ->
            if (event.getContentIfNotHandled() != null) {
                openLoginAndClearTask()
            }
        }
        viewModel.refreshIfStale()
    }

    override fun onResume() {
        super.onResume()
        isOpeningDetail = false
        viewModel.refreshIfStale()
    }

    private fun render(state: ForumUiState) {
        postAdapter.submitList(state.posts)
        binding.swipeRefresh.isRefreshing = state.isRefreshing
        binding.progressInitial.isVisible = state.isInitialLoading
        binding.progressLoadMore.isVisible = state.isLoadingMore

        val showState = state.posts.isEmpty() &&
            !state.isInitialLoading &&
            !state.isRefreshing
        binding.stateContainer.isVisible = showState
        binding.tvStateMessage.setText(
            if (state.initialLoadFailed) loadFailedRes() else emptyRes()
        )
        binding.btnRetry.isVisible = state.initialLoadFailed
    }

    private fun loadNextPageIfNeeded() {
        val layoutManager =
            binding.rvPosts.layoutManager as? LinearLayoutManager ?: return
        val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
        if (postAdapter.itemCount == 0) return
        if (lastVisiblePosition >= postAdapter.itemCount - ForumViewModel.PREFETCH_DISTANCE) {
            viewModel.loadNextPage()
        }
    }

    private fun titleRes(): Int {
        return when (postSource) {
            ForumPostSource.FAVORITES -> R.string.profile_my_favorites
            else -> R.string.profile_my_posts
        }
    }

    private fun emptyRes(): Int {
        return when (postSource) {
            ForumPostSource.FAVORITES -> R.string.my_favorites_empty
            else -> R.string.my_posts_empty
        }
    }

    private fun loadFailedRes(): Int {
        return when (postSource) {
            ForumPostSource.FAVORITES -> R.string.my_favorites_load_failed
            else -> R.string.my_posts_load_failed
        }
    }

    private fun openLoginAndClearTask() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    companion object {
        private const val EXTRA_POST_SOURCE = "post_source"

        /**
         * 创建我的帖子或我的收藏页面。
         *
         * @param context 用于创建 Intent 的上下文
         * @param source 只能传 [ForumPostSource.MY_POSTS] 或 [ForumPostSource.FAVORITES]
         * @return 可直接启动的 Intent
         */
        fun createIntent(context: Context, source: ForumPostSource): Intent {
            require(
                source == ForumPostSource.MY_POSTS ||
                    source == ForumPostSource.FAVORITES
            ) {
                "PostCollectionActivity only supports MY_POSTS or FAVORITES"
            }
            return Intent(context, PostCollectionActivity::class.java).apply {
                putExtra(EXTRA_POST_SOURCE, source.name)
            }
        }
    }
}
