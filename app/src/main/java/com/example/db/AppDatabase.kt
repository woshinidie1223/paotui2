package com.example.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [UserEntity::class, ProductEntity::class, OrderEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jingmiao_database"
                )
                .addCallback(DatabaseCallback(scope))
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDatabase(database.appDao())
                }
            }
        }

        suspend fun populateDatabase(dao: AppDao) {
            // Check if products already exist to be safe
            val currentTime = System.currentTimeMillis()

            val initialProducts = listOf(
                // Seckill products (IsSeckill = true)
                ProductEntity(
                    id = "sk_1",
                    name = "M4 Max 芯片 MacBook Pro - 秒杀热卖",
                    imageUrl = "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?auto=format&fit=crop&w=300&q=80",
                    originalPrice = 12999.00,
                    seckillPrice = 9.90, // Ultimate high ratio seckill!
                    stockCount = 3,      // Very competitive!
                    totalStock = 3,
                    isSeckill = true,
                    startTimeMills = currentTime + 10000, // Starts in 10 seconds!
                    endTimeMills = currentTime + 300000,  // Lasts for 5 minutes
                    category = "seckill",
                    description = "正品国行，Apple 2026年顶配笔记本。京东秒杀特供，限量3台，秒杀价仅需9.9元！"
                ),
                ProductEntity(
                    id = "sk_2",
                    name = "iPhone 18 Pro Max 暗影钛金属 - 限量秒杀",
                    imageUrl = "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=300&q=80",
                    originalPrice = 10999.00,
                    seckillPrice = 1.00,
                    stockCount = 0, // Starts as already sold out, to test state!
                    totalStock = 5,
                    isSeckill = true,
                    startTimeMills = currentTime - 60000, // Underway or ended
                    endTimeMills = currentTime - 30000,
                    category = "seckill",
                    description = "京东秒杀超能囤货季，已结束。全网疯抢，手慢无！"
                ),
                ProductEntity(
                    id = "sk_3",
                    name = "华为 Mate XT 三折叠屏幕手机 - 超人气秒杀",
                    imageUrl = "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=300&q=80",
                    originalPrice = 21999.00,
                    seckillPrice = 199.00,
                    stockCount = 10,
                    totalStock = 10,
                    isSeckill = true,
                    startTimeMills = currentTime + 30000, // Starts in 30s
                    endTimeMills = currentTime + 600000,
                    category = "seckill",
                    description = "首款量产三折叠屏。惊艳黑科技，跑腿抢购优选，极速必达。"
                ),
                ProductEntity(
                    id = "sk_4",
                    name = "索尼 PlayStation 5 Pro 国行主机",
                    imageUrl = "https://images.unsplash.com/photo-1606813907291-d86efa9b94db?auto=format&fit=crop&w=300&q=80",
                    originalPrice = 5499.00,
                    seckillPrice = 2999.00,
                    stockCount = 15,
                    totalStock = 15,
                    isSeckill = true,
                    startTimeMills = currentTime - 5000, // Has already started!
                    endTimeMills = currentTime + 120000,
                    category = "seckill",
                    description = "索尼次世代高性能主机，流畅运行4K 120Hz游戏。现在开抢！"
                ),

                // Normal products (IsSeckill = false)
                ProductEntity(
                    id = "norm_1",
                    name = "特仑苏 纯牛奶 250ml*16盒 整箱装",
                    imageUrl = "https://images.unsplash.com/photo-1550583724-b2692b85b150?auto=format&fit=crop&w=300&q=80",
                    originalPrice = 65.00,
                    seckillPrice = 55.00,
                    stockCount = 999,
                    totalStock = 999,
                    isSeckill = false,
                    startTimeMills = 0,
                    endTimeMills = 0,
                    category = "food",
                    description = "高端好奶，自然香浓。每一滴都是大自然的自然馈赠。"
                ),
                ProductEntity(
                    id = "norm_2",
                    name = "京东巨无霸 混合坚果 750g 坚果零食",
                    imageUrl = "https://images.unsplash.com/photo-1598063412599-2470776b26ec?auto=format&fit=crop&w=300&q=80",
                    originalPrice = 99.00,
                    seckillPrice = 79.00,
                    stockCount = 200,
                    totalStock = 200,
                    isSeckill = false,
                    startTimeMills = 0,
                    endTimeMills = 0,
                    category = "food",
                    description = "富含多种高能量优质坚果，无添加色素，健康休闲零食。"
                ),
                ProductEntity(
                    id = "norm_3",
                    name = "飞利浦 电动牙刷 智能声波震动 HX6730",
                    imageUrl = "https://images.unsplash.com/photo-1559591937-e1700db8488e?auto=format&fit=crop&w=300&q=80",
                    originalPrice = 329.00,
                    seckillPrice = 289.00,
                    stockCount = 150,
                    totalStock = 150,
                    isSeckill = false,
                    startTimeMills = 0,
                    endTimeMills = 0,
                    category = "hardware",
                    description = "声波震动洁齿，深入齿缝，亮白牙齿，温和呵护牙龈。"
                )
            )
            dao.insertProducts(initialProducts)
        }
    }
}
