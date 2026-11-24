package com.xperience.player.service.cast

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight, zero-dependency HTTP/1.1 LAN media micro-server built on [ServerSocket].
 *
 * Allows Chromecast receivers on the same Wi-Fi network to stream local audio files,
 * Android `content://` MediaStore tracks, cached YouTube Music audio files, and local
 * album artwork with full RFC 7233 `Range` (`206 Partial Content`) seeking and CORS headers.
 */
class LocalCastMediaServer(
    private val bindHost: String = "0.0.0.0",
    private val requestedPort: Int = 0,
    private val hostAddressProvider: () -> String? = { resolveLanIpAddress() },
    private val maxRegisteredSources: Int = MAX_REGISTERED_SOURCES
) {
    /**
     * Abstraction over a streamable media asset registered on this server.
     */
    sealed interface LocalMediaSource {
        val mimeType: String
        val suggestedFileName: String

        /** Total byte length, or `-1L` if unavailable. */
        fun contentLength(): Long

        /** Opens an [InputStream] positioned at [offsetBytes]. */
        fun openAt(offsetBytes: Long): InputStream

        data class FileSource(
            val file: File,
            override val mimeType: String,
            override val suggestedFileName: String = file.name
        ) : LocalMediaSource {
            override fun contentLength(): Long = if (file.exists() && file.isFile) file.length() else -1L

            override fun openAt(offsetBytes: Long): InputStream {
                if (offsetBytes <= 0L) {
                    return BufferedInputStream(FileInputStream(file), IO_BUFFER_BYTES)
                }
                val raf = RandomAccessFile(file, "r")
                return try {
                    raf.seek(offsetBytes)
                    RandomAccessFileInputStream(raf)
                } catch (e: Throwable) {
                    runCatching { raf.close() }
                    throw e
                }
            }
        }

        data class ContentUriSource(
            val contentResolver: ContentResolver,
            val uri: Uri,
            override val mimeType: String,
            override val suggestedFileName: String = "track.m4a",
            private val knownSizeBytes: Long = -1L
        ) : LocalMediaSource {
            override fun contentLength(): Long {
                if (knownSizeBytes >= 0L) return knownSizeBytes
                return runCatching {
                    contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                        afd.length.takeIf { it >= 0L }
                            ?: afd.parcelFileDescriptor.statSize.takeIf { it >= 0L }
                            ?: -1L
                    } ?: -1L
                }.getOrDefault(-1L)
            }

            override fun openAt(offsetBytes: Long): InputStream {
                val raw = contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Unable to open ContentResolver stream for $uri")
                return try {
                    if (offsetBytes > 0L) {
                        skipFully(raw, offsetBytes)
                    }
                    BufferedInputStream(raw, IO_BUFFER_BYTES)
                } catch (t: Throwable) {
                    runCatching { raw.close() }
                    throw t
                }
            }
        }

        data class ByteArraySource(
            val bytes: ByteArray,
            override val mimeType: String,
            override val suggestedFileName: String = "media.bin"
        ) : LocalMediaSource {
            override fun contentLength(): Long = bytes.size.toLong()

            override fun openAt(offsetBytes: Long): InputStream {
                val safeStart = offsetBytes.coerceIn(0L, bytes.size.toLong()).toInt()
                return ByteArrayInputStream(bytes, safeStart, bytes.size - safeStart)
            }
        }
    }

    /**
     * Parsed HTTP `Range` evaluation result (RFC 7233).
     */
    sealed interface ByteRangeResult {
        /** Serve full entity (`200 OK`). */
        data object Full : ByteRangeResult

        /** Serve inclusive byte range `[start, end]` (`206 Partial Content`). */
        data class Partial(val start: Long, val end: Long) : ByteRangeResult {
            val contentLength: Long get() = (end - start) + 1L
        }

        /** Requested range cannot be satisfied (`416 Range Not Satisfiable`). */
        data object Unsatisfiable : ByteRangeResult
    }

    private val running = AtomicBoolean(false)
    private val lifecycleLock = Any()

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var acceptExecutor: ExecutorService? = null

    @Volatile
    private var workerExecutor: ExecutorService? = null

    private val registryLock = Any()
    private val registeredSources = ConcurrentHashMap<String, LocalMediaSource>()
    private val keyToToken = ConcurrentHashMap<String, String>()
    private val tokenToKey = ConcurrentHashMap<String, String>()
    private val tokenInsertionOrder = ArrayDeque<String>()
    private val activeClientSockets = Collections.newSetFromMap(ConcurrentHashMap<Socket, Boolean>())

    val isRunning: Boolean
        get() = running.get() && serverSocket?.isClosed == false

    val listeningPort: Int
        get() = serverSocket?.localPort ?: -1

    /**
     * Starts the embedded HTTP server if not already running.
     * @return the bound TCP port, or `-1` if startup failed.
     */
    fun start(): Int = synchronized(lifecycleLock) {
        if (isRunning) return@synchronized listeningPort

        return@synchronized try {
            val bindAddr = InetAddress.getByName(bindHost)
            val socket = ServerSocket(requestedPort, SERVER_BACKLOG, bindAddr).apply {
                reuseAddress = true
            }
            serverSocket = socket
            running.set(true)

            val acceptor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "cast-http-accept").apply { isDaemon = true }
            }
            val workers = Executors.newFixedThreadPool(MAX_WORKER_THREADS) { runnable ->
                Thread(runnable, "cast-http-worker").apply { isDaemon = true }
            }
            acceptExecutor = acceptor
            workerExecutor = workers

            acceptor.execute { acceptLoop(socket, workers) }
            safeLogD("LocalCastMediaServer started on $bindHost:${socket.localPort}")
            socket.localPort
        } catch (t: Throwable) {
            safeLogE("Failed to start LocalCastMediaServer", t)
            stop()
            -1
        }
    }

    /**
     * Stops the HTTP server, closes the listening socket and all active client sockets,
     * shuts down worker threads, and clears registered media tokens.
     */
    fun stop() = synchronized(lifecycleLock) {
        running.set(false)

        val socket = serverSocket
        serverSocket = null
        runCatching { socket?.close() }

        val snapshotClients = activeClientSockets.toList()
        activeClientSockets.clear()
        snapshotClients.forEach { client -> runCatching { client.close() } }

        acceptExecutor?.shutdownNow()
        acceptExecutor = null

        workerExecutor?.let { pool ->
            pool.shutdownNow()
            runCatching { pool.awaitTermination(500, TimeUnit.MILLISECONDS) }
        }
        workerExecutor = null

        clearSources()
        safeLogD("LocalCastMediaServer stopped cleanly")
    }

    /**
     * Clears all registered media source tokens without stopping the listening socket.
     */
    fun clearSources() = synchronized(registryLock) {
        registeredSources.clear()
        keyToToken.clear()
        tokenToKey.clear()
        tokenInsertionOrder.clear()
    }

    /**
     * Registers a [LocalMediaSource] and returns its LAN HTTP URL reachable by Chromecast,
     * or `null` if the server cannot start or no LAN IP address can be determined.
     *
     * Supplying a stable [dedupeKey] (for example, `"audio:<trackId>"`) reuses an existing
     * token if the same source is registered multiple times.
     */
    fun registerSource(
        source: LocalMediaSource,
        dedupeKey: String? = null
    ): String? {
        val port = start()
        if (port <= 0) return null

        val hostIp = resolveReachableHost() ?: return null

        val token = synchronized(registryLock) {
            if (!dedupeKey.isNullOrBlank()) {
                val existing = keyToToken[dedupeKey]
                if (existing != null && registeredSources.containsKey(existing)) {
                    registeredSources[existing] = source
                    // Refresh recency so actively reused tokens are not evicted first
                    tokenInsertionOrder.remove(existing)
                    tokenInsertionOrder.addLast(existing)
                    existing
                } else {
                    evictOldestFifoIfFullLocked()
                    val created = generateToken()
                    registeredSources[created] = source
                    keyToToken[dedupeKey] = created
                    tokenToKey[created] = dedupeKey
                    tokenInsertionOrder.addLast(created)
                    created
                }
            } else {
                evictOldestFifoIfFullLocked()
                val created = generateToken()
                registeredSources[created] = source
                tokenInsertionOrder.addLast(created)
                created
            }
        }

        val safeName = sanitizePathSegment(source.suggestedFileName)
        return "http://$hostIp:$port/media/$token/$safeName"
    }

    fun registerFile(
        file: File,
        mimeType: String,
        dedupeKey: String? = null
    ): String? {
        if (!file.exists() || !file.isFile) return null
        return registerSource(
            source = LocalMediaSource.FileSource(
                file = file,
                mimeType = mimeType,
                suggestedFileName = file.name.ifBlank { "track.m4a" }
            ),
            dedupeKey = dedupeKey
        )
    }

    fun registerContentUri(
        contentResolver: ContentResolver,
        uri: Uri,
        mimeType: String,
        suggestedFileName: String = "track.m4a",
        dedupeKey: String? = null
    ): String? {
        return registerSource(
            source = LocalMediaSource.ContentUriSource(
                contentResolver = contentResolver,
                uri = uri,
                mimeType = mimeType,
                suggestedFileName = suggestedFileName
            ),
            dedupeKey = dedupeKey
        )
    }

    fun registerBytes(
        bytes: ByteArray,
        mimeType: String,
        suggestedFileName: String = "media.bin",
        dedupeKey: String? = null
    ): String? {
        return registerSource(
            source = LocalMediaSource.ByteArraySource(
                bytes = bytes,
                mimeType = mimeType,
                suggestedFileName = suggestedFileName
            ),
            dedupeKey = dedupeKey
        )
    }

    private fun resolveReachableHost(): String? {
        if (bindHost != "0.0.0.0" && bindHost != "::") {
            return bindHost
        }
        return hostAddressProvider()?.takeIf { it.isNotBlank() }
    }

    private fun evictOldestFifoIfFullLocked() {
        val cap = maxRegisteredSources.coerceAtLeast(1)
        while (registeredSources.size >= cap && tokenInsertionOrder.isNotEmpty()) {
            val oldestToken = tokenInsertionOrder.removeFirst()
            registeredSources.remove(oldestToken)
            val key = tokenToKey.remove(oldestToken)
            if (key != null && keyToToken[key] == oldestToken) {
                keyToToken.remove(key)
            }
        }
    }

    private fun acceptLoop(socket: ServerSocket, workers: ExecutorService) {
        while (running.get() && !socket.isClosed) {
            try {
                val client = socket.accept()
                client.soTimeout = CLIENT_READ_TIMEOUT_MS
                client.tcpNoDelay = true
                activeClientSockets.add(client)
                workers.execute {
                    try {
                        handleClient(client)
                    } finally {
                        activeClientSockets.remove(client)
                        runCatching { client.close() }
                    }
                }
            } catch (_: SocketException) {
                // Expected when stop() closes ServerSocket
                break
            } catch (t: Throwable) {
                if (running.get()) {
                    safeLogW("Accept loop error: ${t.message}")
                }
            }
        }
    }

    private fun handleClient(client: Socket) {
        try {
            val input = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII))
            val output = BufferedOutputStream(client.getOutputStream(), IO_BUFFER_BYTES)

            val requestLine = input.readLine() ?: return
            val parts = requestLine.trim().split(' ')
            if (parts.size < 2) {
                writeStatusOnly(output, 400, "Bad Request")
                return
            }

            val method = parts[0].uppercase(Locale.US)
            val rawTarget = parts[1].substringBefore('?')

            val headers = HashMap<String, String>()
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    val key = line.substring(0, colonIdx).trim().lowercase(Locale.US)
                    val value = line.substring(colonIdx + 1).trim()
                    headers[key] = value
                }
            }

            when (method) {
                "OPTIONS" -> {
                    writeCorsPreflight(output)
                    return
                }
                "GET", "HEAD" -> {
                    serveMediaRequest(
                        method = method,
                        path = rawTarget,
                        rangeHeader = headers["range"],
                        output = output
                    )
                }
                else -> {
                    writeStatusOnly(output, 405, "Method Not Allowed")
                }
            }
        } catch (_: SocketException) {
            // Client closed connection early (normal for Chromecast probes and seeks)
        } catch (t: Throwable) {
            safeLogD("Client handler ended: ${t.message}")
        }
    }

    private fun serveMediaRequest(
        method: String,
        path: String,
        rangeHeader: String?,
        output: OutputStream
    ) {
        val token = extractTokenFromPath(path)
        if (token == null) {
            writeStatusOnly(output, 404, "Not Found")
            return
        }

        val source = registeredSources[token]
        if (source == null) {
            writeStatusOnly(output, 404, "Not Found")
            return
        }

        val totalLength = source.contentLength()
        if (totalLength < 0L) {
            writeStatusOnly(output, 404, "Not Found")
            return
        }

        val rangeResult = parseByteRange(rangeHeader, totalLength)
        when (rangeResult) {
            is ByteRangeResult.Unsatisfiable -> {
                val headerBlock = buildString {
                    append("HTTP/1.1 416 Range Not Satisfiable\r\n")
                    appendCommonHeaders()
                    append("Content-Range: bytes */").append(totalLength).append("\r\n")
                    append("Content-Length: 0\r\n")
                    append("\r\n")
                }
                output.write(headerBlock.toByteArray(StandardCharsets.US_ASCII))
                output.flush()
            }

            is ByteRangeResult.Full -> {
                val headerBlock = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    appendCommonHeaders()
                    append("Content-Type: ").append(source.mimeType).append("\r\n")
                    append("Content-Length: ").append(totalLength).append("\r\n")
                    append("\r\n")
                }
                output.write(headerBlock.toByteArray(StandardCharsets.US_ASCII))
                output.flush()
                if (method == "GET" && totalLength > 0L) {
                    streamBytes(source, offset = 0L, bytesToSend = totalLength, output = output)
                }
            }

            is ByteRangeResult.Partial -> {
                val headerBlock = buildString {
                    append("HTTP/1.1 206 Partial Content\r\n")
                    appendCommonHeaders()
                    append("Content-Type: ").append(source.mimeType).append("\r\n")
                    append("Content-Range: bytes ")
                        .append(rangeResult.start)
                        .append('-')
                        .append(rangeResult.end)
                        .append('/')
                        .append(totalLength)
                        .append("\r\n")
                    append("Content-Length: ").append(rangeResult.contentLength).append("\r\n")
                    append("\r\n")
                }
                output.write(headerBlock.toByteArray(StandardCharsets.US_ASCII))
                output.flush()
                if (method == "GET" && rangeResult.contentLength > 0L) {
                    streamBytes(
                        source = source,
                        offset = rangeResult.start,
                        bytesToSend = rangeResult.contentLength,
                        output = output
                    )
                }
            }
        }
    }

    private fun streamBytes(
        source: LocalMediaSource,
        offset: Long,
        bytesToSend: Long,
        output: OutputStream
    ) {
        source.openAt(offset).use { stream ->
            val buffer = ByteArray(IO_BUFFER_BYTES)
            var remaining = bytesToSend
            while (remaining > 0L && running.get()) {
                val maxRead = minOf(buffer.size.toLong(), remaining).toInt()
                val read = stream.read(buffer, 0, maxRead)
                if (read <= 0) break
                output.write(buffer, 0, read)
                remaining -= read.toLong()
            }
            output.flush()
        }
    }

    private fun writeCorsPreflight(output: OutputStream) {
        val response = buildString {
            append("HTTP/1.1 204 No Content\r\n")
            appendCommonHeaders()
            append("Content-Length: 0\r\n")
            append("\r\n")
        }
        output.write(response.toByteArray(StandardCharsets.US_ASCII))
        output.flush()
    }

    private fun writeStatusOnly(output: OutputStream, code: Int, reason: String) {
        val response = buildString {
            append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
            appendCommonHeaders()
            append("Content-Length: 0\r\n")
            append("\r\n")
        }
        output.write(response.toByteArray(StandardCharsets.US_ASCII))
        output.flush()
    }

    private fun StringBuilder.appendCommonHeaders() {
        append("Access-Control-Allow-Origin: *\r\n")
        append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
        append("Access-Control-Allow-Headers: Range, Content-Type, Accept, Origin\r\n")
        append("Access-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges\r\n")
        append("Accept-Ranges: bytes\r\n")
        append("Cache-Control: no-store\r\n")
        append("Connection: close\r\n")
    }

    private class RandomAccessFileInputStream(
        private val raf: RandomAccessFile
    ) : InputStream() {
        override fun read(): Int = raf.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = raf.read(b, off, len)
        override fun close() {
            raf.close()
        }
    }

    internal data class NetworkAddressCandidate(
        val interfaceName: String,
        val ipv4: String,
        val isSiteLocal: Boolean
    )

    companion object {
        private const val TAG = "LocalCastMediaServer"
        private const val SERVER_BACKLOG = 32
        private const val MAX_WORKER_THREADS = 8
        private const val IO_BUFFER_BYTES = 32 * 1024
        private const val CLIENT_READ_TIMEOUT_MS = 15_000
        private const val MAX_REGISTERED_SOURCES = 256

        /**
         * Parses an RFC 7233 single-range `Range: bytes=...` header against [totalLength].
         * Supports `bytes=start-end`, `bytes=start-`, and suffix `bytes=-suffixLength`.
         */
        fun parseByteRange(rangeHeader: String?, totalLength: Long): ByteRangeResult {
            val trimmed = rangeHeader?.trim()
            if (trimmed.isNullOrEmpty()) return ByteRangeResult.Full
            if (totalLength <= 0L) return ByteRangeResult.Unsatisfiable
            if (!trimmed.startsWith("bytes=", ignoreCase = true)) {
                return ByteRangeResult.Full
            }

            // Take the first byte-range-spec if multiple comma-separated ranges are supplied
            val spec = trimmed.substring(6).substringBefore(',').trim()
            val dashIndex = spec.indexOf('-')
            if (dashIndex < 0) return ByteRangeResult.Unsatisfiable

            val startPart = spec.substring(0, dashIndex).trim()
            val endPart = spec.substring(dashIndex + 1).trim()

            return when {
                // Suffix range: bytes=-500 (last 500 bytes)
                startPart.isEmpty() -> {
                    val suffixLen = endPart.toLongOrNull() ?: return ByteRangeResult.Unsatisfiable
                    if (suffixLen <= 0L) return ByteRangeResult.Unsatisfiable
                    val start = (totalLength - suffixLen).coerceAtLeast(0L)
                    ByteRangeResult.Partial(start = start, end = totalLength - 1L)
                }

                // Open-ended range: bytes=1024-
                endPart.isEmpty() -> {
                    val start = startPart.toLongOrNull() ?: return ByteRangeResult.Unsatisfiable
                    if (start < 0L || start >= totalLength) return ByteRangeResult.Unsatisfiable
                    ByteRangeResult.Partial(start = start, end = totalLength - 1L)
                }

                // Explicit range: bytes=0-1023
                else -> {
                    val start = startPart.toLongOrNull() ?: return ByteRangeResult.Unsatisfiable
                    val requestedEnd = endPart.toLongOrNull() ?: return ByteRangeResult.Unsatisfiable
                    if (start < 0L || start >= totalLength || requestedEnd < start) {
                        return ByteRangeResult.Unsatisfiable
                    }
                    val clampedEnd = requestedEnd.coerceAtMost(totalLength - 1L)
                    ByteRangeResult.Partial(start = start, end = clampedEnd)
                }
            }
        }

        internal fun extractTokenFromPath(path: String): String? {
            if (path.contains("..")) return null
            val segments = path.split('/').filter { it.isNotEmpty() }
            // Expected format: /media/<token> or /media/<token>/<filename>
            if (segments.size < 2 || segments[0] != "media") return null
            val token = segments[1]
            if (token.isBlank() || !token.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
                return null
            }
            return token
        }

        internal fun sanitizePathSegment(rawName: String): String {
            val clean = rawName
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .trim('_')
            return clean.ifBlank { "media.bin" }.take(64)
        }

        /**
         * Resolves the device's preferred LAN IPv4 address (prioritizing Wi-Fi `wlan*` / `eth*`
         * site-local addresses so Chromecast receivers on the same subnet can reach this device).
         */
        fun resolveLanIpAddress(): String? {
            return runCatching {
                val candidates = mutableListOf<NetworkAddressCandidate>()
                val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
                while (interfaces.hasMoreElements()) {
                    val nif = interfaces.nextElement()
                    if (!nif.isUp || nif.isLoopback || nif.isVirtual) continue
                    val name = nif.name.orEmpty().lowercase(Locale.US)
                    val addresses = nif.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                            val host = addr.hostAddress ?: continue
                            candidates.add(
                                NetworkAddressCandidate(
                                    interfaceName = name,
                                    ipv4 = host,
                                    isSiteLocal = addr.isSiteLocalAddress
                                )
                            )
                        }
                    }
                }
                selectPreferredLanIpv4(candidates)
            }.getOrNull()
        }

        internal fun selectPreferredLanIpv4(candidates: List<NetworkAddressCandidate>): String? {
            if (candidates.isEmpty()) return null
            val filtered = candidates.filterNot { candidate ->
                val name = candidate.interfaceName.lowercase(Locale.US)
                name.startsWith("tun") ||
                    name.startsWith("tap") ||
                    name.startsWith("ppp") ||
                    name.startsWith("rmnet") ||
                    name.startsWith("ccmni") ||
                    name.startsWith("dummy")
            }
            val pool = filtered.ifEmpty { candidates }
            return pool.minByOrNull { candidate ->
                val name = candidate.interfaceName.lowercase(Locale.US)
                val interfaceRank = when {
                    name.startsWith("wlan") || name.startsWith("wifi") -> 0
                    name.startsWith("eth") || name.startsWith("en") -> 1
                    name.startsWith("ap") || name.startsWith("swlan") -> 2
                    else -> 3
                }
                val siteLocalPenalty = if (candidate.isSiteLocal) 0 else 10
                interfaceRank + siteLocalPenalty
            }?.ipv4
        }

        private fun generateToken(): String = UUID.randomUUID().toString().replace("-", "")

        private fun skipFully(stream: InputStream, bytesToSkip: Long) {
            var remaining = bytesToSkip
            val discard = ByteArray(8192)
            while (remaining > 0L) {
                val skipped = stream.skip(remaining)
                if (skipped > 0L) {
                    remaining -= skipped
                } else {
                    val read = stream.read(discard, 0, minOf(discard.size.toLong(), remaining).toInt())
                    if (read <= 0) break
                    remaining -= read.toLong()
                }
            }
        }

        private fun safeLogD(msg: String) {
            runCatching { Log.d(TAG, msg) }
        }

        private fun safeLogW(msg: String) {
            runCatching { Log.w(TAG, msg) }
        }

        private fun safeLogE(msg: String, t: Throwable) {
            runCatching { Log.e(TAG, msg, t) }
        }
    }
}
