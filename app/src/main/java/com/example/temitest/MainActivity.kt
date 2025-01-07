package com.example.temitest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.example.temitest.ui.theme.TemiTestTheme


import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.util.*


import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json


@Serializable
data class MessageData(
    val command: String,
    val data: String
)



class MainActivity : ComponentActivity() {
    private val latestMessage = MutableStateFlow("")
    private val messages = mutableStateListOf<String>()
    private var webSocketSession: DefaultClientWebSocketSession? = null
    private val sessionMutex = Mutex()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TemiTestTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WebSocketUI(
                        latestMessage = latestMessage.asStateFlow(),
                        messages = messages,
                        onSendMessage = { sendMessage ->
                            lifecycleScope.launch {
                                sendWebSocketMessage(sendMessage)
                            }
                        }
                    )
                }
            }
        }
        // Call the WebSocket function inside a coroutine
        lifecycleScope.launch {
            startWebSocket()
        }
    }

    private suspend fun startWebSocket() {
        val client = HttpClient(OkHttp) {
            install(WebSockets)
        }

        try {
            client.webSocket(
                method = HttpMethod.Get,
//                host = "10.0.2.2",
//                port = 9090,
//                path = "/green"
                  host = "192.168.68.127",
                  port = 9090,
                  path = "/temi"
            ) {
                sessionMutex.withLock {
                    webSocketSession = this
                }

                for (message in incoming) {
                    when (message) {
                        is Frame.Text -> {
                            val receivedText = message.readText()

                            try {
                                // Try to parse JSON
                                val parsedData = Json.decodeFromString<MessageData>(receivedText)
                                withContext(Dispatchers.Main) {
                                    messages.add("server: ${parsedData.data}")
                                    latestMessage.emit("""
                                        JSON Received
                                        - Command: ${parsedData.command}
                                        - Data: ${parsedData.data}
                                    """.trimIndent()
                                    )
                                }
                            } catch (e: SerializationException) {
                                // Handle non-JSON or malformed data
                                withContext(Dispatchers.Main) {
                                    messages.add("server: Non-JSON Received -> $receivedText")
                                    latestMessage.emit("server: Non-JSON Received -> $receivedText")
                                }
                            }
                        }
                        else -> Unit
                    }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                println("WebSocket Error: ${e.message}")
                latestMessage.emit("WebSocket Error: ${e.message}")
                messages.add("[ERROR] WebSocket Error: ${e.message}")
            }
        } finally {
            sessionMutex.withLock {
                webSocketSession = null
            }
            client.close()
        }
    }

    private suspend fun sendWebSocketMessage(message: String) {
        sessionMutex.withLock {
            val jsonStr = "{\"topic\": \"got_speech\",\"data\": \"$message\"}"
            webSocketSession?.send(Frame.Text(jsonStr))
            withContext(Dispatchers.Main) {
                messages.add("client: $message")
            }
        } ?: withContext(Dispatchers.Main) {
            messages.add("[ERROR] WebSocket not connected!")
        }
    }

}

@Composable
fun WebSocketUI(
    latestMessage: StateFlow<String>,
    messages: List<String>,
    onSendMessage: (String) -> Unit
) {
    val message by latestMessage.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState() // State to control LazyColumn scrolling
    val coroutineScope = rememberCoroutineScope() // Coroutine scope for scrolling

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Latest Message: $message",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(12.dp)
        )

        LazyColumn(
            state = listState, // Attach the state
            modifier = Modifier.weight(1f),
            // reverseLayout = true // Ensures the latest message is displayed at the bottom
        ) {
            items(messages.size) { index ->
                Text(
                    text = messages[index],
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Enter message") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                onSendMessage(inputText)
                inputText = "" // Clear input field
                // Scroll to the bottom after sending a message
                coroutineScope.launch {
                    listState.animateScrollToItem(maxOf(messages.size-1, 0))
                }
            }) {
                Text("Send")
            }
        }
    }

    // Auto-scroll when a new message is added
    LaunchedEffect(messages.size) {
        listState.animateScrollToItem(maxOf(messages.size-1, 0))
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    TemiTestTheme {
        Greeting("Android")
    }
}





