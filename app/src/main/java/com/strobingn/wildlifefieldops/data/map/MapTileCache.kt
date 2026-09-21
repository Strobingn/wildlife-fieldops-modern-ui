package com.strobingn.wildlifefieldops.data.map

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class TilePrefetchResult(
    val requested: Int,
    val alreadyCached: Int,
    val downloaded: Int,
    val failed: Int
) {
    val usable: Int get() = alreadyCached + downloaded
}

/**
 * On-device PNG store for service-area map tiles.
 * Tiles are fetched from a public Carto/OSM light basemap (grayscale-friendly)
 * and served back through [CachedMapTileProvider] on the existing GoogleMap.
 */
@Singleton
class MapTileCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val root: File
        get() = File(context.filesDir, TILE_DIR).apply { mkdirs() }

    fun tileFile(coord: TileCoord): File =
        File(root, "${coord.zoom}/${coord.x}/${coord.y}.png")

    fun hasTile(coord: TileCoord): Boolean {
        val file = tileFile(coord)
        return file.isFile && file.length() > MIN_TILE_BYTES
    }

    fun readTile(coord: TileCoord): ByteArray? {
        val file = tileFile(coord)
        if (!file.isFile || file.length() < MIN_TILE_BYTES) return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    fun writeTile(coord: TileCoord, bytes: ByteArray) {
        if (bytes.size < MIN_TILE_BYTES) return
        val file = tileFile(coord)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(file)) {
            file.writeBytes(bytes)
            tmp.delete()
        }
    }

    fun cachedTileCount(): Int {
        if (!root.exists()) return 0
        return root.walkTopDown().count { it.isFile && it.extension == "png" && it.length() > MIN_TILE_BYTES }
    }

    fun getOrFetch(coord: TileCoord, networkAvailable: Boolean): ByteArray? {
        readTile(coord)?.let { return it }
        if (!networkAvailable) return null
        val remote = fetchRemote(coord) ?: return null
        runCatching { writeTile(coord, remote) }
        return remote
    }

    suspend fun prefetch(
        tiles: List<TileCoord>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): TilePrefetchResult = withContext(Dispatchers.IO) {
        if (tiles.isEmpty()) {
            return@withContext TilePrefetchResult(0, 0, 0, 0)
        }
        var already = 0
        val missing = ArrayList<TileCoord>()
        tiles.forEach { coord ->
            if (hasTile(coord)) already++ else missing += coord
        }
        var downloaded = 0
        var failed = 0
        val lock = Any()
        val total = tiles.size
        onProgress(already, total)
        if (missing.isNotEmpty()) {
            val semaphore = Semaphore(FETCH_PARALLELISM)
            coroutineScope {
                missing.map { coord ->
                    async {
                        semaphore.withPermit {
                            val bytes = fetchRemote(coord)
                            synchronized(lock) {
                                if (bytes != null) {
                                    runCatching { writeTile(coord, bytes) }
                                    downloaded++
                                } else {
                                    failed++
                                }
                                onProgress(already + downloaded + failed, total)
                            }
                        }
                    }
                }.awaitAll()
            }
        }
        TilePrefetchResult(
            requested = tiles.size,
            alreadyCached = already,
            downloaded = downloaded,
            failed = failed
        )
    }

    fun fetchRemote(coord: TileCoord): ByteArray? {
        val url = tileUrl(coord)
        return runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "image/png")
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    Log.w(TAG, "tile HTTP $code for $url")
                    return@runCatching null
                }
                val bytes = connection.inputStream.use { it.readBytes() }
                if (bytes.size < MIN_TILE_BYTES) null else bytes
            } finally {
                connection.disconnect()
            }
        }.onFailure {
            Log.w(TAG, "tile fetch failed ${coord.zoom}/${coord.x}/${coord.y}: ${it.message}")
        }.getOrNull()
    }

    companion object {
        private const val TAG = "MapTileCache"
        const val TILE_DIR = "map_tiles"
        const val MIN_TILE_BYTES = 32
        const val TIMEOUT_MS = 8_000
        const val FETCH_PARALLELISM = 4
        const val USER_AGENT = "WildlifeFieldOps/2.3 (offline tile cache; +https://github.com/Strobingn/wildlife-fieldops-modern-ui)"

        fun tileUrl(coord: TileCoord): String {
            val host = when ((coord.x + coord.y) % 3) {
                0 -> "a"
                1 -> "b"
                else -> "c"
            }
            return "https://$host.basemaps.cartocdn.com/light_all/${coord.zoom}/${coord.x}/${coord.y}.png"
        }
    }
}
