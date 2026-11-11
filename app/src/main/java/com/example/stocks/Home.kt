package com.example.stocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

fun ago(d: String): String = try {
    val t = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH).parse(d)!!.time
    val m = (System.currentTimeMillis() - t) / 60000
    when { m < 1 -> "now"; m < 60 -> "${m}m ago"; m < 1440 -> "${m / 60}h ago"; else -> "${m / 1440}d ago" }
} catch (e: Exception) { "" }

@Composable
fun Home(vm: VM, open: (String) -> Unit) {
    val uri = LocalUriHandler.current
    LaunchedEffect(Unit) {
        if (vm.news.value.isEmpty()) vm.loadNews(vm.newsMode.value)
        while (true) { delay(300_000); vm.loadNews(vm.newsMode.value) }
    }
    val movers = vm.top.mapNotNull { vm.quotes[it] }.sortedByDescending { it.pct }
    LazyColumn {
        item {
            Row(Modifier.fillMaxWidth().padding(12.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("^NSEI" to "NIFTY 50", "^BSESN" to "SENSEX", "^NSEBANK" to "BANK NIFTY").forEach { (s, n) ->
                    val q = vm.quotes[s]
                    Card(Modifier.weight(1f)) { Column(Modifier.padding(10.dp)) {
                        Text(n, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1)
                        Text(q?.let { f(it.price) } ?: "…", fontWeight = FontWeight.SemiBold, maxLines = 1)
                        if (q != null) Text("${sign(q.pct)}%", color = col(q.change), style = MaterialTheme.typography.bodySmall)
                    } }
                }
            }
        }
        if (movers.size >= 6) {
            item { Text("Top gainers", Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp), style = MaterialTheme.typography.titleMedium) }
            items(movers.take(3)) { q -> QuoteRow(q, q.symbol, { open(q.symbol) }) }
            item { Text("Top losers", Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp), style = MaterialTheme.typography.titleMedium) }
            items(movers.takeLast(3).reversed()) { q -> QuoteRow(q, q.symbol, { open(q.symbol) }) }
        }
        if (vm.watch.isNotEmpty()) {
            item { Text("Your watchlist", Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp), style = MaterialTheme.typography.titleMedium) }
            items(vm.watch.take(5)) { s -> QuoteRow(vm.quotes[s], s, { open(s) }) }
        }
        item {
            Text("News", Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
            Column(Modifier.fillMaxWidth().clickable { if (n.link.isNotBlank()) uri.openUri(n.link) }.padding(16.dp, 10.dp)) {
                Text(n.title, maxLines = 3, fontWeight = FontWeight.Medium)
                Text(listOf(n.source, ago(n.date)).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            HorizontalDivider()
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
