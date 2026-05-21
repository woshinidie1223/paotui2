package com.example.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // --- User Queries ---
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    fun getUserByEmailSync(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    fun getUserByEmailFlow(email: String): Flow<UserEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertUserSync(user: UserEntity)

    // --- Product Queries ---
    @Query("SELECT * FROM products ORDER BY isSeckill DESC, id ASC")
    fun getAllProductsFlow(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY isSeckill DESC, id ASC")
    fun getAllProductsSync(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: String): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    fun getProductByIdSync(id: String): ProductEntity?

    @Query("SELECT * FROM products WHERE isSeckill = 1")
    suspend fun getSeckillProducts(): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("UPDATE products SET stockCount = stockCount - 1 WHERE id = :id AND stockCount > 0")
    suspend fun decrementStock(id: String): Int

    @Query("UPDATE products SET stockCount = stockCount - 1 WHERE id = :id AND stockCount > 0")
    fun decrementStockSync(id: String): Int

    // --- Order Queries ---
    @Query("SELECT * FROM errand_orders WHERE userEmail = :email ORDER BY createdAt DESC")
    fun getOrdersByEmailFlow(email: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM errand_orders WHERE userEmail = :email ORDER BY createdAt DESC")
    fun getOrdersByEmailSync(email: String): List<OrderEntity>

    @Query("SELECT * FROM errand_orders WHERE id = :id LIMIT 1")
    fun getOrderByIdFlow(id: String): Flow<OrderEntity?>

    @Query("SELECT * FROM errand_orders WHERE id = :id LIMIT 1")
    suspend fun getOrderById(id: String): OrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrderSync(order: OrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrders(orders: List<OrderEntity>)

    @Update
    suspend fun updateOrder(order: OrderEntity)
}
