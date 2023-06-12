package com.example.inhurdle;


import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.*;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;

import org.opencv.dnn.Dnn;
import org.opencv.utils.Converters;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.nio.Buffer;
import java.util.Random;


import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.Manifest.permission_group.CAMERA;
import static android.content.ContentValues.TAG;
import static android.os.Environment.getExternalStorageDirectory;

import static org.opencv.imgproc.Imgproc.FONT_HERSHEY_SIMPLEX;

public class MainActivity extends AppCompatActivity
        implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String WEIGHTS_NAME = "yolov4-custom_best.weights";
    private static final String CFG_FILE = "yolov4-custom.cfg";

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 200;
    private final String TAG = "MainActivity";
    CameraBridgeViewBase cameraBridgeViewBase;
    BaseLoaderCallback baseLoaderCallback;
    boolean startYolo=false;
    boolean firstTimeYolo=false;
    Net yolo;

    private Mat matResult;

    private static String getPath(String file, Context context){
        AssetManager assetManager =context.getAssets();
        BufferedInputStream inputStream=null;
        try {
            inputStream=new BufferedInputStream(assetManager.open(file));
            byte[] data=new byte[inputStream.available()]; //TODO
            inputStream.read(data);
            inputStream.close();
            File outFile=new File(context.getFilesDir(),file);
            FileOutputStream os=new FileOutputStream(outFile);
            os.write(data);
            os.close();
            return outFile.getAbsolutePath();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }
    public void YOLO(View Button){

        Log.i(TAG, "YOLO!!!");
        if (startYolo == false){
            startYolo = true;
            if(firstTimeYolo==false){
                firstTimeYolo = true;
                String yoloCfg = getPath(CFG_FILE, this); //핸드폰내 외부 저장소 경로
                String yoloWeights = getPath(WEIGHTS_NAME, this); //TODO

                yolo = Dnn.readNetFromDarknet(yoloCfg, yoloWeights);
                Log.i(TAG, "path success!");
            }

        } else{
            startYolo = false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraBridgeViewBase = (CameraBridgeViewBase)findViewById(R.id.CameraView);

        cameraBridgeViewBase.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK);
        // front-camera(1), back-camera(0)
        cameraBridgeViewBase.setVisibility(SurfaceView.VISIBLE);
        cameraBridgeViewBase.setCvCameraViewListener(this);
        //System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        baseLoaderCallback = new BaseLoaderCallback(this) {
            @Override
            public void onManagerConnected(int status) {
                super.onManagerConnected(status);

                switch(status){

                    case LoaderCallbackInterface.SUCCESS:
                        Log.i(TAG, "OpenCV loaded successfully");

                        //cameraBridgeViewBase.setOnTouchListener(MainActivity.this);
                        cameraBridgeViewBase.enableView();
                        Log.i(TAG, "cameraBridgeViewBase.enableView()");

                        break;
                    default:
                        super.onManagerConnected(status);
                        break;
                }
            }
        };


        boolean load = OpenCVLoader.initDebug();
        if (load) {
            Log.i(TAG, "Open CV Libraries loaded...");
        } else {
            Log.i(TAG, "Open CV Libraries not loaded...");
        }




    }

    @Override
    protected void onStart() {
        super.onStart();
        boolean _Permission = true; //변수 추가
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){//최소 버전보다 버전이 높은지 확인
            if(ContextCompat.checkSelfPermission(MainActivity.this,
                    android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{android.Manifest.permission_group.CAMERA,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            } else {
                Log.i("MainActivity", "get permission");
            }
        }
        if(_Permission){
            //여기서 카메라뷰 받아옴
            onCameraPermissionGranted();
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
                cameraBridgeViewBase.setCameraPermissionGranted();
            }
        }
    }


    @Override
    public void onCameraViewStarted(int width, int height) {

        Log.i(TAG, "onCameraViewStarted");
        //카메라 뷰 시작될때
        if (startYolo == true){

            String yoloCfg = getPath(CFG_FILE, this); //핸드폰내 외부 저장소 경로
            String yoloWeights = getPath(WEIGHTS_NAME, this);
            yolo = Dnn.readNetFromDarknet(yoloCfg, yoloWeights);
            Log.i(TAG, "GO YOLO!");
        }


    }


    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(cameraBridgeViewBase);
    }



    @Override
    public void onCameraViewStopped() {
        Log.d(TAG, "onCameraViewStopped");
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        //가장 중요한 함수, 여기서 캡쳐하거나 다른 이미지를 삽입하거나 rgb 바꾸거나 등등 수행(여러 트리거를 줄 수 있음)
        //Mat을 활용하여 이미지를 파이썬의 매트릭스 배열처럼 저장할 수 있다
        Log.d(TAG, "onCameraFrame");
        Mat frame = inputFrame.rgba(); //프레임 받기

        if ( matResult == null )
            matResult = new Mat(frame.rows(), frame.cols(), frame.type());

        if (startYolo == true) {
            //Imgproc을 이용해 이미지 프로세싱을 한다.
            Log.d(TAG, "start");
            Imgproc.cvtColor(frame, frame, Imgproc.COLOR_RGBA2RGB);//rgba 체계를 rgb로 변경
            //Imgproc.Canny(frame, frame, 100, 200);
            //Mat gray=Imgproc.cvtColor(frame, frame, Imgproc.COLOR_BGR2GRAY)
            Mat imageBlob = Dnn.blobFromImage(frame, 0.00392, new Size(256, 256), new Scalar(0, 0, 0),/*swapRB*/false, /*crop*/false);
            //뉴런 네트워크에 이미지 넣기

            yolo.setInput(imageBlob);

            //cfg 파일에서 yolo layer number을 확인하여 이를 순전파에 넣어준다.
            //yolv3-tiny는 yolo layer 2개라서 initialCapacity를 2로 준다.
            //yolov4는 layer 3개 -> 3으로 줘야 함
            java.util.List<Mat> result = new java.util.ArrayList<Mat>(3);

            List<String> outBlobNames = new java.util.ArrayList<>();


            //순전파를 진행
            outBlobNames.add(0, "yolo_139");
            outBlobNames.add(1, "yolo_150");
            outBlobNames.add(2, "yolo_161");
            Log.d(TAG, "before forward");
            yolo.forward(result,outBlobNames); //TODO
            Log.d(TAG, "after forward");
            //20%이상의 확률만 출력해준다.
            float confThreshold = 0.2f;

            Log.d(TAG, "YOLO is processing...");

            //class id
            List<Integer> clsIds = new ArrayList<>();
            //
            List<Float> confs = new ArrayList<>();
            //draw rectanglelist
            List<Rect> rects = new ArrayList<>();


            for (int i = 0; i < result.size(); ++i) {

                Mat level = result.get(i);

                for (int j = 0; j < level.rows(); ++j) { //iterate row
                    Mat row = level.row(j);
                    Mat scores = row.colRange(5, level.cols());

                    Core.MinMaxLocResult mm = Core.minMaxLoc(scores);


                    float confidence = (float) mm.maxVal;

                    //여러개의 클래스들 중에 가장 정확도가 높은(유사한) 클래스 아이디를 찾아낸다.
                    Point classIdPoint = mm.maxLoc;


                    if (confidence > confThreshold) {
                        int centerX = (int) (row.get(0, 0)[0] * frame.cols());
                        int centerY = (int) (row.get(0, 1)[0] * frame.rows());
                        int width = (int) (row.get(0, 2)[0] * frame.cols());
                        int height = (int) (row.get(0, 3)[0] * frame.rows());


                        int left = centerX - width / 2;
                        int top = centerY - height / 2;

                        clsIds.add((int) classIdPoint.x);
                        confs.add((float) confidence);


                        rects.add(new Rect(left, top, width, height));

                        Log.d(TAG, "YOLO classes are processing...");
                    }
                }
            }
            int ArrayLength = confs.size();

            if (ArrayLength >= 1) {
                // Apply non-maximum suppression procedure.
                float nmsThresh = 0.2f;


                MatOfFloat confidences = new MatOfFloat(Converters.vector_float_to_Mat(confs));


                Rect[] boxesArray = rects.toArray(new Rect[0]);

                MatOfRect boxes = new MatOfRect(boxesArray);

                MatOfInt indices = new MatOfInt();


                Dnn.NMSBoxes(boxes, confidences, confThreshold, nmsThresh, indices);


                // Draw result boxes:
                int[] ind = indices.toArray();
                for (int i = 0; i < ind.length; ++i) {

                    int idx = ind[i];
                    Rect box = boxesArray[idx];

                    int idGuy = clsIds.get(idx);

                    float conf = confs.get(idx);

                    Log.d(TAG, "YOLO Boxes is processing...");

                    List<String> cocoNames = Arrays.asList("bollard","pole","person","etc");
                    int intConf = (int) (conf * 100);


                    Imgproc.putText(frame, cocoNames.get(idGuy) + " " + intConf + "%", box.tl(),
                            FONT_HERSHEY_SIMPLEX, 2, new Scalar(255, 255, 0), 2);

                    Imgproc.rectangle(frame, box.tl(), box.br(), new Scalar(255, 0, 0), 2);
                }
            }
        }
        return frame; //프레임 리턴
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!OpenCVLoader.initDebug()){
            Toast.makeText(getApplicationContext(),"There's a problem, yo!", Toast.LENGTH_SHORT).show();

            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, baseLoaderCallback);
        }

        else
        {
            baseLoaderCallback.onManagerConnected(baseLoaderCallback.SUCCESS);
        }



    }

    @Override
    protected void onPause() {
        //카메라뷰 중지
        super.onPause();
        if(cameraBridgeViewBase!=null){

            cameraBridgeViewBase.disableView();
        }
        Log.d(TAG, "onPause()");

    }


    @Override
    protected void onDestroy() {
        //카메라뷰 종료
        super.onDestroy();
        if (cameraBridgeViewBase!=null){
            cameraBridgeViewBase.disableView();
        }
        Log.d(TAG, "onDestroy()");
    }
}