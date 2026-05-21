package com.example.location

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class TencentPoi(
    val title: String,
    val address: String,
    val lat: Double,
    val lng: Double
)

object TencentMapHelper {
    private val API_KEY = if (BuildConfig.TENCENT_MAP_KEY.isNotBlank() && !BuildConfig.TENCENT_MAP_KEY.startsWith("MY_")) BuildConfig.TENCENT_MAP_KEY else "T3XBZ-EYLLQ-2CD5S-B7XE6-XOCQV-DBF3P"
    private val SK = if (BuildConfig.TENCENT_MAP_SK.isNotBlank() && !BuildConfig.TENCENT_MAP_SK.startsWith("MY_")) BuildConfig.TENCENT_MAP_SK else "G7t5UrkzbiQFVSJONtupGo8yEbxiGzTV"

    fun md5(input: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance("MD5")
            val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: java.lang.Exception) {
            Log.e("TencentMapHelper", "MD5 calculation failed", e)
            ""
        }
    }

    fun signUrl(path: String, params: Map<String, String>): String {
        val sortedKeys = params.keys.sorted()
        val paramString = sortedKeys.joinToString("&") { key ->
            "$key=${params[key]}"
        }
        val stringToSign = "$path?$paramString$SK"
        val sig = md5(stringToSign)
        
        val encodedQuery = sortedKeys.joinToString("&") { key ->
            val value = params[key] ?: ""
            "$key=${URLEncoder.encode(value, "UTF-8")}"
        }
        return "https://apis.map.qq.com$path?$encodedQuery&sig=$sig"
    }

    // High quality mock offline Fujian POIs dataset (incorporating residential, stores, and street address structures)
    val offlineFujianPois = listOf(
        TencentPoi("融侨华府", "福建省福州市台江区闽江路118号 (高档住宅小区)", 26.0642, 119.3082),
        TencentPoi("世欧王庄四区", "福建省福州市晋安区长乐中路296号 (高密度住宅区)", 26.0768, 119.3298),
        TencentPoi("保利香槟国际", "福建省福州市晋安区塔头路与连江北路交汇处 (大型小区)", 26.0965, 119.3362),
        TencentPoi("仓山万达广场 B区", "福建省福州市仓山区浦上大道306号 (核心商场店铺)", 26.0352, 119.2783),
        TencentPoi("东百中心 A栋", "福建省福州市鼓楼区八一七北路88号 (传统核心百年地标商铺)", 26.0898, 119.2981),
        TencentPoi("厦门中华城 A区", "福建省厦门市思明区中山路1号 (热门街区商业店铺)", 24.4532, 118.0845),
        TencentPoi("厦门大学芙蓉二舍", "福建省厦门市思明区思明南路422号 (高校校区宿舍)", 24.4368, 118.0932),
        TencentPoi("世茂海峡大厦 (双子塔)", "福建省厦门市思明区大学路177号 (标志超级写字楼百货)", 24.4355, 118.0838),
        TencentPoi("福州泰禾广场 SOHO 3号楼", "福建省福州市晋安区岳峰新城竹屿路6号 (公寓住宅与小店集中区)", 26.0955, 119.3450),
        TencentPoi("中庚红鼎天下", "福建省福州市仓山区南江滨西大道东侧 (临江江景住宅小区)", 26.0465, 119.3195),
        TencentPoi("阳光城丽景湾", "福建省福州市闽侯县乌龙江中大道也 (县域新兴住宅区)", 26.0125, 119.2081),
        TencentPoi("五一北路129号金安大厦", "福建省福州市鼓楼区五一北路129号 (老牌商务楼宇与沿街门牌)", 26.0848, 119.3090),
        TencentPoi("福建师范大学旗山校区星雨剧场", "福建省福州市闽侯县大学城科技路1号 (大学生活动中心)", 26.0245, 119.2065),
        TencentPoi("福建协和医院 2号外科大楼", "福建省福州市鼓楼区新权路29号 (骨干综合医疗机构)", 26.0825, 119.3005),
        TencentPoi("平潭综合实验区龙王湾海景高层", "福建省福州市平潭县海滨路58号 (度假湾区住宅小区)", 25.4985, 119.7992),
        TencentPoi("鼓浪屿风景区三一堂", "福建省厦门市思明区鼓浪屿安海路67号 (重点人文地名标志)", 24.4445, 118.0621),
        TencentPoi("晋安区茶会小区 12号楼", "福建省福州市晋安区福马路210号 (民生老旧街坊住宅)", 26.0792, 119.3421),
        TencentPoi("泉州开元寺东塔侧殿", "福建省泉州市鲤城区西街176号 (千年名胜历史文化保护区)", 24.9143, 118.5828),
        TencentPoi("泉州晋江SM广场", "福建省泉州市晋江市罗山街道福兴路111号 (闽南热门综合商圈店铺)", 24.7895, 118.5710),
        TencentPoi("漳州古城牌坊街 85号商铺", "福建省漳州市芗城区延安南路 (沿街市井特色门牌店铺)", 24.5108, 117.6536),
        TencentPoi("宁德万达广场华府 2期", "福建省宁德市东侨经济技术开发区天湖东路1号 (城市核心商业住宅)", 26.6575, 119.5448)
    )

    // Offline search engine: searches locally through the custom Fujian database
    fun searchOfflineFujian(keyword: String): List<TencentPoi> {
        if (keyword.isBlank()) {
            return offlineFujianPois.take(12)
        }
        val query = keyword.trim().lowercase()
        return offlineFujianPois.filter {
            it.title.lowercase().contains(query) || 
            it.address.lowercase().contains(query)
        }
    }

    // Generic HTTP network fetcher
    suspend fun fetchUrl(urlString: String): String = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("Accept", "application/json")
            
            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                response.toString()
            } else {
                Log.e("TencentMapHelper", "Http non-200 code: $responseCode for url: $urlString")
                ""
            }
        } catch (e: Exception) {
            Log.e("TencentMapHelper", "Error calling url: $urlString", e)
            ""
        } finally {
            connection?.disconnect()
        }
    }

    // 1. Geocoding: Address -> Coordinates (lat, lng)
    suspend fun getCoordinateFromAddress(address: String): Pair<Double, Double>? {
        if (address.isBlank()) return null
        return try {
            val url = signUrl("/ws/geocoder/v1", mapOf(
                "address" to address,
                "key" to API_KEY
            ))
            val jsonStr = fetchUrl(url)
            if (jsonStr.isBlank()) return null
            
            val json = JSONObject(jsonStr)
            if (json.getInt("status") == 0) {
                val result = json.getJSONObject("result")
                val location = result.getJSONObject("location")
                Pair(location.getDouble("lat"), location.getDouble("lng"))
            } else {
                Log.w("TencentMapHelper", "Geocoding returned status non-zero: ${json.optString("message")}")
                null
            }
        } catch (e: Exception) {
            Log.e("TencentMapHelper", "Geocoding error for address: $address", e)
            null
        }
    }

    // 2. Reverse Geocoding: Coordinates -> Address + Nearby POIs
    suspend fun getAddressAndPoisFromCoordinate(lat: Double, lng: Double): Pair<String, List<TencentPoi>>? {
        return try {
            val url = signUrl("/ws/geocoder/v1", mapOf(
                "get_poi" to "1",
                "key" to API_KEY,
                "location" to "$lat,$lng"
            ))
            val jsonStr = fetchUrl(url)
            if (jsonStr.isBlank()) return null
            
            val json = JSONObject(jsonStr)
            if (json.getInt("status") == 0) {
                val result = json.getJSONObject("result")
                val address = result.getString("address")
                val poisList = mutableListOf<TencentPoi>()
                
                if (result.has("pois")) {
                    val poisArr = result.getJSONArray("pois")
                    for (i in 0 until poisArr.length()) {
                        val poiObj = poisArr.getJSONObject(i)
                        val title = poiObj.getString("title")
                        val poiAddress = poiObj.optString("address", "")
                        val loc = poiObj.getJSONObject("location")
                        poisList.add(
                            TencentPoi(
                                title = title,
                                address = poiAddress,
                                lat = loc.getDouble("lat"),
                                lng = loc.getDouble("lng")
                             )
                        )
                    }
                }
                Pair(address, poisList)
            } else {
                Log.w("TencentMapHelper", "Reverse Geocoding returned status non-zero: ${json.optString("message")}")
                null
            }
        } catch (e: Exception) {
            Log.e("TencentMapHelper", "Reverse Geocoding error for coordinates: ($lat, $lng)", e)
            null
        }
    }

    // 3. Keyword Autocomplete Suggestion List
    suspend fun getSearchSuggestions(keyword: String, region: String = "上海"): List<TencentPoi> {
        if (keyword.isBlank()) return emptyList()
        return try {
            val url = signUrl("/ws/place/v1/suggestion", mapOf(
                "key" to API_KEY,
                "keyword" to keyword,
                "region" to region
            ))
            val jsonStr = fetchUrl(url)
            if (jsonStr.isBlank()) return emptyList()
            
            val json = JSONObject(jsonStr)
            if (json.getInt("status") == 0) {
                val dataArr = json.getJSONArray("data")
                val suggestions = mutableListOf<TencentPoi>()
                for (i in 0 until dataArr.length()) {
                    val item = dataArr.getJSONObject(i)
                    val title = item.getString("title")
                    val address = item.optString("address", "")
                    val loc = item.getJSONObject("location")
                    suggestions.add(
                        TencentPoi(
                            title = title,
                            address = address,
                            lat = loc.getDouble("lat"),
                            lng = loc.getDouble("lng")
                        )
                    )
                }
                suggestions
            } else {
                Log.w("TencentMapHelper", "Suggestion returned status non-zero: ${json.optString("message")}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("TencentMapHelper", "Suggestion error for key: $keyword", e)
            emptyList()
        }
    }

    // 4. Generate Tension Map Static Image Link
    fun getStaticMapUrl(lat: Double, lng: Double, width: Int = 400, height: Int = 300, zoom: Int = 16): String {
        return signUrl("/ws/staticmap/v1", mapOf(
            "center" to "$lat,$lng",
            "key" to API_KEY,
            "markers" to "color:red|size:large|$lat,$lng",
            "size" to "${width}*${height}",
            "zoom" to "$zoom"
        ))
    }

    // 5. Compute real distance using Haversine algorithm natively
    fun getHaversineDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rEarth = 6371.0 // Radius of earth in kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return rEarth * c
    }
}
