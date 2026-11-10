package com.example.stocks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
    LaunchedEffect(sym) { vm.loadDetail(sym) }
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
                Text("₹" + f(q.price), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("${sign(q.change)} (${sign(q.pct)}%)", color = col(q.change))
            }
        }
        Box(Modifier.fillMaxWidth().height(180.dp).padding(16.dp, 8.dp), contentAlignment = Alignment.Center) {
            if (chart.size > 1) LineChart(chart, Modifier.fillMaxSize()) else CircularProgressIndicator()
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("1D", "1M", "6M", "1Y", "5Y").forEach { r -> FilterChip(range == r, { range = r; vm.loadChart(sym, r) }, { Text(r) }) }
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
        Button({ form = true }, Modifier.fillMaxWidth().padding(16.dp)) { Text("Add to portfolio") }
    }
    if (form) {
        var a by remember { mutableStateOf("") }; var b by remember { mutableStateOf(q?.price?.let { "%.2f".format(it) } ?: "") }
        AlertDialog(onDismissRequest = { form = false },
            confirmButton = { TextButton({
                val x = a.toDoubleOrNull(); val y = b.toDoubleOrNull()
                if (x != null && y != null && x > 0) { vm.addHolding(sym, x, y); form = false }
            }) { Text("Add") } },
            dismissButton = { TextButton({ form = false }) { Text("Cancel") } },
            title = { Text(short(sym)) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(a, { a = it }, singleLine = true, label = { Text("Quantity") })
                OutlinedTextField(b, { b = it }, singleLine = true, label = { Text("Avg buy price") })
            } })
    }
}

fun vcol(v: String) = when (v) { "Bullish", "Strong" -> Green; "Bearish", "Weak" -> Red; else -> Color.Gray }
