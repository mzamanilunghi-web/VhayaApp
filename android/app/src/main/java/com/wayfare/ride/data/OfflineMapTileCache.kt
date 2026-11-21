package com.wayfare.ride.data

import android.content.Context
import com.wayfare.ride.domain.DriverMapEngine
import com.wayfare.ride.domain.OfflineCorridorPack
import com.wayfare.ride.domain.OfflineCorridorPackStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object OfflineMapTileCache {

    private const val CACHE_DIR_NAME = "osm_tile_cache"
    private const val TILE_BASE_URL = "https://tile.openstreetmap.org"
    private const val USER_AGENT = "VhayaAndroid/1.0"

    fun cacheRootDir(context: Context): File =
        File(context.applicationContext.filesDir, CACHE_DIR_NAME)

    fun tileFileFor(context: Context, zoom: Int, x: Int, y: Int): File =
        File(cacheRootDir(context), "$zoom/$x/$y.png")

    fun getCachedTileFile(context: Context, zoom: Int, x: Int, y: Int): File? {
        val file = tileFileFor(context, zoom, x, y)
        return if (file.exists() && file.length() > 0L) file else null
    }

    suspend fun prefetchCorridorPack(
        context: Context,
        pack: OfflineCorridorPack,
        onProgress: (downloaded: Int, total: Int) -> Unit = { _, _ -> }
    ): OfflineCorridorPackStatus = withContext(Dispatchers.IO) {
        val tiles = DriverMapEngine.computeTilesForCorridor(
            origin = pack.origin,
            destination = pack.destination
        )
        val total = tiles.size
        var readyCount = 0
        onProgress(0, total)

        for (tile in tiles) {
            val existing = getCachedTileFile(context, tile.zoom, tile.x, tile.y)
            if (existing != null) {
                readyCount++
                onProgress(readyCount, total)
                continue
            }
            val downloaded = downloadSingleTile(
                context = context,
                zoom = tile.zoom,
                x = tile.x,
                y = tile.y
            )
            if (downloaded) {
                readyCount++
            }
            onProgress(readyCount, total)
        }

        OfflineCorridorPackStatus(
            packId = pack.id,
            downloadedTiles = readyCount,
            totalTiles = total,
            cachedAtMs = System.currentTimeMillis()
        )
    }

    private fun downloadSingleTile(
        context: Context,
        zoom: Int,
        x: Int,
        y: Int
    ): Boolean {
        val target = tileFileFor(context, zoom, x, y)
        return runCatching {
            val conn = (URL("$TILE_BASE_URL/$zoom/$x/$y.png").openConnection() as HttpURLConnection).apply {
                connectTimeout = 4_500
                readTimeout = 4_500
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching false
                val bytes = conn.inputStream.use { it.readBytes() }
                if (bytes.size < 64) return@runCatching false
                target.parentFile?.mkdirs()
                val tmp = File(target.parentFile, "${target.name}.tmp")
                tmp.writeBytes(bytes)
                if (target.exists()) target.delete()
                tmp.renameTo(target)
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
    }

    fun saveTileBytesForTesting(context: Context, zoom: Int, x: Int, y: Int, bytes: ByteArray) {
        val target = tileFileFor(context, zoom, x, y)
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
    }

    fun stats(context: Context): Pair<Int, Long> {
        val root = cacheRootDir(context)
        if (!root.exists()) return 0 to 0L
        var count = 0
        var bytes = 0L
        root.walkTopDown()
            .filter { it.isFile && it.extension == "png" }
            .forEach { f ->
                count++
                bytes += f.length()
            }
        return count to bytes
    }

    fun clear(context: Context) {
        runCatching { cacheRootDir(context).deleteRecursively() }
    }
}
