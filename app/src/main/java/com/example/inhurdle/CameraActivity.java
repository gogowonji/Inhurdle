package com.example.inhurdle;


import static android.Manifest.permission_group.CAMERA;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import android.util.Log;
import android.util.Pair;

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

    // 클래스별 장애물 인식시 갯수 파악과 좌표를 받아오는 리스트
    private static List<Integer> bollard = new ArrayList<>();
    private static List<Integer> pole = new ArrayList<>();
    private static List<Integer> person = new ArrayList<>();
    private static List<Integer> etc = new ArrayList<>();
    private static List<Integer> kickboard = new ArrayList<>();
    private static List<Integer> bicycle = new ArrayList<>();
    private static List<Integer> car = new ArrayList<>();


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
        Mat blob = Dnn.blobFromImage(frame, 1.0 / 255.0, frame_size, mean, false, false);


        net.setInput(blob);

        List<Mat> result = new ArrayList<>(); //yolo 레이어
        List<String> outBlobNames = net.getUnconnectedOutLayersNames(); //yolo 레이어 이름
        net.forward(result, outBlobNames); //순전파 진행 및 추론 - onCameraViewStarted()에서 net으로 이미 받아옴
        Log.i(TAG, "순전파 진행");

        float confThreshold = 0.3f; //0.3 이상의 확률만 출력
        int[]canSpeak = new int[7]; //한 프레임의 장애물을 갯수 세기 + 음성 안내 하기 위한 배열
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

                    //장애물 인식시 클래스별로 바운딩 박스의 중앙 좌표 리스트에 추가
                    if(class_id == 0){
                        bollard.add(centerX); //볼라드
                    }else if(class_id == 1){
                        pole.add(centerX); //기둥
                    }else if(class_id == 2){
                        person.add(centerX); //사람
                    }else if(class_id == 3){
                        etc.add(centerX); //기타 장애물
                    }else if(class_id == 4){
                        kickboard.add(centerX); //킥보드
                    }else if(class_id == 5){
                        bicycle.add(centerX); //자전거
                    }else{
                        car.add(centerX); //자동차
                    }


                    Log.i(TAG, "바운딩 박스 끝");


                }

            }

        }
        Log.i(TAG, "frame.cols() : " + frame.cols());

        naviFunc(frame.size(), canSpeak); //frame의 가로 크기 받아오기, 인덱스별 개수 받아오기

        return frame;
    }

    public void naviFunc(Size f, int classes[]){
        //모든 리스트 좌표를 확인 -> 우선순위 배열로 개수 세기

        //음성안내를 위한 우선순위 배열
        int first[] = new int[7]; //첫번째 우선순위
        int second[] = new int[7]; //두번째 우선순위
        int third[] = new int[7]; //세번째 우선순위

        for(int i = 0; i < 7; i++){
            if(classes[i] > 0){ //클래스가 하나이상 있을 경우에만 확인
                //해당 i가 클래스 인덱스 번호임
                //0:볼라드, 1:기둥, 2:사람, 3:기타 장애물, 4:킥보드, 5:자전거, 6:자동차

                if(i == 0){
                    coordinate(f,bollard); //좌표로 좌, 우, 중앙 영역 중 어디에 위치하는지 확인
                    for(int g = 0; g < bollard.size(); g++){
                        if(bollard.get(g) == 1){ //우선순위 1이라면 첫번째 우선순위에 개수 추가
                            first[i]++;
                        }else if(bollard.get(g) == 2){ //우선순위 2이라면 두번째 우선순위에 개수 추가
                            second[i]++;
                        }else{ //우선순위 3이라면 세번째 우선순위에 개수 추가
                            third[i]++;
                        }
                    }
                }else if(i == 1){
                    coordinate(f,pole);
                    for(int g = 0; g < pole.size(); g++){
                        if(pole.get(g) == 1){
                            first[i]++;
                        }else if(pole.get(g) == 2){
                            second[i]++;
                        }else{
                            third[i]++;
                        }
                    }
                }else if(i == 2) {
                    coordinate(f,person);
                    for(int g = 0; g < person.size(); g++){
                        if(person.get(g) == 1){
                            first[i]++;
                        }else if(person.get(g) == 2){
                            second[i]++;
                        }else{
                            third[i]++;
                        }
                    }
                }else if(i == 3){
                    coordinate(f,etc);
                    for(int g = 0; g < etc.size(); g++){
                        if(etc.get(g) == 1){
                            first[i]++;
                        }else if(etc.get(g) == 2){
                            second[i]++;
                        }else{
                            third[i]++;
                        }
                    }
                }else if(i == 4){
                    coordinate(f,kickboard);
                    for(int g = 0; g < kickboard.size(); g++){
                        if(kickboard.get(g) == 1){
                            first[i]++;
                        }else if(kickboard.get(g) == 2){
                            second[i]++;
                        }else{
                            third[i]++;
                        }
                    }
                }else if(i == 5){
                    coordinate(f,bicycle);
                    for(int g = 0; g < bicycle.size(); g++){
                        if(bicycle.get(g) == 1){
                            first[i]++;
                        }else if(bicycle.get(g) == 2){
                            second[i]++;
                        }else{
                            third[i]++;
                        }
                    }
                }else{
                    coordinate(f,car);
                    for(int g = 0; g < car.size(); g++){
                        if(car.get(g) == 1){
                            first[i]++;
                        }else if(car.get(g) == 2){
                            second[i]++;
                        }else{
                            third[i]++;
                        }
                    }
                }
            }
        }

        speakClasses(first, second, third); //장애물 갯수가 담긴 우선순위 배열을 사용하여 음성안내

    }

    public void coordinate(Size f, List<Integer> A){
        //좌표 확인 후 해당 좌표 대체하고 우선순위 써주기
        int priority = 0;

        for(int k = 0; k < A.size(); k++){
            Log.i(TAG, "A.get(" + k + "): " + A.get(k));
            priority = location(f, A.get(k)); //세 영역중 어디에 해당하는지 확인
            A.set(k,priority); //좌표 대체후 우선순위 써주기

        }
    }


    public int location(Size f, int midX){
        //세 영역 중 어디에 해당하는지 확인

        double wid = f.width; //프레임의 가로 길이 받아오기

        double left = wid/3;
        double right = wid/3*2;
        //3등분으로 나누기

        if(midX < left){ //좌측:우선순위2
            return 2;
        }else if(left <= midX & midX < right){ //중앙:우선순위1
            return 1;
        }else{ //우측:우선순위3
            return 3;
        }

    }


    /*
    영역별 안내 음성 출력
    우선순위
    firstSound() : 중앙
    secondSound() : 좌측
    thirdSound() : 우측
    */


    public void speakClasses(int first[], int second[], int third[]) {
        //우선순위 순으로 음성 안내
        for(int j = 0; j < 7; j++){
            if(first[j] > 0){ //중앙
                firstSound(j);
            }
        }
        for(int j = 0; j < 7; j++){
            if(second[j] > 0){ //좌측
                secondSound(j);
            }
        }
        for(int j = 0; j < 7; j++){
            if(third[j] > 0){ //우측
                thirdSound(j);
            }
        }

    }


    public void firstSound(int j){ //중앙 음성
        try {
            if(j == 0){
                mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.bollard1);
                mediaPlayer.start(); //볼라드 안내 음성 출력
                Thread.sleep(3000);
            }else if(j == 1){
                mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.pole1);
                mediaPlayer.start(); //기둥 안내 음성 출력
                Thread.sleep(3000);
            }else if(j == 2){
                mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.person1);
                mediaPlayer.start(); //사람 안내 음성 출력
                Thread.sleep(3000);
            }else if(j == 3){
                mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.etc1);
                mediaPlayer.start(); //기타 장애물 안내 음성 출력
                Thread.sleep(3000);
            }else if(j == 4){
                mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.kickboard1);
                mediaPlayer.start(); //킥보드 안내 음성 출력
                Thread.sleep(3000);
            }else if(j == 5){
                mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.bicycle1);
                mediaPlayer.start(); //자전거 안내 음성 출력
                Thread.sleep(3000);
            }else{
                mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.car1);
                mediaPlayer.start(); //자동차 안내 음성 출력
                Thread.sleep(3000);
            }
        }catch (InterruptedException e){
            throw new RuntimeException(e);
        }
    }

    public void secondSound(int j){ //좌측 음성
        try {
            if(j == 0){
                mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.bollard2);
                mediaPlayer.start(); //볼라드 안내 음성 출력
                Thread.sleep(3000);
            }else if(j == 1){
                mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.pole2);
                mediaPlayer.start(); //기둥 안내 음성 출력
                Thread.sleep(3000);
            }else if(j == 2){
                mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.person2);
                mediaPlayer.start(); //사람 안내 음성 출력
                Thread.sleep(3000);
            }else if(j == 3){
                mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.etc2);
                mediaPlayer.start(); //기타 장애물 안내 음성 출력
                Thread.sleep(3000);
            }else if(j == 4){
                mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.kickboard2);
                mediaPlayer.start(); //킥보드 안내 음성 출력
                Thread.sleep(3000);
            }else if(j == 5){
                mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.bicycle2);
                mediaPlayer.start(); //자전거 안내 음성 출력
                Thread.sleep(3000);
            }else{
                mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.car2);
                mediaPlayer.start(); //자동차 안내 음성 출력
                Thread.sleep(3000);
            }
        }catch (InterruptedException e){
            throw new RuntimeException(e);
        }
    }

    public void thirdSound(int j){ //우측 음성
        try {
            if(j == 0){
                mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.bollard3);
                mediaPlayer.start(); //볼라드 안내 음성 출력
                Thread.sleep(3000);
            }else if(j == 1){
                mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.pole3);
                mediaPlayer.start(); //기둥 안내 음성 출력
                Thread.sleep(3000);
            }else if(j == 2){
                mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.person3);
                mediaPlayer.start(); //사람 안내 음성 출력
                Thread.sleep(3000);
            }else if(j == 3){
                mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.etc3);
                mediaPlayer.start(); //기타 장애물 안내 음성 출력
                Thread.sleep(3000);
            }else if(j == 4){
                mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.kickboard3);
                mediaPlayer.start(); //킥보드 안내 음성 출력
                Thread.sleep(3000);
            }else if(j == 5){
                mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.bicycle3);
                mediaPlayer.start(); //자전거 안내 음성 출력
                Thread.sleep(3000);
            }else{
                mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.car3);
                mediaPlayer.start(); //자동차 안내 음성 출력
                Thread.sleep(3000);
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