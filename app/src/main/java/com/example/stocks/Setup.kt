package com.example.stocks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AngelSetupDialog(vm: VM, onDismiss: () -> Unit) {
    val cur = remember { vm.angelCreds() }
    var k by remember { mutableStateOf(cur[0]) }
    var c by remember { mutableStateOf(cur[1]) }
    var p by remember { mutableStateOf(cur[2]) }
    var s by remember { mutableStateOf(cur[3]) }
    AlertDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton({ vm.saveAngel(k.trim(), c.trim(), p.trim(), s.trim()) }) { Text("Save & connect") } },
        dismissButton = { Row {
            TextButton({ k = ""; c = ""; p = ""; s = ""; vm.saveAngel("", "", "", "") }) { Text("Disconnect") }
            TextButton(onDismiss) { Text("Close") }
        } },
        title = { Text("Angel One live feed") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Free real-time prices through your own Angel One SmartAPI account. Your details are stored encrypted on this phone only.", fontSize = 12.sp, color = Color.Gray)
            Text("Status: ${vm.angelStatus.value}", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(k, { k = it }, singleLine = true, label = { Text("API key") })
            OutlinedTextField(c, { c = it }, singleLine = true, label = { Text("Client code") })
            OutlinedTextField(p, { p = it }, singleLine = true, label = { Text("PIN") }, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(s, { s = it }, singleLine = true, label = { Text("TOTP secret") }, visualTransformation = PasswordVisualTransformation())
            Text("Create an app at smartapi.angelone.in to get the API key. The TOTP secret is the text code shown when you enable TOTP on your Angel One account.", fontSize = 11.sp, color = Color.Gray)
        } })
}
