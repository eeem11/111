package com.example.baidu_map;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.Gradient;
import com.baidu.mapapi.map.HeatMap;
import com.baidu.mapapi.map.LogoPosition;
import com.baidu.mapapi.map.MapPoi;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.Overlay;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.Polygon;
import com.baidu.mapapi.map.PolygonOptions;
import com.baidu.mapapi.map.Stroke;
import com.baidu.mapapi.map.WeightedLatLng;
import com.baidu.mapapi.map.track.TraceAnimationListener;
import com.baidu.mapapi.map.track.TraceOptions;
import com.baidu.mapapi.map.track.TraceOverlay;
import com.baidu.mapapi.model.LatLng;

import com.example.baidu_map.clusterutil.clustering.Cluster;
import com.example.baidu_map.clusterutil.clustering.ClusterItem;
import com.example.baidu_map.clusterutil.clustering.ClusterManager;
import com.example.baidu_map.clusterutil.clustering.view.DefaultClusterRenderer;
import com.example.baidu_map.clusterutil.ui.IconGenerator;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SecondMapActivity extends Activity {
    private List<Overlay> drawingOverlays = new ArrayList<>();
    private Button btnAddNumberedPoints, btnClearDraw;
    private boolean isDrawing = false;

    private int numberedPointCount = 0;
    private List<LatLng> numberedPoints = new ArrayList<>();
    private Polygon currentPolygon;

    // 圈选相关变量
    private int areaSuspectedCount = 0;
    private int areaAqiPointCount = 0;
    private double areaAvgAQI = 0;

    private List<MyItem> clusterItems = new ArrayList<>();
    private MapView mMapView;
    private BaiduMap mBaiduMap;
    private LinearLayout traceControls;
    private TraceOverlay mTraceOverlay; // 用于管理轨迹覆盖层

    private ClusterManager<MyItem> mClusterManager;
    private LinearLayout clusterControls;
    private HeatMap mHeatMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second_map);

        // 设置状态栏为透明
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);


        // 获取地图控件引用
        mMapView = findViewById(R.id.bmapView2);
        mBaiduMap = mMapView.getMap();

        // 动态获取状态栏高度并设置内边距
        int statusBarHeight = getStatusBarHeight();
        mMapView.setPadding(0, statusBarHeight, 0, 0);

        // 初始化轨迹相关控件
        CheckBox cbTrace = findViewById(R.id.cb_trace);
        traceControls = findViewById(R.id.trace_controls);
        Button btnShowTrace = findViewById(R.id.btn_show_trace);
        Button btnListenTrace = findViewById(R.id.btn_listen_trace);
        Button btnClearTrace = findViewById(R.id.btn_clear_trace);

        // 一级复选框事件：控制二级布局的显示/隐藏
        cbTrace.setOnCheckedChangeListener((buttonView, isChecked) -> {
            traceControls.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (!isChecked) {
                clearTrace(); // 取消选中时清除轨迹
            }
        });

        // 二级按钮点击事件
        btnShowTrace.setOnClickListener(v -> showTrace(false)); // 显示轨迹但不监听
        btnListenTrace.setOnClickListener(v -> showTrace(true)); // 显示轨迹并监听
        btnClearTrace.setOnClickListener(v -> clearTrace()); // 清除轨迹

        // 初始化点聚合相关控件
        CheckBox cbCluster = findViewById(R.id.cb_cluster);
        cbCluster.setChecked(true);
        cbCluster.setOnCheckedChangeListener((buttonView, isChecked) -> {
            clusterControls.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (isChecked) {
                initCluster(); // 初始化或刷新聚合
            } else {
                clearClusterPoints(); // 清除聚合点
            }
        });

        clusterControls = findViewById(R.id.cluster_controls);

        Button btnAddPoints = findViewById(R.id.btn_add_points);
        Button btnClearPoints = findViewById(R.id.btn_clear_points);

        // 一级复选框事件：控制二级布局的显示/隐藏
        cbCluster.setOnCheckedChangeListener((buttonView, isChecked) -> {
            clusterControls.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (isChecked) {
                initCluster(); // 初始化点聚合
            } else {
                clearClusterPoints(); // 取消选中时清除点
            }
        });

        // 二级按钮点击事件
        btnAddPoints.setOnClickListener(v -> enableAddPointMode());
        btnClearPoints.setOnClickListener(v -> clearClusterPoints());

        // 添加热力图
        CheckBox cbHeatMap = findViewById(R.id.cb_heatmap);
        cbHeatMap.setChecked(true); // 默认勾选
        setupHeatMap(); // 手动初始化热力图

        cbHeatMap.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                setupHeatMap();
            } else {
                if (mHeatMap != null) {
                    // 尝试通过移除热力图的数据来达到类似移除热力图的效果
                    mHeatMap.removeHeatMap();
                    mHeatMap = null;
                }
            }
        });

        mBaiduMap.setPadding(0, 0, 0, 270);
        mMapView.setLogoPosition(LogoPosition.logoPostionRightTop);


        // 在onCreate方法中找到地图初始化部分，添加：
        mBaiduMap.setOnMapLoadedCallback(() -> {
            // 地图加载完成后初始化聚合
            if (mClusterManager == null) {
                initCluster();
            }
            // 设置初始地图中心点（示例）
            LatLng center = new LatLng(39.963175, 116.400244);
            mBaiduMap.setMapStatus(MapStatusUpdateFactory.newLatLngZoom(center, 10));
        });

        Button btnTraceSource = findViewById(R.id.btn_trace_source);
        btnTraceSource.setOnClickListener(v -> {
            // 获取所有点并传递给MainActivity
            List<LatLng> points = getAllClusterPoints();
            Intent intent = new Intent();
            intent.setAction("com.example.baidu_map.SYNC_POINTS");
            intent.putExtra("points", new LatLngListWrapper(points));
            sendBroadcast(intent);
            Toast.makeText(this, "已同步" + points.size() + "个点到主地图", Toast.LENGTH_SHORT).show();
        });

        Button btnInfo = findViewById(R.id.btn_info);
        LinearLayout infoPanel = findViewById(R.id.info_panel); // 需要先在XML中添加这个布局

        btnInfo.setOnClickListener(v -> {
            infoPanel.setVisibility(infoPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            updateSummaryInfo(); // 更新信息
        });
        
        // 新增按钮点击事件处理1
        btnAddNumberedPoints = findViewById(R.id.btn_add_numbered_points);
        btnClearDraw = findViewById(R.id.btn_clear_draw);

        btnAddNumberedPoints.setOnClickListener(v -> {
            String buttonText = btnAddNumberedPoints.getText().toString();
            if (buttonText.equals("绘制区域")) {
                startDrawingMode();
            } else if (buttonText.equals("完成绘制")) {
                finishDrawing();
            }
        });

        btnClearDraw.setOnClickListener(v -> clearDrawing());
        btnAddNumberedPoints.setText("绘制区域");
        btnClearDraw.setVisibility(View.GONE);
    }

    private boolean isPointInPolygon(LatLng testPoint, List<LatLng> polygon) {
        boolean result = false;
        int j = polygon.size() - 1;

        for (int i = 0; i < polygon.size(); j = i++) {
            LatLng p1 = polygon.get(i);
            LatLng p2 = polygon.get(j);

            if ((p1.latitude > testPoint.latitude) != (p2.latitude > testPoint.latitude) &&
                    (testPoint.longitude < (p2.longitude - p1.longitude) *
                            (testPoint.latitude - p1.latitude) / (p2.latitude - p1.latitude) + p1.longitude)) {
                result = !result;
            }
        }
        return result;
    }

    private List<Object[]> getPointsInPolygon() {
        List<Object[]> filteredData = new ArrayList<>();
        Log.d("PolygonDebug", "Polygon points: " + numberedPoints);

        for (Object[] data : DataContainer.sampleData) {
            double lng = (double) data[7];
            double lat = (double) data[8];
            LatLng point = new LatLng(lat, lng);
            boolean isIncluded = isPointInPolygon(point, numberedPoints);
            Log.d("PolygonDebug", "检查点坐标: " + point + " 是否在区域: " + isIncluded);
            Log.d("FilterDebug", "Data point at " + point +
                    " included: " + isIncluded +
                    " AQI: " + AQIProcessor.calculateAQI(
                    (double) data[1],  // PM2.5
                    (double) data[2],  // PM10
                    (double) data[3],  // SO2
                    (double) data[4],  // NO2
                    (double) data[5],  // O3
                    (double) data[6]   // CO
            ));

            if (isIncluded) {
                filteredData.add(data);
            }
        }
        Log.d("FilterDebug", "Total points in polygon: " + filteredData.size());
        return filteredData;
    }

    private void finishDrawing() {
        if (numberedPoints.size() >= 3) {
            // 清除旧多边形
            if (currentPolygon != null) {
                currentPolygon.remove();
                drawingOverlays.remove(currentPolygon);
                currentPolygon = null;
            }

            // 生成新多边形
            currentPolygon = (Polygon) mBaiduMap.addOverlay(new PolygonOptions()
                    .points(numberedPoints)
                    .stroke(new Stroke(5, 0xAA00FF00))
                    .fillColor(0x2200FF00));
            drawingOverlays.add(currentPolygon);

            // 重置按钮状态
            btnAddNumberedPoints.setText("绘制区域");
            btnClearDraw.setVisibility(View.VISIBLE);
            isDrawing = false;
        } else {
            Toast.makeText(this, "至少需要3个点才能绘制区域", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearDrawing() {
        isDrawing = false;
        numberedPointCount = 0;
        numberedPoints.clear();

        // 只清除绘制相关覆盖物
        for (Overlay overlay : drawingOverlays) {
            overlay.remove();
        }
        drawingOverlays.clear();
        currentPolygon = null;

        btnAddNumberedPoints.setText("绘制区域");
        btnClearDraw.setVisibility(View.GONE);
    }

    private void startDrawingMode() {
        isDrawing = true;
        numberedPointCount = 0;
        numberedPoints.clear();

        // 显示清除按钮
        btnClearDraw.setVisibility(View.VISIBLE); // 新增代码

        // 只清除绘制相关的覆盖物
        for (Overlay overlay : drawingOverlays) {
            overlay.remove();
        }
        drawingOverlays.clear();

        btnAddNumberedPoints.setText("绘制至少三个点");
        enableAddNumberedPointMode();
    }

    private void enableAddNumberedPointMode() {
        mBaiduMap.setOnMapClickListener(new BaiduMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                numberedPointCount++;
                numberedPoints.add(latLng);

                // 添加标记并记录
                BitmapDescriptor bitmap = createNumberedIcon(numberedPointCount);
                Overlay marker = mBaiduMap.addOverlay(new MarkerOptions().position(latLng).icon(bitmap));
                drawingOverlays.add(marker);

                // 更新按钮状态
                if (numberedPoints.size() >= 3) {
                    btnAddNumberedPoints.setText("完成绘制");
                } else {
                    btnAddNumberedPoints.setText("已添加 " + numberedPoints.size() + "/3 个点");
                }
            }

            @Override
            public void onMapPoiClick(MapPoi mapPoi) {}
        });
    }

    private BitmapDescriptor createNumberedIcon(int number) {
        // 获取Drawable对象
        Drawable drawable = ContextCompat.getDrawable(this, R.drawable.icon_marka1);
        Bitmap originalBitmap;

        if (drawable instanceof BitmapDrawable) {
            // 处理普通位图
            originalBitmap = ((BitmapDrawable) drawable).getBitmap();
        } else if (drawable instanceof VectorDrawable) {
            // 处理矢量图：创建Bitmap并绘制VectorDrawable
            int width = drawable.getIntrinsicWidth();
            int height = drawable.getIntrinsicHeight();
            originalBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(originalBitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
        } else {
            // 其他类型处理或抛出异常
            throw new IllegalArgumentException("Unsupported drawable type");
        }

        Bitmap bitmapWithNumber = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(bitmapWithNumber);

        // 设置画笔和绘制数字（原有代码）
        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setTextSize(30);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setAntiAlias(true);

        float textWidth = paint.measureText(String.valueOf(number));
        float x = (bitmapWithNumber.getWidth() - textWidth) / 2;
        float y = bitmapWithNumber.getHeight() / 2 + paint.getTextSize() / 2;

        canvas.drawText(String.valueOf(number), x, y, paint);
        return BitmapDescriptorFactory.fromBitmap(bitmapWithNumber);
    }

    private void updateSummaryInfo() {
        TextView tvSummary = findViewById(R.id.tv_summary);
        CheckBox cbHeatMap = findViewById(R.id.cb_heatmap);
        CheckBox cbCluster = findViewById(R.id.cb_cluster);
        CheckBox cbTrace = findViewById(R.id.cb_trace);
        TextView tvPrimaryPollutant = findViewById(R.id.tv_primary_pollutant);
        String primaryPollutant = "无";

        SpannableStringBuilder builder = new SpannableStringBuilder();
        String bullet = "•  ";



        // 热力图状态
        appendStatusLine(builder, bullet + "热力图状态：", mHeatMap != null);
        // 点集合状态
        appendStatusLine(builder, bullet + "点集合状态：", cbCluster.isChecked());
        // 轨迹显示状态
        appendStatusLine(builder, bullet + "轨迹显示状态：", cbTrace.isChecked());

        Log.d("Debug", "updateSummaryInfo: currentPolygon is " + (currentPolygon != null ? "not null" : "null"));

        double aqiValue = 0;
        Map<String, Double> pollutants = new HashMap<>();

        if (currentPolygon != null) {
            // 圈选区域统计
            List<Object[]> areaData = getPointsInPolygon();
            Log.d("Debug", "圈选区域数据量: " + areaData.size());
            areaSuspectedCount = areaData.size();
            areaAqiPointCount = areaData.size();

            double totalAQI = 0;
            for (Object[] data : areaData) {
                double pm25 = (double) data[1];
                double pm10 = (double) data[2];
                double so2 = (double) data[3];
                double no2 = (double) data[4];
                double o3 = (double) data[5];
                double co = (double) data[6];
                double aqi = AQIProcessor.calculateAQI(pm25, pm10, so2, no2, o3, co);
                totalAQI += aqi;

                pollutants.put("PM2.5", pollutants.getOrDefault("PM2.5", 0.0) + pm25);
                pollutants.put("PM10", pollutants.getOrDefault("PM10", 0.0) + pm10);
                pollutants.put("SO2", pollutants.getOrDefault("SO2", 0.0) + so2);
                pollutants.put("NO2", pollutants.getOrDefault("NO2", 0.0) + no2);
                pollutants.put("O3", pollutants.getOrDefault("O3", 0.0) + o3);
                pollutants.put("CO", pollutants.getOrDefault("CO", 0.0) + co);
            }

            if (!areaData.isEmpty()) {
                areaAvgAQI = totalAQI / areaData.size();
                for (Map.Entry<String, Double> entry : pollutants.entrySet()) {
                    pollutants.put(entry.getKey(), entry.getValue() / areaData.size());
                }
                // 获取首要污染物
                double maxValue = 0;
                for (Map.Entry<String, Double> entry : pollutants.entrySet()) {
                    if (entry.getValue() > maxValue) {
                        maxValue = entry.getValue();
                        primaryPollutant = entry.getKey();
                    }
                }
                Log.d("PrimaryDebug", "首要污染物计算: maxValue=" + maxValue + " pollutant=" + primaryPollutant);
                aqiValue = areaAvgAQI;
            }

            builder.append(bullet).append("圈选区域统计：\n")
                    .append("   疑似污染源：").append(String.valueOf(areaSuspectedCount)).append("个\n")
                    .append("   AQI数据点：").append(String.valueOf(areaAqiPointCount)).append("个\n")
                    .append("   平均AQI：").append(String.format(Locale.CHINA, "%.1f", areaAvgAQI)).append("\n");

            Log.d("AQIDebug", "圈选区域AQI: " + areaAvgAQI);
            Log.d("PollutantDebug", "圈选污染物: " + pollutants.toString());
        } else {
            // 全局统计
            Log.d("Debug", "全局数据量: " + DataContainer.sampleData.size());
            double totalAQI = 0;
            for (Object[] data : DataContainer.sampleData) {
                double pm25 = (double) data[1];
                double pm10 = (double) data[2];
                double so2 = (double) data[3];
                double no2 = (double) data[4];
                double o3 = (double) data[5];
                double co = (double) data[6];
                double aqi = AQIProcessor.calculateAQI(pm25, pm10, so2, no2, o3, co);
                totalAQI += aqi;

                pollutants.put("PM2.5", pollutants.getOrDefault("PM2.5", 0.0) + pm25);
                pollutants.put("PM10", pollutants.getOrDefault("PM10", 0.0) + pm10);
                pollutants.put("SO2", pollutants.getOrDefault("SO2", 0.0) + so2);
                pollutants.put("NO2", pollutants.getOrDefault("NO2", 0.0) + no2);
                pollutants.put("O3", pollutants.getOrDefault("O3", 0.0) + o3);
                pollutants.put("CO", pollutants.getOrDefault("CO", 0.0) + co);
            }

            if (!DataContainer.sampleData.isEmpty()) {
                double avgAQI = totalAQI / DataContainer.sampleData.size();
                Log.d("AQIDebug", "全局平均AQI: " + avgAQI);
                Log.d("PollutantDebug", "全局污染物: " + pollutants.toString());
                for (Map.Entry<String, Double> entry : pollutants.entrySet()) {
                    double avg = entry.getValue() / DataContainer.sampleData.size();
                    pollutants.put(entry.getKey(), avg); // 更新为平均值
                }
                // 获取首要污染物
                double maxValue = 0;
                for (Map.Entry<String, Double> entry : pollutants.entrySet()) {
                    if (entry.getValue() > maxValue) {
                        maxValue = entry.getValue();
                        primaryPollutant = entry.getKey();
                    }
                }
                Log.d("PrimaryDebug", "首要污染物计算: maxValue=" + maxValue + " pollutant=" + primaryPollutant);
                aqiValue = avgAQI;
                for (Map.Entry<String, Double> entry : pollutants.entrySet()) {
                    double avg = entry.getValue() / DataContainer.sampleData.size();
                    pollutants.put(entry.getKey(), avg);
                    if (avg > maxValue) {
                        maxValue = avg;
                        primaryPollutant = entry.getKey();
                    }
                }
            }else {
                Log.d("Debug", "无全局数据可用");
            }

            builder.append(bullet).append("全局统计：\n")
                    .append("   疑似污染源：").append(String.valueOf(clusterItems.size())).append("个\n")
                    .append("   AQI数据点：").append(String.valueOf(DataContainer.sampleData.size())).append("个\n")
                    .append("   平均AQI：").append(String.format(Locale.CHINA, "%.1f", aqiValue)).append("\n");
        }

        tvPrimaryPollutant.setText(primaryPollutant);

        // 设置AQI显示
        TextView tvAqiValue = findViewById(R.id.tv_aqi_value);
        TextView tvAirQuality = findViewById(R.id.tv_air_quality);

        LinearLayout aqiLayout = findViewById(R.id.aqi_layout);
        LinearLayout airQualityLayout = findViewById(R.id.air_quality_layout);
        //建议
        LinearLayout pollutantLayout = findViewById(R.id.pollutant_layout);
        TextView tvSuggestion = findViewById(R.id.tv_suggestion);


        int aqiInt = (int) aqiValue;
        tvAqiValue.setText(String.valueOf(aqiInt));
        String qualityLevel = getAirQualityLevel(aqiValue);
        tvAirQuality.setText(qualityLevel);

        int colorRes = getAqiColorResource(aqiValue);
        int bgColor = ContextCompat.getColor(this, colorRes);
        aqiLayout.setBackgroundColor(bgColor);
        airQualityLayout.setBackgroundColor(bgColor);
        pollutantLayout.setBackgroundColor(bgColor);

        tvSuggestion.setText(getSuggestion(aqiValue));
        tvSummary.setText(builder);

        // 更新时间
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINA);
        TextView tvUpdateTime = findViewById(R.id.tv_update_time);
        tvUpdateTime.setText("更新时间：" + sdf.format(new Date()));

        tvAqiValue.setText(aqiValue > 0 ? String.valueOf((int) aqiValue) : "--");
        tvAirQuality.setText(TextUtils.isEmpty(qualityLevel) ? "无数据" : qualityLevel);
        tvPrimaryPollutant.setText(TextUtils.isEmpty(primaryPollutant) ? "无" : primaryPollutant);

        String locationName = "北京市";
        TextView tvLocation = findViewById(R.id.tv_location);
        tvLocation.setText(locationName);

        // AQI数值
        String aqiText = "实时AQI：\n" + (aqiValue > 0 ? (int) aqiValue : "--");
        tvAqiValue.setText(String.valueOf((int) aqiValue));

        // 空气质量
        String qualityText = "空气质量：\n" + (TextUtils.isEmpty(qualityLevel) ? "无数据" : qualityLevel);
        tvAirQuality.setText(qualityLevel);

        // 污染物
        String pollutantText = "首要污染物：\n" + (TextUtils.isEmpty(primaryPollutant) ? "无" : primaryPollutant);
        tvPrimaryPollutant.setText(primaryPollutant);

        // 建议（保留标题）
        String suggestionText = "出行建议：\n" + getSuggestion(aqiValue);
        tvSuggestion.setText(getSuggestion(aqiValue));
    }

    private int getAqiColorResource(double aqi) {
        if (aqi <= 50) return R.color.aqi_good;        // 优-绿色
        else if (aqi <= 100) return R.color.aqi_moderate;    // 良-黄色
        else if (aqi <= 150) return R.color.aqi_unhealthy_sensitive;  // 轻度-橙色
        else if (aqi <= 200) return R.color.aqi_unhealthy;    // 中度-红色
        else if (aqi <= 300) return R.color.aqi_very_unhealthy; // 重度-紫色
        else return R.color.aqi_hazardous;              // 严重-褐红色
    }

    private String getAirQualityLevel(double aqi) {
        if (aqi <= 50) return "优";
        else if (aqi <= 100) return "良";
        else if (aqi <= 150) return "轻度";
        else if (aqi <= 200) return "中度";
        else if (aqi <= 300) return "重度";
        else return "严重";
    }

    private String getSuggestion(double aqi) {
        if (aqi <= 50) {
            return "各类人群可正常活动";
        } else if (aqi <= 100) {
            return "极少数异常敏感人群应减少户外活动";
        } else if (aqi <= 150) {
            return "儿童、老年人及心脏病、呼吸系统疾病患者应减少长时间、高强度的户外锻炼";
        } else if (aqi <= 200) {
            return "疾病患者避免长时间、高强度的户外锻炼，一般人群适量减少户外运动";
        } else if (aqi <= 300) {
            return "儿童、老年人及心脏病、肺病患者应停留在室内，停止户外运动";
        } else {
            return "儿童、老年人和病人应当留在室内，避免体力消耗";
        }
    }

    private void appendStatusLine(SpannableStringBuilder builder, String prefix, boolean isEnabled) {
        int start = builder.length();
        builder.append(prefix);

        String status = isEnabled ? "已启用\n" : "未启用\n";
        int color = isEnabled ? Color.GREEN : Color.RED;

        int statusStart = builder.length();
        builder.append(status);

        // 设置状态文字颜色
        builder.setSpan(new ForegroundColorSpan(color),
                statusStart, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private List<LatLng> getAllClusterPoints() {
        List<LatLng> points = new ArrayList<>();
        for (MyItem item : clusterItems) {
            points.add(item.getPosition());
        }
        return points;
    }

    // 自定义可序列化的LatLng列表包装类
    public static class LatLngListWrapper implements Serializable {
        private List<LatLng> points;
        public LatLngListWrapper(List<LatLng> points) { this.points = points; }
        public List<LatLng> getPoints() { return points; }
    }

    /**
     * 向地图添加大量Marker点（示例数据）
     */
    private void addMarkers() {
        List<MyItem> items = new ArrayList<>();

        // 示例数据（参考ClusterMarkerActivity的坐标点）
        items.add(new MyItem(new LatLng(40.109965, 116.380244)));
        items.add(new MyItem(new LatLng(40.106965, 116.359199)));
        items.add(new MyItem(new LatLng(40.105965, 116.405541)));
        items.add(new MyItem(new LatLng(40.103175, 116.401394)));
        items.add(new MyItem(new LatLng(40.102821, 116.421394)));
        // 添加更多点（可复制ClusterMarkerActivity中的坐标点）

        clusterItems.addAll(items);
        mClusterManager.addItems(items);
        mClusterManager.cluster(); // 触发聚合计算
    }

    public class CustomClusterRenderer extends DefaultClusterRenderer<MyItem> {
        private final IconGenerator mIconGenerator;

        public CustomClusterRenderer(Context context, BaiduMap map, ClusterManager<MyItem> clusterManager, IconGenerator iconGenerator) {
            super(context, map, clusterManager);
            mIconGenerator = iconGenerator;
        }

        @Override
        protected void onBeforeClusterRendered(Cluster<MyItem> cluster, MarkerOptions markerOptions) {
            // 使用 Activity 的 Context
            mIconGenerator.setBackground(ContextCompat.getDrawable(SecondMapActivity.this, R.drawable.cluster_icon_bg));
            Bitmap icon = mIconGenerator.makeIcon(String.valueOf(cluster.getSize()));
            markerOptions.icon(BitmapDescriptorFactory.fromBitmap(icon));
        }
    }

    /**
     * 初始化点聚合管理类
     */
    private void initCluster() {
        // 仅初始化一次ClusterManager
        if (mClusterManager == null) {
            IconGenerator iconGenerator = new IconGenerator(this);
            mClusterManager = new ClusterManager<>(this, mBaiduMap);
            mBaiduMap.setOnMapStatusChangeListener(mClusterManager);
            mBaiduMap.setOnMarkerClickListener(mClusterManager);

            // 设置自定义渲染器
            mClusterManager.setRenderer(new CustomClusterRenderer(this, mBaiduMap, mClusterManager, iconGenerator));
        }

        // 添加点击监听器
        mClusterManager.setOnClusterClickListener(cluster -> {
            Toast.makeText(this, "该区域疑似污染源 " + cluster.getSize() + " 个", Toast.LENGTH_SHORT).show();
            return false;
        });

        mClusterManager.setOnClusterItemClickListener(item -> {
            Toast.makeText(this, "疑似污染源", Toast.LENGTH_SHORT).show();
            return false;
        });

        // 清空旧数据并添加新数据
        mClusterManager.clearItems();
        addMarkers(); // 调用添加标记点的方法
        mClusterManager.cluster(); // 触发聚合
    }

    /**
     * 启用地图点击添加点模式
     */
    private void enableAddPointMode() {
        mBaiduMap.setOnMapClickListener(new BaiduMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                MyItem item = new MyItem(latLng);
                mClusterManager.addItem(item);
                mClusterManager.cluster();
            }

            @Override
            public void onMapPoiClick(MapPoi mapPoi) {

            }
        });
    }

    /**
     * 清除所有聚合点
     */
    private void clearClusterPoints() {
        if (mClusterManager != null) {
            mClusterManager.clearItems();
            mClusterManager.cluster();
        }
    }

    /**
     * ClusterItem实现类（添加到类内部）
     */
    public static class MyItem implements ClusterItem {
        private final LatLng mPosition;

        public MyItem(LatLng position) {
            mPosition = position;
        }

        @Override
        public LatLng getPosition() {
            return mPosition;
        }

        @Override
        public BitmapDescriptor getBitmapDescriptor() {
            return BitmapDescriptorFactory.fromResource(R.drawable.icon_gcoding);
        }
    }

    /**
     * 显示轨迹动画
     * @param enableListener 是否启用轨迹监听
     */
    private void showTrace(boolean enableListener) {
        // 清除旧轨迹
        if (mTraceOverlay != null) {
            mTraceOverlay.remove();
        }

        // 1. 配置轨迹参数
        TraceOptions traceOptions = new TraceOptions()
                .points(getTraceLocation()) // 获取轨迹点（需自定义）
                .color(0xAAFF0000) // 轨迹颜色
                .width(10) // 轨迹宽度
                .animate(true) // 开启动画
                .animationTime(5000) // 动画时长
                .animationType(TraceOptions.TraceAnimateType.TraceOverlayAnimationEasingCurveLinear);

        // 2. 设置轨迹图标（可选）
        BitmapDescriptor icon = BitmapDescriptorFactory.fromResource(R.drawable.marker_blue);
        traceOptions.icon(icon).setPointMove(true); // 图标平滑移动

        // 3. 添加轨迹覆盖层
        if (enableListener) {
            // 带监听器的轨迹
            mTraceOverlay = mBaiduMap.addTraceOverlay(traceOptions, new TraceAnimationListener() {

                @Override
                public void onTraceAnimationUpdate(float v) {

                }

                @Override
                public void onTraceUpdatePosition(LatLng position) {
                    Log.d("Trace", "位置更新：" + position.latitude + ", " + position.longitude);
                }

                @Override
                public void onTraceAnimationFinish() {
                    Log.d("Trace", "动画结束");
                }
            });
        } else {
            // 不带监听器的轨迹
            mTraceOverlay = mBaiduMap.addTraceOverlay(traceOptions, null);
        }
    }

    /**
     * 清除轨迹
     */
    private void clearTrace() {
        if (mTraceOverlay != null) {
            mTraceOverlay.remove();
            mTraceOverlay = null;
        }
    }

    /**
     * 生成示例轨迹点
     */
    private List<LatLng> getTraceLocation() {
        List<LatLng> points = new ArrayList<>();
        points.add(new LatLng(39.90469, 116.40717));
        points.add(new LatLng(31.23037, 121.47370));
        points.add(new LatLng(23.12911, 113.26436));
        return points;
    }

    /**
     * 生成示例热力图
     */
    private void setupHeatMap() {
        if (mHeatMap != null) {
            mHeatMap.removeHeatMap();
            mHeatMap = null;
        }

        // 生成热力图数据
        List<WeightedLatLng> heatData = AQIProcessor.processAQIData();

        // 创建热力图参数
        HeatMap heatMap = new HeatMap.Builder()
                .weightedData(heatData)
                .gradient(new Gradient(
                        new int[]{Color.BLUE, Color.CYAN, Color.GREEN, Color.YELLOW, Color.RED},
                        new float[]{0.1f, 0.3f, 0.6f, 0.8f, 1.0f}
                ))
                .radius(30) // 每个点的显示半径
                .opacity(0.7f)
                .build();

        // 添加热力图
        mBaiduMap.addHeatMap(heatMap);
        mHeatMap = heatMap;

    }

    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mMapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mTraceOverlay != null) {
            mTraceOverlay.remove();
        }
        if (mClusterManager != null) {
            mClusterManager.clearItems();
        }
        mMapView.onDestroy();
    }
}