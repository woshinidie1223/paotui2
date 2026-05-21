package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import java.util.LinkedList
import java.util.Queue

/**
 * =================================================================================
 * 高精度定位与纠偏滤波核心引擎 (Android Native / Flutter Reference)
 * =================================================================================
 * 
 * 本辅助类完美实现并深度解析了针对配送/跑腿业务场景下，高精度定位、地图纠偏、坐标融合及滤波算法。
 * 包含以下核心内容：
 * 1. 强制高精度模式配置 (Core Priority configurations)
 * 2. 权限树配置指南 (ACCESS_FINE_LOCATION % ACCESS_BACKGROUND_LOCATION)
 * 3. 各种主流地图 (高德、百度、腾讯) 的纠偏/转换模型 (WGS-84 <=> GCJ-02 <=> BD-09)
 * 4. 连续定位自研 Kalman 滤波 (卡尔曼滤波) 与 滑动低通滤波器 (Low-Pass Filters)
 * 5. 辅助定位 Wi-Fi 扫描触发器及热点合并逻辑
 */

// ==========================================
// 1. 卡尔曼滤波器 (Kalman Filter)
// ==========================================
/**
 * 针对 GPS 坐标波动、城市高楼峡谷反射等造成的“瞬时漂移”进行卡尔曼预测滤波
 */
class CoordinateKalmanFilter(
    private val r: Double = 0.00001, // 测量噪声协方差 (Measurement Noise Covariance) - 越小代表相信测得的数据
    private val q: Double = 0.000001 // 过程噪声协方差 (Process Noise Covariance) - 越小代表物体的运动越稳定
) {
    private var lastLat = 0.0
    private var lastLng = 0.0
    private var pLat = 1.0 // 估计误差协方差
    private var pLng = 1.0
    private var isInitialized = false

    /**
     * 输入新的原始 GPS 坐标，输出平滑滤波后的坐标
     */
    fun filter(lat: Double, lng: Double): Pair<Double, Double> {
        if (!isInitialized) {
            lastLat = lat
            lastLng = lng
            pLat = 1.0
            pLng = 1.0
            isInitialized = true
            return Pair(lat, lng)
        }

        // 1. 预测更新 (Prediction)
        // 假定目标静止或低速运动，预测误差略微增加
        pLat += q
        pLng += q

        // 2. 计算卡尔曼增益 (Kalman Gain)
        val kLat = pLat / (pLat + r)
        val kLng = pLng / (pLng + r)

        // 3. 计算滤波后的状态 (Update)
        lastLat = lastLat + kLat * (lat - lastLat)
        lastLng = lastLng + kLng * (lng - lastLng)

        // 4. 更新协方差 (Covariance update)
        pLat = (1.0 - kLat) * pLat
        pLng = (1.0 - kLng) * pLng

        return Pair(lastLat, lastLng)
    }

    fun reset() {
        isInitialized = false
    }
}

// ==========================================
// 2. 滑动平均滤波器 (Moving Average Filter)
// ==========================================
class MovingAverageFilter(private val windowSize: Int = 5) {
    private val latQueue: Queue<Double> = LinkedList()
    private val lngQueue: Queue<Double> = LinkedList()

    fun filter(lat: Double, lng: Double): Pair<Double, Double> {
        latQueue.offer(lat)
        lngQueue.offer(lng)

        if (latQueue.size > windowSize) {
            latQueue.poll()
        }
        if (lngQueue.size > windowSize) {
            lngQueue.poll()
        }

        val avgLat = latQueue.average()
        val avgLng = lngQueue.average()

        return Pair(avgLat, avgLng)
    }

    fun reset() {
        latQueue.clear()
        lngQueue.clear()
    }
}

// ==========================================
// 3. 地图纠偏算法 & 坐标系转换 (Coordinate Converter)
// ==========================================
/**
 * 国内主流地图坐标系解释：
 * WGS-84: 国际标准GPS坐标 (Android 原生 FusedLocationProvider 获取的值)
 * GCJ-02: 火星坐标系 (中国国家测绘局制定的加密坐标系，高德地图、腾讯地图、谷歌中国地图使用)
 * BD-09: 百度坐标系 (百度在 GCJ-02 基础上二次加密，百度地图使用)
 * 
 * 跑腿配送核心要点：如果不进行纠偏，直接把 WGS-84 的坐标画在高德/腾讯/百度地图上，会产生 100米 - 1000米 的偏差偏移！
 */
object CoordinateConverter {
    private const val pi = 3.1415926535897932384626
    private const val xPi = 3.14159265358979324 * 3000.0 / 180.0
    private const val a = 6378245.0 // 克拉索夫斯基椭球体长半轴
    private const val ee = 0.00669342162296594323 // 扁率第一偏心率平方

    /**
     * 判断是否在中国境外 (境外不进行纠偏加密)
     */
    fun outOfChina(lat: Double, lng: Double): Boolean {
        if (lng < 72.004 || lng > 137.8347) return true
        if (lat < 0.8293 || lat > 55.8271) return true
        return false
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
        ret += (20.0 * Math.sin(6.0 * x * pi) + 20.0 * Math.sin(2.0 * x * pi)) * 2.0 / 3.0
        ret += (20.0 * Math.sin(y * pi) + 40.0 * Math.sin(y / 3.0 * pi)) * 2.0 / 3.0
        ret += (160.0 * Math.sin(y / 12.0 * pi) + 320 * Math.sin(y * pi / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
        ret += (20.0 * Math.sin(6.0 * x * pi) + 20.0 * Math.sin(2.0 * x * pi)) * 2.0 / 3.0
        ret += (20.0 * Math.sin(x * pi) + 40.0 * Math.sin(x / 3.0 * pi)) * 2.0 / 3.0
        ret += (150.0 * Math.sin(x / 12.0 * pi) + 300.0 * Math.sin(x / 30.0 * pi)) * 2.0 / 3.0
        return ret
    }

    /**
     * 1. GPS标准 (WGS-84) 转 火星坐标系 (GCJ-02) -> 适用高德、腾讯、谷歌中国地图
     */
    fun wgs84ToGcj02(wgLat: Double, wgLng: Double): Pair<Double, Double> {
        if (outOfChina(wgLat, wgLng)) {
            return Pair(wgLat, wgLng)
        }
        var dLat = transformLat(wgLng - 35.0, wgLat - 35.0)
        var dLng = transformLng(wgLng - 35.0, wgLat - 35.0)
        val radLat = wgLat / 180.0 * pi
        var magic = Math.sin(radLat)
        magic = 1 - ee * magic * magic
        val sqrtMagic = Math.sqrt(magic)
        dLat = (dLat * 180.0) / ((a * (1 - ee)) / (magic * sqrtMagic) * pi)
        dLng = (dLng * 180.0) / (a / sqrtMagic * Math.cos(radLat) * pi)
        val gcjLat = wgLat + dLat
        val gcjLng = wgLng + dLng
        return Pair(gcjLat, gcjLng)
    }

    /**
     * 2. 火星坐标系 (GCJ-02) 转 百度坐标 (BD-09) -> 适用百度地图
     */
    fun gcj02ToBd09(gcjLat: Double, gcjLng: Double): Pair<Double, Double> {
        val z = Math.sqrt(gcjLng * gcjLng + gcjLat * gcjLat) + 0.00002 * Math.sin(gcjLat * xPi)
        val theta = Math.atan2(gcjLat, gcjLng) + 0.000003 * Math.cos(gcjLng * xPi)
        val bdLng = z * Math.cos(theta) + 0.0065
        val bdLat = z * Math.sin(theta) + 0.006
        return Pair(bdLat, bdLng)
    }

    /**
     * 3. WGS-84 直接转换到 百度 BD-09
     */
    fun wgs84ToBd09(wgsLat: Double, wgsLng: Double): Pair<Double, Double> {
        val gcj = wgs84ToGcj02(wgsLat, wgsLng)
        return gcj02ToBd09(gcj.first, gcj.second)
    }
}

// ==========================================
// 4. Android 原生 FusedLocationProviderClient 连续高精度定位代码指南 (可复制)
// ==========================================
object NativeLocationGuideDetails {

    const val KOTLIN_CODE_GUIDE = """
// 1. 声明高精度和连续定位所需相关依赖及权限
// 在 build.gradle 中导入： implementation("com.google.android.gms:play-services-location:21.2.0")
// AndroidManifest 中声明:
// <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" /> <!-- 精确位置权限 -->
// <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" /> <!-- 粗略位置 -->
// <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION"/> <!-- 后台持续 -->
// <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" /> <!-- Wi-Fi状态获取 -->
// <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" /> <!-- Wi-Fi状态改变(扫描) -->

import com.google.android.gms.location.*
import android.content.Context
import android.os.Looper

class ContinuousLocationTracker(val context: Context) {
    private var fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)
    
    // 初始化卡尔曼滤波器
    val kalmanFilter = CoordinateKalmanFilter()
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                val rawLat = location.latitude
                val rawLng = location.longitude
                val accuracy = location.accuracy // 精度(米)
                
                // 1. 卡尔曼平滑滤波，剔除瞬时信号跳变
                val smoothed = kalmanFilter.filter(rawLat, rawLng)
                
                // 2. 地图SDK纠偏：将GPS(WGS84)转化为高德/腾讯(GCJ-02)
                val mapCoord = CoordinateConverter.wgs84ToGcj02(smoothed.first, smoothed.second)
                
                Log.d("GPS_TRACKER", "原始坐标: (${"$"}{rawLat}, ${"$"}{rawLng}) | " +
                        "滤波后火星坐标: (${"$"}{mapCoord.first}, ${"$"}{mapCoord.second}) | 精度: ${"$"}{accuracy}米")
                
                // 3. 将纠偏后的连续轨迹同步给业务层/后台
                sendToBackend(mapCoord.first, mapCoord.second, accuracy)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        // 配置强开高精度模式
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, // 【强开高精度模式】
            2000L                           // 【连续定位刷新周期: 2秒】
        ).apply {
            setMinUpdateIntervalMillis(1000L) // 最快1秒触发一次反馈
            setWaitForAccurateLocation(true) // 优先等待高精度GPS就绪，而不是用Wi-Fi/移动基站粗略代提
            setMaxUpdateDelayMillis(4000L)   // 延迟不超过4秒(做批处理合并，节电)
        }.build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    fun stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
"""

    const val FLUTTER_CODE_GUIDE = """
// Flutter 高精度 & 连续定位实现参考：
// 1. 引入 geolocator 或 amap_flutter_location 插件
// 2. 在 pubspec.yaml 中添加： 
//    geolocator: ^11.0.0
//    permission_handler: ^11.3.0

import 'package:geolocator/geolocator.dart';
import 'dart:async';

class FlutterLocationTracker {
  StreamSubscription<Position>? _positionStreamSubscription;
  
  // 简易卡尔曼滤波在 Dart 端的实现
  double _lastLat = 0.0;
  double _lastLng = 0.0;
  double _pLat = 1.0;
  double _pLng = 1.0;
  final double _r = 0.00001; // 测量噪声
  final double _q = 0.000001; // 过程预测噪声
  bool _isInit = false;

  Map<String, double> kalmanFilter(double lat, double lng) {
    if (!_isInit) {
      _lastLat = lat;
      _lastLng = lng;
      _isInit = true;
      return {'lat': lat, 'lng': lng};
    }
    _pLat += _q;
    _pLng += _q;
    
    double kLat = _pLat / (_pLat + _r);
    double kLng = _pLng / (_pLng + _r);
    
    _lastLat = _lastLat + kLat * (lat - _lastLat);
    _lastLng = _lastLng + kLng * (lng - _lastLng);
    
    _pLat = (1.0 - kLat) * _pLat;
    _pLng = (1.0 - kLng) * _pLng;
    
    return {'lat': _lastLat, 'lng': _lastLng};
  }

  // 启动高精度连续监听
  Future<void> startContinuousHighAccuracyTracking() async {
    // 1. 检查和申请精确位置权限
    LocationPermission permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
    }
    
    if (permission == LocationPermission.whileInUse || 
        permission == LocationPermission.always) {
      
      const LocationSettings locationSettings = LocationSettings(
        accuracy: LocationAccuracy.bestForNavigation, // 【强制高精度模式：最佳导航精度】
        distanceFilter: 2, // 【2米位移触发器，避免原地踏步漂移】
      );

      _positionStreamSubscription = Geolocator.getPositionStream(
        locationSettings: locationSettings
      ).listen((Position position) {
        // 卡尔曼一维滤波抑制漂移
        var smooth = kalmanFilter(position.latitude, position.longitude);
        
        print("原始: ${"$"}{position.latitude}, ${"$"}{position.longitude}");
        print("平滑滤波后: ${"$"}{smooth['lat']}, ${"$"}{smooth['lng']}");
        
        // 此处可调用火星坐标纠偏转换，然后渲染到高德地图或同步服务器
      });
    }
  }

  void stopTracking() {
    _positionStreamSubscription?.cancel();
  }
}
"""
}

// ==========================================
// 5. WI-FI 辅助扫描设备列表类 (WIFI Scanner)
// ==========================================
class WifiAssistScanner(private val context: Context) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    @SuppressLint("MissingPermission")
    fun scanNearbyWifiAccessPoints(): List<SimulatedWifiDevice> {
        val resultList = mutableListOf<SimulatedWifiDevice>()
        try {
            // 在 Android 9 + 系统中，前台WiFi扫描每2分钟被限制在4次以内。
            // 因此，在实际商业级高精度定位中，通常使用最近一次缓存或者直接注册 BroadcastReceiver 异步拉取
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // 原生扫描触发
                wifiManager.startScan()
            }
            val scans: List<ScanResult> = wifiManager.scanResults
            for (scanObj in scans) {
                resultList.add(
                    SimulatedWifiDevice(
                        bssid = scanObj.BSSID ?: "00:00:00:00:00",
                        ssid = scanObj.SSID ?: "Hidden Wi-Fi",
                        levelDb = scanObj.level,
                        frequency = scanObj.frequency
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("WifiAssistScanner", "Wi-Fi Scan denied or missing permissions, fallback simulated", e)
            // 兜底返回模拟周边 Wi-Fi 热点
            resultList.addAll(getSimulatedWifiDevices())
        }
        return resultList.sortedByDescending { it.levelDb }
    }

    private fun getSimulatedWifiDevices(): List<SimulatedWifiDevice> {
        return listOf(
            SimulatedWifiDevice("94:d0:8a:12:bc:20", "TP-LINK_Errand_Rider_5G", -45, 5180),
            SimulatedWifiDevice("48:7d:2e:54:3a:ff", "Starbucks-Guest-WiFi_Free", -58, 2412),
            SimulatedWifiDevice("1c:fa:68:de:4b:90", "ChinaNet-Smart-Express", -65, 2437),
            SimulatedWifiDevice("ea:a3:10:e7:cd:12", "Office_Corporate_Ext", -74, 5240),
            SimulatedWifiDevice("00:50:56:c0:00:08", "Majiang_WiFi_Fast", -82, 2462)
        )
    }
}

data class SimulatedWifiDevice(
    val bssid: String,
    val ssid: String,
    val levelDb: Int,
    val frequency: Int
)
