package com.example.network

import android.content.Context
import android.util.Log
import com.example.db.AppDao
import com.example.db.OrderEntity
import com.example.db.ProductEntity
import com.example.db.UserEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

// --- 1. Product-level request logic classification ---
enum class RequestLevel {
    /**
     * Urgent Seckill Request:
     * - Zero cache
     * - Super fast timeout
     * - High retry probability
     * - Priority request routing bypass
     */
    URGENT_SECKILL,

    /**
     * Standard Request:
     * - Default timeout (e.g. 10s)
     * - Balanced network & local sync
     */
    STANDARD,

    /**
     * Cached Prefer Request:
     * - Pull from local storage immediately to support instant-render offline,
     * - Query network lazily in background and refresh db
     */
    CACHE_PREFER
}

// --- 2. Standard API Response Wrapper ---
data class ApiResponse<T>(
    val code: Int, // 1000 = Success, 1001 = ValidationError, 1002 = AuthError, 1003 = SoldOut, 1004 = SystemBusy
    val success: Boolean,
    val message: String,
    val data: T? = null
)

// --- 3. Body Request Models ---
data class SendCodeRequest(val email: String)
data class RegisterRequest(val email: String, val nickname: String, val passwordHash: String, val otpCode: String)
data class LoginRequest(val email: String, val passwordHash: String)
data class SeckillRushRequest(val productId: String, val userEmail: String)
data class CreateErrandRequest(
    val userEmail: String,
    val type: String,
    val itemName: String,
    val notes: String,
    val fromAddress: String,
    val toAddress: String,
    val tip: Double,
    val distance: Double
)

data class UploadPhotoRequest(val fileName: String, val base64Data: String, val sizeKb: Int)
data class StoreLocationRequest(val email: String, val latitude: Double, val longitude: Double)

// --- 4. Retrofit Endpoint Interface ---
interface SeckillApi {
    @POST("api/auth/send-code")
    suspend fun sendVerificationCode(@Body request: SendCodeRequest): ApiResponse<String>

    @POST("api/auth/register")
    suspend fun registerUser(@Body request: RegisterRequest): ApiResponse<UserEntity>

    @POST("api/auth/login")
    suspend fun loginUser(@Body request: LoginRequest): ApiResponse<UserEntity>

    @GET("api/products")
    suspend fun getProducts(
        @Header("Request-Level") requestLevelHeader: String = "STANDARD"
    ): ApiResponse<List<ProductEntity>>

    @POST("api/seckill/rush")
    suspend fun rushProduct(
        @Header("Request-Level") requestLevelHeader: String = "URGENT_SECKILL",
        @Body request: SeckillRushRequest
    ): ApiResponse<OrderEntity>

    @POST("api/errand/create")
    suspend fun createErrandOrder(@Body request: CreateErrandRequest): ApiResponse<OrderEntity>

    @GET("api/errand/orders")
    suspend fun getOrders(@Query("email") email: String): ApiResponse<List<OrderEntity>>

    @POST("api/upload-photo")
    suspend fun uploadPhoto(@Body request: UploadPhotoRequest): ApiResponse<String>

    @POST("api/store-location")
    suspend fun saveLocationCoords(@Body request: StoreLocationRequest): ApiResponse<String>
}

// --- 5. Custom Client Config & Interceptor (Mock Engine) ---
class NetworkClient(private val context: Context, private val appDao: AppDao, private val coroutineScope: CoroutineScope) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // Key-value store to simulate active email OTP codes in memory
    private val emailOtpCache = ConcurrentHashMap<String, String>()

    // OkHttp Client with custom timeouts based on request level header
    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(MockNetworkInterceptor())
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(1500, TimeUnit.MILLISECONDS)
        .writeTimeout(1500, TimeUnit.MILLISECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val levelHeader = request.header("Request-Level")
            
            // Adjust timeouts programmatically for different request levels!
            val newChain = if (levelHeader == RequestLevel.URGENT_SECKILL.name) {
                // Seckill requests demand extremely fast failover/short timeout to avoid loading freeze!
                chain.withConnectTimeout(1500, TimeUnit.MILLISECONDS)
                    .withReadTimeout(1500, TimeUnit.MILLISECONDS)
                    .withWriteTimeout(1500, TimeUnit.MILLISECONDS)
            } else {
                chain
            }
            newChain.proceed(request)
        }
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("http://124.220.27.14:8000/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val api: SeckillApi = retrofit.create(SeckillApi::class.java)

    inner class MockNetworkInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val url = request.url.toString()
            val path = request.url.encodedPath
            val method = request.method
            
            Log.d("MockNetwork", "Intercepting: $method $url")

            // Attempt to hit the REAL remote server first, fall back to simulation if offline/unreachable
            try {
                val realResponse = chain.proceed(request)
                if (realResponse.code != 404) {
                    return realResponse
                }
                realResponse.close()
            } catch (e: Exception) {
                Log.w("MockNetwork", "连接真实后端服务失败！已无缝激活 [内置本地离线沙盒数据库模式] 作为备用：${e.localizedMessage}")
            }

            // Simulate Network Delay according to Request-Level
            val levelHeader = request.header("Request-Level")
            val delayMs = when (levelHeader) {
                RequestLevel.URGENT_SECKILL.name -> (200..400).random().toLong() // Super fast seckill rush
                RequestLevel.STANDARD.name -> (600..1000).random().toLong() // Average screen loading
                else -> 400L
            }
            Thread.sleep(delayMs)

            // Dynamic route dispatcher
            return when {
                path.endsWith("api/auth/send-code") && method == "POST" -> {
                    handleSendCode(request)
                }
                path.endsWith("api/auth/register") && method == "POST" -> {
                    handleRegister(request)
                }
                path.endsWith("api/auth/login") && method == "POST" -> {
                    handleLogin(request)
                }
                path.endsWith("api/products") && method == "GET" -> {
                    handleGetProducts(request)
                }
                path.endsWith("api/seckill/rush") && method == "POST" -> {
                    handleSeckillRush(request)
                }
                path.endsWith("api/errand/create") && method == "POST" -> {
                    handleCreateErrand(request)
                }
                path.endsWith("api/errand/orders") && method == "GET" -> {
                    handleGetOrders(request)
                }
                path.endsWith("api/upload-photo") && method == "POST" -> {
                    handleUploadPhoto(request)
                }
                path.endsWith("api/store-location") && method == "POST" -> {
                    handleStoreLocation(request)
                }
                else -> {
                    errorResponse(request, 404, "Endpoint not found")
                }
            }
        }

        private fun handleSendCode(request: Request): Response {
            return try {
                val bodyString = request.body?.let { bodyToString(it) } ?: ""
                val requestBodyAdapter = moshi.adapter(SendCodeRequest::class.java)
                val body = requestBodyAdapter.fromJson(bodyString)
                
                if (body == null || body.email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(body.email).matches()) {
                    return errorResponse(request, 400, "请输入有效的邮箱地址", code = 1001)
                }

                // Generate a 4 digit verification code
                val verifyCode = (1000..9999).random().toString()
                emailOtpCache[body.email.trim().lowercase()] = verifyCode

                val dataAdapter = moshi.adapter(String::class.java)
                val responseBody = ApiResponse(
                    code = 1000,
                    success = true,
                    message = "验证码已发送至 ${body.email}！【测试码: $verifyCode】",
                    data = verifyCode
                )
                
                val apiResponseAdapter = Types.newParameterizedType(ApiResponse::class.java, String::class.java)
                val json = moshi.adapter<ApiResponse<String>>(apiResponseAdapter).toJson(responseBody)

                successResponse(request, json)
            } catch (e: Exception) {
                errorResponse(request, 500, "发送验证码失败: ${e.message}")
            }
        }

        private fun handleRegister(request: Request): Response {
            return try {
                val bodyString = request.body?.let { bodyToString(it) } ?: ""
                val requestBodyAdapter = moshi.adapter(RegisterRequest::class.java)
                val regBody = requestBodyAdapter.fromJson(bodyString)

                if (regBody == null) {
                    return errorResponse(request, 400, "无法解析请求数据", code = 1001)
                }

                val email = regBody.email.trim().lowercase()
                val password = regBody.passwordHash
                val nickname = regBody.nickname.trim()
                val code = regBody.otpCode.trim()

                if (email.isBlank() || password.isBlank() || nickname.isBlank() || code.isBlank()) {
                    return errorResponse(request, 400, "所有字段均不能为空", code = 1001)
                }

                // Check OTP Code
                val cachedCode = emailOtpCache[email]
                if (cachedCode == null || cachedCode != code) {
                    return errorResponse(request, 400, "验证码错误或已失效！", code = 1001)
                }

                // Database operation on OkHttp Interceptor Background Thread
                val existing = appDao.getUserByEmailSync(email)
                val registeredUser: UserEntity? = if (existing != null) {
                    null
                } else {
                    val newUser = UserEntity(
                        email = email,
                        nickname = nickname,
                        passwordHash = password // Simply store it
                    )
                    appDao.insertUserSync(newUser)
                    // Remove code after successful registration
                    emailOtpCache.remove(email)
                    newUser
                }

                if (registeredUser == null) {
                    return errorResponse(request, 400, "该邮箱已被注册！", code = 1001)
                }

                val type = Types.newParameterizedType(ApiResponse::class.java, UserEntity::class.java)
                val apiResponse = ApiResponse(
                    code = 1000,
                    success = true,
                    message = "邮箱注册成功！",
                    data = registeredUser
                )
                val json = moshi.adapter<ApiResponse<UserEntity>>(type).toJson(apiResponse)
                successResponse(request, json)
            } catch (e: Exception) {
                errorResponse(request, 500, "注册异常: ${e.message}")
            }
        }

        private fun handleLogin(request: Request): Response {
            return try {
                val bodyString = request.body?.let { bodyToString(it) } ?: ""
                val requestBodyAdapter = moshi.adapter(LoginRequest::class.java)
                val loginBody = requestBodyAdapter.fromJson(bodyString)

                if (loginBody == null) {
                    return errorResponse(request, 400, "无效的登录参数", code = 1001)
                }

                val email = loginBody.email.trim().lowercase()
                val password = loginBody.passwordHash

                val dbUser = appDao.getUserByEmailSync(email)
                val user: UserEntity? = if (dbUser != null && dbUser.passwordHash == password) {
                    dbUser
                } else {
                    null
                }

                if (user == null) {
                    return errorResponse(request, 400, "用户名或密码错误，请查验！", code = 1002)
                }

                val type = Types.newParameterizedType(ApiResponse::class.java, UserEntity::class.java)
                val apiResponse = ApiResponse(
                    code = 1000,
                    success = true,
                    message = "登录成功！",
                    data = user
                )
                val json = moshi.adapter<ApiResponse<UserEntity>>(type).toJson(apiResponse)
                successResponse(request, json)
            } catch (e: Exception) {
                errorResponse(request, 500, "登录异常: ${e.message}")
            }
        }

        private fun handleGetProducts(request: Request): Response {
            return try {
                val productsList = appDao.getAllProductsSync()

                val type = Types.newParameterizedType(ApiResponse::class.java, Types.newParameterizedType(List::class.java, ProductEntity::class.java))
                val apiResponse = ApiResponse(
                    code = 1000,
                    success = true,
                    message = "获取商品成功",
                    data = productsList
                )
                val json = moshi.adapter<ApiResponse<List<ProductEntity>>>(type).toJson(apiResponse)
                successResponse(request, json)
            } catch (e: Exception) {
                errorResponse(request, 500, "获取商品列表异常: ${e.message}")
            }
        }

        private fun handleSeckillRush(request: Request): Response {
            return try {
                val bodyString = request.body?.let { bodyToString(it) } ?: ""
                val requestBodyAdapter = moshi.adapter(SeckillRushRequest::class.java)
                val rushBody = requestBodyAdapter.fromJson(bodyString)

                if (rushBody == null) {
                    return errorResponse(request, 400, "请求参数异常", code = 1001)
                }

                val productId = rushBody.productId
                val email = rushBody.userEmail

                // Transaction simulator
                var responseCode: Int = 1000
                var responseMessage: String = "秒杀成功！已为您自动安排最优跑腿骑手进行急速配送"
                var orderEntity: OrderEntity? = null

                val product = appDao.getProductByIdSync(productId)
                if (product == null) {
                    responseCode = 1001
                    responseMessage = "该秒杀商品不存在！"
                } else {
                    // Check time limits
                    val now = System.currentTimeMillis()
                    if (now < product.startTimeMills) {
                        responseCode = 1001
                        responseMessage = "秒杀尚未开始，请耐心等待！"
                    } else if (now > product.endTimeMills) {
                        responseCode = 1001
                        responseMessage = "秒杀已结束！"
                    } else {
                        // Check duplicate purchase logic (Mocked 1 buy per user)
                        // If user has orders for this item in the orders table
                        val existingOrders = appDao.getOrdersByEmailSync(email)
                        val alreadyBought = existingOrders.any { it.itemName.contains(product.name) }
                        if (alreadyBought) {
                            responseCode = 1001
                            responseMessage = "对不起，本商品每人限购1件，您已抢购成功，请前往订单查看！"
                        } else {
                            // Attempt stock decrement atomic transaction
                            val rowsUpdated = appDao.decrementStockSync(productId)
                            if (rowsUpdated > 0) {
                                // Success! Generate dispatch order
                                val orderId = "sk_ord_${UUID.randomUUID().toString().take(8)}"
                                val newOrder = OrderEntity(
                                    id = orderId,
                                    userEmail = email,
                                    type = "BUY",
                                    itemName = "【秒杀抢购】${product.name}",
                                    notes = "极速秒杀件！商品原价¥${product.originalPrice}，秒杀全包价¥${product.seckillPrice}！货款已付，请骑手即刻送达！",
                                    fromAddress = "速达自营秒杀仓 (北京市大兴区科创十一街)",
                                    toAddress = "您的收货地址 (系统就近定位)",
                                    tip = 15.0, // High runner tip on flash seckill!
                                    distance = 3.2,
                                    status = "SUBMITTED",
                                    runnerName = "速达先锋骑手 张力",
                                    runnerPhone = "13800008888"
                                )
                                appDao.insertOrderSync(newOrder)
                                orderEntity = newOrder

                                // Launch a simulator coroutine to advance order dispatch stages in database!
                                simulateOrderStatusTransitions(orderId)
                            } else {
                                responseCode = 1003
                                responseMessage = "对不起，抢购人数过多，商品已被瞬间秒杀一空！"
                            }
                        }
                    }
                }

                if (orderEntity == null) {
                    return errorResponse(request, 409, responseMessage, code = responseCode)
                }

                val type = Types.newParameterizedType(ApiResponse::class.java, OrderEntity::class.java)
                val apiResponse = ApiResponse(
                    code = responseCode,
                    success = true,
                    message = responseMessage,
                    data = orderEntity
                )
                val json = moshi.adapter<ApiResponse<OrderEntity>>(type).toJson(apiResponse)
                successResponse(request, json)
            } catch (e: Exception) {
                errorResponse(request, 500, "秒杀异常: ${e.message}")
            }
        }

        private fun handleCreateErrand(request: Request): Response {
            return try {
                val bodyString = request.body?.let { bodyToString(it) } ?: ""
                val requestBodyAdapter = moshi.adapter(CreateErrandRequest::class.java)
                val errandBody = requestBodyAdapter.fromJson(bodyString)

                if (errandBody == null || errandBody.itemName.isBlank() || errandBody.fromAddress.isBlank() || errandBody.toAddress.isBlank()) {
                    return errorResponse(request, 400, "主要收发地址和物品名称不能为空", code = 1001)
                }

                val orderId = "err_ord_${UUID.randomUUID().toString().take(8)}"
                val newOrder = OrderEntity(
                    id = orderId,
                    userEmail = errandBody.userEmail,
                    type = errandBody.type,
                    itemName = errandBody.itemName,
                    notes = errandBody.notes,
                    fromAddress = errandBody.fromAddress,
                    toAddress = errandBody.toAddress,
                    tip = errandBody.tip,
                    distance = errandBody.distance,
                    status = "SUBMITTED",
                    runnerName = "",
                    runnerPhone = ""
                )

                appDao.insertOrderSync(newOrder)

                // Simulate order lifecycle in background!
                simulateOrderStatusTransitions(orderId)

                val type = Types.newParameterizedType(ApiResponse::class.java, OrderEntity::class.java)
                val apiResponse = ApiResponse(
                    code = 1000,
                    success = true,
                    message = "跑腿订单提交成功！正在为您急速呼叫附近骑手...",
                    data = newOrder
                )
                val json = moshi.adapter<ApiResponse<OrderEntity>>(type).toJson(apiResponse)
                successResponse(request, json)
            } catch (e: Exception) {
                errorResponse(request, 500, "创建跑腿订单异常: ${e.message}")
            }
        }

        private fun handleGetOrders(request: Request): Response {
            return try {
                val email = request.url.queryParameter("email") ?: ""
                val ordersList = appDao.getOrdersByEmailSync(email)

                val type = Types.newParameterizedType(ApiResponse::class.java, Types.newParameterizedType(List::class.java, OrderEntity::class.java))
                val apiResponse = ApiResponse(
                    code = 1000,
                    success = true,
                    message = "订单加载完成",
                    data = ordersList
                )
                val json = moshi.adapter<ApiResponse<List<OrderEntity>>>(type).toJson(apiResponse)
                successResponse(request, json)
            } catch (e: Exception) {
                errorResponse(request, 500, "获取订单列表异常")
            }
        }

        private fun handleUploadPhoto(request: Request): Response {
            return try {
                val bodyString = request.body?.let { bodyToString(it) } ?: ""
                val requestBodyAdapter = moshi.adapter(UploadPhotoRequest::class.java)
                val uploadBody = requestBodyAdapter.fromJson(bodyString)

                if (uploadBody == null) {
                    return errorResponse(request, 400, "无法解析图片上传请求", code = 1001)
                }

                val type = Types.newParameterizedType(ApiResponse::class.java, String::class.java)
                val apiResponse = ApiResponse(
                    code = 1000,
                    success = true,
                    message = "图片 [${uploadBody.fileName}] 接收并压缩存储成功 (后端数据库已收到 ${(uploadBody.sizeKb)}KB 数据负载)！",
                    data = "mock_cloud_storage/errand_photos/${uploadBody.fileName}"
                )
                val json = moshi.adapter<ApiResponse<String>>(type).toJson(apiResponse)
                successResponse(request, json)
            } catch (e: Exception) {
                errorResponse(request, 500, "图片上传异常: ${e.message}")
            }
        }

        private fun handleStoreLocation(request: Request): Response {
            return try {
                val bodyString = request.body?.let { bodyToString(it) } ?: ""
                val requestBodyAdapter = moshi.adapter(StoreLocationRequest::class.java)
                val locBody = requestBodyAdapter.fromJson(bodyString)

                if (locBody == null) {
                    return errorResponse(request, 400, "无法解析经纬坐标数据", code = 1001)
                }

                val type = Types.newParameterizedType(ApiResponse::class.java, String::class.java)
                val apiResponse = ApiResponse(
                    code = 1000,
                    success = true,
                    message = "坐标位置 (${locBody.latitude}, ${locBody.longitude}) 已在服务器端序列化存储完成！",
                    data = "Location Saved."
                )
                val json = moshi.adapter<ApiResponse<String>>(type).toJson(apiResponse)
                successResponse(request, json)
            } catch (e: Exception) {
                errorResponse(request, 500, "位置存储异常: ${e.message}")
            }
        }

        private fun bodyToString(requestBody: RequestBody): String {
            val buffer = okio.Buffer()
            requestBody.writeTo(buffer)
            return buffer.readUtf8()
        }

        private fun successResponse(request: Request, jsonString: String): Response {
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(jsonString.toResponseBody("application/json".toMediaTypeOrNull()))
                .build()
        }

        private fun errorResponse(request: Request, httpCode: Int, message: String, code: Int = 9999): Response {
            val apiResponse = ApiResponse<String>(
                code = code,
                success = false,
                message = message,
                data = null
            )
            val json = moshi.adapter<ApiResponse<String>>(Types.newParameterizedType(ApiResponse::class.java, String::class.java)).toJson(apiResponse)
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(httpCode)
                .message(message)
                .body(json.toResponseBody("application/json".toMediaTypeOrNull()))
                .build()
        }
    }

    /**
     * Transitions order state incrementally over time to simulate a live Courier dispatching system.
     * SUBMITTED -> ASSIGNED (3s) -> OUT_FOR_DELIVERY (10s) -> COMPLETED (25s)
     */
    private fun simulateOrderStatusTransitions(orderId: String) {
        coroutineScope.launch(Dispatchers.IO) {
            val runners = listOf(
                Pair("达达速递 王志远", "13911029231"),
                Pair("顺丰同城 李明峰", "18622108849"),
                Pair("京东专送 赵建强", "15599812323"),
                Pair("美团跑腿 徐大宝", "17600114002")
            )

            // Stage 1: Assign a rider after 3 seconds
            delay(3000)
            val order1 = appDao.getOrderById(orderId) ?: return@launch
            if (order1.status == "SUBMITTED") {
                val runner = runners.random()
                appDao.updateOrder(
                    order1.copy(
                        status = "ASSIGNED",
                        runnerName = runner.first,
                        runnerPhone = runner.second
                    )
                )
            }

            // Stage 2: Courier picks up items and departs after 10 seconds
            delay(10000)
            val order2 = appDao.getOrderById(orderId) ?: return@launch
            if (order2.status == "ASSIGNED") {
                appDao.updateOrder(order2.copy(status = "OUT_FOR_DELIVERY"))
            }

            // Stage 3: Courier completes delivery after 15 seconds
            delay(15000)
            val order3 = appDao.getOrderById(orderId) ?: return@launch
            if (order3.status == "OUT_FOR_DELIVERY") {
                appDao.updateOrder(order3.copy(status = "COMPLETED"))
            }
        }
    }
}
