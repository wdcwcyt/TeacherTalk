package com.zl.teachertalk.base;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

/**
 * ViewBinding 封装基类 Fragment (Java版)
 * @param <VB> 对应页面的 ViewBinding 类
 */
public abstract class BaseFragment<VB extends ViewBinding> extends Fragment {

    // ViewBinding 实例
    protected VB binding;

    protected Handler mHandler;



    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // 创建 Binding
        binding = createBinding(inflater, container);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mHandler = new Handler(Looper.getMainLooper());


    }

    /**
     * 创建 ViewBinding（子类必须实现）
     */
    protected abstract VB createBinding(LayoutInflater inflater, ViewGroup container);






    /**
     * 设置控件的高度为 状态栏高度
     * @param view 要设置高度的View（比如占位View、状态栏占位条）
     */
    protected void setViewHeightToStatusBar(View view) {
        if (view == null) return;

        // 获取状态栏高度
        int statusBarHeight = getStatusBarHeight();

        // 设置给View
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = statusBarHeight;
        view.setLayoutParams(params);
    }

    /**
     * 获取系统状态栏高度（工具方法）
     */
    private int getStatusBarHeight() {
        int height = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            height = getResources().getDimensionPixelSize(resourceId);
        }
        return height;
    }





    /**
     * 销毁时释放，防止内存泄漏
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }
    }
}