package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val email: String,
    val nickname: String,
    val passwordHash: String,
    val points: Double = 100.0, // Give 100 points by default for convenience
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val imageUrl: String,
    val originalPrice: Double,
    val seckillPrice: Double,
    val stockCount: Int,
    val totalStock: Int,
    val isSeckill: Boolean,
    val startTimeMills: Long, // Start time of seckill
    val endTimeMills: Long,
    val category: String, // "seckill", "hardware", "food", "digital"
    val description: String = ""
)

@Entity(tableName = "errand_orders")
data class OrderEntity(
    @PrimaryKey val id: String,
    val userEmail: String,
    val type: String, // "DELIVER" (帮我送), "BUY" (帮我买), "PICK_UP" (帮我取)
    val itemName: String,
    val notes: String = "",
    val fromAddress: String,
    val toAddress: String,
    val tip: Double,
    val distance: Double,
    val status: String, // "SUBMITTED", "ASSIGNED", "OUT_FOR_DELIVERY", "COMPLETED", "CANCELLED"
    val runnerName: String = "",
    val runnerPhone: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
