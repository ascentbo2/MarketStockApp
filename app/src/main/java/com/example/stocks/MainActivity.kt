package com.example.stocks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner

val Green = Color(0xFF2ECC71); val Red = Color(0xFFFF5252); val Orange = Color(0xFFFF7A00)
fun f(d: Double) = "%,.2f".format(d)
fun sign(d: Double) = (if (d >= 0) "+" else "") + f(d)
fun col(d: Double) = if (d >= 0) Green else Red
fun short(s: String) = s.removeSuffix(".NS").removeSuffix(".BO")

class MainActivity : ComponentActivity() {
    private val vm: VM by viewModels()
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Orange, background = Color.Black, surface = Color(0xFF121212),
            onBackground = Color.White, onSurface = Color.White, surfaceVariant = Color(0xFF1E1E1E), onSurfaceVariant = Color(0xFFB0B0B0))) { App(vm) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: VM) {
    var tab by remember { mutableIntStateOf(0) }
    var sel by remember { mutableStateOf<String?>(null) }
    val lc = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        while (true) {
            if (lc.currentState.isAtLeast(Lifecycle.State.RESUMED)) vm.refresh().join()
            delay(if (vm.source.value == "Yahoo") 15_000 else 5_000)
        }
    }
    vm.article.value?.let { ArticleScreen(it) { vm.article.value = null }; return }
    sel?.let { Detail(vm, it) { sel = null }; return }
    val titles = listOf("Home", "Markets Dashboard", "Ideas", "News", "Portfolio")
    Scaffold(
        topBar = { TopAppBar(title = { Text(titles[tab], fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
            actions = { Text(vm.source.value, style = MaterialTheme.typography.bodySmall, color = Color.Gray); IconButton({ vm.refresh() }) { Icon(Icons.Default.Refresh, "Refresh") } }) },
        bottomBar = { NavigationBar(containerColor = Color(0xFF0D0D0D)) {
            listOf("Home" to Icons.Default.Home, "Markets" to Icons.Default.ShowChart, "Ideas" to Icons.Default.Lightbulb,
                "News" to Icons.Default.Article, "Portfolio" to Icons.Default.AccountBalanceWallet)
                .forEachIndexed { i, (l, ic) ->
                    NavigationBarItem(tab == i, { tab = i }, { Icon(ic, l) }, label = { Text(l) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Orange, selectedTextColor = Orange,
                            indicatorColor = Color.Transparent, unselectedIconColor = Color.Gray, unselectedTextColor = Color.Gray))
                }
        } }
    ) { pad -> Box(Modifier.padding(pad)) { when (tab) {
        0 -> Home(vm) { sel = it }; 1 -> MarketsDash(vm) { sel = it }; 2 -> Ideas(vm) { sel = it }; 3 -> NewsScreen(vm)
        else -> Portfolio(vm) { sel = it } } } }
}

@Composable
fun QuoteRow(q: Quote?, sym: String, onClick: () -> Unit = {}, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(short(sym), fontWeight = FontWeight.SemiBold)
            Text(q?.name ?: "Loading…", style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        if (q != null) Column(horizontalAlignment = Alignment.End) {
            Text(f(q.price), fontWeight = FontWeight.SemiBold, color = col(q.change))
            Text("${sign(q.change)} (${sign(q.pct)}%)", color = col(q.change), style = MaterialTheme.typography.bodySmall)
        }
        trailing()
    }
    HorizontalDivider()
}

@Composable
fun SearchDialog(title: String, hint: String, results: List<Pair<String, String>>, onSearch: (String) -> Unit,
                 onPick: (Pair<String, String>) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onDismiss) { Text("Cancel") } },
        title = { Text(title) }, text = { Column {
            OutlinedTextField(q, { q = it; onSearch(it) }, singleLine = true, label = { Text(hint) })
            LazyColumn(Modifier.heightIn(max = 300.dp)) { items(results) { r ->
                Text(r.second + if (r.first.contains('.')) "  (${r.first})" else "",
                    Modifier.fillMaxWidth().clickable { onPick(r) }.padding(vertical = 10.dp))
                HorizontalDivider()
            } }
        } })
}

@Composable
fun Markets(vm: VM, open: (String) -> Unit) = LazyColumn {
    item { Text("Indices", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium) }
    items(vm.indices) { QuoteRow(vm.quotes[it], it, { open(it) }) }
    item { Text("Top stocks", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium) }
    items(vm.top) { s -> QuoteRow(vm.quotes[s], s, { open(s) }) }
}

@Composable
fun Watchlist(vm: VM, open: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn {
            if (vm.watch.isEmpty()) item { Text("Tap + to search and add stocks", Modifier.padding(16.dp)) }
            items(vm.watch.toList()) { s ->
                QuoteRow(vm.quotes[s], s, { open(s) }) { IconButton({ vm.removeWatch(s) }) { Icon(Icons.Default.Close, "Remove") } }
            }
        }
        FloatingActionButton({ show = true }, Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Default.Add, "Add") }
    }
    if (show) SearchDialog("Search stock", "e.g. Tata, Infosys", vm.results.toList(), { vm.search(it) },
        { vm.addWatch(it.first); show = false; vm.results.clear() }, { show = false; vm.results.clear() })
}

@Composable
fun Funds(vm: VM) {
    var show by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn {
            if (vm.funds.isEmpty()) item { Text("Tap + to search and add mutual funds", Modifier.padding(16.dp)) }
            items(vm.funds.toList()) { fd ->
                val n = vm.navs[fd.code]
                Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(fd.name, Modifier.weight(1f), maxLines = 2)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(n?.let { "₹" + f(it.nav) } ?: "…", fontWeight = FontWeight.SemiBold)
                        if (n != null) Text("NAV ${n.date}", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton({ vm.removeFund(fd) }) { Icon(Icons.Default.Close, "Remove") }
                }
                HorizontalDivider()
            }
        }
        FloatingActionButton({ show = true }, Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Default.Add, "Add") }
    }
    if (show) SearchDialog("Search mutual fund", "e.g. Parag Parikh, SBI Bluechip", vm.fundResults.toList(), { vm.searchFunds(it) },
        { vm.addFund(it.first, it.second); show = false; vm.fundResults.clear() }, { show = false; vm.fundResults.clear() })
}

@Composable
fun Portfolio(vm: VM, open: (String) -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var isFund by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(Pair("", "")) }
    val stockRows = vm.holds.map { h -> PRow(short(h.symbol), h.symbol, qs(h.qty), h.qty, h.avg, h.date, vm.quotes[h.symbol]?.price ?: h.avg) { vm.removeHolding(h) } }
    val fundRows = vm.fundHolds.map { h -> PRow(h.name, null, qs(h.units) + " units", h.units, h.avg, h.date, vm.navs[h.code]?.nav ?: h.avg) { vm.removeFundHolding(h) } }
    val all = stockRows + fundRows
    val invested = all.sumOf { it.qty * it.rate }; val current = all.sumOf { it.qty * it.price }; val pnl = current - invested
    val lt = all.filter { it.date > 0 && isLong(it.date) }.sumOf { it.qty * (it.price - it.rate) }
    val st = all.filter { it.date > 0 && !isLong(it.date) }.sumOf { it.qty * (it.price - it.rate) }
    val nodate = all.count { it.date == 0L }
    fun close() { step = 0; vm.results.clear(); vm.fundResults.clear() }
    Box(Modifier.fillMaxSize()) {
        LazyColumn {
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp)) { Column(Modifier.padding(16.dp)) {
                    Text("Current value"); Text("₹${f(current)}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Invested ₹${f(invested)}")
                    Text("P&L ${sign(pnl)} (${if (invested > 0) sign(pnl / invested * 100) else "0.00"}%)", color = col(pnl), fontWeight = FontWeight.SemiBold)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Long-term gain/loss"); Text(sign(lt), color = col(lt), fontWeight = FontWeight.SemiBold) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Short-term gain/loss"); Text(sign(st), color = col(st), fontWeight = FontWeight.SemiBold) }
                    Text("Unrealised, before tax. Long term means held for more than 12 months." +
                        (if (nodate > 0) " $nodate holding(s) with no purchase date are left out of the long/short split." else ""),
                        Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                } }
            }
            item { Text("Stocks", Modifier.padding(16.dp, 4.dp), style = MaterialTheme.typography.titleMedium) }
            if (stockRows.isEmpty()) item { Text("No stocks yet", Modifier.padding(16.dp, 4.dp)) }
            items(stockRows) { r -> PortRow(r) { open(r.sym!!) } }
            item { Text("Mutual funds", Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp), style = MaterialTheme.typography.titleMedium) }
            if (fundRows.isEmpty()) item { Text("No mutual funds yet", Modifier.padding(16.dp, 4.dp)) }
            items(fundRows) { r -> PortRow(r) }
            item { Spacer(Modifier.height(80.dp)) }
        }
        FloatingActionButton({ step = 1 }, Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Default.Add, "Add") }
    }
    when (step) {
        1 -> AlertDialog(onDismissRequest = { close() }, confirmButton = { TextButton({ close() }) { Text("Cancel") } },
            title = { Text("Add to portfolio") }, text = { Column {
                Text("Stock", Modifier.fillMaxWidth().clickable { isFund = false; step = 2 }.padding(vertical = 14.dp))
                HorizontalDivider()
                Text("Mutual fund", Modifier.fillMaxWidth().clickable { isFund = true; step = 2 }.padding(vertical = 14.dp))
            } })
        2 -> if (isFund) SearchDialog("Search mutual fund", "e.g. Parag Parikh", vm.fundResults.toList(), { vm.searchFunds(it) }, { pick = it; step = 3 }, { close() })
             else SearchDialog("Search stock", "e.g. Tata, Infosys", vm.results.toList(), { vm.search(it) }, { pick = it; step = 3 }, { close() })
        3 -> HoldingForm(pick.second, isFund, if (isFund) vm.navs[pick.first]?.nav else vm.quotes[pick.first]?.price,
            { q, r, d -> if (isFund) vm.addFundHolding(pick.first, pick.second, q, r, d) else vm.addHolding(pick.first, q, r, d); close() }, { close() })
    }
}

@Composable
fun HoldRow(title: String, sub: String, value: String, pnl: Double, onClick: () -> Unit = {}, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(sub, style = MaterialTheme.typography.bodySmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(value); Text(sign(pnl), color = col(pnl), style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onDelete) { Icon(Icons.Default.Delete, "Remove") }
    }
    HorizontalDivider()
}
