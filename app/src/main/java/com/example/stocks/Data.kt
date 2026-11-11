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
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Quote(val symbol: String, val name: String, val price: Double, val change: Double, val pct: Double, val time: Long = 0L)
data class Holding(val symbol: String, val qty: Double, val avg: Double, val date: Long = 0L)
data class Fund(val code: String, val name: String)
data class FundHolding(val code: String, val name: String, val units: Double, val avg: Double, val date: Long = 0L)
data class Nav(val nav: Double, val date: String)
data class NewsItem(val title: String, val source: String, val date: String, val link: String)
data class Mover(val symbol: String, val name: String, val price: Double, val change: Double, val pct: Double, val volume: Double, val high: Double, val low: Double, val buyQty: Double = -1.0, val sellQty: Double = -1.0)

data class Stats(val prev: Double?, val dayHigh: Double?, val dayLow: Double?, val h52: Double?, val l52: Double?, val vol: Double?)
data class Candle(val t: Long, val o: Double, val h: Double, val l: Double, val c: Double, val v: Double)
data class Hist(val closes: List<Double>, val candles: List<Candle>, val stats: Stats)
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
        Quote(sym, m.optString("shortName", m.optString("longName", sym)), p, p - prev, (p - prev) / prev * 100, m.optLong("regularMarketTime"))
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
        val ts = r.getJSONArray("timestamp")
        val q = r.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0)
        val cl = q.getJSONArray("close"); val op = q.optJSONArray("open"); val hi = q.optJSONArray("high")
        val lo = q.optJSONArray("low"); val vo = q.optJSONArray("volume")
        val candles = (0 until cl.length()).filter { !cl.isNull(it) }.map { i ->
            val c = cl.getDouble(i)
            Candle(ts.getLong(i), op?.optDouble(i, c) ?: c, hi?.optDouble(i, c) ?: c, lo?.optDouble(i, c) ?: c, c, vo?.optDouble(i, 0.0) ?: 0.0)
        }
        val list = candles.map { it.c }
        fun d(k: String) = if (m.has(k)) m.getDouble(k) else null
        Hist(list, candles, Stats(d("chartPreviousClose"), d("regularMarketDayHigh"), d("regularMarketDayLow"),
            d("fiftyTwoWeekHigh"), d("fiftyTwoWeekLow"), d("regularMarketVolume")))
    } catch (e: Exception) { null }

    fun news(q: String): List<NewsItem> = try {
        val p = android.util.Xml.newPullParser()
        p.setInput(java.io.StringReader(get("https://news.google.com/rss/search?q=${enc(q)}&hl=en-IN&gl=IN&ceid=IN:en")))
        val out = mutableListOf<NewsItem>()
        var t = ""; var l = ""; var d = ""; var s = ""; var inItem = false; var tag = ""
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> { tag = p.name; if (tag == "item") { inItem = true; t = ""; l = ""; d = ""; s = "" } }
                XmlPullParser.TEXT -> if (inItem) when (tag) { "title" -> t += p.text; "link" -> l += p.text; "pubDate" -> d += p.text; "source" -> s += p.text }
                XmlPullParser.END_TAG -> {
                    if (p.name == "item") {
                        inItem = false
                        val head = if (" - " in t) t.substringBeforeLast(" - ").trim() else t.trim()
                        val src = s.ifBlank { if (" - " in t) t.substringAfterLast(" - ").trim() else "" }
                        out.add(NewsItem(head, src, d.trim(), l.trim()))
                    }
                    tag = ""
                }
            }
            ev = p.next()
        }
        out.take(30)
    } catch (e: Exception) { emptyList() }

    private var nseCookie: String? = null
    private var nseAt = 0L
    private val NSE_UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    private val NSE_INDEX = mapOf("NIFTY 50" to "^NSEI", "NIFTY BANK" to "^NSEBANK", "NIFTY IT" to "^CNXIT", "NIFTY AUTO" to "^CNXAUTO",
        "NIFTY PHARMA" to "^CNXPHARMA", "NIFTY FMCG" to "^CNXFMCG", "NIFTY METAL" to "^CNXMETAL")

    private fun nseGet(path: String): String {
        if (nseCookie == null || System.currentTimeMillis() - nseAt > 600_000) {
            val h = URL("https://www.nseindia.com/").openConnection() as HttpURLConnection
            h.setRequestProperty("User-Agent", NSE_UA); h.setRequestProperty("Accept", "text/html,*/*")
            h.connectTimeout = 8000; h.readTimeout = 8000
            h.responseCode
            nseCookie = h.headerFields["Set-Cookie"]?.joinToString("; ") { it.substringBefore(";") }
            nseAt = System.currentTimeMillis(); h.disconnect()
        }
        val c = URL("https://www.nseindia.com$path").openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", NSE_UA); c.setRequestProperty("Accept", "*/*")
        c.setRequestProperty("Referer", "https://www.nseindia.com/"); c.setRequestProperty("Cookie", nseCookie ?: "")
        c.connectTimeout = 8000; c.readTimeout = 8000
        if (c.responseCode != 200) { nseCookie = null; throw java.io.IOException("NSE " + c.responseCode) }
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    // Unofficial: reads the same public JSON the nseindia.com website uses. Keys are Yahoo-style symbols (TCS.NS, ^NSEI).
    fun nseQuotes(): Map<String, Quote> {
        val out = HashMap<String, Quote>(); val now = System.currentTimeMillis() / 1000
        try {
            val st = JSONObject(nseGet("/api/equity-stockIndices?index=NIFTY%20500")).getJSONArray("data")
            for (i in 0 until st.length()) {
                val o = st.getJSONObject(i)
                if (o.optInt("priority", 0) == 1 || !o.has("lastPrice")) continue
                val s = o.optString("symbol") + ".NS"
                val nm = o.optJSONObject("meta")?.optString("companyName")?.takeIf { it.isNotBlank() } ?: o.optString("symbol")
                out[s] = Quote(s, nm, o.getDouble("lastPrice"), o.optDouble("change", 0.0), o.optDouble("pChange", 0.0), now)
            }
        } catch (e: Exception) { }
        try {
            val ix = JSONObject(nseGet("/api/allIndices")).getJSONArray("data")
            for (i in 0 until ix.length()) {
                val o = ix.getJSONObject(i); val name = o.optString("index")
                val y = NSE_INDEX[name] ?: continue
                out[y] = Quote(y, name, o.getDouble("last"), o.optDouble("variation", 0.0), o.optDouble("percentChange", 0.0), now)
            }
        } catch (e: Exception) { }
        return out
    }

    private fun encq(s: String) = enc(s).replace("+", "%20")

    // All stocks of one NSE index in a single call (unofficial nseindia.com JSON).
    fun nseIndexStocks(index: String): List<Mover> = try {
        val a = JSONObject(nseGet("/api/equity-stockIndices?index=${encq(index)}")).getJSONArray("data")
        (0 until a.length()).map { a.getJSONObject(it) }
            .filter { it.optInt("priority", 0) != 1 && it.has("lastPrice") }
            .map { o ->
                Mover(o.optString("symbol"), o.optJSONObject("meta")?.optString("companyName")?.takeIf { it.isNotBlank() } ?: o.optString("symbol"),
                    o.getDouble("lastPrice"), o.optDouble("change", 0.0), o.optDouble("pChange", 0.0), o.optDouble("totalTradedVolume", 0.0),
                    o.optDouble("dayHigh", 0.0), o.optDouble("dayLow", 0.0))
            }
    } catch (e: Exception) { emptyList() }

    // Total pending buy and sell quantity (order book) for one stock.
    fun nseOrderBook(symbol: String): Pair<Double, Double>? = try {
        val b = JSONObject(nseGet("/api/quote-equity?symbol=${encq(symbol)}&section=trade_info")).getJSONObject("marketDeptOrderBook")
        b.getDouble("totalBuyQuantity") to b.getDouble("totalSellQuantity")
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
    val indices = listOf("^NSEI", "^BSESN", "^NSEBANK", "^CNXIT", "^CNXAUTO", "^CNXPHARMA", "^CNXFMCG", "^CNXMETAL")
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
        for (i in 0 until a.length()) a.getJSONObject(i).let { add(Holding(it.getString("s"), it.getDouble("q"), it.getDouble("a"), it.optLong("d", 0L))) }
    }
    val funds = mutableStateListOf<Fund>().apply {
        val a = JSONArray(sp.getString("f", "[]"))
        for (i in 0 until a.length()) a.getJSONObject(i).let { add(Fund(it.getString("c"), it.getString("n"))) }
    }
    val fundHolds = mutableStateListOf<FundHolding>().apply {
        val a = JSONArray(sp.getString("fh", "[]"))
        for (i in 0 until a.length()) a.getJSONObject(i).let { add(FundHolding(it.getString("c"), it.getString("n"), it.getDouble("u"), it.getDouble("a"), it.optLong("d", 0L))) }
    }

    private fun save() {
        val h = JSONArray(); holds.forEach { h.put(JSONObject().put("s", it.symbol).put("q", it.qty).put("a", it.avg).put("d", it.date)) }
        val f = JSONArray(); funds.forEach { f.put(JSONObject().put("c", it.code).put("n", it.name)) }
        val fh = JSONArray(); fundHolds.forEach { fh.put(JSONObject().put("c", it.code).put("n", it.name).put("u", it.units).put("a", it.avg).put("d", it.date)) }
        sp.edit().putString("w", watch.joinToString(",")).putString("h", h.toString())
            .putString("f", f.toString()).putString("fh", fh.toString()).apply()
    }

    val source = mutableStateOf("Yahoo")

    fun refresh() = viewModelScope.launch {
        val syms = (indices + top + watch + holds.map { it.symbol }).distinct()
        val codes = (funds.map { it.code } + fundHolds.map { it.code }).distinct()
        val nse = withContext(Dispatchers.IO) { Api.nseQuotes() }
        syms.forEach { s -> nse[s]?.let { quotes[s] = it } }
        val rest = syms.filter { it !in nse }
        source.value = if (rest.size == syms.size) "Yahoo" else if (rest.isEmpty()) "NSE" else "NSE + Yahoo"
        val q = rest.map { async(Dispatchers.IO) { Api.quote(it) } }
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

    val news = mutableStateOf<List<NewsItem>>(emptyList())
    val article = mutableStateOf<NewsItem?>(null)
    val newsMode = mutableStateOf(0)
    val newsLoading = mutableStateOf(false)

    fun loadNews(mode: Int) {
        newsMode.value = mode; newsLoading.value = news.value.isEmpty()
        val q = if (mode == 1 && watch.isNotEmpty()) watch.take(6).joinToString(" OR ") { short(it) } + " stock India"
                else "Sensex Nifty Indian stock market"
        viewModelScope.launch { news.value = withContext(Dispatchers.IO) { Api.news(q) }; newsLoading.value = false }
    }

    val stockNews = mutableStateOf<List<NewsItem>>(emptyList())
    val stockNewsDone = mutableStateOf(false)

    fun loadStockNews(sym: String) {
        stockNews.value = emptyList(); stockNewsDone.value = false
        val q = if (sym.startsWith("^")) "Sensex Nifty Indian stock market" else short(sym) + " share price news India"
        viewModelScope.launch { stockNews.value = withContext(Dispatchers.IO) { Api.news(q) }; stockNewsDone.value = true }
    }

    val ideas = mutableStateMapOf<String, Signal>()
    val ideasLoading = mutableStateOf(false)

    fun loadIdeas() {
        ideasLoading.value = ideas.isEmpty()
        viewModelScope.launch {
            (top + watch).distinct().map { s -> async(Dispatchers.IO) { s to Api.history(s, "1y", "1d")?.closes?.let { signal(it) } } }
                .awaitAll().forEach { (s, g) -> if (g != null) ideas[s] = g }
            ideasLoading.value = false
        }
    }

    val movers = mutableStateOf<List<Mover>>(emptyList())
    val moversIndex = mutableStateOf("NIFTY 50")
    val moversLoading = mutableStateOf(false)
    val moversError = mutableStateOf(false)

    fun loadMovers(index: String, book: Boolean = false): Job {
        moversLoading.value = movers.value.isEmpty(); moversError.value = false
        return viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { Api.nseIndexStocks(index) }
            if (moversIndex.value != index) return@launch
            moversLoading.value = false
            if (list.isEmpty()) { moversError.value = movers.value.isEmpty(); return@launch }
            val prev = movers.value.associateBy { it.symbol }
            movers.value = if (book) list.map { m -> prev[m.symbol]?.let { m.copy(buyQty = it.buyQty, sellQty = it.sellQty) } ?: m } else list
            if (!book) return@launch
            // Buyers only / sellers only: check the order book of stocks sitting at a day high/low after a 2%+ move.
            val up = list.filter { it.pct >= 1.9 && it.high > 0 && it.price >= it.high - 1e-6 }.sortedByDescending { it.pct }.take(8)
            val down = list.filter { it.pct <= -1.9 && it.low > 0 && it.price <= it.low + 1e-6 }.sortedBy { it.pct }.take(8)
            val ob = (up + down).map { m -> async(Dispatchers.IO) { m.symbol to Api.nseOrderBook(m.symbol) } }.awaitAll().toMap()
            if (moversIndex.value != index) return@launch
            movers.value = movers.value.map { m -> ob[m.symbol]?.let { m.copy(buyQty = it.first, sellQty = it.second) } ?: m.copy(buyQty = -1.0, sellQty = -1.0) }
        }
    }

    val chart = mutableStateOf<List<Candle>>(emptyList())
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
            val h = a.await(); stats.value = h?.stats; chart.value = h?.candles ?: emptyList()
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
        viewModelScope.launch { chart.value = withContext(Dispatchers.IO) { Api.history(sym, r, i) }?.candles ?: emptyList() }
    }

    fun addWatch(s: String) { if (s !in watch) { watch.add(s); save(); refresh() } }
    fun removeWatch(s: String) { watch.remove(s); save() }
    fun addHolding(s: String, q: Double, a: Double, d: Long = 0L) { holds.add(Holding(s.uppercase(), q, a, d)); save(); refresh() }
    fun removeHolding(h: Holding) { holds.remove(h); save() }
    fun addFund(c: String, n: String) { if (funds.none { it.code == c }) { funds.add(Fund(c, n)); save(); refresh() } }
    fun removeFund(f: Fund) { funds.remove(f); save() }
    fun addFundHolding(c: String, n: String, u: Double, a: Double, d: Long = 0L) { fundHolds.add(FundHolding(c, n, u, a, d)); save(); refresh() }
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
