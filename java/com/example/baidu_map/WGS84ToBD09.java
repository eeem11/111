package com.example.baidu_map;

public class WGS84ToBD09 {
private static final double x_pi = 3.14159265358979324 * 3000.0 / 180.0;

private static final double pi = 3.1415926535897932384626; // π
private static final double a = 6378245.0;// 长半轴
private static final double ee = 0.00669342162296594323; // 扁率
public static double[] wgs84ToBd09(double
                                                          lng, double lat) {
double[] gcj02 = wgs84ToGcj02(lng, lat);
return gcj02ToBd09(gcj02[0], gcj02[1]);
}
private static double[] wgs84ToGcj02(double lng, double lat) {
if (outOfChina(lng, lat)) {
return new double[]{lng, lat};
}
double dlat = transformLat(lng - 105.0,
                lat - 35.0);
double dlng = transformLng(lng - 105.0,
                lat - 35.0);
double radlat = lat / 180.0 * pi;
double magic = Math.sin(radlat);
magic = 1 - ee * magic * magic;
double sqrtmagic = Math.sqrt(magic);
dlat = (dlat * 180.0) / ((a * (1 - ee)) / (magic * sqrtmagic) * pi);
dlng = (dlng * 180.0) / (a / sqrtmagic * Math.cos(radlat) * pi);
double mglat = lat + dlat;
double mglng = lng + dlng;
return new double[]{mglng, mglat};
}
private static double[] gcj02ToBd09(double lng, double lat) {
    double z = Math.sqrt(lng * lng + lat * lat) + 0.00002 * Math.sin(lat * x_pi);
    double theta = Math.atan2(lat, lng) + 0.000003 * Math.cos(lng * x_pi);
    double bd_lng = z * Math.cos(theta) + 0.0065;
    double bd_lat = z * Math.sin(theta) + 0.006;

    return new double[]{bd_lng, bd_lat};
}

private static double transformLat(double lng, double lat) {
    double ret = -100.0 + 2.0 * lng + 3.0 * lat + 0.2 * lat * lat + 0.1 * lng * lat + 0.2 *
            Math.sqrt(Math.abs(lng));
    ret += (20.0 * Math.sin(6.0 * lng * pi) + 20.0 * Math.sin(2.0 * lng * pi)) * 2.0 / 3.0;
    ret += (20.0 * Math.sin(lat * pi) + 40.0 * Math.sin(lat / 3.0 * pi)) * 2.0 / 3.0;
    ret += (160.0 * Math.sin(lat / 12.0 * pi) + 320 * Math.sin(lat * pi / 30.0)) * 2.0 / 3.0;
    return ret;
}
private static double transformLng(double lng, double lat) {
    double ret = 300.0 + lng + 2.0 * lat + 0.1 * lng * lng + 0.1 * lng * lat + 0.1 * Math.sqrt(Math.abs(lng));ret += (20.0 * Math.sin(6.0 * lng * pi) + 20.0 * Math.sin(2.0 * lng * pi)) * 2.0 / 3.0;
    ret += (20.0 * Math.sin(lng * pi) + 40.0 * Math.sin(lng / 3.0 * pi)) * 2.0 / 3.0;
    ret += (150.0 * Math.sin(lng / 12.0 * pi) + 300.0 * Math.sin(lng / 30.0 * pi)) * 2.0 / 3.0;
    return ret;
}
private static boolean outOfChina(double lng, double lat) {
    return !(lng > 73.66 && lng < 135.05 && lat > 3.86 && lat < 53.55);
}
}
