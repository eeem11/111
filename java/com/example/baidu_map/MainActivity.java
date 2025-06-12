package com.example.baidu_map;

import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.LogoPosition;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.TextOptions;
import com.baidu.mapapi.map.UiSettings;
import com.baidu.mapapi.utils.DistanceUtil;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationConfiguration;

import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.Polyline;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.model.LatLng;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private LinearLayout traceOptionsContainer;
    private CheckBox toggleLine;
    private List<LatLng> pollutionSources = new ArrayList<>();
    private CheckBox cbDeviceTrace, cbPollutionSource;
    private TabNewsFragment tabNewsFragment; // 用于持有TabNewsFragment的引用
    private volatile boolean isDataRefreshRunning = true;
    private ContentObserver rotationObserver;

    private TextView distanceTextView;
    private Polyline connectionLine;
    private boolean isLineVisible = false;

    private double[] gpsData = new double[2]; // 新增：存储最新经纬度的数组
    private Locator locator; // 修改：延迟初始化
    private ImageButton setSelfCenter;
    private ImageButton setLocatorCenter;
    private BaiduMap mBaiduMap;      // 定义百度地图对象
    private LocationClient mLocationClient;  // 定义定位客户端对象，用于启动和管理定位功能
    private boolean isFirstLoc = true; // 标记是否是第一次获取到定位数据，用于首次定位时更新地图视图，首次获取数据时以自己为中心
    private float direction = 0f;  // 保存设备当前的方向角度，用于地图方向箭头的调整
    private SensorManager sensorManager; // 定义传感器管理器，用于管理设备的各种传感器（如方向传感器）

    private MapView mMapView = null;

    private RadioGroup mapType;         // 地图类型
    private RadioButton normalBtn;      // 普通地图类型
    private RadioButton satelliteBtn;   // 卫星地图类型
    private CheckBox trafficEnabled;    // 路况图
    private CheckBox heatMapEnabled;   // 热力图


    private float[] rotationMatrix = new float[9];
    private float[] orientationAngles = new float[3];

    private float targetDirection = 0f;     // 目标方向角度
    private float currentDirection = 0f;    // 当前显示方向角度
    private final Handler smoothHandler = new Handler();
    private static final float SMOOTH_FACTOR = 0.1f;  // 增大平滑系数提高响应速度
    private static final long UPDATE_INTERVAL = 16L;  // 保持60FPS刷新率
    private float lastAppliedDirection = 0f;          // 记录最后实际应用的方向

    private final Runnable smoothUpdater = new Runnable() {
        @Override
        public void run() {
            // 使用角度差最短路径算法
            float delta = targetDirection - currentDirection;
            delta = ((delta % 360) + 540) % 360 - 180; // 规范化到[-180,180]

            // 应用平滑过渡（阻尼插值）
            currentDirection += delta * SMOOTH_FACTOR;
            currentDirection = (currentDirection % 360 + 360) % 360;

            // 只有当变化超过1度时才更新UI
            if (Math.abs(currentDirection - lastAppliedDirection) > 1f) {
                runOnUiThread(() -> {
                    direction = currentDirection;
                    updateMapOrientation(); // 新增方法
                    lastAppliedDirection = currentDirection;
                });
            }

            smoothHandler.postDelayed(this, UPDATE_INTERVAL);
        }
    };

    private void updateMapOrientation() {
        if (mBaiduMap != null && mBaiduMap.getLocationData() != null) {
            MyLocationData locData = new MyLocationData.Builder()
                    .direction(direction)
                    .latitude(mBaiduMap.getLocationData().latitude)
                    .longitude(mBaiduMap.getLocationData().longitude)
                    .accuracy(mBaiduMap.getLocationData().accuracy)
                    .build();
            mBaiduMap.setMyLocationData(locData);
        }
    }


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

    //gpsData[0]-longitude经度;gpsData[1]-gpsData[1]纬度
    private BroadcastReceiver dataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
                double longitude = intent.getDoubleExtra("longitude", 0);
                double latitude = intent.getDoubleExtra("latitude", 0);

                Log.d("MainActivity", "Received longitude: " + longitude);
                Log.d("MainActivity", "Received latitude: " + latitude);

                // 坐标转换
                double[] bd09 = WGS84ToBD09.wgs84ToBd09(longitude, latitude);
                    gpsData[0] = bd09[0]; // 经度
                    gpsData[1] = bd09[1]; // 纬度

                // 打印转换结果到终端
                Log.d("MainActivity", "BD-09 longitude: " + bd09[0]);
                Log.d("MainActivity", "BD-09 latitude: " + bd09[1]);

            // 更新定位器位置
            // 延迟初始化 Locator（确保首次数据到达后创建）
            if (locator == null) {
                locator = new Locator(gpsData[1], gpsData[0], mBaiduMap); // 注意经纬度顺序
            } else {
                locator.updatePosition(gpsData[1], gpsData[0], mBaiduMap);
            }
            if (isLineVisible) {
                updateConnectionLine();
            }
        }
    };

    @SuppressLint({"MissingInflatedId", "UnspecifiedRegisterReceiverFlag"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SDKInitializer.initialize(getApplicationContext());
        setContentView(R.layout.activity_main);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(16, 0, 16, 0); // 左右间距16dp

        toggleLine = findViewById(R.id.btn_toggle_line);
        traceOptionsContainer = findViewById(R.id.traceOptionsContainer);

        cbDeviceTrace = new CheckBox(this);
        cbDeviceTrace.setText("设备循迹");
        cbDeviceTrace.setLayoutParams(params);

        cbPollutionSource = new CheckBox(this);
        cbPollutionSource.setText("污染源");
        cbPollutionSource.setLayoutParams(params);

        traceOptionsContainer.addView(cbDeviceTrace);
        traceOptionsContainer.addView(cbPollutionSource);

        cbDeviceTrace.setOnCheckedChangeListener((b, checked) -> {
            isLineVisible = checked;
            updateConnectionLine();
        });
        cbPollutionSource.setOnCheckedChangeListener((b, checked) -> {
            updatePollutionMarkers();
        });

        // 注册广播接收器
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                SecondMapActivity.LatLngListWrapper wrapper = (SecondMapActivity.LatLngListWrapper) intent.getSerializableExtra("points");
                pollutionSources = wrapper.getPoints();
                if (cbPollutionSource.isChecked()) updatePollutionMarkers();
            }
        }, new IntentFilter("com.example.baidu_map.SYNC_POINTS"));
        
        distanceTextView = findViewById(R.id.distance_text_view);

        // 隐藏状态栏和导航栏，并允许内容延伸
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );
        // 适配刘海屏（API 28+）
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        getWindow().setAttributes(lp);

        Button switchButton = findViewById(R.id.btn_switch);
        switchButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, DataActivity.class);
            startActivity(intent);
        });

        mMapView = findViewById(R.id.bmapView);
        //获取地图控件引用
        mapType = findViewById(R.id.id_rp_maptype);
        //获取地图类型RadioGroup实例，用于切换地图类型
        normalBtn = findViewById(R.id.id_btn_normal);
        //获取“普通地图”类型的RadioButton实例
        satelliteBtn = findViewById(R.id.id_btn_satellite);
        //获取“卫星地图”类型的RadioButton实例
        trafficEnabled = findViewById(R.id.id_cb_trafficEnabled);
        //获取路况图启用状态的CheckBox实例
        heatMapEnabled = findViewById(R.id.id_cb_heatMapEnabled);
        //获取热力图启用状态的CheckBox实例

        mBaiduMap = mMapView.getMap();

        UiSettings mUiSettings = mBaiduMap.getUiSettings();
        boolean enable = true;
        mUiSettings.setCompassEnabled(enable);

        //获取百度地图对象
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        //获取手机传感器服务

        setSelfCenter = findViewById(R.id.btn_locate);
        // 获取btn_locate实例
        setLocatorCenter = findViewById(R.id.btn_locator);
        // 获取btn_locator实例

        locator = new Locator(gpsData[0], gpsData[1], mBaiduMap);
        // 创建定位器对象

        IntentFilter filter = new IntentFilter(DataActivity.DATA_UPDATE_ACTION);
        registerReceiver(dataReceiver, filter);

        mBaiduMap.setPadding(0, 10, 10, 270);
        mMapView.setLogoPosition(LogoPosition.logoPostionRightTop);

        Button btnMap = findViewById(R.id.btn_map);
        btnMap.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SecondMapActivity.class);
            startActivity(intent);
        });

        initLocation();//初始化
        initEvent();
        //调用点击示例
        startDataRefreshInMain();
    }

    private void updatePollutionMarkers() {
        mBaiduMap.clear(); // 清除旧标记
        if (cbPollutionSource.isChecked()) {
            for (LatLng point : pollutionSources) {
                BitmapDescriptor icon = BitmapDescriptorFactory.fromResource(R.drawable.icon_pollution);
                mBaiduMap.addOverlay(new MarkerOptions().position(point).icon(icon));

                if (mBaiduMap.getLocationData() != null) {
                    LatLng myLoc = new LatLng(
                            mBaiduMap.getLocationData().latitude,
                            mBaiduMap.getLocationData().longitude
                    );
                    double distance = DistanceUtil.getDistance(myLoc, point);
                    mBaiduMap.addOverlay(new TextOptions()
                            .text(String.format("%.1f米", distance))
                            .position(point)
                            .fontSize(24)
                            .fontColor(0xFFFF0000));
                }
            }
        }
    }

    private void startDataRefreshInMain() {
        new Thread(() -> {
            while (isDataRefreshRunning) {
                try {
                    // 从华为IoT设备获取GPS数据（后台线程执行）
                    HuaweiIOT gpsDevice = new HuaweiIOT(
                            "l17738422005", "test", "Lww827717.",
                            "67fe0d105367f573f782f7e3",
                            "864814071622293", "GPS_DATA",
                            "b8b855d242.st1.iotda-app.cn-north-4.myhuaweicloud.com"
                    );

                    String latitudeStr = gpsDevice.getAtt("Lat", "shadow");
                    String longitudeStr = gpsDevice.getAtt("Lon", "shadow");
                    double latitude = Double.parseDouble(latitudeStr);
                    double longitude = Double.parseDouble(longitudeStr);

                    // 坐标转换（WGS84转BD09）
                    double[] bd09 = WGS84ToBD09.wgs84ToBd09(longitude, latitude);
                    final double finalLongitude = bd09[0];
                    final double finalLatitude = bd09[1];

                    // 在主线程更新定位器和传递数据
                    runOnUiThread(() -> {
                        // 更新MainActivity中的定位器
                        if (locator == null) {
                            locator = new Locator(finalLatitude, finalLongitude, mBaiduMap);
                        } else {
                            locator.updatePosition(finalLatitude, finalLongitude, mBaiduMap);
                        }
                        if (isLineVisible) {
                            updateConnectionLine();
                        }

                        // 传递数据到TabNewsFragment
                        if (tabNewsFragment != null) {
                            tabNewsFragment.onLocationDataReceived(finalLatitude, finalLongitude);
                        }
                    });

                    Thread.sleep(3000); // 3秒刷新一次
                } catch (Exception e) {
                    Log.e("MainActivity", "数据获取失败", e);
                }
            }
        }).start();
    }

    public void setTabNewsFragment(TabNewsFragment fragment) {
        this.tabNewsFragment = fragment;
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            // 使用低通滤波器处理原始数据
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientationAngles);

            // 原始方向计算
            float newDirection = (float) Math.toDegrees(orientationAngles[0]);
            newDirection = (newDirection + 360) % 360;

            // 使用滑动平均滤波（窗口大小为3）
            targetDirection = (targetDirection * 2 + newDirection) / 3;
        }
    }

    private void initLocation() {  //该方法实现初始化定位 要在onCreate中调用
        /* 1.获取定位权限 创建定位管理对象 */
        LocationClient.setAgreePrivacy(true);
        // 同意使用定位权限，确保应用可以访问定位功能
        try {
            mLocationClient = new LocationClient(getApplicationContext());
            // 创建LocationClient实例，用于管理定位操作
        } catch (Exception e) {
            throw new RuntimeException(e);
            // 捕获并抛出创建LocationClient时的异常
        }
        // 定义定位监听器，接收定位数据并处理
        BDLocationListener myListener = new MyLocationListener();
        // 获取监听器实例
        mLocationClient.registerLocationListener(myListener);
        // 注册位置监听器，用于接收并处理定位信息

        /* 2.用户自定义定位模式 */
        MyLocationConfiguration.LocationMode mCurrentMode = MyLocationConfiguration.LocationMode.NORMAL;
        //定义定位模式为普通模式
        // 自定义定位图标，设置为空表示使用默认图标
        int accuracyCircleFillColor = 0xAAFFFF88;
        // 自定义精度圈填充颜色，设置为淡黄色
        int accuracyCircleStrokeColor = 0xAA00FF00;
        // 自定义精度圈边框颜色，设置为绿色
        MyLocationConfiguration mLocationConfiguration = new MyLocationConfiguration(
        // 创建定位配置对象并设置相应参数
                mCurrentMode,
                // 定位模式
                true,
                // 是否显示方向信息
                null,
                // 自定义定位图标
                accuracyCircleFillColor,
                // 精度圈填充颜色
                accuracyCircleStrokeColor
                // 精度圈边框颜色
        );
        mBaiduMap.setMyLocationConfiguration(mLocationConfiguration);
        // 设置百度地图的定位配置

        /* 3.初始化定位参数 */
        LocationClientOption option = new LocationClientOption();
        // 创建LocationClientOption对象，用于配置定位参数定义定位器监听事件
        option.setIsNeedAddress(true);
        // 设置是否需要地址信息
        option.setCoorType("bd09ll");
        // 设置坐标类型为bd09ll（百度坐标系）
        option.setScanSpan(1000);
        // 设置定位扫描间隔为1秒
        option.setOpenGps(true);
        // 启用GPS定位
        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
        // 高精度模式
        mLocationClient.setLocOption(option);
        // 设置定位参数到LocationClient
        mLocationClient.start();
        // 启动定位
    }

    public class MyLocationListener implements BDLocationListener {
    // 定义自定义的定位监听器，用于接收并处理定位信息
        @Override
        public void onReceiveLocation(BDLocation location) {
            if (location == null || mMapView == null)
            // 如果获取到的定位信息为空，或地图视图为 null，则不处理
                return;
            MyLocationData locData = new
                    MyLocationData.Builder()
                    // 构建定位数据对象，包含 定位精度、方向、纬度、经度等信息
                    .accuracy(location.getRadius())
                    // 设置定位精度
                    .direction(direction)
                    // 设置当前方向信息（基于设备的方向）生命周期方法 Activity 的生命周期方法用于管理 Activity 的创建、暂停、恢复、停止和销毁等状态，这些方法在特定的生命周期阶段被调用。
                    .latitude(location.getLatitude())
                    // 设置纬度
                    .longitude(location.getLongitude())
                    // 设置经度
                    .build();
            Log.d("MainActivity", "Longitude:" + location.getLongitude() + " Latitude:" + location.getLatitude()); //打印经纬度日志
            mBaiduMap.setMyLocationData(locData);
            // 更新百度地图的定位数据
            if (isFirstLoc) {// 定位到以自己为中心
                LatLng ll = new LatLng(location.getLatitude(), location.getLongitude());// 设置当前位置为地图中心点
                float zoomLevel = 18.0f;// 设置地图缩放级别，这里设为18.0f表示~21
                MapStatusUpdate u = MapStatusUpdateFactory.newLatLngZoom(ll, zoomLevel);// 更新地图状态，设置新的地图中心点和缩放级别
                mBaiduMap.animateMapStatus(u);// 动画效果更新地图状态
                isFirstLoc = false;  // 标记为非第一次定位，避免重复操作
            }
            if (isLineVisible) {
                updateConnectionLine();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    //SensorEventListener实现方法
    }
    private void initEvent() {
        // 设置地图类型选择的监听事件
        mapType.setOnCheckedChangeListener((radioGroup, i) -> {
            // 如果选择了普通地图类型
            if (i == normalBtn.getId()) {
                mMapView.getMap().setMapType(BaiduMap.MAP_TYPE_NORMAL);// 设置为普通地图
            }
            // 如果选择了卫星地图类型
            else if (i == satelliteBtn.getId()) {
                mMapView.getMap().setMapType(BaiduMap.MAP_TYPE_SATELLITE); // 设置为卫星地图
            }
        });
        // 设置路况图复选框的监听事件
        trafficEnabled.setOnCheckedChangeListener((compoundButton, b) -> {
            mMapView.getMap().setTrafficEnabled(b); // 根据复选框状态开启或关闭路况图
        });
        // 设置热力图复选框的监听事件
        heatMapEnabled.setOnCheckedChangeListener((compoundButton, b) -> {
            //添加点击监听事件使用USB调试安装APP进行测试。
            mMapView.getMap().setBaiduHeatMapEnabled(b);
            //根据复选框状态开启或关闭热力图
        });

        setSelfCenter.setOnClickListener(v -> {mLocationClient.requestLocation();
            isFirstLoc = true;
            // 直接触发一次地图更新
            if (mLocationClient != null && mLocationClient.isStarted()) {
                mLocationClient.requestLocation();
            }
        });

        setLocatorCenter.setOnClickListener(view -> {
            if (locator != null) {
                LatLng ll = locator.getPosition();
                float zoomLevel = 18.0f;
                MapStatusUpdate u = MapStatusUpdateFactory.newLatLngZoom(ll, zoomLevel);
                mBaiduMap.animateMapStatus(u);
            }
        });

        toggleLine.setOnCheckedChangeListener((buttonView, isChecked) -> {
            traceOptionsContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE); // 直接控制可见性
            if (!isChecked) {
                cbDeviceTrace.setChecked(false);
                cbPollutionSource.setChecked(false);
                updateConnectionLine();
                updatePollutionMarkers();
            }
        });

        // 设备循迹的监听器：控制连线
        cbDeviceTrace.setOnCheckedChangeListener((b, checked) -> {
            updateConnectionLine();
        });

        // 污染源的监听器：控制标记
        cbPollutionSource.setOnCheckedChangeListener((b, checked) -> {
            updatePollutionMarkers();
        });
    }

    private void updateConnectionLine() {
        if (mBaiduMap.getLocationData() == null) {
            distanceTextView.setText("定位未就绪");
            distanceTextView.setVisibility(View.VISIBLE);
            return;
        }
        if (connectionLine != null) {
            connectionLine.remove();
            connectionLine = null;
        }

        distanceTextView.setVisibility(View.GONE); // 默认隐藏



        if (cbDeviceTrace.isChecked()) {
            LatLng myLocation = new LatLng(
                    mBaiduMap.getLocationData().latitude,
                    mBaiduMap.getLocationData().longitude
            );
            LatLng deviceLocation = locator != null ? locator.getPosition() : null;

            if (myLocation != null && deviceLocation != null) {
                // 绘制连线
                List<LatLng> points = new ArrayList<>();
                points.add(myLocation);
                points.add(deviceLocation);
                connectionLine = (Polyline) mBaiduMap.addOverlay(new PolylineOptions()
                        .width(10)
                        .color(0xAAFF0000)
                        .points(points));

                // 计算并显示距离
                double distance = DistanceUtil.getDistance(myLocation, deviceLocation);
                String text = String.format("距离：%.1f米", distance);
                distanceTextView.setText(text);
                distanceTextView.setVisibility(View.VISIBLE);
            }
        }
                if (mBaiduMap.getLocationData() == null || locator == null) {
                    distanceTextView.setText("位置未就绪");
                    distanceTextView.setVisibility(View.VISIBLE);
                }

    }

    @Override
    protected void onStart() {
        //在Activity 被显示时调用，通常用来开始地图定位。
        super.onStart();
        mBaiduMap.setMyLocationEnabled(true);
        // 启用地图定位
        mLocationClient.start();
        //开始定位服务
    }
    @Override
    protected void onStop() {
    //在Activity 被停止时调用，通常用来停止地图定位，避免不必要的资源消耗。
        super.onStop();
        mBaiduMap.setMyLocationEnabled(false);
        // 禁用地图定位
        mLocationClient.stop();
        // 停止定位服务
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 适配刘海屏
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        getWindow().setAttributes(lp);
    }

    @Override
    protected void onResume() {
        // 活动恢复时执行（从暂停中恢复）
        super.onResume();
        mMapView.onResume();
        // 恢复地图视图的状态
        sensorManager.registerListener(
                this,
                sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR),
                SensorManager.SENSOR_DELAY_GAME // 改为游戏级延迟（~20ms）
        );
        // 注册方向传感器监听器，设置传感器更新的延迟为游戏模式

        rotationObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                setScreenOrientationBasedOnSystem();
            }
        };
        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
                false,
                rotationObserver
        );
        setScreenOrientationBasedOnSystem();
        smoothHandler.post(smoothUpdater);
        targetDirection = currentDirection = lastAppliedDirection = direction;
    }

    @Override
    protected void onPause() {
        //活动暂停时执行
        super.onPause();
        mMapView.onPause();
        // 暂停地图视图的状态
        sensorManager.unregisterListener(this);
        // 注销方向传感器监听器
        if (rotationObserver != null) {
            getContentResolver().unregisterContentObserver(rotationObserver);
            rotationObserver = null;
        }
        smoothHandler.removeCallbacks(smoothUpdater);
    }

    @Override
    protected void onDestroy() {
    // 活动销毁时执行
        super.onDestroy();
        // 调用父类的销毁方法
        mMapView.onDestroy();
        // 销毁地图视图
        mMapView = null;
        // 释放地图视图资源
        isDataRefreshRunning = false;

    }

}