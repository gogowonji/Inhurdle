package com.example.inhurdle;
import androidx.appcompat.app.AppCompatActivity;



import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;


public class MainActivity extends AppCompatActivity {
    private MediaPlayer mediaPlayer;
    private final String TAG = "MainActivity";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ImageButton useButton = this.findViewById(R.id.Use_btn); //사용방법 버튼
        ImageButton cameraButton = this.findViewById(R.id.Camera_btn); //카메라 버튼
        useButton.setOnClickListener(new View.OnClickListener() { //사용방법 버튼 클릭시
            @Override
            public void onClick(View view) {

                mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.use);
                mediaPlayer.start(); //음성안내 출력
                Log.i(TAG, "mediaPlayer.start()");

            }
        });

        cameraButton.setOnClickListener(new View.OnClickListener() { //카메라 버튼 클릭시
            @Override
            public void onClick(View v) {
                Intent cameraIn = new Intent(MainActivity.this, CameraActivity.class);
                startActivity(cameraIn); //CameraActivity 실행
                finish();
            }
        });


    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        if(mediaPlayer != null){
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}