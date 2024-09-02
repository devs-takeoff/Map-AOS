package com.example.jjapp;

import static android.util.Log.println;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.media.AudioManager;


import org.json.JSONObject;

@SuppressLint("MissingFirebaseInstanceTokenRefresh")
public class MyFirebaseMessagingService extends FirebaseMessagingService {
    private static final String CHANNEL_ID = "default_channel_id";



    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        // FCM 메시지를 수신했을 때의 처리
        if (remoteMessage.getNotification() != null) {
            // 알림 메시지 처리
            sendNotification(remoteMessage.getNotification().getTitle(), remoteMessage.getNotification().getBody());

            Intent intent = new Intent("com.example.NEW_MESSAGE");
            intent.putExtra("message", remoteMessage.getNotification().getBody());
            // 로컬 브로드캐스트를 사용하여 메시지를 전송합니다.
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);


//            // 현재 알림 볼륨을 저장 (필요에 따라 원래 상태로 되돌리기 위해)
//            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
//
//            // 알림 소리의 최대 볼륨을 가져오기
//            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION);
//
//            // 알림 소리의 볼륨을 최대치로 설정
//            audioManager.setStreamVolume(STREAM_NOTIFICATION, maxVolume, AudioManager.FLAG_PLAY_SOUND);

        }
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        String url = "http://211.44.188.113:54000/saveToken";

        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("token", token);

            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                    Request.Method.POST, url, jsonObject,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {

                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {

                        }
                    }
            );

            RequestQueue requestQueue = Volley.newRequestQueue(this);
            requestQueue.add(jsonObjectRequest);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendNotification(String title, String body) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.png_3304)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setSound(defaultSoundUri, AudioManager.STREAM_ALARM)
                        .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Channel human readable title", NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        notificationManager.notify(0, notificationBuilder.build());
    }
}