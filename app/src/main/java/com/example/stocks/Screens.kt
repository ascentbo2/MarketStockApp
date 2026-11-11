package com.example.stocks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

val Line = Color(0xFF2A2A2A)

@Composable
fun MarketsDash(vm: VM, open: (String) -> Unit) {
    var side by remember { mutableIntStateOf(0) }
    val sides = listOf("Indices", "Indian\nStocks", "Trending\nStocks", "Mutual\nFunds", "Watchlist")
    val trending = vm.top.mapNotNull { vm.quotes[it] }.sortedByDescending { abs(it.pct) }.map { it.symbol }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(78.dp).fillMaxHeight().background(Color(0xFF1A1A1A))) {
            sides.forEachIndexed { i, s ->
                Box(Modifier.fillMaxWidth().height(68.dp).background(if (side == i) Color.Black else Color.Transparent).clickable { side = i },
                    contentAlignment = Alignment.CenterStart) {
                    if (side == i) Box(Modifier.width(3.dp).fillMaxHeight().background(Orange))
                    Text(s, Modifier.padding(start = 12.dp), color = if (side == i) Orange else Color.LightGray, fontSize = 12.sp, lineHeight = 15.sp)
                }
                HorizontalDivider(color = Line)
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (side) {
                0 -> IndicesPane(vm, open)
                1 -> IndianStocksPane(vm, open)
                2 -> StocksPane(vm, trending, false, open)
                3 -> Funds(vm)
                else -> Watchlist(vm, open)
            }
        }
    }
}

@Composable
fun IndexRow(q: Quote?, name: String, onClick: () -> Unit) {
    val c = if (q != null) col(q.change) else Color.Gray
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(10.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).border(1.5.dp, c, CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (q == null || q.change >= 0) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, null, tint = c, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            if (q != null && q.time > 0) Text(SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(q.time * 1000)), fontSize = 10.sp, color = Color.Gray)
        }
        if (q != null) {
            Text(f(q.price), Modifier.width(78.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = c)
            Column(Modifier.width(66.dp), horizontalAlignment = Alignment.End) {
                Text(sign(q.change), color = c, fontSize = 11.sp); Text("${sign(q.pct)}%", color = c, fontSize = 11.sp)
            }
        } else Text("…", color = Color.Gray)
    }
    HorizontalDivider(color = Line)
}

@Composable
fun IndicesPane(vm: VM, open: (String) -> Unit) {
    val names = listOf("^BSESN" to "SENSEX", "^NSEI" to "NIFTY 50", "^NSEBANK" to "NIFTY BANK", "^CNXIT" to "NIFTY IT",
        "^CNXAUTO" to "NIFTY AUTO", "^CNXPHARMA" to "NIFTY PHARMA", "^CNXFMCG" to "NIFTY FMCG", "^CNXMETAL" to "NIFTY METAL")
    val tq = vm.top.mapNotNull { vm.quotes[it] }
    val adv = tq.count { it.pct > 0 }; val dec = tq.count { it.pct < 0 }
    LazyColumn {
        item {
            Row(Modifier.fillMaxWidth().padding(10.dp, 8.dp)) {
                Text("INDIAN INDICES", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text("VALUE", Modifier.width(78.dp), fontSize = 10.sp, color = Color.Gray)
                Text("CHANGE(%)", Modifier.width(66.dp), fontSize = 10.sp, color = Color.Gray)
            }
            HorizontalDivider(color = Line)
        }
        items(names) { (s, n) -> IndexRow(vm.quotes[s], n) { open(s) } }
        item {
            Column(Modifier.padding(12.dp)) {
                Text("Advance / Decline (tracked stocks)", fontSize = 11.sp, color = Color.Gray)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(6.dp)) {
                    Box(Modifier.weight(adv + 0.001f).fillMaxHeight().background(Green))
                    Box(Modifier.weight(dec + 0.001f).fillMaxHeight().background(Red))
                }
                Row(Modifier.fillMaxWidth()) {
                    Text("$adv", color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("$dec", color = Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun StocksPane(vm: VM, list: List<String>, search: Boolean, open: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    LazyColumn {
        if (search) item {
            OutlinedButton({ show = true }, Modifier.fillMaxWidth().padding(10.dp)) { Text("Search any NSE / BSE stock") }
        }
        items(list) { s -> QuoteRow(vm.quotes[s], s, { open(s) }) }
    }
    if (show) SearchDialog("Search stock", "e.g. Tata, Infosys", vm.results.toList(), { vm.search(it) },
        { show = false; vm.results.clear(); open(it.first) }, { show = false; vm.results.clear() })
}

@Composable
fun Ideas(vm: VM, open: (String) -> Unit) {
    var flt by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { vm.loadIdeas() }
    val rows = (vm.top + vm.watch).distinct().mapNotNull { s -> vm.ideas[s]?.let { s to it } }
        .filter { flt == 0 || (flt == 1 && it.second.verdict == "Bullish") || (flt == 2 && it.second.verdict == "Bearish") }
        .sortedByDescending { it.second.positive.toDouble() / it.second.reasons.size }
        .let { if (flt == 2) it.reversed() else it }
    LazyColumn {
        item {
            Text("Rule-based technical signals (moving averages, RSI, momentum). Not investment advice.",
                Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp), fontSize = 11.sp, color = Color.Gray)
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("All", "Bullish", "Bearish").forEachIndexed { i, l -> FilterChip(flt == i, { flt = i }, { Text(l) }) }
            }
        }
        if (vm.ideasLoading.value) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        else if (rows.isEmpty()) item { Text("No ideas to show right now.", Modifier.padding(16.dp), color = Color.Gray) }
        items(rows) { (s, g) ->
            val q = vm.quotes[s]
            Row(Modifier.fillMaxWidth().clickable { open(s) }.padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(short(s), fontWeight = FontWeight.SemiBold)
                    Text(q?.name ?: "", fontSize = 11.sp, color = Color.Gray, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (q != null) Text("₹" + f(q.price), color = col(q.change))
                    Text(g.verdict, color = vcol(g.verdict), fontWeight = FontWeight.Bold)
                    Text("${g.positive} of ${g.reasons.size} indicators positive", fontSize = 10.sp, color = Color.Gray)
                }
            }
            HorizontalDivider(color = Line)
        }
    }
}

@Composable
fun NewsScreen(vm: VM) {
    val uri = LocalUriHandler.current
    LaunchedEffect(Unit) { if (vm.news.value.isEmpty()) vm.loadNews(vm.newsMode.value) }
    LazyColumn {
        item {
            Row(Modifier.fillMaxWidth().padding(12.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(vm.newsMode.value == 0, { vm.news.value = emptyList(); vm.loadNews(0) }, { Text("Market") })
                FilterChip(vm.newsMode.value == 1, { vm.news.value = emptyList(); vm.loadNews(1) }, { Text("My stocks") })
            }
        }
        if (vm.newsLoading.value) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        else if (vm.news.value.isEmpty()) item {
            Column(Modifier.padding(16.dp)) {
                Text("No news available right now.", color = Color.Gray)
                TextButton({ vm.loadNews(vm.newsMode.value) }) { Text("Reload news") }
            }
        }
        items(vm.news.value) { n ->
            Column(Modifier.fillMaxWidth().clickable { if (n.link.isNotBlank()) vm.article.value = n }.padding(16.dp, 10.dp)) {
                Text(n.title, maxLines = 3, fontWeight = FontWeight.Medium)
                Text(listOf(n.source, ago(n.date)).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 11.sp, color = Color.Gray)
            }
            HorizontalDivider(color = Line)
        }
    }
}

fun vol(v: Double) = when { v >= 1e7 -> "%.2f Cr".format(v / 1e7); v >= 1e5 -> "%.2f L".format(v / 1e5); else -> "%,.0f".format(v) }

@Composable
fun MoverRow(m: Mover, cat: Int, onClick: () -> Unit) {
    val c = col(m.change)
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(10.dp, 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(m.symbol, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            Text(m.name, fontSize = 10.sp, color = Color.Gray, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(f(m.price), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = c)
            Text("${sign(m.change)} (${sign(m.pct)}%)", color = c, fontSize = 11.sp)
            if (cat == 2) Text("Vol " + vol(m.volume), fontSize = 10.sp, color = Color.Gray)
            if (cat == 3) Text("Buy qty " + vol(m.buyQty), fontSize = 10.sp, color = Green)
            if (cat == 4) Text("Sell qty " + vol(m.sellQty), fontSize = 10.sp, color = Red)
        }
    }
    HorizontalDivider(color = Line)
}

@Composable
fun IndianStocksPane(vm: VM, open: (String) -> Unit) {
    var cat by remember { mutableIntStateOf(0) }
    var show by remember { mutableStateOf(false) }
    val indexList = listOf("NIFTY 50", "NIFTY NEXT 50", "NIFTY 100", "NIFTY 200", "NIFTY 500", "NIFTY BANK", "NIFTY MIDCAP 100", "NIFTY SMALLCAP 100")
    val cats = listOf("Top Gainers", "Top Losers", "Top Volume", "Buyers Only", "Sellers Only")
    LaunchedEffect(vm.moversIndex.value, cat >= 3) { while (true) { vm.loadMovers(vm.moversIndex.value, cat >= 3).join(); delay(5_000) } }
    val all = vm.movers.value
    val rows = when (cat) {
        0 -> all.filter { it.pct > 0 }.sortedByDescending { it.pct }.take(15)
        1 -> all.filter { it.pct < 0 }.sortedBy { it.pct }.take(15)
        2 -> all.sortedByDescending { it.volume }.take(15)
        3 -> all.filter { it.buyQty > 0 && it.sellQty == 0.0 }.sortedByDescending { it.pct }
        else -> all.filter { it.sellQty > 0 && it.buyQty == 0.0 }.sortedBy { it.pct }
    }
    LazyColumn {
        item {
            Text("Select index", Modifier.padding(10.dp, 8.dp, 10.dp, 2.dp), fontSize = 10.sp, color = Color.Gray)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                indexList.forEach { n ->
                    FilterChip(vm.moversIndex.value == n, { if (vm.moversIndex.value != n) { vm.movers.value = emptyList(); vm.moversIndex.value = n } },
                        { Text(n.removePrefix("NIFTY ").let { if (it == "50") "Nifty 50" else it }, fontSize = 11.sp) })
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp, 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                cats.forEachIndexed { i, t -> FilterChip(cat == i, { cat = i }, { Text(t, fontSize = 11.sp) }) }
            }
            TextButton({ show = true }) { Text("Search any NSE / BSE stock", fontSize = 12.sp) }
        }
        if (vm.moversLoading.value) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        else if (vm.moversError.value) item {
            Column(Modifier.padding(16.dp)) {
                Text("Couldn't load NSE data for ${vm.moversIndex.value}. The NSE website may be blocking the request.", color = Color.Gray, fontSize = 12.sp)
                TextButton({ vm.loadMovers(vm.moversIndex.value) }) { Text("Retry") }
            }
        } else if (rows.isEmpty()) item {
            Text(when (cat) {
                3 -> "No stock has only buyers right now in ${vm.moversIndex.value}. This also shows when the market is closed."
                4 -> "No stock has only sellers right now in ${vm.moversIndex.value}. This also shows when the market is closed."
                else -> "No stocks to show."
            }, Modifier.padding(16.dp), color = Color.Gray, fontSize = 12.sp)
        }
        items(rows) { m -> MoverRow(m, cat) { open(m.symbol + ".NS") } }
        if (cat >= 3) item {
            Text("Checked among stocks up or down 2% or more and sitting at the day's high or low. Buyers only means no pending sell orders; sellers only means no pending buy orders.",
                Modifier.padding(12.dp), fontSize = 10.sp, color = Color.Gray)
        }
    }
    if (show) SearchDialog("Search stock", "e.g. Tata, Infosys", vm.results.toList(), { vm.search(it) },
        { show = false; vm.results.clear(); open(it.first) }, { show = false; vm.results.clear() })
}
