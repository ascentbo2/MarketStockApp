package com.example.stocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class PRow(val title: String, val sym: String?, val qtyText: String, val qty: Double, val rate: Double, val date: Long, val price: Double, val del: () -> Unit)

fun qs(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

fun fmtDate(ms: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

// Long term = held for more than 12 months (listed equity shares and equity-oriented funds).
fun isLong(ms: Long): Boolean {
    val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")); c.timeInMillis = ms; c.add(Calendar.YEAR, 1)
    return System.currentTimeMillis() > c.timeInMillis
}

@Composable
fun PortRow(r: PRow, onClick: () -> Unit = {}) {
    val p = (r.price - r.rate) * r.qty
    val sub = "${r.qtyText} @ ₹${f(r.rate)} = ₹${f(r.qty * r.rate)}\n" +
        (if (r.date > 0) "Bought ${fmtDate(r.date)} · ${if (isLong(r.date)) "Long term" else "Short term"}" else "Purchase date not set")
    HoldRow(r.title, sub, "₹${f(r.price * r.qty)}", p, onClick, r.del)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoldingForm(title: String, isFund: Boolean, defaultRate: Double?, onAdd: (Double, Double, Long) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var r by remember { mutableStateOf(defaultRate?.let { "%.2f".format(it) } ?: "") }
    var date by remember { mutableStateOf<Long?>(null) }
    var pick by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val qv = q.toDoubleOrNull(); val rv = r.toDoubleOrNull()
    val amt = if (qv != null && rv != null) qv * rv else null
    AlertDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton({
            val d = date
            if (qv == null || qv <= 0) err = "Enter a valid ${if (isFund) "units" else "quantity"}"
            else if (rv == null || rv <= 0) err = "Enter a valid rate"
            else if (d == null) err = "Select the purchase date"
            else if (d > System.currentTimeMillis()) err = "Purchase date cannot be in the future"
            else onAdd(qv, rv, d)
        }) { Text("Add") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        title = { Text(title, maxLines = 2) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(q, { q = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                label = { Text(if (isFund) "Units" else "Quantity") })
            OutlinedTextField(r, { r = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                label = { Text(if (isFund) "Purchase NAV (rate)" else "Purchase rate (₹ per share)") })
            Box {
                OutlinedTextField(date?.let { fmtDate(it) } ?: "", {}, readOnly = true, singleLine = true, label = { Text("Purchase date") },
                    trailingIcon = { Icon(Icons.Default.DateRange, null) }, modifier = Modifier.fillMaxWidth())
                Box(Modifier.matchParentSize().clickable { pick = true })
            }
            Text("Purchase amount: " + (amt?.let { "₹" + f(it) } ?: "–"), fontWeight = FontWeight.SemiBold)
            if (err.isNotEmpty()) Text(err, color = Red, fontSize = 12.sp)
        } })
    if (pick) {
        val st = rememberDatePickerState()
        DatePickerDialog(onDismissRequest = { pick = false },
            confirmButton = { TextButton({ date = st.selectedDateMillis; pick = false }) { Text("OK") } },
            dismissButton = { TextButton({ pick = false }) { Text("Cancel") } }) { DatePicker(st) }
    }
}
