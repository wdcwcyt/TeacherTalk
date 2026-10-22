package com.zl.teachertalk.ui.forum

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zl.teachertalk.R
import com.zl.teachertalk.adapter.ForumPostAdapter
import com.zl.teachertalk.appContainer
import com.zl.teachertalk.base.BaseFragment
import com.zl.teachertalk.bean.ForumLoadError
import com.zl.teachertalk.bean.ForumUiState
import com.zl.teachertalk.databinding.FragmentForumBinding
import com.zl.teachertalk.model.forum.ForumViewModel

/**
 * 论坛列表页：展示帖子列表，并处理手动刷新、自动刷新和分页预加载。
 */
class ForumFragment : BaseFragment<FragmentForumBinding>() {

    private val postAdapter = ForumPostAdapter()

    private val viewModel: ForumViewModel by lazy {
        ViewModelProvider(
            this,
            ForumViewModel.factory(requireContext().appContainer.forumRepository)
        )[ForumViewModel::class.java]
    }

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentForumBinding {
        return FragmentForumBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentBinding = binding ?: return
        currentBinding.btnSearch.setOnClickListener {
            Toast.makeText(
                requireContext(),
                R.string.forum_search_pending,
                Toast.LENGTH_SHORT
            ).show()
        }

        currentBinding.swipeRefresh.setColorSchemeResources(R.color.main_primary)
        currentBinding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshManually()
        }

        currentBinding.rvPosts.layoutManager = LinearLayoutManager(requireContext())
        currentBinding.rvPosts.adapter = postAdapter
        currentBinding.rvPosts.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                loadNextPageIfNeeded()
            }
        })

        currentBinding.btnRetry.setOnClickListener {
            viewModel.refreshManually()
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            render(state)
        }
        viewModel.loadErrors.observe(viewLifecycleOwner) { event ->
            val error = event.getContentIfNotHandled() ?: return@observe
            val messageRes = when (error) {
                ForumLoadError.REFRESH_FAILED -> R.string.forum_refresh_failed
                ForumLoadError.LOAD_MORE_FAILED -> R.string.forum_load_more_failed
            }
            Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
        }
        viewModel.refreshIfStale()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshIfStale()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            viewModel.refreshIfStale()
        }
    }

    private fun render(state: ForumUiState) {
        val currentBinding = binding ?: return

        postAdapter.submitList(state.posts)
        currentBinding.swipeRefresh.isRefreshing = state.isRefreshing
        currentBinding.progressInitial.isVisible = state.isInitialLoading
        currentBinding.progressLoadMore.isVisible = state.isLoadingMore

        val showStateContainer = state.posts.isEmpty() &&
            !state.isInitialLoading &&
            !state.isRefreshing
        currentBinding.stateContainer.isVisible = showStateContainer
        currentBinding.tvStateMessage.setText(
            if (state.initialLoadFailed) {
                R.string.forum_load_failed
            } else {
                R.string.forum_empty
            }
        )
        currentBinding.btnRetry.isVisible = state.initialLoadFailed

    }

    private fun loadNextPageIfNeeded() {
        val currentBinding = binding ?: return
        val layoutManager = currentBinding.rvPosts.layoutManager as? LinearLayoutManager ?: return
        val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
        val itemCount = postAdapter.itemCount
        if (itemCount == 0) return

        if (lastVisiblePosition >= itemCount - ForumViewModel.PREFETCH_DISTANCE) {
            viewModel.loadNextPage()
        }
    }
}
