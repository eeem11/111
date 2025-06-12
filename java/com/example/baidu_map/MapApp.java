//在SDK各功能组件使用之前都需要调用“SDKInitializer.initialize(getApplicationContext())”，
//因此在应用创建时初始化SDK引用的Context为全局变量。
//新建一个自定义的Application，在其onCreate方法中完成SDK的初始化。
package com.example.baidu_map;

import android.app.Application;

import com.baidu.mapapi.CoordType;
import com.baidu.mapapi.SDKInitializer;

public class MapApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SDKInitializer.setAgreePrivacy(this, true);
        //用于设置用户是否同意百度地图SDK的隐私协议。true表示用户同意隐私协议
        SDKInitializer.initialize(this);
        //初始化百度地图SDK。在使用SDK的任何组件（如地图视图、定位服务等）之前，必须调用此方法。
        SDKInitializer.setCoordType(CoordType.BD09LL);
        //设置坐标系类型。百度地图SDK支持不同的坐标系统，CoordType.BD09LL表示使用百度坐标系（BD09LL坐标）
    }
}
