package com.example.baidu_map;

import android.util.Log;

import com.baidu.mapapi.map.WeightedLatLng;
import com.baidu.mapapi.model.LatLng;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



public class AQIProcessor {
    // 定义4米对应的经纬度偏移量（近似值，适用于低纬度）
    private static final double GRID_SIZE = 10.0 / 111320.0; // 1度≈111320米

    public static LatLng findSuspectedSource(List<WeightedLatLng> heatData) {
        double maxAQI = 0;
        LatLng source = null;
        for (WeightedLatLng data : heatData) {
            if (data.getIntensity() > maxAQI) {
                maxAQI = data.getIntensity();
                source = data.mLatLng;
            }
        }
        return source;
    }

    public static List<WeightedLatLng> processAQIData() {
        Map<String, List<Double>> gridAQIMap = new HashMap<>();
        double lng = 0;
        double lat = 0;
        String gridKey = null;
        Log.d("GridDebug", "Data at (" + lng + "," + lat + ") -> Grid: " + gridKey);

        // 遍历所有样本数据
        for (Object[] data : DataContainer.sampleData) {
            lng = (double) data[7];
            lat = (double) data[8];

            // 计算所属网格的索引
            gridKey = getGridKey(lng, lat);

            // 计算AQI
            double aqi = calculateAQI(
                    (double) data[1], (double) data[2],
                    (double) data[3], (double) data[4],
                    (double) data[5], (double) data[6]
            );

            // 将AQI存入对应网格
            if (!gridAQIMap.containsKey(gridKey)) {
                gridAQIMap.put(gridKey, new ArrayList<>());
            }
            gridAQIMap.get(gridKey).add(aqi);
        }

        // 生成热力图数据点
        List<WeightedLatLng> heatData = new ArrayList<>();
        for (Map.Entry<String, List<Double>> entry : gridAQIMap.entrySet()) {
            String[] parts = entry.getKey().split(",");

            double gridLng = Double.parseDouble(parts[0]);
            double gridLat = Double.parseDouble(parts[1]);

            double centerLng = Double.parseDouble(parts[0]) + GRID_SIZE / 2;
            double centerLat = Double.parseDouble(parts[1]) + GRID_SIZE / 2;

            // 计算网格内AQI平均值
            double avgAQI = entry.getValue().stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);

            heatData.add(new WeightedLatLng(new LatLng(centerLat, centerLng), avgAQI));
        }

        return heatData;
    }

    private static String getGridKey(double lng, double lat) {
        // 将经纬度对齐到网格左下角
        double gridLng = Math.floor(lng / GRID_SIZE) * GRID_SIZE;
        double gridLat = Math.floor(lat / GRID_SIZE) * GRID_SIZE;
        return gridLng + "," + gridLat;
    }

    static double calculateAQI(double pm25, double pm10, double so2,
                               double no2, double o3, double co) {
        // 根据HJ 633-2012计算各污染物IAQI
        double iaqiPm25 = calculateIAQI(pm25, new double[]{0, 35, 75, 115, 150, 250, 350, 500});
        double iaqiPm10 = calculateIAQI(pm10, new double[]{0, 50, 150, 250, 350, 420, 500, 600});
        double iaqiSo2 = calculateIAQI(so2, new double[]{0, 50, 150, 475, 800, 1600, 2100, 2620});
        double iaqiNo2 = calculateIAQI(no2, new double[]{0, 40, 80, 180, 280, 565, 750, 940});
        double iaqiO3 = calculateIAQI(o3, new double[]{0, 100, 160, 215, 265, 800, 1000, 1200});
        double iaqiCo = calculateIAQI(co, new double[]{0, 2, 4, 14, 24, 36, 48, 60});

        // AQI取最大值
        return Math.max(Math.max(Math.max(iaqiPm25, iaqiPm10),
                Math.max(iaqiSo2, iaqiNo2)), Math.max(iaqiO3, iaqiCo));
    }

    private static double calculateIAQI(double Cp, double[] bp) {
        int i = 0;
        while (i < bp.length - 1 && Cp > bp[i + 1]) i++;
        double iaqiHi = (i * 50) + 50;
        double iaqiLo = i * 50;
        double bpHi = bp[i + 1];
        double bpLo = bp[i];
        return (iaqiHi - iaqiLo) / (bpHi - bpLo) * (Cp - bpLo) + iaqiLo;
    }
}
