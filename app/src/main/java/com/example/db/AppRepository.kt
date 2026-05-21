package com.example.db

import com.example.network.RequestLevel
import com.example.network.SeckillApi
import com.example.network.ApiResponse
import com.example.network.SendCodeRequest
import com.example.network.RegisterRequest
import com.example.network.LoginRequest
import com.example.network.SeckillRushRequest
import com.example.network.CreateErrandRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class AppRepository(
    private val appDao: AppDao,
    private val api: SeckillApi
) {
    // Current Active Local Orders for real-time reactivity
    fun getOrdersFlow(email: String): Flow<List<OrderEntity>> = appDao.getOrdersByEmailFlow(email)

    // Current logged in user reactive observer
    fun getUserFlow(email: String): Flow<UserEntity?> = appDao.getUserByEmailFlow(email)

    suspend fun updateUser(user: UserEntity) {
        appDao.insertUser(user)
    }

    // Current Active Live Products Reactive Feed
    fun getProductsFlow(): Flow<List<ProductEntity>> = appDao.getAllProductsFlow()

    fun getOrderByIdFlow(orderId: String): Flow<OrderEntity?> = appDao.getOrderByIdFlow(orderId)

    /**
     * Sends an email OTP code (Simulated backend trigger)
     */
    suspend fun sendVerificationCode(email: String): ApiResponse<String> {
        return try {
            api.sendVerificationCode(SendCodeRequest(email))
        } catch (e: Exception) {
            ApiResponse(code = 9999, success = false, message = "网络请求失败，请检查连接: ${e.localizedMessage}")
        }
    }

    /**
     * Registers a new account
     */
    suspend fun registerUser(email: String, nickname: String, passwordHash: String, otpCode: String): ApiResponse<UserEntity> {
        return try {
            api.registerUser(RegisterRequest(email, nickname, passwordHash, otpCode))
        } catch (e: Exception) {
            ApiResponse(code = 9999, success = false, message = "网络请求失败: ${e.localizedMessage}")
        }
    }

    /**
     * Standard Login
     */
    suspend fun loginUser(email: String, passwordHash: String): ApiResponse<UserEntity> {
        return try {
            api.loginUser(LoginRequest(email, passwordHash))
        } catch (e: Exception) {
            ApiResponse(code = 9999, success = false, message = "登录网络超时: ${e.localizedMessage}")
        }
    }

    /**
     * Refreshes products from the network according to request priority level
     */
    suspend fun refreshProducts(requestLevel: RequestLevel): ApiResponse<List<ProductEntity>> {
        return try {
            // Tell Retrofit client what class/level we want
            val response = api.getProducts(requestLevelHeader = requestLevel.name)
            if (response.success && response.data != null) {
                appDao.insertProducts(response.data)
            }
            response
        } catch (e: Exception) {
            ApiResponse(code = 9999, success = false, message = "获取网络列表失败: ${e.localizedMessage}")
        }
    }

    /**
     * Submits an urgent seckill rush-to-buy request with fast timeout priority
     */
    suspend fun rushSeckillProduct(productId: String, userEmail: String): ApiResponse<OrderEntity> {
        return try {
            val response = api.rushProduct(
                requestLevelHeader = RequestLevel.URGENT_SECKILL.name,
                request = SeckillRushRequest(productId, userEmail)
            )
            // If the seckill was a success, we update the product state locally too so it reflects immediately!
            if (response.success && response.data != null) {
                val prod = appDao.getProductById(productId)
                if (prod != null) {
                    val nextStock = (prod.stockCount - 1).coerceAtLeast(0)
                    appDao.updateProduct(prod.copy(stockCount = nextStock))
                }
            }
            response
        } catch (e: java.net.SocketTimeoutException) {
            ApiResponse(code = 1004, success = false, message = "抢购人数暴增，通道过于拥挤，请稍后重试！")
        } catch (e: Exception) {
            ApiResponse(code = 9999, success = false, message = "抢购失败: ${e.localizedMessage}")
        }
    }

    /**
     * Generates standard custom running errand order (Buy for me/Send/Pick)
     */
    suspend fun createErrandOrder(
        userEmail: String,
        type: String,
        itemName: String,
        notes: String,
        fromAddress: String,
        toAddress: String,
        tip: Double,
        distance: Double
    ): ApiResponse<OrderEntity> {
        return try {
            val req = CreateErrandRequest(
                userEmail = userEmail,
                type = type,
                itemName = itemName,
                notes = notes,
                fromAddress = fromAddress,
                toAddress = toAddress,
                tip = tip,
                distance = distance
            )
            val res = api.createErrandOrder(req)
            if (res.success && res.data != null) {
                appDao.insertOrder(res.data)
            }
            res
        } catch (e: Exception) {
            ApiResponse(code = 9999, success = false, message = "下单网络失败: ${e.localizedMessage}")
        }
    }

    /**
     * Pulls active order status list from remote server API and caches in Local database
     */
    suspend fun refreshOrders(userEmail: String): ApiResponse<List<OrderEntity>> {
        return try {
            val response = api.getOrders(userEmail)
            if (response.success && response.data != null) {
                appDao.insertOrders(response.data)
            }
            response
        } catch (e: Exception) {
            ApiResponse(code = 9999, success = false, message = "同步服务器订单失败: ${e.localizedMessage}")
        }
    }

    suspend fun uploadPhoto(fileName: String, base64Data: String, sizeKb: Int): ApiResponse<String> {
        return try {
            api.uploadPhoto(com.example.network.UploadPhotoRequest(fileName, base64Data, sizeKb))
        } catch (e: Exception) {
            ApiResponse(code = 9999, success = false, message = "图片服务器上传失败: ${e.localizedMessage}")
        }
    }

    suspend fun storeLocationCoords(email: String, latitude: Double, longitude: Double): ApiResponse<String> {
        return try {
            api.saveLocationCoords(com.example.network.StoreLocationRequest(email, latitude, longitude))
        } catch (e: Exception) {
            ApiResponse(code = 9999, success = false, message = "同步经纬坐标到服务器失败: ${e.localizedMessage}")
        }
    }
}
