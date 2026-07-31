package com.creativem.toblauncher

import android.content.Context
import java.io.File

object OfflineMapManager {

    private const val MAP_FILE_NAME = "region_offline.map"
    private const val POI_FILE_NAME = "region_offline.poi"

    fun getMapFile(context: Context): File = File(context.filesDir, MAP_FILE_NAME)
    fun getPoiFile(context: Context): File = File(context.filesDir, POI_FILE_NAME)

    fun isMapDownloaded(context: Context): Boolean {
        val file = getMapFile(context)
        return file.exists() && file.length() > (1024 * 1024)
    }

    fun isPoiDownloaded(context: Context): Boolean {
        val file = getPoiFile(context)
        return file.exists() && file.length() > (1024 * 1024)
    }
}