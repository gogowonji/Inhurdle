package com.example.inhurdle;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.os.Bundle;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
public class StartActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
        (new Handler(Looper.getMainLooper())).postDelayed((Runnable)(new Runnable() {
            public final void run() {
                Intent intent = new Intent((Context)StartActivity.this, MainActivity.class);
                StartActivity.this.startActivity(intent);
                StartActivity.this.finish();
            }
        }), 3000L); //3초간 splash 화면 띄우기

    }
}