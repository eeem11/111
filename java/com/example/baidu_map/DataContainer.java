package com.example.baidu_map;

import java.util.ArrayList;
import java.util.List;

public class DataContainer {
    public static List<Object[]> sampleData = new ArrayList<>();

    static {
        // 示例数据格式：[时间, PM2.5, PM10, SO2, NO2, O3, CO, 经度, 纬度]
        addSampleData("2025-05-09 09:01:00", 35.0, 50.0, 10.0, 20.0, 60.0, 0.8, 116.40717, 39.90469);
        addSampleData("2025-05-09 09:12:00", 35.0, 50.0, 10.0, 20.0, 60.0, 0.8, 117.40717, 39.90469);
        addSampleData("2025-05-09 09:22:00", 45.0, 65.0, 12.0, 25.0, 55.0, 1.0, 116.40730, 39.90475);
        addSampleData("2025-05-09 09:30:00", 55.0, 75.0, 15.0, 30.0, 70.0, 1.2, 116.40745, 39.90482);
        addSampleData("2025-05-09 10:00:00", 70.0, 90.0, 20.0, 40.0, 80.0, 1.5, 116.40717, 39.90469);
        addSampleData("2025-05-09 10:15:00", 85.0, 110.0, 25.0, 45.0, 95.0, 2.0, 116.40800, 39.90500);
    }
    private static void addSampleData(String time, double pm25, double pm10, double so2,
                                      double no2, double o3, double co, double lng, double lat) {
        sampleData.add(new Object[]{time, pm25, pm10, so2, no2, o3, co, lng, lat});
    }

    public static Object[] getLatestData() {
        if (!sampleData.isEmpty()) {
            return sampleData.get(sampleData.size() - 1);
        }
        return null;
    }
}