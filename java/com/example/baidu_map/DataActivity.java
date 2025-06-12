package com.example.baidu_map;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class DataActivity extends AppCompatActivity {
    private boolean isLocked = false;
    private ImageView btnLock;
    private ViewPager2 viewPager;
    private TabLayout tab_layout;
    private String[] titles = {"实时数据","数据设置","实时数据图","含量分析图","雷达图"};

    private Handler handler = new Handler();
    private Runnable refreshRunnable;
    public static final String DATA_UPDATE_ACTION = "com.example.baidu_map.DATA_UPDATE";

    private ContentObserver rotationObserver;

    private boolean isAutoRotateEnabled() {
        return Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
    }

    private void setScreenOrientationBasedOnSystem() {
        if (isAutoRotateEnabled()) {
            // 允许自动旋转
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        } else {
            // 锁定当前方向
            int currentOrientation = getResources().getConfiguration().orientation;
            if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data);

        //初始化控件
        tab_layout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.viewPager);
        btnLock = findViewById(R.id.btn_lock);

        btnLock.setOnClickListener(v -> toggleLockState());

        // 设置点击监听
        btnLock.setOnClickListener(v -> toggleLockState());


        // 全屏并延伸内容
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        // 适配横竖屏的刘海
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        getWindow().setAttributes(lp);

        //viewPager 设置一个adpter
        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                String title = titles[position];
                TabNewsFragment tabNewsFragment = TabNewsFragment.newInstance(title);

                return tabNewsFragment;
            }

            @Override
            public int getItemCount() {
                return titles.length;
            }
        });

        //tab_Layout点击事件
        tab_layout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {

                //设置viewpager选中当前页
                viewPager.setCurrentItem(tab.getPosition(),false);

            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });


        //tab_layout和viewPAger关联
        TabLayoutMediator tabLayoutMediator =new TabLayoutMediator(tab_layout, viewPager, new TabLayoutMediator.TabConfigurationStrategy() {
            @Override
            public void onConfigureTab(@NonNull TabLayout.Tab tab, int position) {
                tab.setText(titles[position]);
            }
        });

        tabLayoutMediator.attach();
        btnLock = findViewById(R.id.btn_lock);
        btnLock.setOnClickListener(v -> toggleLockState());
    }


    private void toggleLockState() {
        isLocked = !isLocked;

        // 切换图标
        btnLock.setImageResource(isLocked ? R.drawable.ic_lock_closed : R.drawable.ic_lock_open);

        // 控制交互
        viewPager.setUserInputEnabled(!isLocked); // 禁用/启用滑动切换
        setTabClickable(!isLocked); // 控制Tab点击

        btnLock.animate()
                .scaleX(0.9f).scaleY(0.9f)
                .setDuration(80)
                .withEndAction(() ->
                        btnLock.animate().scaleX(1f).scaleY(1f).setDuration(80))
                .start();

        // 添加状态提示
        Toast.makeText(this,
                isLocked ? "已锁定切换" : "已解锁切换",
                Toast.LENGTH_SHORT).show();
    }

    private void setTabClickable(boolean enable) {
        // 获取Tab容器（适用于Material Components 1.3.0+）
        ViewGroup tabContainer = (ViewGroup) tab_layout.getChildAt(0);
        if (tabContainer != null) {
            for (int i = 0; i < tabContainer.getChildCount(); i++) {
                View tabView = tabContainer.getChildAt(i);
                if (tabView != null) {
                    tabView.setClickable(enable);
                    tabView.setFocusable(enable);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(refreshRunnable);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
        viewPager.requestLayout();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 注册监听器
        rotationObserver = new ContentObserver(new Handler()) {
        };
        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
                false,
                rotationObserver
        );
        setScreenOrientationBasedOnSystem();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 注销监听器
        if (rotationObserver != null) {
            getContentResolver().unregisterContentObserver(rotationObserver);
            rotationObserver = null;
        }
    }

}