package com.example.stocks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun LineChart(data: List<Double>, modifier: Modifier = Modifier) {
    val c = if (data.last() >= data.first()) Green else Red
    Canvas(modifier) {
        if (data.size < 2) return@Canvas
        val mn = data.min(); val mx = data.max(); val rng = if (mx > mn) mx - mn else 1.0
        val path = Path()
        data.forEachIndexed { i, v ->
            val x = size.width * i / (data.size - 1); val y = (size.height * (1 - (v - mn) / rng)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val fill = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(fill, c.copy(alpha = 0.12f))
        drawPath(path, c, style = Stroke(4f))
    }
}

@Composable
fun StatRow(l: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(l, color = Color.Gray); Text(v, fontWeight = FontWeight.Medium)
    }
    HorizontalDivider()
}

@Composable
fun Detail(vm: VM, sym: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var range by remember { mutableStateOf("1D") }
    var form by remember { mutableStateOf(false) }
    var candle by remember { mutableStateOf(false) }
    var sma by remember { mutableStateOf(false) }
    var vol by remember { mutableStateOf(true) }
    val uri = LocalUriHandler.current
    LaunchedEffect(sym) { vm.loadDetail(sym); vm.loadStockNews(sym) }
    val q = vm.quotes[sym]; val st = vm.stats.value; val sg = vm.sig.value; val chart = vm.chart.value
    val mine = vm.holds.filter { it.symbol == sym }
    val tc = vm.tech.value; val fu = vm.fund.value; val fs = vm.fundSig.value
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text(short(sym), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val on = sym in vm.watch
            IconButton({ if (on) vm.removeWatch(sym) else vm.addWatch(sym) }) { Icon(if (on) Icons.Default.Star else Icons.Default.StarBorder, "Watchlist") }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(q?.name ?: sym, style = MaterialTheme.typography.bodySmall)
            if (q != null) {
                Text("₹" + f(q.price), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = col(q.change))
                Text("${sign(q.change)} (${sign(q.pct)}%)", color = col(q.change))
            }
        }
        Box(Modifier.fillMaxWidth().height(320.dp).padding(16.dp, 8.dp), contentAlignment = Alignment.Center) {
            if (chart.size > 1) PriceChart(chart, range, candle, sma, vol, Modifier.fillMaxSize()) else CircularProgressIndicator()
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("1D", "1M", "6M", "1Y", "5Y").forEach { r -> FilterChip(range == r, { range = r; vm.loadChart(sym, r) }, { Text(r) }) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(!candle, { candle = false }, { Text("Line") })
            FilterChip(candle, { candle = true }, { Text("Candles") })
            FilterChip(sma, { sma = !sma }, { Text("SMA") })
            FilterChip(vol, { vol = !vol }, { Text("Volume") })
        }
        Card(Modifier.fillMaxWidth().padding(16.dp)) { Column(Modifier.padding(16.dp)) {
            Text("Trend signal", style = MaterialTheme.typography.titleMedium)
            if (sg == null) Text("Not enough price history to compute a signal.", style = MaterialTheme.typography.bodySmall) else {
                Text(sg.verdict, color = when (sg.verdict) { "Bullish" -> Green; "Bearish" -> Red; else -> Color.Gray },
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${sg.positive} of ${sg.reasons.size} indicators positive", style = MaterialTheme.typography.bodySmall)
                sg.reasons.forEach { (t, b) ->
                    Row(Modifier.padding(top = 6.dp)) { Text(if (b) "▲ " else "▼ ", color = if (b) Green else Red); Text(t) }
                }
            }
            Text("Based on price-trend indicators only (moving averages, RSI, 1-month return). Not investment advice.",
                Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        } }
        if (tc.isNotEmpty()) Card(Modifier.fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 16.dp)) { Column(Modifier.padding(16.dp)) {
            Text("Technical indicators", style = MaterialTheme.typography.titleMedium)
            Text("${tc.count { it.view == "Bullish" }} bullish · ${tc.count { it.view == "Bearish" }} bearish · ${tc.count { it.view == "Neutral" }} neutral",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            tc.forEach { t ->
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(t.name); Text(t.value, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                    Text(t.view, color = vcol(t.view), fontWeight = FontWeight.Medium)
                }
            }
        } }
        Card(Modifier.fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 16.dp)) { Column(Modifier.padding(16.dp)) {
            Text("Fundamentals", style = MaterialTheme.typography.titleMedium)
            if (!vm.fundDone.value) Text("Loading…", style = MaterialTheme.typography.bodySmall)
            else if (fu == null) Text("Fundamental data is not available for this symbol.", style = MaterialTheme.typography.bodySmall)
            else {
                fu.sector?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                fs?.let { s ->
                    Text(s.verdict, color = vcol(s.verdict), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${s.positive} of ${s.reasons.size} checks passed", style = MaterialTheme.typography.bodySmall)
                    s.reasons.forEach { (t, b) -> Row(Modifier.padding(top = 6.dp)) { Text(if (b) "▲ " else "▼ ", color = if (b) Green else Red); Text(t) } }
                }
                fu.rating?.let { r ->
                    Text("Analyst consensus: $r" + (fu.target?.let { " · target ₹" + f(it) } ?: "") + (fu.analysts?.let { " ($it analysts)" } ?: ""),
                        Modifier.padding(top = 12.dp), fontWeight = FontWeight.Medium)
                }
                fu.rows.forEach { (l, v) ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(l, color = Color.Gray); Text(v) }
                }
                Text("Ratios vary by sector; compare with similar companies. Not investment advice.",
                    Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        } }
        Text("Key stats", Modifier.padding(16.dp, 0.dp), style = MaterialTheme.typography.titleMedium)
        if (st != null) {
            listOf("Prev close" to st.prev, "Day high" to st.dayHigh, "Day low" to st.dayLow, "52W high" to st.h52, "52W low" to st.l52)
                .forEach { (l, v) -> StatRow(l, v?.let { f(it) } ?: "–") }
            StatRow("Volume", st.vol?.let { "%,.0f".format(it) } ?: "–")
        }
        if (mine.isNotEmpty() && q != null) {
            val qty = mine.sumOf { it.qty }; val inv = mine.sumOf { it.qty * it.avg }; val pnl = qty * q.price - inv
            Text("Your position", Modifier.padding(16.dp, 16.dp, 16.dp, 0.dp), style = MaterialTheme.typography.titleMedium)
            StatRow("Quantity", "$qty"); StatRow("Avg buy price", "₹" + f(inv / qty)); StatRow("Current value", "₹" + f(qty * q.price))
            Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("P&L", color = Color.Gray); Text("${sign(pnl)} (${sign(pnl / inv * 100)}%)", color = col(pnl), fontWeight = FontWeight.Medium)
            }
        }
        Text("News", Modifier.padding(16.dp, 16.dp, 16.dp, 0.dp), style = MaterialTheme.typography.titleMedium)
        if (!vm.stockNewsDone.value) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (vm.stockNews.value.isEmpty()) Text("No recent news found for this stock.", Modifier.padding(16.dp), color = Color.Gray)
        vm.stockNews.value.take(8).forEach { n ->
            Column(Modifier.fillMaxWidth().clickable { if (n.link.isNotBlank()) vm.article.value = n }.padding(16.dp, 10.dp)) {
                Text(n.title, maxLines = 3, fontWeight = FontWeight.Medium)
                Text(listOf(n.source, ago(n.date)).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            HorizontalDivider()
        }
        Button({ form = true }, Modifier.fillMaxWidth().padding(16.dp)) { Text("Add to portfolio") }
    }
    if (form) HoldingForm(short(sym), false, q?.price, { qty, rate, d -> vm.addHolding(sym, qty, rate, d); form = false }, { form = false })
}

fun vcol(v: String) = when (v) { "Bullish", "Strong" -> Green; "Bearish", "Weak" -> Red; else -> Color.Gray }

fun smaSeries(c: List<Double>, n: Int): List<Double?> {
    var sum = 0.0
    return c.mapIndexed { i, v -> sum += v; if (i >= n) sum -= c[i - n]; if (i >= n - 1) sum / n else null }
}

@Composable
fun PriceChart(d: List<Candle>, range: String, candle: Boolean, sma: Boolean, vol: Boolean, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    var sel by remember(d) { mutableStateOf<Int?>(null) }
    val fT = remember(range) { SimpleDateFormat(when (range) { "1D" -> "HH:mm"; "1M", "6M" -> "dd MMM"; else -> "MMM yy" }, Locale.getDefault()) }
    val fF = remember(range) { SimpleDateFormat(if (range == "1D") "dd MMM, HH:mm" else "dd MMM yyyy", Locale.getDefault()) }
    val s20 = remember(d) { smaSeries(d.map { it.c }, 20) }
    val s50 = remember(d) { smaSeries(d.map { it.c }, 50) }
    val txt = TextStyle(fontSize = 10.sp, color = Color.Gray)
    val grid = Color.Gray.copy(alpha = 0.25f)
    val orange = Color(0xFFFF9800); val blue = Color(0xFF2196F3)
    val up = d.last().c >= d.first().c
    val cur = d[sel ?: d.lastIndex]
    Column(modifier) {
        Text(fF.format(Date(cur.t * 1000)) + "  O ${f(cur.o)}  H ${f(cur.h)}  L ${f(cur.l)}  C ${f(cur.c)}", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Vol " + "%,.0f".format(cur.v), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            if (sma) { Text("● SMA 20", color = orange, style = MaterialTheme.typography.bodySmall); Text("● SMA 50", color = blue, style = MaterialTheme.typography.bodySmall) }
        }
        Canvas(Modifier.fillMaxWidth().weight(1f)
            .pointerInput(d) {
                val axisW = 56.dp.toPx()
                fun idx(x: Float) = ((x / (size.width - axisW)) * d.size - 0.5f).roundToInt().coerceIn(0, d.lastIndex)
                detectHorizontalDragGestures(onDragStart = { sel = idx(it.x) }, onDragEnd = { sel = null }, onDragCancel = { sel = null }) { ch, _ -> sel = idx(ch.position.x) }
            }
            .pointerInput(d) {
                val axisW = 56.dp.toPx()
                detectTapGestures(onPress = { o ->
                    sel = ((o.x / (size.width - axisW)) * d.size - 0.5f).roundToInt().coerceIn(0, d.lastIndex)
                    tryAwaitRelease(); sel = null
                })
            }) {
            val axisW = 56.dp.toPx(); val xAxisH = 18.dp.toPx(); val top = 6.dp.toPx()
            val w = size.width - axisW; val h = size.height
            val volH = if (vol) (h - xAxisH) * 0.2f else 0f
            val ph = h - xAxisH - volH - top
            val hi = d.maxOf { it.h }; val lo = d.minOf { it.l }; val rng = if (hi > lo) hi - lo else 1.0
            fun y(v: Double) = top + (ph * (1 - (v - lo) / rng)).toFloat()
            fun x(i: Int) = w * (i + 0.5f) / d.size
            for (k in 0..4) {
                val v = lo + rng * k / 4; val yy = y(v)
                drawLine(grid, Offset(0f, yy), Offset(w, yy), 1f)
                drawText(tm, f(v), Offset(w + 4.dp.toPx(), yy - 6.dp.toPx()), txt)
            }
            for (k in 0..3) {
                val i = ((d.size - 1) * k / 3f).toInt()
                val lay = tm.measure(fT.format(Date(d[i].t * 1000)), txt)
                val xx = (x(i) - lay.size.width / 2f).coerceIn(0f, w - lay.size.width)
                drawText(lay, topLeft = Offset(xx, h - xAxisH + 2.dp.toPx()))
            }
            val bw = (w / d.size * 0.7f).coerceAtLeast(1f)
            if (vol) {
                val vm = d.maxOf { it.v }.coerceAtLeast(1.0)
                d.forEachIndexed { i, c ->
                    val bh = (volH * (c.v / vm)).toFloat()
                    drawRect((if (c.c >= c.o) Green else Red).copy(alpha = 0.35f), Offset(x(i) - bw / 2, h - xAxisH - bh), Size(bw, bh))
                }
            }
            if (candle) {
                d.forEachIndexed { i, c ->
                    val col = if (c.c >= c.o) Green else Red
                    drawLine(col, Offset(x(i), y(c.h)), Offset(x(i), y(c.l)), 1.5f)
                    val t = y(maxOf(c.o, c.c)); val bh = (y(minOf(c.o, c.c)) - t).coerceAtLeast(1.5f)
                    drawRect(col, Offset(x(i) - bw / 2, t), Size(bw, bh))
                }
            } else {
                val p = Path()
                d.forEachIndexed { i, c -> if (i == 0) p.moveTo(x(i), y(c.c)) else p.lineTo(x(i), y(c.c)) }
                val col = if (up) Green else Red
                val fill = Path().apply { addPath(p); lineTo(x(d.lastIndex), top + ph); lineTo(x(0), top + ph); close() }
                drawPath(fill, col.copy(alpha = 0.12f)); drawPath(p, col, style = Stroke(3f))
            }
            if (sma) listOf(s20 to orange, s50 to blue).forEach { (ser, col) ->
                val p = Path(); var started = false
                ser.forEachIndexed { i, v -> if (v != null) { if (!started) { p.moveTo(x(i), y(v)); started = true } else p.lineTo(x(i), y(v)) } }
                if (started) drawPath(p, col, style = Stroke(2f))
            }
            sel?.let { i ->
                val xx = x(i); val yy = y(d[i].c)
                drawLine(Color.Gray, Offset(xx, top), Offset(xx, h - xAxisH), 1.5f)
                drawLine(Color.Gray, Offset(0f, yy), Offset(w, yy), 1.5f)
                drawCircle(if (up) Green else Red, 5.dp.toPx(), Offset(xx, yy))
            }
        }
    }
}
