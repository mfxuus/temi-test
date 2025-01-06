package com.example.temitest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.temitest.ui.theme.TemiTestTheme


import io.ktor.serialization.kotlinx.*
import kotlinx.serialization.json.*

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.util.*
//...
fun websocket() {
    val client = HttpClient(OkHttp) {
        install(WebSockets) {
//            contentConverter = KotlinxWebsocketSerializationConverter(Json)
        }
    }
    runBlocking {
        client.webSocket(method = HttpMethod.Get, host = "10.0.2.2", port = 9090, path = "/green") {
            while(true) {
                val othersMessage = incoming.receive() as? Frame.Text
                val strOtherMessage = othersMessage?.readText()
                println(strOtherMessage)
                send(strOtherMessage + "abc")
//                val myMessage = Scanner(System.`in`).next()
//                if(myMessage != null) {
//                    send(myMessage)
//                }
            }
        }
    }
    client.close()
}




class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TemiTestTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting("Android")
                }
            }
        }
        websocket()
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