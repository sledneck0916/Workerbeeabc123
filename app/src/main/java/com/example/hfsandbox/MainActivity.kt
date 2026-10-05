package com.example.hfsandbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(this)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { App(store) } } }
    }
}

@Composable
fun App(store: Store) {
    var backend by remember { mutableStateOf(store.backend) }
    var key by remember { mutableStateOf(store.key(backend)) }
    var settings by remember { mutableStateOf(key.isBlank()) }
    if (settings) {
        Settings(backend, key) { b, k ->
            store.backend = b; store.setKey(b, k); backend = b; key = k; settings = false
        }
    } else {
        Chat(FreeBackends(backend, key), backend) { settings = true }
    }
}

@Composable
fun Settings(current: Backend, currentKey: String, onSave: (Backend, String) -> Unit) {
    var b by remember { mutableStateOf(current) }
    var k by remember { mutableStateOf(currentKey) }
    Column(Modifier.padding(24.dp).statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Free model runner", style = MaterialTheme.typography.headlineMedium)
        Backend.values().forEach {
            Row(Modifier.clickable { b = it; k = "" }.fillMaxWidth()) {
                RadioButton(b == it, { b = it; k = "" })
                Text(it.label, Modifier.padding(top = 12.dp))
            }
        }
        Text(b.keyHelp, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(k, { k = it }, label = { Text("API key") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Button({ onSave(b, k.trim()) }, enabled = k.isNotBlank()) { Text("Save") }
    }
}

@Composable
fun Chat(api: FreeBackends, backend: Backend, onSettings: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<String>()) }
    var model by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Search for a free model (e.g. llama, qwen, gemma).") }
    var busy by remember { mutableStateOf(false) }
    val msgs = remember { mutableStateListOf<Pair<String, String>>() }
    var input by remember { mutableStateOf("") }

    fun run(block: suspend () -> Unit) = scope.launch {
        busy = true
        try { block() } catch (e: Exception) { status = "Error: ${e.message}" }
        busy = false
    }

    Column(Modifier.padding(16.dp).statusBarsPadding().navigationBarsPadding().imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(backend.label, style = MaterialTheme.typography.titleMedium)
            TextButton(onSettings) { Text("Settings") }
        }
        Text(status, style = MaterialTheme.typography.bodySmall)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        if (model == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Search models") },
                    singleLine = true, modifier = Modifier.weight(1f))
                Button(enabled = !busy, onClick = {
                    run {
                        results = withContext(Dispatchers.IO) { api.search(query) }
                        status = "${results.size} free models. Tap one."
                    }
                }) { Text("Go") }
            }
            LazyColumn(Modifier.weight(1f)) {
                items(results) { r ->
                    Text(r, Modifier.fillMaxWidth().clickable {
                        model = r; msgs.clear(); status = "Using $r"
                    }.padding(vertical = 10.dp))
                    HorizontalDivider()
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(msgs) { (role, text) -> Text("$role: $text", Modifier.padding(vertical = 4.dp)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(input, { input = it }, modifier = Modifier.weight(1f), label = { Text("Message") })
                Button(enabled = !busy && input.isNotBlank(), onClick = {
                    val text = input; input = ""; msgs.add("user" to text)
                    run {
                        val hist = JSONArray()
                        msgs.forEach { (r, c) -> hist.put(JSONObject().put("role", r).put("content", c)) }
                        msgs.add("assistant" to withContext(Dispatchers.IO) { api.chat(model!!, hist) })
                    }
                }) { Text("Send") }
            }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { model = null }) { Text("Change model") }
        }
    }
}
