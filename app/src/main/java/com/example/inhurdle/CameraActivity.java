package com.example.inhurdle;


import static android.Manifest.permission_group.CAMERA;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;

import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.imgproc.Imgproc;


public class CameraActivity extends AppCompatActivity implements CvCameraViewListener2 {
    private static final String TAG = "CameraActivity";
    private static List<String> classNames; //클래스명 받아오는 리스트
    private static List<Scalar> colors=new ArrayList<>(); //박스 컬러 저장하는 리스트

    private Net net; //YOLOv4 모델 받아올 변수
    private CameraBridgeViewBase mOpenCvCameraView; //카메라 연결 변수
    BaseLoaderCallback mLoaderCallback; //카메라 연결 확인 콜백 변수
    private MediaPlayer mediaPlayer; //음성 출력 변수
    private static int[]canSpeak = new int[7]; //한 프레임의 장애물을 갯수 세기 + 음성 안내 하기 위한 배열

    private static int[]centerXlist = new int[7]; //구체적 위치 안내시 bounding box x좌표 저장 위한 배열
    private static int[]centerYlist = new int[7]; //구체적 위치 안내시 bounding box y좌표 저장 위한 배열
    //private static Pair<Integer,Integer> center1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        //카메라 연결하기
        mOpenCvCameraView = findViewById(R.id.CameraView);
        mOpenCvCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK);
        mOpenCvCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mLoaderCallback = new BaseLoaderCallback(this) {
            @Override
            public void onManagerConnected(int status) {
                super.onManagerConnected(status);

                switch (status) {
                    case LoaderCallbackInterface.SUCCESS:
                    {
                        Log.i(TAG, "OpenCV loaded successfully");
                        mOpenCvCameraView.enableView();
                    } break;
                    default:
                    {
                        super.onManagerConnected(status);
                    } break;
                }
            }
        };
        classNames = readLabels("obj.txt", this); //클래스명 가져오기
        //bounding box 색상 정하기
        for(int i=0; i<classNames.size(); i++)
            colors.add(randomColor());

    }
    @Override
    protected void onStart() {
        super.onStart();
        boolean _Permission = true; //변수 추가
        //권한 설정
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){//최소 버전보다 버전이 높은지 확인
            if(checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 200);
                _Permission = false;
                Log.d(TAG, "Camera permission false");
            }
            if(_Permission){
                onCameraPermissionGranted(); //카메라 접근 권한 확인
                Log.d(TAG, "Camera permissionGranted()");
            }
        }
        //기존 코드
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){ //최소 버전보다 버전이 높은지 확인
            if(ContextCompat.checkSelfPermission(CameraActivity.this,
                    android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(CameraActivity.this,
                        new String[]{android.Manifest.permission_group.CAMERA,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                Log.d(TAG, "External Storage permission");
            }
        }
        if(_Permission){
            onCameraPermissionGranted(); //카메라 접근 권한 확인
            Log.d(TAG, "Camera permissionGranted()");
        }
    }

    //카메라 접근 권한
    protected void onCameraPermissionGranted() {
        List<? extends CameraBridgeViewBase> cameraViews = getCameraViewList();
        if (cameraViews == null) {
            return;
        }
        for (CameraBridgeViewBase cameraBridgeViewBase: cameraViews) {
            if (cameraBridgeViewBase != null) {
                Log.d(TAG, "setCameraPermissionGreanted");
                cameraBridgeViewBase.setCameraPermissionGranted(); //없으면 카메라 화면이 안 켜짐
            }
        }
    }

    //카메라 접근 권한
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(mOpenCvCameraView);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }



    @Override
    public void onCameraViewStarted(int width, int height) {
        String modelConfiguration = getAssetsFile("yolov4-tiny-custom.cfg", this);
        String modelWeights = getAssetsFile("yolov4-tiny-custom_best.weights", this);
        net = Dnn.readNetFromDarknet(modelConfiguration, modelWeights); //yolo 모델 로딩
        Log.i(TAG, "모델 로딩");
    }



    @Override
    public void onCameraViewStopped() {
    }



    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        //가장 중요한 함수

        Mat frame = inputFrame.rgba();
        Imgproc.cvtColor(frame, frame, Imgproc.COLOR_RGBA2RGB); //이미지 프로세싱
        Size frame_size = new Size(416, 416);
        Scalar mean = new Scalar(127.5);

        //뉴런 네트워크에 이미지 넣기
        Mat blob = Dnn.blobFromImage(frame, 1.0 / 255.0, frame_size, mean, false, false); //swapRB 변경
        //Mat blob = Dnn.blobFromImage(frame, 1.0 / 255.0, new Size(416, 416), new Scalar(0, 0, 0),false, false);

        net.setInput(blob);

        List<Mat> result = new ArrayList<>(); //yolo 레이어
        List<String> outBlobNames = net.getUnconnectedOutLayersNames(); //yolo 레이어 이름
        net.forward(result, outBlobNames); //순전파 진행 및 추론 - onCameraViewStarted()에서 net으로 이미 받아옴
        Log.i(TAG, "순전파 진행");

        float confThreshold = 0.3f; //0.3 이상의 확률만 출력

       Arrays.fill(canSpeak,0); //Bounding Box 출력 이전, 클래스 안내 위한 배열 false로 초기화



        //Bounding Box 출력
        for (int i = 0; i < result.size(); ++i) { //레이어 갯수만큼 for문 돌기

            Mat level = result.get(i);
            Log.i(TAG, "포문 진입1");
            for (int j = 0; j < level.rows(); ++j) {
                Mat row = level.row(j);
                Mat scores = row.colRange(5, level.cols()); //레이어 일부열 추출 yolov4-tiny
                // scores : 각 class 에 대한 probability
                Core.MinMaxLocResult mm = Core.minMaxLoc(scores); //scores의 최소 최대 위치 찾기
                float confidence = (float) mm.maxVal; //객체 감지 퍼센트
                Point classIdPoint = mm.maxLoc; //여러개의 클래스들 중에 가장 정확도가 높은 클래스 찾기
                Log.i(TAG, "포문 진입2");

                if (confidence > confThreshold) { //threshold보다 높게 감지된 객체만 표시하기
                    Log.i(TAG, "바운딩 박스 진입 1");
                    int centerX = (int) (row.get(0, 0)[0] * frame.cols()); //bounding box 중앙 x좌표
                    int centerY = (int) (row.get(0, 1)[0] * frame.rows()); //box 중앙 y좌표
                    int width = (int) (row.get(0, 2)[0] * frame.cols()); //box width
                    int height = (int) (row.get(0, 3)[0] * frame.rows()); //box height

                    int left = (int)(centerX - width * 0.5); //bounding box의 좌측상단 x좌표
                    int top = (int)(centerY - height * 0.5); //bounding box의 좌측상단 y좌표
                    int right = (int)(centerX + width * 0.5); //bounding box의 우측하단 x좌표
                    int bottom = (int)(centerY + height * 0.5); //bounding box의 우측하단 y좌표

                    Point left_top = new Point(left, top); //bounding box의 좌측상단 좌표
                    Point right_bottom = new Point(right, bottom); //bounding box의 우측하단 좌표

                    Point label_left_top = new Point(left, top-5); //라벨 좌표
                    DecimalFormat df = new DecimalFormat("#.##"); //클래스 확률 포맷

                    int class_id = (int) classIdPoint.x; //클래스명
                    String label= classNames.get(class_id) + ": " + df.format(confidence); //클래스명 + 클래스 확률
                    Scalar color= colors.get(class_id); //클래스별 컬러

                    Imgproc.rectangle(frame, left_top, right_bottom, color, 3, 2); //bounding box 그리기
                    Imgproc.putText(frame, label, label_left_top, Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(0, 0, 0), 4); //글자 그림자 넣어 주기
                    Imgproc.putText(frame, label, label_left_top, Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 2); //클래스명, 확률 출력

                    canSpeak[class_id]++;  //음성 안내 위해 해당 클래스 인덱스 개수 받아오기
                    centerXlist[class_id] = centerX; //bounding box 중앙 좌표 받아오기 -> 함수 호출시 가장 마지막 객체의 좌표 받아오게 됨
                    centerYlist[class_id] = centerY;
                    Log.i(TAG, "centerX:" + centerX);
                    Log.i(TAG, "centerY:" + centerY);
                    Log.i(TAG, "바운딩 박스 끝");


                }

            }

        }
        Log.i(TAG, "frame.cols() : " + frame.cols());
        //******************************프레임 크기를 가져오기*******************************
        naviFunc(frame.size(), canSpeak, centerXlist, centerYlist); // frame의 가로 크기 받아오기, 인덱스별 개수, 각 객체의 중앙좌표 가져오기

        /*
        아직 단순히 한 번 안내하기 때문에 한 프레임을 받아오고 함수 호출 가능
        여러 번 안내하기 위해서는 for문 안에서 함수 호출 필요
        */


        //speakClasses(canSpeak); //음성 안내
        return frame;
    }


    public void naviFunc(Size f, int classes[], int midX[], int midY[]){
        int priority[] = new int[8];
        for(int i = 0; i < 7; i++){
            if(classes[i] == 1){
                int whereX = midX[i];
                int whereY = midY[i];

                priority[i] = location(f, whereX, whereY); //우선순위 정하기

                Log.i(TAG, "크기 : " + f);
                Log.i(TAG, "위치 : " + midX[i] + ", " + midY[i]);

                //speakClasses(i, priority);

            }
            else if(classes[i] > 1){
                priority[i] = 7; //여러개 있을때
                Log.i(TAG, "여러개 있을때");
                //speakClasses(i,midX);
            }

        }
        speakClasses(priority);

    }

    public int location(Size f, int midX, int midY){

        double wid = f.width;
        double hei = f.height;

        double left = wid/3;
        double right = wid/3*2;
        double row = hei/2;

        if(midX < left){ //대각선 좌측
            if(midY >= row) //아래
                return 3;
            else //위
                return 5;

        }else if(left <= midX & midX < right){ //전방
            if(midY >= row) //아래
                return 1;
            else //위
                return 2;

        }else{ //대각선 우측
            if(midY >= row) //아래
                return 4;
            else //위
                return 6;
        }

    }

    public void speakClasses(int pri[]) {
        try {
            for (int i = 0; i < 7; i++) {
                if (pri[i] == 7) { //여러개 있을 때
                    if (i == 0) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.bollard);
                        mediaPlayer.start(); //장애물 안내 음성 출력
                        Thread.sleep(3000); //음성 겹치지 않게 3초 기다리기
                    } else if (i == 1) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.pole);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 2) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.person);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 3) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.etc);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 4) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.kickboard);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 5) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.bicycle);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.car);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    }
                } else if (pri[i] == 1) { //중앙 가까이
                    if (i == 0) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.bollard2);
                        mediaPlayer.start(); //장애물 안내 음성 출력
                        Thread.sleep(3000); //음성 겹치지 않게 3초 기다리기
                    } else if (i == 1) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.pole2);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 2) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.person2);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 3) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.etc2);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 4) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.kickboard2);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 5) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.bicycle2);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.car2);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    }
                } else if (pri[i] == 2) { //중앙 멀리
                    if (i == 0) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.bollard2);
                        mediaPlayer.start(); //장애물 안내 음성 출력
                        Thread.sleep(3000); //음성 겹치지 않게 3초 기다리기
                    } else if (i == 1) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.pole2);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 2) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.person2);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 3) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.etc2);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 4) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.kickboard2);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 5) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.bicycle2);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.car2);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    }
                } else if (pri[i] == 3) { //왼쪽 가까이
                    if (i == 0) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.bollard1);
                        mediaPlayer.start(); //장애물 안내 음성 출력
                        Thread.sleep(3000); //음성 겹치지 않게 3초 기다리기
                    } else if (i == 1) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.pole1);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 2) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.person1);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 3) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.etc1);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 4) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.kickboard1);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 5) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.bicycle1);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.car1);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    }
                } else if (pri[i] == 4) { //오른쪽 가까이
                    if (i == 0) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.bollard3);
                        mediaPlayer.start(); //장애물 안내 음성 출력
                        Thread.sleep(3000); //음성 겹치지 않게 3초 기다리기
                    } else if (i == 1) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.pole3);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 2) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.person3);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 3) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.etc3);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 4) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.kickboard3);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 5) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.bicycle3);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.car3);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    }
                } else if (pri[i] == 5) { //왼쪽 멀리
                    if (i == 0) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.bollard1);
                        mediaPlayer.start(); //장애물 안내 음성 출력
                        Thread.sleep(3000); //음성 겹치지 않게 3초 기다리기
                    } else if (i == 1) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.pole1);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 2) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.person1);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 3) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.etc1);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 4) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.kickboard1);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 5) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.bicycle1);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.car1);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    }
                } else if (pri[i] == 6) { //오른쪽 멀리
                    if (i == 0) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.bollard3);
                        mediaPlayer.start(); //장애물 안내 음성 출력
                        Thread.sleep(3000); //음성 겹치지 않게 3초 기다리기
                    } else if (i == 1) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.pole3);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 2) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.person3);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 3) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.etc3);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 4) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.kickboard3);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else if (i == 5) {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.bicycle3);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    } else {
                        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.car3);
                        mediaPlayer.start();
                        Thread.sleep(3000);
                    }
                }
            }
        }catch (InterruptedException e){
            throw new RuntimeException(e);
        }

    }

    //YOLOv4 모델 관련 파일 읽어오기
    private static String getAssetsFile(String file, Context context) {

        AssetManager assetManager = context.getAssets();
        BufferedInputStream inputStream;
        try {
            // Read data from assets.
            inputStream = new BufferedInputStream(assetManager.open(file));
            byte[] data = new byte[inputStream.available()];
            inputStream.read(data);
            inputStream.close();
            // Create copy file in storage.
            File outFile = new File(context.getFilesDir(), file);
            FileOutputStream os = new FileOutputStream(outFile);
            os.write(data);
            os.close();
            // Return a path to file which may be read in common way.
            return outFile.getAbsolutePath();
        } catch (IOException ex) {
            Log.i(TAG, "Failed to upload a file");
        }
        return "";
    }


    //obj.txt에서 클래스명 가져오기
    private List<String> readLabels (String file, Context context)
    {
        AssetManager assetManager = context.getAssets();
        BufferedInputStream inputStream;
        List<String> labelsArray = new ArrayList<>();
        try {
            // Read data from assets.
            inputStream = new BufferedInputStream(assetManager.open(file));
            byte[] data = new byte[inputStream.available()];
            inputStream.read(data);
            inputStream.close();
            // Create copy file in storage.
            File outFile = new File(context.getFilesDir(), file);
            FileOutputStream os = new FileOutputStream(outFile);
            os.write(data);
            os.close();
            Scanner fileScanner = new Scanner(new File(outFile.getAbsolutePath())).useDelimiter("\n");
            String label;
            while (fileScanner.hasNext()) {
                label = fileScanner.next();
                labelsArray.add(label);
            }
            fileScanner.close();
        } catch (IOException ex) {
            Log.i(TAG, "Failed to read labels!");
        }
        return labelsArray;
    }


    private Scalar randomColor() {
        Random random = new Random();
        int r = random.nextInt(255);
        int g = random.nextInt(255);
        int b = random.nextInt(255);
        return new Scalar(r,g,b);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
        if(mediaPlayer != null){
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}