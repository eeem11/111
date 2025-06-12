package com.example.baidu_map;

import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.Marker;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.model.LatLng;

public class Locator {
    private LatLng position;
    private Marker marker;
    public Locator(double latitude, double longitude, BaiduMap baiduMap) {
        this.position = new LatLng(latitude, longitude);
        this.marker = (Marker) baiduMap.addOverlay(new MarkerOptions()
                .position(position)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_location))
                .title("目标点"));
 }
    /* targetPoint.updatePosition(newLatitude,newLongitude, mBaiduMap); */
    public void updatePosition(double latitude, double longitude, BaiduMap baiduMap) {
    // 更新位置
    this.position = new LatLng(latitude, longitude);
    // 更新标记的位置
    marker.setPosition(position);
     }
    public LatLng getPosition() {
        return position;
    }
}
