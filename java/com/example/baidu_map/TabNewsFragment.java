package com.example.baidu_map;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TabNewsFragment extends Fragment {

    private List<EnvData> currentDataList = new ArrayList<>();
    private Calendar startCal = Calendar.getInstance();
    private Calendar endCal = Calendar.getInstance();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    public static final String DATA_UPDATE_ACTION = "com.example.baidu_map.DATA_UPDATE";
    private static final String ARG_PARAM = "title";
    private String title;
    private WebView webView;
    private TextView defaultText;

    private Runnable refreshRunnable;

    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 1) {
                List<EnvData> dataList = (List<EnvData>) msg.obj;
                updateTable(dataList);
            }
        }
    };

    public TabNewsFragment() {
        // Required empty public constructor
    }

    public void onLocationDataReceived(double latitude, double longitude) {
        // 处理接收到的数据（例如更新UI或存储）
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                try {
                    // 假设需要更新WebView中的经纬度显示
                    JSONObject data = new JSONObject();
                    data.put("latitude", latitude);
                    data.put("longitude", longitude);
                    sendDataToWebView(data.toString());
                } catch (JSONException e) {
                    Log.e("TabNewsFragment", "JSON构建失败", e);
                }
            });
        }
    }

    public class WebAppInterface {
        @JavascriptInterface
        public void updateSensorData(String jsonData) {
            // 处理数据更新
        }
    }

    public static TabNewsFragment newInstance(String param) {
        TabNewsFragment fragment = new TabNewsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM, param);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            title = getArguments().getString(ARG_PARAM);
        }
    }

    private double parseSafe(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            Log.e("DATA_PARSE", "解析数值失败: " + value, e);
            return 0.0;
        }
    }

    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        int layoutResId = getLayoutResId();
        View view = null;
        view = inflater.inflate(layoutResId, container, false);

        // 根据布局初始化控件
        switch (title) {
            case "实时数据图":
                webView = view.findViewById(R.id.webview);
                if (webView != null) {
                    setupDynamicDataWebView();
                }
                break;
            case "实时数据":
                webView = view.findViewById(R.id.webview);
                setupRealtimeDataWebView();
                startDataRefresh();
                break;
            case "数据设置":
                Button btnStartTime = view.findViewById(R.id.btn_start_time);
                @SuppressLint({"MissingInflatedId", "LocalSuppress"}) Button btnEndTime = view.findViewById(R.id.btn_end_time);
                @SuppressLint({"MissingInflatedId", "LocalSuppress"}) Button btnQuery = view.findViewById(R.id.btn_query);
                TextView tvTimeRange = view.findViewById(R.id.tv_time_range);
                TextView tvEmptyTip = view.findViewById(R.id.tv_empty_tip);
                TableLayout dataTableBody = view.findViewById(R.id.data_table_body);

                btnStartTime.setOnClickListener(v -> showDateTimePicker(true));
                btnEndTime.setOnClickListener(v -> showDateTimePicker(false));
                btnQuery.setOnClickListener(v -> queryData());
                endCal = Calendar.getInstance();
                startCal = (Calendar) endCal.clone();
                startCal.add(Calendar.HOUR, -2); // 结束时间设为当前，开始时间减2小时
                updateTimeDisplay(); // 更新显示
                break;
            case "含量分析图":
                webView = view.findViewById(R.id.webview);
                setupContentAnalysisWebView();
                break;
            case "雷达图":
                webView = view.findViewById(R.id.webview);
                setupRadarChartWebView();
                break;
            default:
                defaultText = view.findViewById(R.id.default_text);
                defaultText.setText(title);
                break;
        }

        return view;
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private void setupRealtimeDataWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new WebAppInterface(), "Android");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                try {
                    JSONObject data = new JSONObject();
                    data.put("latitude", "0.0");
                    data.put("longitude", "0.0");
                    data.put("temp", "0");
                    data.put("humi", "0");
                    data.put("co2", "0");
                    data.put("methanal", "0");
                    data.put("pm25", "0");
                    data.put("tvoc", "0");
                    sendDataToWebView(data.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                webView.loadUrl("javascript:window.addEventListener('resize', function() { " +
                        "sensors.forEach(sensor => sensor.chart.resize()); })");
            }
        });
        webView.loadUrl("file:///android_asset/1.html");
    }

    private void sendDataToWebView(String jsonData) {
        if (webView != null && getActivity() != null) {
            webView.evaluateJavascript(
                    "javascript:updateAllData(" + jsonData + ")",
                    value -> Log.d("WEBVIEW", "Data updated"));
        }
    }

    // 根据屏幕方向获取布局资源ID
    private int getLayoutResId() {
        int orientation = getResources().getConfiguration().orientation;
        switch (title) {
            case "含量分析图":
            case "实时数据图":
            case "雷达图":
            case "实时数据":
                return orientation == Configuration.ORIENTATION_PORTRAIT
                        ? R.layout.fragment_comparison
                        : R.layout.fragment_comparison_landscape;
            case "数据设置":
                return R.layout.fragment_data_settings;
            default:
                return R.layout.fragment_default;
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupRadarChartWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                webView.loadUrl("javascript:window.addEventListener('resize', function() { myChart.resize(); })");
            }
        });
        webView.loadUrl("file:///android_asset/radar-custom.html");
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private void setupDynamicDataWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new WebAppInterface(), "Android"); // 添加接口
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // 初始数据发送
                sendDynamicDataToWebView();
                webView.loadUrl("javascript:window.addEventListener('resize', function() { myChart.resize(); })");
            }
        });
        webView.loadUrl("file:///android_asset/dynamic-data2.html");
    }

    // 新增方法：发送数据到 dynamic-data2.html
    private void sendDynamicDataToWebView() {
        if (currentDataList.isEmpty()) return;
        EnvData latestData = currentDataList.get(currentDataList.size() - 1);
        try {
            JSONObject data = new JSONObject();
            data.put("co2", latestData.getCO2());
            data.put("pm25", latestData.getPM25());
            data.put("pm10", latestData.getPM10());
            data.put("so2", latestData.getSO2());
            data.put("no2", latestData.getNO2());
            data.put("o3", latestData.getO3());
            data.put("co", latestData.getCO());

            webView.evaluateJavascript("javascript:receiveDataFromJava('" + data.toString() + "')", null);
        } catch (JSONException e) {
            Log.e("DYNAMIC_DATA", "JSON构建失败", e);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupContentAnalysisWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                generateChartData();
                webView.loadUrl("javascript:window.addEventListener('resize', function() { myChart.resize(); })");
            }
        });
        webView.loadUrl("file:///android_asset/dataset-link.html");
    }

    private void generateChartData() {
        try {
            if (currentDataList.isEmpty()) return;

            // 解析时间范围
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date start = sdf.parse(currentDataList.get(0).getCreatedAt());
            Date end = sdf.parse(currentDataList.get(currentDataList.size()-1).getCreatedAt());

            // 生成动态横坐标
            List<String> timePoints = new ArrayList<>();
            Calendar cal = Calendar.getInstance();
            cal.setTime(start);

            // 调整起始时间为最近的半小时
            int minute = cal.get(Calendar.MINUTE);
            if (minute > 30) {
                cal.set(Calendar.MINUTE, 30);
            } else {
                cal.set(Calendar.MINUTE, 0);
            }
            cal.set(Calendar.SECOND, 0);

            // 生成时间点（半小时间隔）
            while (cal.getTime().before(end) || cal.getTime().equals(end)) {
                timePoints.add(new SimpleDateFormat("HH:mm").format(cal.getTime()));
                cal.add(Calendar.MINUTE, 30);
            }

            // 构建数据矩阵
            String[] products = {"PM2.5", "PM10", "SO2", "NO2", "O3", "CO"};
            double[][] values = new double[products.length][timePoints.size()];

            // 填充数据
            for (EnvData data : currentDataList) {
                Date dataTime = sdf.parse(data.getCreatedAt());
                for (int i=0; i<timePoints.size(); i++) {
                    Date tpStart = new SimpleDateFormat("HH:mm").parse(timePoints.get(i));
                    Date tpEnd = new Date(tpStart.getTime() + 1800000); // 30分钟间隔

                    if (dataTime.after(tpStart) && dataTime.before(tpEnd)) {
                        values[0][i] = data.getPM25();
                        values[1][i] = data.getPM10();
                        values[2][i] = data.getSO2();
                        values[3][i] = data.getNO2();
                        values[4][i] = data.getO3();
                        values[5][i] = data.getCO();
                    }
                }
            }

            // 构建JSON数据
            JSONArray dataArray = new JSONArray();
            JSONArray header = new JSONArray().put("product");
            for (String tp : timePoints) header.put(tp);
            dataArray.put(header);

            for (int i=0; i<products.length; i++) {
                JSONArray row = new JSONArray().put(products[i]);
                for (double val : values[i]) row.put(val);
                dataArray.put(row);
            }

            webView.loadUrl("javascript:updateChart(" + dataArray.toString() + ")");

        } catch (Exception e) {
            Log.e("CHART_DATA", "图表数据生成失败", e);
        }
    }

    private void startDataRefresh() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                new Thread(() -> {
                    try {
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

                        HuaweiIOT envDevice = new HuaweiIOT(
                                "l17738422005", "test", "Lww827717.",
                                "67fe0cc15367f573f782f7d2",
                                "67fe0cc15367f573f782f7d2_2021518129_01", "Environment_DATA",
                                "b8b855d242.st1.iotda-app.cn-north-4.myhuaweicloud.com"
                        );

                        String tempStr = envDevice.getAtt("temp", "shadow");
                        String humiStr = envDevice.getAtt("humi", "shadow");
                        String co2Str = envDevice.getAtt("CO2", "shadow");
                        String methanalStr = envDevice.getAtt("Methanal", "shadow");
                        String pm25Str = envDevice.getAtt("PM25", "shadow");
                        String tvocStr = envDevice.getAtt("TVOC", "shadow");
                        String pm10Str = envDevice.getAtt("PM10", "shadow");

                        JSONObject data = new JSONObject();
                        data.put("latitude", latitudeStr);
                        data.put("longitude", longitudeStr);
                        data.put("temp", parseSafe(tempStr));
                        data.put("humi", parseSafe(humiStr));
                        data.put("co2", parseSafe(co2Str));
                        data.put("methanal", parseSafe(methanalStr));
                        data.put("pm25", parseSafe(pm25Str));
                        data.put("tvoc", parseSafe(tvocStr));

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> sendDataToWebView(data.toString()));
                        }
                    if (title.equals("实时数据图") && webView != null) {
                        getActivity().runOnUiThread(() -> sendDynamicDataToWebView());
                    }
                    } catch (Exception e) {
                        Log.e("DATA_REFRESH", "数据刷新异常", e);
                    } finally {
                        handler.postDelayed(this, 3000);
                    }

                }).start();
            }
        };
        handler.post(refreshRunnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
            refreshRunnable = null;
        }
        webView = null;
    }

    private void showDateTimePicker(boolean isStartTime) {
        final Calendar calendar = isStartTime ? startCal : endCal;
        new DatePickerDialog(requireContext(), (view, year, month, day) -> {
            new TimePickerDialog(requireContext(), (timeView, hour, minute) -> {
                calendar.set(year, month, day, hour, minute);
                updateTimeDisplay();
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if ("数据设置".equals(title)) {
            queryData(); // 确保视图已初始化后调用
        }
    }
    private void updateTimeDisplay() {
        if (getView() == null) return;
        String start = dateFormat.format(startCal.getTime());
        String end = dateFormat.format(endCal.getTime());
        TextView tvTimeRange = getView().findViewById(R.id.tv_time_range);
        if (tvTimeRange != null) {
            tvTimeRange.setText(String.format("时间范围：%s 至 %s", start, end));
        }

    }

    private void queryData() {
        new Thread(() -> {
            try {
                SimpleDateFormat queryFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                queryFormat.setLenient(false);

                Date startDate = queryFormat.parse(dateFormat.format(startCal.getTime()));
                Date endDate = queryFormat.parse(dateFormat.format(endCal.getTime()));

                currentDataList.clear(); // 清空旧数据

                // 转换DataContainer数据为EnvData格式
                SimpleDateFormat containerFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                        containerFormat.setLenient(false);

                for (Object[] data : DataContainer.sampleData) {
                    Date dataTime = containerFormat.parse((String) data[0]);

                    if (dataTime.after(startDate) && dataTime.before(endDate)) {
                        EnvData envData = new EnvData();
                        envData.setCreatedAt((String) data[0]);
                        // 字段映射（根据DataContainer数据结构）
                        envData.setPM25((Double) data[1]);
                        envData.setPM10((Double) data[2]);
                        envData.setSO2((Double) data[3]);   // SO2 对应 DataContainer 的第三个字段
                        envData.setNO2((Double) data[4]);   // NO2 对应第四个字段
                        envData.setO3((Double) data[5]);    // O3 对应第五个字段
                        envData.setCO((Double) data[6]);    // CO 对应第六个字段
                        envData.setLongitude((Double) data[7]);  // 经度
                        envData.setLatitude((Double) data[8]);   // 纬度
                        currentDataList.add(envData);
                    }
                }

                if (getActivity() != null && isAdded()) { // 检查Fragment是否附加
                    getActivity().runOnUiThread(() -> {
                        if (isAdded()) { // 再次检查
                            updateTable(currentDataList);
                            if (webView != null) {
                                generateChartData();
                                webView.reload();
                            }
                        }
                    });
                }
            } catch (Exception e) {
                Log.e("QUERY_DATA", "数据查询异常", e);
            }
        }).start();
    }

    private void updateTable(List<EnvData> dataList) {
        TableLayout dataTableBody = getView().findViewById(R.id.data_table_body);
        TextView tvEmptyTip = getView().findViewById(R.id.tv_empty_tip);

        dataTableBody.removeAllViews();

        if (dataList.isEmpty()) {
            // 显示提示信息，隐藏表格
            tvEmptyTip.setVisibility(View.VISIBLE);
            dataTableBody.setVisibility(View.GONE);

            // 生成时间范围字符串
            String timeRange = String.format(
                    "%s - %s",
                    dateFormat.format(startCal.getTime()),
                    dateFormat.format(endCal.getTime())
            );tvEmptyTip.setText("数据库中没找到 " + timeRange + " 范围的数据");
        } else {
            // 隐藏提示信息，显示表格
            tvEmptyTip.setVisibility(View.GONE);
            dataTableBody.setVisibility(View.VISIBLE);

            int index = 1; // 初始化序号计数器
            for (EnvData data : dataList) {
                TableRow row = new TableRow(requireContext());

                TextView indexView = createTextView(String.valueOf(index), 0);
                TextView timeView = createTextView(data.getCreatedAt(), 1);
                TextView pm25View = createTextView(String.format("%.2f", data.getPM25()), 2);
                TextView pm10View = createTextView(String.format("%.2f", data.getPM10()), 3);
                TextView so2View = createTextView(String.format("%.2f", data.getSO2()), 4);
                TextView no2View = createTextView(String.format("%.2f", data.getNO2()), 5);
                TextView o3View = createTextView(String.format("%.2f", data.getO3()), 6);
                TextView coView = createTextView(String.format("%.2f", data.getCO()), 7);
                TextView lngView = createTextView(String.format("%.5f", data.getLongitude()), 8);
                TextView latView = createTextView(String.format("%.5f", data.getLatitude()), 9);

                row.addView(indexView);
                row.addView(timeView);
                row.addView(pm25View);  // PM2.5
                row.addView(pm10View);  // PM10
                row.addView(so2View);   // SO2
                row.addView(no2View);   // NO2
                row.addView(o3View);    // O3
                row.addView(coView);    // CO
                row.addView(lngView);   // 经度
                row.addView(latView);   // 纬度

                dataTableBody.addView(row);
                index++;
            }
        }
    }

    private TextView createTextView(String text, int columnIndex) {
        TextView textView = new TextView(requireContext());
        textView.setText(text);
        textView.setPadding(8, 8, 8, 8);
        textView.setGravity(Gravity.CENTER);

        // 根据列索引设置宽度（与表头一致）
        TableRow.LayoutParams params = new TableRow.LayoutParams();
        switch (columnIndex) {
            case 0: // 序号列
                params.width = (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 50, getResources().getDisplayMetrics());
                break;
            case 1: // 时间列
                params.width = (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 140, getResources().getDisplayMetrics());
                break;
            case 8:
            case 9:
                params.width = (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 100, getResources().getDisplayMetrics());
                break;
            default: // 其他列（PM2.5、PM10等）
                params.width = (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 50, getResources().getDisplayMetrics()); // 统一设为100dp
                break;
        }
        textView.setLayoutParams(params);
        return textView;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}