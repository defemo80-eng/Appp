package com.example

import android.app.Application
import com.example.data.download.DownloadEngine
import com.example.data.local.DeiPlayDatabase
import com.example.data.local.UserPreferences
import com.example.data.remote.WorldCatalogSync
import com.example.data.repository.DeiPlayRepository
import com.example.player.DeiPlayPlayerController

class DeiPlayApplication : Application() {
    val database: DeiPlayDatabase by lazy { DeiPlayDatabase.getInstance(this) }
    val preferences: UserPreferences by lazy { UserPreferences(this) }
    val worldCatalogSync: WorldCatalogSync by lazy { WorldCatalogSync(database) }
    val downloadEngine: DownloadEngine by lazy { DownloadEngine(this, database.downloadDao()) }
    val repository: DeiPlayRepository by lazy {
        DeiPlayRepository(
            context = this,
            db = database,
            preferences = preferences,
            worldCatalogSync = worldCatalogSync,
            downloadEngine = downloadEngine
        )
    }
    val playerController: DeiPlayPlayerController by lazy {
        DeiPlayPlayerController(this, repository)
    }
}
