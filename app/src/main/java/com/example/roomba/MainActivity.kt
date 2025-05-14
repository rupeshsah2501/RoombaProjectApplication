package com.example.roomba

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.text.split

    class MainActivity : AppCompatActivity() {

        var currentStage = 0

        private lateinit var btnForward: ImageButton
        private lateinit var btnStop: ImageButton
        private lateinit var btnRight: ImageButton
        private lateinit var btnBackward: ImageButton
        private lateinit var btnStart: ImageButton
        private lateinit var btnLeft: ImageButton

        private lateinit var ultrasonicLeft: TextView
        private lateinit var ultrasonicMiddle: TextView
        private lateinit var ultrasonicRight: TextView
        private lateinit var irLeft: TextView
        private lateinit var irMiddle: TextView
        private lateinit var irRight: TextView

        private val BASE_URL = "http://192.168.1.10:80/api"
        private val handler = Handler(Looper.getMainLooper())
        private val client = OkHttpClient()

        private lateinit var logTextView: TextView
        private lateinit var logScrollView: ScrollView

        private lateinit var seekBar : SeekBar
        private lateinit var minLabel : TextView
        private lateinit var maxLabel : TextView

        private lateinit var switchonoff : SwitchCompat

        private val statusRunnable = object : Runnable {
            override fun run() {
                fetchSensorData()
                fetchLogData()
                handler.postDelayed(this, 300)
            }
        }

        private lateinit var connManager: ConnectivityManager
        private lateinit var networkCallback: ConnectivityManager.NetworkCallback

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main) // Set the main layout for this activity

            // Initialize your UI components
            btnForward = findViewById(R.id.btnForward)
            btnBackward = findViewById(R.id.btnBackward)
            btnLeft = findViewById(R.id.btnLeft)
            btnRight = findViewById(R.id.btnRight)
            btnStart = findViewById(R.id.btnStart)
            btnStop = findViewById(R.id.btnStop)

            irLeft = findViewById(R.id.irLeft)
            irMiddle = findViewById(R.id.irMiddle)
            irRight = findViewById(R.id.irRight)
            ultrasonicLeft = findViewById(R.id.ultrasonicLeft)
            ultrasonicMiddle = findViewById(R.id.ultrasonicMiddle)
            ultrasonicRight = findViewById(R.id.ultrasonicRight)

            seekBar = findViewById(R.id.seekBar)
            seekBar.max = 2
            minLabel = findViewById(R.id.sliderMinLabel)
            maxLabel = findViewById(R.id.sliderMaxLabel)
            switchonoff = findViewById(R.id.mySwitch)


            logTextView = findViewById(R.id.logTextView)
            logScrollView = findViewById(R.id.logScrollView)
            logTextView.movementMethod = ScrollingMovementMethod()

            // Button click listeners
            btnForward.setOnClickListener { sendCommand("forward") }
            btnBackward.setOnClickListener { sendCommand("backward") }
            btnLeft.setOnClickListener { sendCommand("left") }
            btnRight.setOnClickListener { sendCommand("right") }
            btnStart.setOnClickListener { sendCommand("start") }
            btnStop.setOnClickListener { sendCommand("stop") }

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentStage = progress
                    minLabel.text = getString(R.string.percentage_zero)
                    maxLabel.text = getString(R.string.percentage_hundred)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {

                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    when (currentStage) {
                        0 -> sendCommand("vaccumoff")
                        1 -> sendCommand("vaccumhalf")
                        2 -> sendCommand("vaccumfull")
                    }
                }
            })

            switchonoff.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    sendCommand("brushon")
                } else {
                    sendCommand("brushoff")
                }
            }


            connManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    connManager.bindProcessToNetwork(network)
                    Log.d("Network", "Wi-Fi connected")
                    handler.post(statusRunnable)
                }

                override fun onLost(network: Network) {
                    Log.d("Network", "Wi-Fi disconnected")
                    handler.removeCallbacks(statusRunnable)
                    Toast.makeText(this@MainActivity, "Lost Wi-Fi connection", Toast.LENGTH_SHORT).show()
                }
            }

            connManager.requestNetwork(request, networkCallback)
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacks(statusRunnable)
            connManager.unregisterNetworkCallback(networkCallback)
        }

        private fun sendCommand(command: String) {
            val request: Request
            if (command == "forward" || command == "backward" || command == "left" || command == "right" || command == "start" ||
                command == "stop"){

                val requestBody = FormBody.Builder()
                    .add("cmd", command)
                    .build()

                request = Request.Builder()
                    .url("$BASE_URL/move/")
                    .post(requestBody)
                    .build()
            }
            else if ( command == "vaccumoff" || command == "vaccumhalf" || command == "vaccumfull" ){
                val requestBody = FormBody.Builder()
                    .add("cmd", command)
                    .build()

                request = Request.Builder()
                    .url("$BASE_URL/sucksation/")
                    .post(requestBody)
                    .build()
            }
            else if(command == "brushon" || command == "brushoff"){
                val requestBody = FormBody.Builder()
                    .add("cmd", command)
                    .build()

                request = Request.Builder()
                    .url("$BASE_URL/brush/")
                    .post(requestBody)
                    .build()
            }
            else{
                // Invalid command
                Log.w("ESP32", "Unknown command: $command")
                return
            }

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("ESP32", "Failed to send command: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        Log.d("ESP32", "Command sent successfully: ${response.body?.string()}")
                    } else {
                        Log.e("ESP32", "Error: ${response.code} ${response.body?.string()}")
                    }
                }
            })
        }

        // Fetch sensor and log data
        private fun fetchSensorData() {
            fetchIR("/irsensor/left", irLeft)
            fetchIR("/irsensor/middle", irMiddle)
            fetchIR("/irsensor/right", irRight)
            fetchUS("/ussensor/left", ultrasonicLeft)
            fetchUS("/ussensor/middle", ultrasonicMiddle)
            fetchUS("/ussensor/right", ultrasonicRight)
        }

        private fun fetchIR(endpoint: String, target: TextView) {
            val request = Request.Builder().url(BASE_URL + endpoint).get().build()
            client.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    response.body?.string()?.let { body ->
                        val value = if (body.trim() == "1") "NGND" else "GND"
                        runOnUiThread { target.text = value }
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    Log.e("IR Fetch", "GET $endpoint failed", e)
                }
            })
        }

        private fun fetchUS(endpoint: String, target: TextView) {
            val request = Request.Builder().url(BASE_URL + endpoint).get().build()
            client.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    response.body?.string()?.let { body ->
                        val cm = body.replace("[^0-9]".toRegex(), "")
                        if (cm.isNotBlank()) {
                            runOnUiThread { target.text = getString(R.string.distance_format, cm)
                            }
                        }
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    Log.e("US Fetch", "GET $endpoint failed", e)
                }
            })
        }

        private fun fetchLogData() {
            val request = Request.Builder().url("$BASE_URL/status").get().build()
            client.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    val body = response.body?.string()
                    if (body != null) {
                        val processedLog = body.split("\n")
                        runOnUiThread {
                            for (line in processedLog) {
                                if (line.isNotBlank()) {
                                    logTextView.append("$line\n")
                                }
                            }
                            logScrollView.post { logScrollView.fullScroll(View.FOCUS_DOWN) }
                        }
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    Log.e("ESP32 Log", "Failed to fetch logs", e)
                }
            })
        }

    }


