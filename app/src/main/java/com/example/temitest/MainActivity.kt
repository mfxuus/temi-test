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
//import androidx.compose.ui.tooling.preview.Preview
import com.example.temitest.ui.theme.TemiTestTheme


import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.util.*
//import java.util.Base64


import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64

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

//temi
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;
import com.robotemi.sdk.TtsRequest.Status;


import com.robotemi.sdk.constants.*;
import com.robotemi.sdk.SttLanguage;
import com.robotemi.sdk.listeners.OnMovementStatusChangedListener;
import com.robotemi.sdk.permission.Permission;
import com.robotemi.sdk.telepresence.*;
import com.robotemi.sdk.Robot.TtsListener;
import com.robotemi.sdk.Robot.AsrListener;
import com.robotemi.sdk.listeners.OnBeWithMeStatusChangedListener;
import com.robotemi.sdk.listeners.OnConstraintBeWithStatusChangedListener;
import com.robotemi.sdk.UserInfo;
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener;
import com.robotemi.sdk.listeners.OnDetectionStateChangedListener;


//camera
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.camera.core.Preview

import android.os.Build
import androidx.camera.core.ImageCapture
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.app.ActivityCompat
import com.example.temitest.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
//import android.Manifest
import android.content.ContentValues
//import android.content.pm.PackageManager
//import android.os.Build
//import android.os.Bundle
import android.provider.MediaStore
//import androidx.appcompat.app.AppCompatActivity
//import androidx.camera.core.ImageCapture
//import androidx.camera.video.Recorder
//import androidx.camera.video.Recording
//import androidx.camera.video.VideoCapture
//import androidx.core.app.ActivityCompat
//import androidx.core.content.ContextCompat
//import com.android.example.cameraxapp.databinding.ActivityMainBinding
//import java.util.concurrent.ExecutorService
//import java.util.concurrent.Executors
//import android.widget.Toast
//import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.CameraSelector
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.PermissionChecker
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.io.encoding.ExperimentalEncodingApi


@Serializable
data class MessageData(
    val command: String,
    val data: String
)

//private var PERMISSIONS_REQUIRED = arrayOf(Manifest.permission.CAMERA)

typealias LumaListener = (luma: Double) -> Unit


class MainActivity : ComponentActivity(),
    AsrListener {

    private lateinit var viewBinding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private lateinit var cameraExecutor: ExecutorService


    private val robot = Robot.getInstance()
    override fun onAsrResult (asrResult: String, sttLanguage: SttLanguage) {
        // Implementation of Robot.NlpListener method
        println("got message. stop listening.")
        robot.finishConversation()
        lifecycleScope.launch {
            sendWebSocketMessage(asrResult)
        }
    }

    private val latestMessage = MutableStateFlow("")
    private val messages = mutableStateListOf<String>()
    private var webSocketSession: DefaultClientWebSocketSession? = null
    private val sessionMutex = Mutex()


    override fun onStart() {
        super.onStart()
        robot.addAsrListener(this)
        robot.requestToBeKioskApp()
    }
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
                        robot = robot,
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
            startWebSocket(robot=robot)
        }

        // add the storage access permission request for Android 9 and below.
//        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
//            val permissionList = PERMISSIONS_REQUIRED.toMutableList()
//            permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
//            PERMISSIONS_REQUIRED = permissionList.toTypedArray()
//        }

         viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        // Set up the listeners for take photo and video capture buttons
        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }
//        viewBinding.videoCaptureButton.setOnClickListener { captureVideo() }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && it.value == false)
                    permissionGranted = false
            }
            // startCamera()
            if (!permissionGranted) {
                Toast.makeText(baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }

    @OptIn(ExperimentalEncodingApi::class)
    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }
//
//        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        // ==Not working on Temi==
//        imageCapture.takePicture(
//            ContextCompat.getMainExecutor(this),
//            object : ImageCapture.OnImageCapturedCallback() {
//                private fun ByteBuffer.toByteArray(): ByteArray {
//                    rewind()    // Rewind the buffer to zero
//                    val data = ByteArray(remaining())
//                    get(data)   // Copy the buffer into a byte array
//                    return data // Return the byte array
//                }
//
//                private fun ByteArray.toBase64(): String =
//                    Base64.Default.encode(this)
//
//                override fun onError(exc: ImageCaptureException) {
//                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
//                }
//
//                override fun onCaptureSuccess(image: ImageProxy){
//                    val buffer = image.planes[0].buffer
//                    val data = buffer.toByteArray().toBase64()
//                    image.close()
//                    val msg = "Photo capture succeeded."
//                    Log.d(TAG, msg)
//                    Log.i("BASE64", data);
//                }
//            }
//        )
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }

    private fun captureVideo() {}

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()


            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY) // Fastest capture
//                .setTargetResolution(Size(1280, 720))
                .setJpegQuality(80) // Adjust quality to avoid buffer overflows
                .build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))

    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
            ).toTypedArray()
//        mutableListOf (
//        Manifest.permission.CAMERA,
//        Manifest.permission.RECORD_AUDIO
//        ).apply {
//            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
//                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
//            }
//        }.toTypedArray()
    }

    override fun onStop() {
        super.onStop()
        robot.removeAsrListener(this)
    }
    private suspend fun startWebSocket(robot: Robot) {
        val client = HttpClient(OkHttp) {
            install(WebSockets)
        }
        try {
            client.webSocket(
                method = HttpMethod.Get,
//                host = "10.0.2.2",
//                port = 9090,
//                path = "/green"
                  host = "192.168.8.154",
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
                                    robot.speak(TtsRequest.create(parsedData.data, false));
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
    robot: Robot,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = {
                robot.wakeup()
            }) {
                Text("Start Listen")
            }
            Spacer(modifier = Modifier.width(36.dp))
            Button(onClick = {
                robot.setKioskModeOn(false)
            }) {
                Text("Exit Kiosk Mode [Not working]")
            }


        }
    }

    // Auto-scroll when a new message is added
    LaunchedEffect(messages.size) {
        listState.animateScrollToItem(maxOf(messages.size-1, 0))
    }
}

//@Composable
//fun Greeting(name: String, modifier: Modifier = Modifier) {
//    Text(
//        text = "Hello $name!",
//        modifier = modifier
//    )
//}

//@Preview(showBackground = true)
//@Composable
//fun GreetingPreview() {
//    TemiTestTheme {
//        Greeting("Android")
//    }
//}





