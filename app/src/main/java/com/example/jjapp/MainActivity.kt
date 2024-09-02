package com.example.jjapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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
    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 321
        private const val LOCATION_BACKGROUND_PERMISSION_REQUEST_CODE = 3211
    }

    private val messageReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // 브로드캐스트에서 메시지를 추출합니다.
            val message = intent.getStringExtra("message")
            // 메시지를 토스트로 표시합니다.
            Toast.makeText(this@MainActivity, "Received message: $message", Toast.LENGTH_LONG)
                .show()
            getLocationAndSendToServer()
        }
    }

    private val locationReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // 브로드캐스트에서 메시지를 추출합니다.
            val latitude = intent.getDoubleExtra("latitude", 0.0)
            val longitude = intent.getDoubleExtra("longitude", 0.0)

            println("LOMAN GPS: ${latitude},  ${longitude}")
            sendLocationToServer(latitude, longitude)
        }
    }

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

                val url = "http://211.44.188.113:54000/saveToken"
                try {
                    val jsonObject = JSONObject()
                    jsonObject.put("token", token)

                    val stringRequest = object : StringRequest(
                        Request.Method.POST, url,
                        Response.Listener { response ->
                            println("saveToken success")
                        },
                        Response.ErrorListener { error ->
                            error.printStackTrace()
                            println("saveToken fail")
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
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

        // 로컬 브로드캐스트 리시버를 등록합니다.
        LocalBroadcastManager.getInstance(this).registerReceiver(
            messageReceiver,
            IntentFilter("com.example.NEW_MESSAGE")
        )

        LocalBroadcastManager.getInstance(this).registerReceiver(
            locationReceiver,
            IntentFilter("com.loman.location")
        )

        requestPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // 권한 결과 처리
            when {
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true &&
                        permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true -> {
                    // 모든 권한이 허용된 경우
                    handlePermissionsGranted()
                }
                else -> {
                    // 권한이 거부된 경우
                    requestPermissions()
                }
            }
        }

        checkAndRequestPermissions()
    }

    private val PERMISSIONS_REQUEST_CODE = 1001
    private fun checkAndRequestPermissions() {
        val requiredPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.FOREGROUND_SERVICE_LOCATION
        )

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSIONS_REQUEST_CODE
            )
        } else {
            startLocationService()
        }
    }


    private fun startLocationService() {
        val serviceIntent = Intent(this, LocationService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }



    private fun requestPermissions() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                // 모든 권한이 이미 허용된 경우
                handlePermissionsGranted()
            }
            else -> {
                // 권한 요청
                requestPermissionsLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.FOREGROUND_SERVICE_LOCATION
                ))
            }
        }
    }

    private fun handlePermissionsGranted() {
        // 권한이 허용된 경우 실행할 로직

        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                // 모든 권한이 이미 허용된 경우
                startLocationService()
            }
            else -> {
                requestBackgroundLocationPermission()
            }
        }
    }

    private fun handlePermissionsDenied() {
        // 권한이 거부된 경우 실행할 로직
        Toast.makeText(this, "Location permission is required for this app to work.", Toast.LENGTH_LONG).show()
    }


    private fun requestBackgroundLocationPermission() {
        // 배경 위치 권한 요청
        requestPermissionsLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ))
    }

//    private fun startLocationService() {
//        val intent = Intent(this, LocationService::class.java)
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            startForegroundService(intent)
//        } else {
//            startService(intent)
//        }
//    }


//    private fun startRepeatingTask() {
//        handler.post(runnableCode)
//    }
//
//    private fun stopRepeatingTask() {
//        handler.removeCallbacks(runnableCode)
//    }

//    private val runnableCode = object : Runnable {
//        override fun run() {
//            getLocationAndSendToServer()
//            handler.postDelayed(this, interval)
//        }
//    }

    @SuppressLint("MissingPermission")
    private fun getLocationAndSendToServer() {
//        fusedLocationClient.lastLocation
//            .addOnCompleteListener(this) { task ->
//                if (task.isSuccessful && task.result != null) {
//                    val location: Location = task.result
//                    val latitude = location.latitude
//                    val longitude = location.longitude
//                    textViewStart.text = "Latitude: $latitude, Longitude: $longitude"
//                    textViewStop.text = ""
//                    sendLocationToServer(latitude, longitude)
//                } else {
//                    Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show()
//                }
//            }
    }

    private fun sendLocationToServer(latitude: Double, longitude: Double) {
        val url = "http://211.44.188.113:54000/savelocation"
        val jsonObject = JSONObject()
        jsonObject.put("latitude", latitude)
        jsonObject.put("longitude", longitude)

        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                println("LOMAN GPS:Location sent to server")
//                Toast.makeText(this, "Location sent to server: $response", Toast.LENGTH_SHORT).show()
            },
            Response.ErrorListener { error ->
                error.printStackTrace()
                println("LOMAN GPS: Failed to send location")
//                Toast.makeText(this, "Failed to send location: ${error.message}", Toast.LENGTH_SHORT).show()
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
//        stopRepeatingTask()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                startRepeatingTask()
                // 권한이 부여되면 현재 위치를 가져오는 메서드 호출
//                startLocationService()
                requestBackgroundLocationPermission()
            } else {
                // 권한이 거부되면 사용자에게 알림을 표시하거나 다른 조치를 취할 수 있음
            }
        } else if (requestCode == LOCATION_BACKGROUND_PERMISSION_REQUEST_CODE) {
            startLocationService()
        }
    }
}