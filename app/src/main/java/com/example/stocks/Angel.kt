package com.example.stocks

import android.content.Context
import android.content.SharedPreferences
import android.util.JsonReader
import android.util.JsonToken
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Encrypted on-device storage for the Angel One login details. */
object Vault {
    private val names = listOf("k", "c", "p", "s")
    private fun prefs(ctx: Context): SharedPreferences? = try {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(ctx, "angel_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    } catch (e: Exception) { null }
    fun load(ctx: Context): List<String> { val p = prefs(ctx); return names.map { p?.getString(it, "") ?: "" } }
    fun save(ctx: Context, v: List<String>) { prefs(ctx)?.edit()?.apply { names.forEachIndexed { i, n -> putString(n, v[i]) }; apply() } }
}

object Totp {
    private const val ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private fun base32(s: String): ByteArray {
        var buf = 0; var bits = 0; val out = ArrayList<Byte>()
        for (ch in s.uppercase().filter { it in ALPHA }) {
            buf = ((buf shl 5) or ALPHA.indexOf(ch)) and 0x0fff; bits += 5
            if (bits >= 8) { out.add(((buf shr (bits - 8)) and 0xff).toByte()); bits -= 8 }
        }
        return out.toByteArray()
    }
    fun now(secret: String): String {
        val t = System.currentTimeMillis() / 1000 / 30
        val msg = ByteArray(8) { i -> (t shr (8 * (7 - i))).toByte() }
        val mac = Mac.getInstance("HmacSHA1"); mac.init(SecretKeySpec(base32(secret), "HmacSHA1"))
        val h = mac.doFinal(msg); val o = h[h.size - 1].toInt() and 0x0f
        val bin = ((h[o].toInt() and 0x7f) shl 24) or ((h[o + 1].toInt() and 0xff) shl 16) or ((h[o + 2].toInt() and 0xff) shl 8) or (h[o + 3].toInt() and 0xff)
        return (bin % 1_000_000).toString().padStart(6, '0')
    }
}

/** Angel One SmartAPI: login + Live Market Data (FULL mode, up to 50 symbols per request, max 1 request per second). */
object Angel {
    private const val BASE = "https://apiconnect.angelone.in"
    @Volatile var status = "Not set up"
    private var apiKey = ""; private var client = ""; private var pin = ""; private var secret = ""
    private var dir: File? = null
    private var jwt: String? = null
    private var loginBlockedUntil = 0L
    private var lastCall = 0L
    private var master = false
    private val nse = HashMap<String, String>()
    private val bse = HashMap<String, String>()
    private val idx = HashMap<String, Pair<String, String>>()
    private val IDX = mapOf("^NSEI" to "nifty 50", "^NSEBANK" to "nifty bank", "^BSESN" to "sensex", "^CNXIT" to "nifty it",
        "^CNXAUTO" to "nifty auto", "^CNXPHARMA" to "nifty pharma", "^CNXFMCG" to "nifty fmcg", "^CNXMETAL" to "nifty metal")

    @Synchronized fun configure(d: File, k: String, c: String, p: String, s: String) {
        dir = d; apiKey = k; client = c; pin = p; secret = s; jwt = null; loginBlockedUntil = 0L
        status = if (k.isBlank() || c.isBlank() || p.isBlank() || s.isBlank()) "Not set up" else "Not connected"
    }

    private fun post(path: String, body: String, auth: Boolean): String {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 8000; c.readTimeout = 12000
        c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("X-UserType", "USER"); c.setRequestProperty("X-SourceID", "WEB")
        c.setRequestProperty("X-ClientLocalIP", "192.168.1.10"); c.setRequestProperty("X-ClientPublicIP", "106.193.147.98")
        c.setRequestProperty("X-MACAddress", "fe80::216e:6507:4b90:3719"); c.setRequestProperty("X-PrivateKey", apiKey)
        if (auth) c.setRequestProperty("Authorization", "Bearer $jwt")
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        return (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: "{\"status\":false,\"message\":\"HTTP $code\"}"
    }

    @Synchronized fun login(): Boolean {
        if (System.currentTimeMillis() < loginBlockedUntil) return false
        return try {
            status = "Connecting…"
            val body = JSONObject().put("clientcode", client).put("password", pin).put("totp", Totp.now(secret)).toString()
            val r = JSONObject(post("/rest/auth/angelbroking/user/v1/loginByPassword", body, false))
            if (r.optBoolean("status")) {
                jwt = r.getJSONObject("data").getString("jwtToken").removePrefix("Bearer ").trim(); status = "Connected"; true
            } else { status = "Login failed: " + r.optString("message"); loginBlockedUntil = System.currentTimeMillis() + 600_000; false }
        } catch (e: Exception) { status = "Login error: " + (e.message ?: e.javaClass.simpleName); loginBlockedUntil = System.currentTimeMillis() + 120_000; false }
    }

    private fun JsonReader.str(): String = if (peek() == JsonToken.NULL) { nextNull(); "" } else nextString()

    private fun download(f: File) {
        val c = URL("https://margincalculator.angelbroking.com/OpenAPI_File/files/OpenAPIScripMaster.json").openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 90000
        val tmp = File(f.parentFile, "angel_master.tmp")
        JsonReader(InputStreamReader(c.inputStream, "UTF-8").buffered()).use { r ->
            tmp.bufferedWriter().use { w ->
                r.beginArray()
                while (r.hasNext()) {
                    var token = ""; var symbol = ""; var inst = ""; var seg = ""
                    r.beginObject()
                    while (r.hasNext()) when (r.nextName()) {
                        "token" -> token = r.str(); "symbol" -> symbol = r.str(); "instrumenttype" -> inst = r.str(); "exch_seg" -> seg = r.str()
                        else -> r.skipValue()
                    }
                    r.endObject()
                    if (seg == "NSE" && symbol.endsWith("-EQ")) w.write("N|${symbol.removeSuffix("-EQ")}|$token\n")
                    else if (seg == "BSE" && inst.isEmpty()) w.write("B|${symbol.removeSuffix("-EQ")}|$token\n")
                    else if (inst == "AMXIDX") w.write("I|$symbol|$token|$seg\n")
                }
                r.endArray()
            }
        }
        f.delete(); tmp.renameTo(f)
    }

    @Synchronized fun loadMaster(): Boolean {
        if (master) return true
        val f = File(dir ?: return false, "angel_master.txt")
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > 24 * 3600 * 1000L) {
            try { status = "Downloading instrument list…"; download(f) }
            catch (e: Exception) { status = "Instrument list failed: " + (e.message ?: e.javaClass.simpleName); if (!f.exists()) return false }
        }
        nse.clear(); bse.clear(); idx.clear()
        f.forEachLine { l ->
            val p = l.split("|")
            when (p[0]) { "N" -> nse[p[1]] = p[2]; "B" -> bse[p[1]] = p[2]; "I" -> if (p.size >= 4) idx[p[1].lowercase()] = p[3] to p[2] }
        }
        master = nse.isNotEmpty(); return master
    }

    @Synchronized fun ready(): Boolean {
        if (apiKey.isBlank() || client.isBlank() || pin.isBlank() || secret.isBlank()) return false
        if (!master && !loadMaster()) return false
        if (jwt == null && !login()) return false
        return true
    }

    @Synchronized private fun throttle() {
        val w = 1100 - (System.currentTimeMillis() - lastCall); if (w > 0) Thread.sleep(w); lastCall = System.currentTimeMillis()
    }

    @Synchronized private fun fetch(tokens: Map<String, List<String>>): JSONArray? {
        for (attempt in 0..1) {
            if (jwt == null && !login()) return null
            throttle()
            val ex = JSONObject(); tokens.forEach { (k, v) -> ex.put(k, JSONArray(v)) }
            val r = try { JSONObject(post("/rest/secure/angelbroking/market/v1/quote/", JSONObject().put("mode", "FULL").put("exchangeTokens", ex).toString(), true)) }
                    catch (e: Exception) { status = "Data error: " + (e.message ?: e.javaClass.simpleName); return null }
            if (r.optBoolean("status")) { status = "Connected"; return r.getJSONObject("data").optJSONArray("fetched") ?: JSONArray() }
            val msg = r.optString("message")
            if (attempt == 0 && (r.optString("errorcode").startsWith("AG800") || msg.contains("token", true))) jwt = null
            else { status = "Data error: $msg"; return null }
        }
        return null
    }

    private fun resolve(y: String): Pair<String, String>? = when {
        y.startsWith("^") -> IDX[y]?.let { idx[it] }
        y.endsWith(".NS") -> nse[y.removeSuffix(".NS")]?.let { "NSE" to it }
        y.endsWith(".BO") -> y.removeSuffix(".BO").let { n -> (bse[n] ?: if (n.isNotEmpty() && n.all { it.isDigit() }) n else null)?.let { "BSE" to it } }
        else -> null
    }

    /** Live quotes for Yahoo-style symbols (TCS.NS, ^NSEI, 544405.BO). Symbols Angel One cannot resolve are left out. */
    fun quotes(ys: List<String>): Map<String, Quote> {
        val out = HashMap<String, Quote>()
        if (!ready()) return out
        val back = HashMap<String, String>()
        ys.forEach { y -> resolve(y)?.let { (e, t) -> back["$e:$t"] = y } }
        val now = System.currentTimeMillis() / 1000
        for (chunk in back.keys.toList().chunked(50)) {
            val ex = HashMap<String, MutableList<String>>()
            chunk.forEach { k -> val (e, t) = k.split(":"); ex.getOrPut(e) { mutableListOf() }.add(t) }
            val arr = fetch(ex) ?: return out
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val y = back[o.optString("exchange") + ":" + o.optString("symbolToken")] ?: continue
                if (!o.has("ltp")) continue
                out[y] = Quote(y, o.optString("tradingSymbol", y).removeSuffix("-EQ"), o.getDouble("ltp"), o.optDouble("netChange", 0.0), o.optDouble("percentChange", 0.0), now)
            }
        }
        return out
    }

    /** All stocks of an NSE index with price, change, volume and total pending buy/sell quantity. */
    fun movers(index: String): List<Mover> {
        if (!ready()) return emptyList()
        val syms = Api.constituents(index)
        val toks = syms.mapNotNull { s -> nse[s]?.let { it to s } }.toMap()
        val out = ArrayList<Mover>()
        for (chunk in toks.keys.toList().chunked(50)) {
            val arr = fetch(mapOf("NSE" to chunk)) ?: return out
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val s = toks[o.optString("symbolToken")] ?: continue
                if (!o.has("ltp")) continue
                out.add(Mover(s, s, o.getDouble("ltp"), o.optDouble("netChange", 0.0), o.optDouble("percentChange", 0.0), o.optDouble("tradeVolume", 0.0),
                    o.optDouble("high", 0.0), o.optDouble("low", 0.0), o.optDouble("totBuyQuan", -1.0), o.optDouble("totSellQuan", -1.0)))
            }
        }
        return out
    }
}
