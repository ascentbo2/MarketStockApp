package com.example.stocks

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Quote(val symbol: String, val name: String, val price: Double, val change: Double, val pct: Double)
data class Holding(val symbol: String, val qty: Double, val avg: Double)
data class Fund(val code: String, val name: String)
data class FundHolding(val code: String, val name: String, val units: Double, val avg: Double)
data class Nav(val nav: Double, val date: String)

data class Stats(val prev: Double?, val dayHigh: Double?, val dayLow: Double?, val h52: Double?, val l52: Double?, val vol: Double?)
data class Hist(val closes: List<Double>, val stats: Stats)
data class Signal(val verdict: String, val positive: Int, val reasons: List<Pair<String, Boolean>>)

object Api {
    private var crumb: String? = null
    private var cookie: String? = null
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "Mozilla/5.0")
        cookie?.let { c.setRequestProperty("Cookie", it) }
        c.connectTimeout = 8000; c.readTimeout = 8000
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    fun quote(sym: String): Quote? = try {
        val m = JSONObject(get("https://query1.finance.yahoo.com/v8/finance/chart/${enc(sym)}?range=1d&interval=1d"))
            .getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
        val p = m.getDouble("regularMarketPrice"); val prev = m.getDouble("chartPreviousClose")
        Quote(sym, m.optString("shortName", m.optString("longName", sym)), p, p - prev, (p - prev) / prev * 100)
    } catch (e: Exception) { null }

    fun search(q: String): List<Pair<String, String>> = try {
        val a = JSONObject(get("https://query1.finance.yahoo.com/v1/finance/search?quotesCount=10&newsCount=0&q=${enc(q)}")).getJSONArray("quotes")
        (0 until a.length()).map { a.getJSONObject(it) }
            .filter { it.optString("symbol").let { s -> s.endsWith(".NS") || s.endsWith(".BO") } }
            .map { it.getString("symbol") to it.optString("shortname", it.getString("symbol")) }
    } catch (e: Exception) { emptyList() }

    fun fundSearch(q: String): List<Pair<String, String>> = try {
        val a = JSONArray(get("https://api.mfapi.in/mf/search?q=${enc(q)}"))
        (0 until minOf(a.length(), 20)).map { a.getJSONObject(it) }.map { it.get("schemeCode").toString() to it.getString("schemeName") }
    } catch (e: Exception) { emptyList() }

    fun history(sym: String, range: String, interval: String): Hist? = try {
        val r = JSONObject(get("https://query1.finance.yahoo.com/v8/finance/chart/${enc(sym)}?range=$range&interval=$interval"))
            .getJSONObject("chart").getJSONArray("result").getJSONObject(0)
        val m = r.getJSONObject("meta")
        val c = r.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).getJSONArray("close")
        val list = (0 until c.length()).filter { !c.isNull(it) }.map { c.getDouble(it) }
        fun d(k: String) = if (m.has(k)) m.getDouble(k) else null
        Hist(list, Stats(d("chartPreviousClose"), d("regularMarketDayHigh"), d("regularMarketDayLow"),
            d("fiftyTwoWeekHigh"), d("fiftyTwoWeekLow"), d("regularMarketVolume")))
    } catch (e: Exception) { null }

    private fun auth() {
        if (crumb != null) return
        val c = URL("https://fc.yahoo.com").openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "Mozilla/5.0"); c.instanceFollowRedirects = false; c.connectTimeout = 8000
        c.responseCode
        cookie = c.headerFields["Set-Cookie"]?.joinToString("; ") { it.substringBefore(";") }
        crumb = get("https://query1.finance.yahoo.com/v1/test/getcrumb").trim()
    }

    fun fundamentals(sym: String): Fundamentals? = try {
        auth()
        val r = JSONObject(get("https://query1.finance.yahoo.com/v10/finance/quoteSummary/${enc(sym)}?modules=summaryDetail,defaultKeyStatistics,financialData,assetProfile&crumb=${enc(crumb!!)}"))
            .getJSONObject("quoteSummary").getJSONArray("result").getJSONObject(0)
        val sd = r.optJSONObject("summaryDetail"); val ks = r.optJSONObject("defaultKeyStatistics")
        val fd = r.optJSONObject("financialData"); val ap = r.optJSONObject("assetProfile")
        fun raw(o: JSONObject?, k: String): Double? = o?.optJSONObject(k)?.let { if (it.has("raw")) it.getDouble("raw") else null }
        val m = mapOf("cap" to raw(sd, "marketCap"), "pe" to raw(sd, "trailingPE"), "fpe" to raw(sd, "forwardPE"),
            "pb" to raw(ks, "priceToBook"), "eps" to raw(ks, "trailingEps"), "dy" to raw(sd, "dividendYield"), "beta" to raw(sd, "beta"),
            "roe" to raw(fd, "returnOnEquity"), "roa" to raw(fd, "returnOnAssets"), "pm" to raw(fd, "profitMargins"),
            "om" to raw(fd, "operatingMargins"), "revg" to raw(fd, "revenueGrowth"), "eg" to raw(fd, "earningsGrowth"),
            "de" to raw(fd, "debtToEquity"), "cr" to raw(fd, "currentRatio")).filterValues { it != null }.mapValues { it.value!! }
        val rows = mutableListOf<Pair<String, String>>()
        fun add(l: String, k: String, fm: (Double) -> String) { m[k]?.let { rows.add(l to fm(it)) } }
        add("Market cap", "cap") { "₹%,.0f Cr".format(it / 1e7) }
        add("P/E (TTM)", "pe") { "%.2f".format(it) }
        add("Forward P/E", "fpe") { "%.2f".format(it) }
        add("P/B", "pb") { "%.2f".format(it) }
        add("EPS (TTM)", "eps") { "₹%.2f".format(it) }
        add("Dividend yield", "dy") { "%.2f%%".format(it * 100) }
        add("Beta", "beta") { "%.2f".format(it) }
        add("ROE", "roe") { "%.1f%%".format(it * 100) }
        add("ROA", "roa") { "%.1f%%".format(it * 100) }
        add("Profit margin", "pm") { "%.1f%%".format(it * 100) }
        add("Operating margin", "om") { "%.1f%%".format(it * 100) }
        add("Revenue growth", "revg") { "%.1f%%".format(it * 100) }
        add("Earnings growth", "eg") { "%.1f%%".format(it * 100) }
        add("Debt / Equity", "de") { "%.1f%%".format(it) }
        add("Current ratio", "cr") { "%.2f".format(it) }
        val rating = fd?.optString("recommendationKey")?.takeIf { it.isNotBlank() && it != "none" }
            ?.replace('_', ' ')?.split(' ')?.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        Fundamentals(rows, m, rating, raw(fd, "targetMeanPrice"), raw(fd, "numberOfAnalystOpinions")?.toInt(), ap?.optString("sector")?.takeIf { it.isNotBlank() })
    } catch (e: Exception) { crumb = null; cookie = null; null }

    fun nav(code: String): Nav? = try {
        val d = JSONObject(get("https://api.mfapi.in/mf/$code/latest")).getJSONArray("data").getJSONObject(0)
        Nav(d.getString("nav").toDouble(), d.getString("date"))
    } catch (e: Exception) { null }
}

class VM(app: Application) : AndroidViewModel(app) {
    private val sp = app.getSharedPreferences("stocks", 0)
    val indices = listOf("^NSEI", "^BSESN", "^NSEBANK")
    val top = listOf("RELIANCE.NS", "TCS.NS", "HDFCBANK.NS", "INFY.NS", "ICICIBANK.NS", "SBIN.NS", "ITC.NS", "LT.NS", "BHARTIARTL.NS", "TATAMOTORS.NS")
    val quotes = mutableStateMapOf<String, Quote>()
    val navs = mutableStateMapOf<String, Nav>()
    val results = mutableStateListOf<Pair<String, String>>()
    val fundResults = mutableStateListOf<Pair<String, String>>()
    val watch = mutableStateListOf<String>().apply {
        addAll(sp.getString("w", "RELIANCE.NS,TCS.NS,INFY.NS")!!.split(",").filter { it.isNotBlank() })
    }
    val holds = mutableStateListOf<Holding>().apply {
        val a = JSONArray(sp.getString("h", "[]"))
        for (i in 0 until a.length()) a.getJSONObject(i).let { add(Holding(it.getString("s"), it.getDouble("q"), it.getDouble("a"))) }
    }
    val funds = mutableStateListOf<Fund>().apply {
        val a = JSONArray(sp.getString("f", "[]"))
        for (i in 0 until a.length()) a.getJSONObject(i).let { add(Fund(it.getString("c"), it.getString("n"))) }
    }
    val fundHolds = mutableStateListOf<FundHolding>().apply {
        val a = JSONArray(sp.getString("fh", "[]"))
        for (i in 0 until a.length()) a.getJSONObject(i).let { add(FundHolding(it.getString("c"), it.getString("n"), it.getDouble("u"), it.getDouble("a"))) }
    }

    private fun save() {
        val h = JSONArray(); holds.forEach { h.put(JSONObject().put("s", it.symbol).put("q", it.qty).put("a", it.avg)) }
        val f = JSONArray(); funds.forEach { f.put(JSONObject().put("c", it.code).put("n", it.name)) }
        val fh = JSONArray(); fundHolds.forEach { fh.put(JSONObject().put("c", it.code).put("n", it.name).put("u", it.units).put("a", it.avg)) }
        sp.edit().putString("w", watch.joinToString(",")).putString("h", h.toString())
            .putString("f", f.toString()).putString("fh", fh.toString()).apply()
    }

    fun refresh() = viewModelScope.launch {
        val syms = (indices + top + watch + holds.map { it.symbol }).distinct()
        val codes = (funds.map { it.code } + fundHolds.map { it.code }).distinct()
        val q = syms.map { async(Dispatchers.IO) { Api.quote(it) } }
        val n = codes.map { c -> async(Dispatchers.IO) { c to Api.nav(c) } }
        q.awaitAll().filterNotNull().forEach { quotes[it.symbol] = it }
        n.awaitAll().forEach { (c, v) -> if (v != null) navs[c] = v }
    }

    private var sj: Job? = null
    private var fj: Job? = null
    fun search(q: String) { sj?.cancel(); sj = viewModelScope.launch {
        delay(300); val r = if (q.length < 2) emptyList() else withContext(Dispatchers.IO) { Api.search(q) }
        results.clear(); results.addAll(r) } }
    fun searchFunds(q: String) { fj?.cancel(); fj = viewModelScope.launch {
        delay(300); val r = if (q.length < 3) emptyList() else withContext(Dispatchers.IO) { Api.fundSearch(q) }
        fundResults.clear(); fundResults.addAll(r) } }

    val chart = mutableStateOf<List<Double>>(emptyList())
    val stats = mutableStateOf<Stats?>(null)
    val sig = mutableStateOf<Signal?>(null)
    val tech = mutableStateOf<List<Tech>>(emptyList())
    val fund = mutableStateOf<Fundamentals?>(null)
    val fundSig = mutableStateOf<Signal?>(null)
    val fundDone = mutableStateOf(false)

    fun loadDetail(sym: String) {
        chart.value = emptyList(); stats.value = null; sig.value = null
        tech.value = emptyList(); fund.value = null; fundSig.value = null; fundDone.value = false
        viewModelScope.launch {
            val a = async(Dispatchers.IO) { Api.history(sym, "1d", "5m") }
            val b = async(Dispatchers.IO) { Api.history(sym, "1y", "1d") }
            if (sym !in quotes) withContext(Dispatchers.IO) { Api.quote(sym) }?.let { quotes[sym] = it }
            val h = a.await(); stats.value = h?.stats; chart.value = h?.closes ?: emptyList()
            val yc = b.await()?.closes
            sig.value = yc?.let { signal(it) }; tech.value = yc?.let { technicals(it) } ?: emptyList()
        }
        viewModelScope.launch {
            val fu = withContext(Dispatchers.IO) { Api.fundamentals(sym) }
            fund.value = fu; fundSig.value = fu?.let { fundSignal(it) }; fundDone.value = true
        }
    }

    fun loadChart(sym: String, range: String) {
        val (r, i) = when (range) { "1D" -> "1d" to "5m"; "1M" -> "1mo" to "1d"; "6M" -> "6mo" to "1d"; "1Y" -> "1y" to "1d"; else -> "5y" to "1wk" }
        viewModelScope.launch { chart.value = withContext(Dispatchers.IO) { Api.history(sym, r, i) }?.closes ?: emptyList() }
    }

    fun addWatch(s: String) { if (s !in watch) { watch.add(s); save(); refresh() } }
    fun removeWatch(s: String) { watch.remove(s); save() }
    fun addHolding(s: String, q: Double, a: Double) { holds.add(Holding(s.uppercase(), q, a)); save(); refresh() }
    fun removeHolding(h: Holding) { holds.remove(h); save() }
    fun addFund(c: String, n: String) { if (funds.none { it.code == c }) { funds.add(Fund(c, n)); save(); refresh() } }
    fun removeFund(f: Fund) { funds.remove(f); save() }
    fun addFundHolding(c: String, n: String, u: Double, a: Double) { fundHolds.add(FundHolding(c, n, u, a)); save(); refresh() }
    fun removeFundHolding(h: FundHolding) { fundHolds.remove(h); save() }
}

fun sma(c: List<Double>, n: Int): Double? = if (c.size >= n) c.takeLast(n).average() else null

fun rsi(c: List<Double>, n: Int = 14): Double? {
    if (c.size <= n) return null
    var g = 0.0; var l = 0.0
    for (i in c.size - n until c.size) { val d = c[i] - c[i - 1]; if (d > 0) g += d else l -= d }
    return if (l == 0.0) 100.0 else 100 - 100 / (1 + g / l)
}

fun signal(c: List<Double>): Signal? {
    if (c.size < 50) return null
    val p = c.last(); val r = mutableListOf<Pair<String, Boolean>>()
    val s20 = sma(c, 20)!!; val s50 = sma(c, 50)!!; val s200 = sma(c, 200)
    r.add("Price ${if (p > s20) "above" else "below"} 20-day average (\u20b9${f(s20)})" to (p > s20))
    r.add("Price ${if (p > s50) "above" else "below"} 50-day average (\u20b9${f(s50)})" to (p > s50))
    r.add("20-day average ${if (s20 > s50) "above" else "below"} 50-day average" to (s20 > s50))
    if (s200 != null) r.add("Price ${if (p > s200) "above" else "below"} 200-day average (\u20b9${f(s200)})" to (p > s200))
    rsi(c)?.let { v ->
        val note = when { v > 70 -> "overbought, may pull back"; v < 30 -> "oversold, may bounce"; v >= 50 -> "positive momentum"; else -> "weak momentum" }
        r.add("RSI ${"%.0f".format(v)} ($note)" to ((v >= 50 && v <= 70) || v < 30))
    }
    val ret = (p / c[c.size - 22] - 1) * 100
    r.add("1-month return ${sign(ret)}%" to (ret > 0))
    val pos = r.count { it.second }; val ratio = (2.0 * pos - r.size) / r.size
    return Signal(if (ratio >= 0.5) "Bullish" else if (ratio <= -0.5) "Bearish" else "Neutral", pos, r)
}

data class Tech(val name: String, val value: String, val view: String)
data class Fundamentals(val rows: List<Pair<String, String>>, val raw: Map<String, Double>, val rating: String?, val target: Double?, val analysts: Int?, val sector: String?)

fun ema(c: List<Double>, n: Int): List<Double> {
    val k = 2.0 / (n + 1); var e = c[0]
    return c.map { e = it * k + e * (1 - k); e }
}

fun technicals(c: List<Double>): List<Tech> {
    val p = c.last(); val out = mutableListOf<Tech>()
    rsi(c)?.let { out.add(Tech("RSI (14)", "%.1f".format(it), if (it > 70) "Bearish" else if (it < 30) "Bullish" else "Neutral")) }
    if (c.size >= 35) {
        val macd = ema(c, 12).zip(ema(c, 26)) { a, b -> a - b }; val sg = ema(macd, 9)
        out.add(Tech("MACD (12, 26, 9)", "MACD ${"%.2f".format(macd.last())} · Signal ${"%.2f".format(sg.last())}", if (macd.last() > sg.last()) "Bullish" else "Bearish"))
    }
    for (n in listOf(20, 50, 200)) sma(c, n)?.let { out.add(Tech("SMA $n", "₹" + f(it), if (p > it) "Bullish" else "Bearish")) }
    if (c.size >= 20) {
        val w = c.takeLast(20); val mid = w.average(); val sd = Math.sqrt(w.sumOf { (it - mid) * (it - mid) } / 20)
        val up = mid + 2 * sd; val lo = mid - 2 * sd
        out.add(Tech("Bollinger Bands (20, 2)", "Lower ₹${f(lo)} · Upper ₹${f(up)}", if (p > up) "Bearish" else if (p < lo) "Bullish" else "Neutral"))
        out.add(Tech("Support / Resistance (20-day)", "₹${f(w.min())} / ₹${f(w.max())}", "Neutral"))
    }
    return out
}

fun fundSignal(fu: Fundamentals): Signal? {
    val m = fu.raw; val r = mutableListOf<Pair<String, Boolean>>()
    m["pe"]?.let { r.add("P/E ${"%.1f".format(it)} (${if (it in 0.0..25.0) "reasonable" else "expensive or loss-making"})" to (it in 0.0..25.0)) }
    m["roe"]?.let { r.add("ROE ${"%.1f".format(it * 100)}%" to (it >= 0.15)) }
    m["de"]?.let { r.add("Debt/Equity ${"%.0f".format(it)}%" to (it <= 100)) }
    m["pm"]?.let { r.add("Profit margin ${"%.1f".format(it * 100)}%" to (it >= 0.10)) }
    m["revg"]?.let { r.add("Revenue growth ${"%.1f".format(it * 100)}%" to (it > 0)) }
    m["eg"]?.let { r.add("Earnings growth ${"%.1f".format(it * 100)}%" to (it > 0)) }
    if (r.size < 3) return null
    val pos = r.count { it.second }; val ratio = pos.toDouble() / r.size
    return Signal(if (ratio >= 0.7) "Strong" else if (ratio <= 0.4) "Weak" else "Average", pos, r)
}
