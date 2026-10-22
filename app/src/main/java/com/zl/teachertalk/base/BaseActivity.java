package com.zl.teachertalk.base;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.viewbinding.ViewBinding;

public abstract class BaseActivity<VB extends ViewBinding> extends AppCompatActivity {

    protected VB binding;
    // 安全的主线程 Handler
    protected Handler mHandler;

    /**
     * 由子类实现，传入对应的 ViewBinding
     */
    protected abstract VB onCreateBinding(LayoutInflater inflater);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 初始化 Handler（主线程，安全无泄漏）
        mHandler = new Handler(Looper.getMainLooper());

        // 沉浸式状态栏
        setImmersiveStatusBar(this, true);

        // 创建 ViewBinding
        binding = onCreateBinding(getLayoutInflater());
        setContentView(binding.getRoot());
    }

    private void setImmersiveStatusBar(Activity activity, boolean isLightStatusBar) {
        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(isLightStatusBar);

        View contentView = findViewById(Window.ID_ANDROID_CONTENT);
        ViewCompat.setOnApplyWindowInsetsListener(contentView, (v, insets) -> {
            // 只获取底部导航栏高度
            int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            // 只设置底部padding，顶部0，不遮挡沉浸式
            v.setPadding(0, 0, 0, bottom);
            return insets.consumeSystemWindowInsets();
        });

    }




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



    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 释放 ViewBinding
        binding = null;
        // 移除所有消息 + 释放 Handler，彻底避免内存泄漏
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
    }
}