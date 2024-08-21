package com.example.jjapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var textViewStart: TextView
    private lateinit var buttonStart: Button
    private lateinit var textViewStop: TextView
    private lateinit var buttonStop: Button
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var requestQueue: RequestQueue
    private val handler = Handler(Looper.getMainLooper())
    private val interval: Long = 10000

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContentView(R.layout.activity_main)

        textViewStart = findViewById(R.id.textview_start)
        buttonStart = findViewById(R.id.button_start)
        textViewStop = findViewById(R.id.textview_stop)
        buttonStop = findViewById(R.id.button_stop)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        requestQueue = Volley.newRequestQueue(this)

        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if(isGranted) {
                startRepeatingTask()
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }

        buttonStart.setOnClickListener {
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            } else {
                startRepeatingTask()
            }
        }

        buttonStop.setOnClickListener {
            textViewStart.text = ""
            textViewStop.text = "STOP"
            stopRepeatingTask()
        }

        FirebaseMessaging.getInstance().subscribeToTopic("news")
            .addOnCompleteListener { task: Task<Void?> ->
                var msg = "Subscribed to news topic"
                if (!task.isSuccessful) {
                    msg = "Subscription failed"
                }
                println(msg)
            }

        // Retrieve the current FCM registration token
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task: Task<String> ->
                if (!task.isSuccessful) {
                    println("Fetching FCM registration token failed")
                    return@addOnCompleteListener
                }
                // Get new FCM registration token
                val token = task.result
                println("FCM Registration Token: $token")
            }
    }

    private fun startRepeatingTask() {
        handler.post(runnableCode)
    }

    private fun stopRepeatingTask() {
        handler.removeCallbacks(runnableCode)
    }

    private val runnableCode = object : Runnable {
        override fun run() {
            getLocationAndSendToServer()
            handler.postDelayed(this, interval)
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLocationAndSendToServer() {
        fusedLocationClient.lastLocation
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful && task.result != null) {
                    val location: Location = task.result
                    val latitude = location.latitude
                    val longitude = location.longitude
                    textViewStart.text = "Latitude: $latitude, Longitude: $longitude"
                    textViewStop.text = ""
                    sendLocationToServer(latitude, longitude)
                } else {
                    Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun sendLocationToServer(latitude: Double, longitude: Double) {
        val url = "http://211.44.188.113:54000/savelocation"
        val jsonObject = JSONObject()
        jsonObject.put("latitude", latitude)
        jsonObject.put("longitude", longitude)

        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                Toast.makeText(this, "Location sent to server: $response", Toast.LENGTH_SHORT).show()
            },
            Response.ErrorListener { error ->
                error.printStackTrace()
                Toast.makeText(this, "Failed to send location: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Content-Type"] = "application/json"
                return headers
            }

            override fun getBody(): ByteArray {
                return jsonObject.toString().toByteArray()
            }
        }

        requestQueue.add(stringRequest)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRepeatingTask()
    }
}