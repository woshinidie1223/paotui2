package com.example

import android.app.Application
import com.example.db.AppDatabase
import com.example.db.AppRepository
import com.example.network.NetworkClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ErrandApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob())

    val database by lazy { AppDatabase.getDatabase(this, applicationScope) }
    val networkClient by lazy { NetworkClient(this, database.appDao(), applicationScope) }
    val repository by lazy { AppRepository(database.appDao(), networkClient.api) }

    override fun onCreate() {
        super.onCreate()
        // Prefetch products right away to ensure they populate promptly
        applicationScope.launch(Dispatchers.IO) {
            com.example.location.TencentMapHelper.verifyTencentApiKeyDiagnostics()
        }
    }
}
