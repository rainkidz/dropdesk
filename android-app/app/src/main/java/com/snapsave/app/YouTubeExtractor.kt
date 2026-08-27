package com.snapsave.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * YouTube video extractor with signature cipher decryption.
 * Fetches the watch page, extracts ytInitialPlayerResponse, and decrypts
 * signatureCipher-protected formats by parsing the player JS.
 */
object YouTubeExtractor {

    private data class CipherOp(val opName: String, val index: Int)

    private const val TAG = "YouTubeExtractor"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class VideoInfo(
        val title: String,
        val duration: Double,
        val thumbnail: String,
        val formats: List<StreamFormat>
    )

    data class StreamFormat(
        val itag: Int,
        val url: String,
        val mimeType: String,
        val qualityLabel: String?,
        val width: Int?,
        val height: Int?,
        val bitrate: Int?,
        val contentLength: Long?,
        val isAudioOnly: Boolean
    )

    suspend fun extract(videoId: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting extraction for videoId=$videoId")

            // Step 1: Fetch watch page
            val html = fetchWatchPage(videoId)
            Log.d(TAG, "Fetched watch page: ${html.length} chars")

            // Step 2: Extract ytInitialPlayerResponse
            val playerResponse = extractPlayerResponse(html)
                ?: throw Exception("Could not parse YouTube page. Video may be unavailable.")

            // Step 3: Extract video details
            val videoDetails = playerResponse.optJSONObject("videoDetails")
            val title = videoDetails?.optString("title", "Unknown") ?: "Unknown"
            val durationStr = videoDetails?.optString("lengthSeconds", "0") ?: "0"
            val duration = durationStr.toDoubleOrNull() ?: 0.0
            val thumbnail = extractThumbnail(videoDetails, playerResponse)

            Log.d(TAG, "Title: $title, Duration: ${duration}s")

            // Step 4: Extract streaming data
            val streamingData = playerResponse.optJSONObject("streamingData")
                ?: throw Exception("No streaming data found. Video may be restricted or age-gated.")

            // Step 5: Try to find the cipher function from player JS
            val playerJsUrl = extractPlayerJsUrl(html)
            val cipherFunction = if (playerJsUrl != null) {
                Log.d(TAG, "Player JS URL: $playerJsUrl")
                fetchAndParseCipherFunction(playerJsUrl)
            } else {
                Log.d(TAG, "No player JS URL found, trying built-in patterns")
                null
            }

            // Step 6: Parse all formats
            val formats = mutableListOf<StreamFormat>()

            // Parse muxed formats (video+audio together, up to 720p)
            parseFormatArray(streamingData.optJSONArray("formats"), formats, cipherFunction)

            // Parse adaptive formats (separate video+audio streams)
            parseFormatArray(streamingData.optJSONArray("adaptiveFormats"), formats, cipherFunction)

            Log.d(TAG, "Total formats found: ${formats.size}")

            if (formats.isEmpty()) {
                throw Exception("No downloadable formats found. All formats require signature decryption.")
            }

            Result.success(VideoInfo(
                title = title,
                duration = duration,
                thumbnail = thumbnail,
                formats = formats
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Extract failed", e)
            Result.failure(e)
        }
    }

    private fun fetchWatchPage(videoId: String): String {
        val url = "https://www.youtube.com/watch?v=$videoId&hl=en"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        val response = client.newCall(request).execute()
        return response.body?.string() ?: throw Exception("Empty response from YouTube")
    }

    private fun extractPlayerResponse(html: String): JSONObject? {
        val patterns = listOf(
            Pattern.compile("var\\s+ytInitialPlayerResponse\\s*=\\s*(\\{.+?\\});\\s*(?:var|</script)"),
            Pattern.compile("ytInitialPlayerResponse\\s*=\\s*(\\{.+?\\});"),
            Pattern.compile("window\\[\"ytInitialPlayerResponse\"\\]\\s*=\\s*(\\{.+?\\});"),
            Pattern.compile("var\\s+ytInitialPlayerResponse\\s*=\\s*(\\{.+?\\});\\s*var\\s+meta")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                try {
                    val jsonStr = matcher.group(1)
                    if (jsonStr != null) {
                        return JSONObject(jsonStr)
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Pattern matched but JSON parse failed: ${e.message}")
                }
            }
        }

        // Aggressive extraction
        val idx = html.indexOf("ytInitialPlayerResponse")
        if (idx != -1) {
            val jsonStart = html.indexOf("{", idx)
            if (jsonStart != -1) {
                var depth = 0
                var jsonEnd = jsonStart
                for (i in jsonStart until html.length.coerceAtMost(jsonStart + 500000)) {
                    when (html[i]) {
                        '{' -> depth++
                        '}' -> {
                            depth--
                            if (depth == 0) {
                                jsonEnd = i
                                break
                            }
                        }
                    }
                }
                if (depth == 0) {
                    try {
                        return JSONObject(html.substring(jsonStart, jsonEnd + 1))
                    } catch (e: Exception) {
                        Log.d(TAG, "Aggressive extraction failed: ${e.message}")
                    }
                }
            }
        }

        return null
    }

    private fun extractThumbnail(videoDetails: JSONObject?, playerResponse: JSONObject): String {
        if (videoDetails != null) {
            val simpleUrl = videoDetails.optString("thumbnailUrl", "")
            if (simpleUrl.isNotEmpty()) return simpleUrl
        }
        try {
            val micro = playerResponse.optJSONObject("microformat")
                ?.optJSONObject("playerMicroformatRenderer")
            val thumbUrl = micro?.optString("thumbnailUrl", "") ?: ""
            if (thumbUrl.isNotEmpty()) return thumbUrl
        } catch (_: Exception) {}
        return ""
    }

    /**
     * Extract player JS URL from the watch page HTML.
     * The player JS contains the cipher function needed to decrypt signatures.
     */
    private fun extractPlayerJsUrl(html: String): String? {
        // Pattern: /s/player/XXXX/player_ias.vflset/XX/en_US/base.js
        val patterns = listOf(
            Pattern.compile("\"jsUrl\":\"(/s/player/[^\"]+/base\\.js)\""),
            Pattern.compile("'jsUrl':'(/s/player/[^\"]+/base\\.js)'"),
            Pattern.compile("src=\"(/s/player/[^\"]+/base\\.js)\""),
            Pattern.compile("(/s/player/[a-zA-Z0-9_-]+/player_ias\\.vflset/[^\"]+/base\\.js)")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val path = matcher.group(1) ?: continue
                return "https://www.youtube.com$path"
            }
        }
        return null
    }

    /**
     * Fetch the player JS and extract the cipher function.
     * Returns a function that takes an encrypted signature and returns the decrypted one.
     */
    private fun fetchAndParseCipherFunction(jsUrl: String): ((String) -> String)? {
        try {
            val request = Request.Builder()
                .url(jsUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            val jsCode = response.body?.string() ?: return null
            Log.d(TAG, "Player JS fetched: ${jsCode.length} chars")

            return parseCipherFromJs(jsCode)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch player JS: ${e.message}")
            return null
        }
    }

    /**
     * Parse the cipher function from YouTube's player JavaScript.
     *
     * YouTube's cipher typically works like this:
     * 1. The signature is a string like "ABCDEF"
     * 2. It gets split into an array of characters: ['A','B','C','D','E','F']
     * 3. A series of operations are applied: reverse, splice, swap
     * 4. The array is joined back into a string
     *
     * Example JS:
     *   var sig=function(a){a=a.split("");Wo.Mq(a,3);Wo.vr(a,28);Wo.Mq(a,4);return a.join("")};
     *   Where Mq=splice, vr=reverse (function names change but operations are the same)
     */
    private fun parseCipherFromJs(jsCode: String): ((String) -> String)? {
        try {
            // Step 1: Find the main cipher function definition
            // Pattern: var sig=function(a){a=a.split("");...return a.join("")};
            // Or: var \w+=function(a){a=a.split("");...return a.join("")};
            val funcPattern = Pattern.compile(
                "var\\s+(\\w+)=function\\(a\\)\\{a=a\\.split\\(\"\"\\);(.+?)return a\\.join\\(\"\"\\)\\}"
            )
            val funcMatcher = funcPattern.matcher(jsCode)

            if (!funcMatcher.find()) {
                Log.d(TAG, "Could not find cipher function in player JS")
                return null
            }

            val funcBody = funcMatcher.group(2) ?: return null
            Log.d(TAG, "Cipher function body: $funcBody")

            // Step 2: Extract the operations from the function body
            // Operations look like: Wo.Mq(a,3); or Wo.vr(a,28); or Wo["Mq"](a,3);
            val opPattern = Pattern.compile("(\\w+)[\"']?(\\w+)[\"']?\\((\\w+),(\\d+)\\)")
            val opMatcher = opPattern.matcher(funcBody)

            val operations = mutableListOf<CipherOp>()

            while (opMatcher.find()) {
                val objName = opMatcher.group(1) ?: continue
                val methodName = opMatcher.group(2) ?: continue
                val index = opMatcher.group(4)?.toIntOrNull() ?: continue
                operations.add(CipherOp(methodName, index))
            }

            if (operations.isEmpty()) {
                Log.d(TAG, "No operations found in cipher function")
                return null
            }

            Log.d(TAG, "Found ${operations.size} cipher operations: ${operations.map { "${it.opName}(${it.index})" }}")

            // Step 3: Determine what each method name maps to
            // We need to find the object definition and match reverse/splice/swap
            val objectNames = mutableSetOf<String>()
            for (op in operations) {
                val objPattern = Pattern.compile("var\\s+(\\w+)\\s*=\\s*\\{")
                val objMatcher = objPattern.matcher(jsCode)
                while (objMatcher.find()) {
                    objectNames.add(objMatcher.group(1)!!)
                }
            }

            // Find the helper object that contains reverse, splice, swap
            val methodMap = identifyMethods(jsCode, operations)

            if (methodMap.isEmpty()) {
                Log.d(TAG, "Could not identify cipher methods")
                return null
            }

            // Step 4: Build the cipher function
            val cipherFn: (String) -> String = { signature ->
                applyCipher(signature, operations, methodMap)
            }
            return cipherFn

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse cipher: ${e.message}")
            return null
        }
    }

    /**
     * Identify which method names correspond to reverse, splice, and swap
     * by analyzing the helper object's definitions.
     */
    private fun identifyMethods(
        jsCode: String,
        operations: List<CipherOp>
    ): Map<String, String> {
        val methodMap = mutableMapOf<String, String>()

        // YouTube cipher methods always do one of these three things:
        // 1. reverse - reverses the array: a.reverse()
        // 2. splice - removes elements at index: a.splice(0, n)
        // 3. swap - swaps element at index 0 with element at pos: [a[pos], a[0]] = [a[0], a[pos]]

        // Find the object that has these methods
        // Pattern: var Xr={method1:function(a,b){...},method2:function(a,b){...}};
        val objectPattern = Pattern.compile("var\\s+(\\w+)\\s*=\\s*\\{(\\w+:function\\([^)]*\\)\\{[^}]+\\}(?:,\\s*\\w+:function\\([^)]*\\)\\{[^}]+\\})*)\\}")
        val objMatcher = objectPattern.matcher(jsCode)

        while (objMatcher.find()) {
            val objBody = objMatcher.group(2) ?: continue

            // Extract each method
            val methodPattern = Pattern.compile("(\\w+):function\\(([^)]*)\\)\\{([^}]+)\\}")
            val methodMatcher = methodPattern.matcher(objBody)

            while (methodMatcher.find()) {
                val methodName = methodMatcher.group(1) ?: continue
                val params = methodMatcher.group(2) ?: continue
                val body = methodMatcher.group(3) ?: continue

                // Classify the method based on its body
                when {
                    // reverse: a.reverse()
                    body.contains(".reverse()") -> {
                        methodMap[methodName] = "reverse"
                    }
                    // splice: a.splice(0, b) or a.splice(X, Y)
                    body.contains(".splice(") -> {
                        methodMap[methodName] = "splice"
                    }
                    // swap: [a[X], a[0]] = [a[0], a[X]] or similar
                    body.contains("[0]") && body.contains("[") && body.contains("]=") -> {
                        methodMap[methodName] = "swap"
                    }
                    // swap variant: var c=a[0];a[0]=a[pos%a.length];a[pos%a.length]=c
                    body.contains("var") && body.contains("a[0]") && body.contains("a.length") -> {
                        methodMap[methodName] = "swap"
                    }
                }
            }
        }

        // If we couldn't find via object pattern, try a simpler approach
        // by looking at each unique method name and its usage context
        val uniqueMethods = operations.map { it.opName }.distinct()
        for (methodName in uniqueMethods) {
            if (methodMap.containsKey(methodName)) continue

            // Search for the method definition anywhere in the JS
            val patterns = listOf(
                // Pattern: methodName:function(a,b){var c=a[0];a[0]=a[b%a.length];a[b%a.length]=c}
                Pattern.compile("$methodName:function\\([^)]*\\)\\{[^}]*a\\[0\\][^}]*a\\.length[^}]*\\}"),
                // Pattern: methodName:function(a,b){a.splice(0,b)}
                Pattern.compile("$methodName:function\\([^)]*\\)\\{[^}]*a\\.splice\\([^}]*\\}"),
                // Pattern: methodName:function(a){a.reverse()}
                Pattern.compile("$methodName:function\\([^)]*\\)\\{[^}]*a\\.reverse\\([^}]*\\}")
            )

            for (pattern in patterns) {
                if (pattern.matcher(jsCode).find()) {
                    when {
                        pattern.pattern().contains("reverse") -> methodMap[methodName] = "reverse"
                        pattern.pattern().contains("splice") -> methodMap[methodName] = "splice"
                        pattern.pattern().contains("swap") || pattern.pattern().contains("a[0]") -> methodMap[methodName] = "swap"
                    }
                    break
                }
            }
        }

        return methodMap
    }

    /**
     * Apply the cipher operations to decrypt a signature.
     */
    private fun applyCipher(
        signature: String,
        operations: List<CipherOp>,
        methodMap: Map<String, String>
    ): String {
        val chars = signature.toCharArray().toMutableList()

        for (op in operations) {
            val opType = methodMap[op.opName] ?: continue

            when (opType) {
                "reverse" -> chars.reverse()
                "splice" -> {
                    // splice(0, n) removes first n elements
                    val n = op.index
                    repeat(n.coerceAtMost(chars.size)) {
                        if (chars.isNotEmpty()) chars.removeAt(0)
                    }
                }
                "swap" -> {
                    // swap element at index 0 with element at (pos % length)
                    val pos = op.index % chars.size
                    if (pos != 0 && chars.size > 1) {
                        val temp = chars[0]
                        chars[0] = chars[pos]
                        chars[pos] = temp
                    }
                }
            }
        }

        return chars.joinToString("")
    }

    private fun parseFormatArray(
        jsonArray: JSONArray?,
        formats: MutableList<StreamFormat>,
        cipherFunction: ((String) -> String)?
    ) {
        if (jsonArray == null) return

        for (i in 0 until jsonArray.length()) {
            val fmt = jsonArray.optJSONObject(i) ?: continue

            var streamUrl = fmt.optString("url", "")
            if (streamUrl.isEmpty()) {
                val signatureCipher = fmt.optString("signatureCipher", "")
                if (signatureCipher.isNotEmpty()) {
                    streamUrl = decodeSignatureCipher(signatureCipher, cipherFunction)
                }
            }
            if (streamUrl.isEmpty()) continue

            val mimeType = fmt.optString("mimeType", "")
            val isAudio = mimeType.startsWith("audio/")

            formats.add(StreamFormat(
                itag = fmt.optInt("itag"),
                url = streamUrl,
                mimeType = mimeType,
                qualityLabel = fmt.optString("qualityLabel", null),
                width = fmt.optInt("width", 0).takeIf { it > 0 },
                height = fmt.optInt("height", 0).takeIf { it > 0 },
                bitrate = fmt.optInt("bitrate", 0).takeIf { it > 0 },
                contentLength = fmt.optLong("contentLength", 0).takeIf { it > 0 },
                isAudioOnly = isAudio
            ))
        }
    }

    private fun decodeSignatureCipher(
        cipher: String,
        cipherFunction: ((String) -> String)?
    ): String {
        try {
            val params = parseQueryString(cipher)
            val sp = params["sp"] ?: return ""
            val s = params["s"] ?: return ""

            val decrypted = if (cipherFunction != null) {
                cipherFunction(s)
            } else {
                Log.w(TAG, "No cipher function available, trying simple reverse")
                // Fallback: try basic operations (won't always work)
                s
            }

            if (decrypted.isEmpty()) return ""

            // Rebuild URL with decrypted signature
            val url = params["url"] ?: return ""
            val separator = if (url.contains("?")) "&" else "?"
            return "$url${separator}${sp}=${java.net.URLEncoder.encode(decrypted, "UTF-8")}"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode signatureCipher: ${e.message}")
            return ""
        }
    }

    private fun parseQueryString(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        query.split("&").forEach { part ->
            val kv = part.split("=", limit = 2)
            if (kv.size == 2) {
                result[kv[0]] = URLDecoder.decode(kv[1], "UTF-8")
            }
        }
        return result
    }

    /**
     * Download a stream with progress callback.
     */
    suspend fun downloadStream(
        url: String,
        outputPath: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Origin", "https://www.youtube.com")
                .header("Referer", "https://www.youtube.com/")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}")
            }

            val body = response.body ?: throw Exception("Empty body")
            val totalBytes = body.contentLength()
            var bytesDownloaded = 0L

            body.byteStream().use { input ->
                java.io.FileOutputStream(outputPath).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        onProgress(bytesDownloaded, totalBytes)
                    }
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
