package com.example.inhurdle;


import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;

import android.util.Log;
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
    private static List<String> classNames;
    private static List<Scalar> colors=new ArrayList<>();
    private Net net;
    private CameraBridgeViewBase mOpenCvCameraView;
    private boolean permissionGranted = false;

    BaseLoaderCallback mLoaderCallback;
    private MediaPlayer mediaPlayer;

    private static boolean[]canSpeak = new boolean[4]; //장애물을 중복 없이 음성 안내 하기 위한 배열


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

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
                        Log.i(TAG, "CameraView is enable");
                    } break;
                    default:
                    {
                        super.onManagerConnected(status);
                    } break;
                }
            }
        };
        classNames = readLabels("obj.txt", this);
        for(int i=0; i<classNames.size(); i++)
            colors.add(randomColor());
        Log.i(TAG, "classNames colors");
    }
    @Override
    protected void onStart() {
        super.onStart();
        boolean _Permission = true; //변수 추가
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){ //최소 버전보다 버전이 높은지 확인
            if(ContextCompat.checkSelfPermission(CameraActivity.this,
                    android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(CameraActivity.this,
                        new String[]{android.Manifest.permission_group.CAMERA,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            } else {
                Log.i(TAG, "Permission pass");
            }
        }
        if(_Permission){
            onCameraPermissionGranted(); //카메라 접근 권한
            Log.i(TAG, "Permission success");
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
        Log.d(TAG, "onCameraViewStarted");
        String modelConfiguration = getAssetsFile("yolov4-custom.cfg", this);
        String modelWeights = getAssetsFile("yolov4-custom_best.weights", this);
        net = Dnn.readNetFromDarknet(modelConfiguration, modelWeights); //yolo 모델 로딩
        Log.i(TAG, "Dnn.readNetFromDarknet");
    }



    @Override
    public void onCameraViewStopped() {
        Log.i(TAG, "onCameraViewStopped");
    }



    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        //가장 중요한 함수

        Log.d(TAG, "onCameraFrame");
        Mat frame = inputFrame.rgba();
        Imgproc.cvtColor(frame, frame, Imgproc.COLOR_RGBA2RGB); //이미지 프로세싱
        Log.d(TAG, "Imgproc");
        Size frame_size = new Size(256, 256);
        Scalar mean = new Scalar(127.5);

        Mat blob = Dnn.blobFromImage(frame, 1.0 / 255.0, frame_size, mean, true, false);
        //뉴런 네트워크에 이미지 넣기
        net.setInput(blob);
        Log.d(TAG, "Dnn.blobFromImage");


        List<Mat> result = new ArrayList<>(); //yolov4 레이어
        List<String> outBlobNames = net.getUnconnectedOutLayersNames(); //yolov4 레이어 이름
        net.forward(result, outBlobNames); //순전파 진행 - onCameraViewStarted()에서 net으로 이미 받아옴
        Log.d(TAG, "forward");

        float confThreshold = 0.3f; //0.3 이상의 확률만 출력

        Arrays.fill(canSpeak,false); //Bounding Box 출력 이전, 클래스 안내 위한 배열 false로 초기화


        //Bounding Box 출력
        for (int i = 0; i < result.size(); ++i) {

            Mat level = result.get(i);
            for (int j = 0; j < level.rows(); ++j) {
                Mat row = level.row(j);
                Mat scores = row.colRange(5, level.cols());
                Core.MinMaxLocResult mm = Core.minMaxLoc(scores);
                float confidence = (float) mm.maxVal; //객체 감지 퍼센트
                Point classIdPoint = mm.maxLoc; //여러개의 클래스들 중에 가장 정확도가 높은 클래스 찾기


                if (confidence > confThreshold) { //threshold보다 높게 감지된 객체만 표시하기

                    int centerX = (int) (row.get(0, 0)[0] * frame.cols()); //bounding box 중앙 x좌표
                    int centerY = (int) (row.get(0, 1)[0] * frame.rows()); //box 중앙 y좌표
                    int width = (int) (row.get(0, 2)[0] * frame.cols()); //box width
                    int height = (int) (row.get(0, 3)[0] * frame.rows());//box height

                    Log.i(TAG, "frame.cols() :" + frame.cols());
                    Log.i(TAG, "frame.rows() :" + frame.rows());
                    Log.i(TAG, "width :" + width);
                    Log.i(TAG, "height :" + height);

                    int left = (int)(centerX - width * 0.5);
                    int top = (int)(centerY - height * 0.5);
                    int right = (int)(centerX + width * 0.5);
                    int bottom = (int)(centerY + height * 0.5);


                    Point left_top = new Point(left, top);
                    Point right_bottom=new Point(right, bottom);

                    Point label_left_top = new Point(left, top-5);
                    DecimalFormat df = new DecimalFormat("#.##"); //클래스 확률 포맷

                    int class_id = (int) classIdPoint.x; //클래스명
                    String label= classNames.get(class_id) + ": " + df.format(confidence); //클래스명 + 클래스 확률
                    Scalar color= colors.get(class_id); //클래스별 컬러


                    Imgproc.rectangle(frame, left_top, right_bottom, color, 3, 2);
                    Imgproc.putText(frame, label, label_left_top, Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(0, 0, 0), 4); //글자 그림자 넣어 주려고
                    Imgproc.putText(frame, label, label_left_top, Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 2);

                    Log.i(TAG, "label - " + label);
                    canSpeak[class_id] = true;


                }

            }

        }

        speakClasses(canSpeak); //음성 안내
        Log.i(TAG, "------------------one frame------------------");
        return frame;
    }

    public void speakClasses(boolean bool[]) {

        try {
            if (bool[0]) { //obj.txt의 클래스명 순서와 동일 (0:bollard 1:pole 2:person 3:etc)
                mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.bollard);
                mediaPlayer.start();
                Log.i(TAG, "bollard");
                Thread.sleep(3000); //음성 겹치지 않게 3초 기다리기
            }
            if (bool[1]) {
                mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.pole);
                mediaPlayer.start();
                Log.i(TAG, "pole");
                Thread.sleep(3000);

            }
            if (bool[2]) {
                mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.person);
                mediaPlayer.start();
                Log.i(TAG, "person");
                Thread.sleep(3000);

            }
            if(bool[3]){
                mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.etc);
                mediaPlayer.start();
                Log.i(TAG, "etc");
                Thread.sleep(3000);
            }

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }


    private static String getAssetsFile(String file, Context context) {
        Log.d(TAG, "getAssetsFile");
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



    private List<String> readLabels (String file, Context context)
    {
        Log.d(TAG, "readLabels");
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