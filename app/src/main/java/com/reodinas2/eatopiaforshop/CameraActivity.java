package com.reodinas2.eatopiaforshop;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.reodinas2.eatopiaforshop.api.FaceApi;
import com.reodinas2.eatopiaforshop.api.NetworkClient;
import com.reodinas2.eatopiaforshop.model.Res;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

@ExperimentalGetImage
public class CameraActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PERMISSIONS = 1000;
    public static int RESTAURANT_ID = 17810;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

    private PreviewView previewView;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    ProcessCameraProvider cameraProvider;

    SimpleDateFormat sf; // UTC 타임존을 위한 변수
    SimpleDateFormat df; // Local 타임존을 위한 변수
    String reservTime;

    private InputImage image;
    // Initialize the face detector
    private FaceDetectorOptions options =
            new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                    .setMinFaceSize(0.4f)
                    .enableTracking()
                    .build();
    private FaceDetector detector = FaceDetection.getClient(options);
    int previousTrackingId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        requestPermissions();

        sf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        sf.setTimeZone(TimeZone.getTimeZone("UTC"));
        df.setTimeZone(TimeZone.getDefault());

        previewView = findViewById(R.id.previewView);

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                startCameraX(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, getExecutor());


    }

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this,
                        android.Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.CAMERA, android.Manifest.permission.READ_MEDIA_IMAGES},
                    REQUEST_CODE_PERMISSIONS);
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                return;
            } else {
                Toast.makeText(this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
    }


    Executor getExecutor() {
        return ContextCompat.getMainExecutor(this);
    }

    @SuppressLint("RestrictedApi")
    private void startCameraX(ProcessCameraProvider cameraProvider) {
        cameraProvider.unbindAll();
        // Camera Selector use case
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();
        // Preview use case
        Preview preview = new Preview.Builder()
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Image capture use case
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();


        // Image analysis use case
        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(getExecutor(), new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy imageProxy) {
                image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());
                // Detect faces in the image
                Task<List<Face>> result =
                        detector.process(image)
                                .addOnSuccessListener(new OnSuccessListener<List<Face>>() {
                                    @Override
                                    public void onSuccess(List<Face> faces) {

                                        for (Face face : faces) {

                                            if (face.getTrackingId() != null){
                                                int currentTrackingId = face.getTrackingId();
                                                Log.i("EATOPIA", "" + currentTrackingId);

                                                if (currentTrackingId != previousTrackingId){
                                                    Snackbar.make(previewView, "얼굴을 감지했습니다.", Snackbar.LENGTH_SHORT).show();

                                                    capturePhoto();
                                                    previousTrackingId = currentTrackingId;

                                                }
                                            }

                                        }

                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {

                                    }
                                })
                                .addOnCompleteListener(new OnCompleteListener<List<Face>>() {
                                    @Override
                                    public void onComplete(@NonNull Task<List<Face>> task) {
                                        imageProxy.close();
                                    }
                                });

            }
        });

        //bind to lifecycle:
        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture, imageAnalysis);

    }

    private void capturePhoto() {

        long timestamp = System.currentTimeMillis();

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, timestamp);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");

        imageCapture.takePicture(
                new ImageCapture.OutputFileOptions.Builder(
                        getContentResolver(),
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                ).build(),
                getExecutor(),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Toast.makeText(CameraActivity.this, "고객정보를 확인 중...", Toast.LENGTH_SHORT).show();

                        Uri savedUri = outputFileResults.getSavedUri();
                        String filePath = getRealPathFromURI(savedUri);
                        File imageFile = new File(filePath);

                        Retrofit retrofit  = NetworkClient.getRetrofitClient(CameraActivity.this);
                        FaceApi api = retrofit.create(FaceApi.class);

                        // 멀티파트로 파일을 보내는 경우, 파일 파라미터를 만든다.
                        RequestBody fileBody = RequestBody.create(imageFile, MediaType.parse("image/jpg"));
                        MultipartBody.Part photo = MultipartBody.Part.createFormData("photo", imageFile.getName(), fileBody);

                        Call<Res> call = api.faceSearch(RESTAURANT_ID, photo);

                        call.enqueue(new Callback<Res>() {
                            @Override
                            public void onResponse(Call<Res> call, Response<Res> response) {
                                Log.i("LOGCAT", "Success");
                                Log.i("LOGCAT", String.valueOf(response));

                                if (response.isSuccessful()) {
                                    if (response.body().getMsg() != null) {
                                        Toast toast = Toast.makeText(CameraActivity.this, "" +response.body().getMsg(), Toast.LENGTH_SHORT);
//                                        toast.setGravity(Gravity.CENTER, 0, 0);
                                        toast.show();
                                        Log.i("LOGCAT", "1. "+response.body().getMsg());

                                    }else if (response.body().getOrderInfo().isEmpty()){
                                        Toast toast = Toast.makeText(CameraActivity.this,
                                                response.body().getUserInfo().getNickname()+"님. 현재 매장에서 진행중인 주문내역이 없습니다.", Toast.LENGTH_SHORT);
//                                        toast.setGravity(Gravity.CENTER, 0, 0);
                                        toast.show();
                                        Log.i("LOGCAT", "2. 등록된 고객입니다 " +response.body().getUserInfo().getNickname());

                                    }else {

                                        Log.i("LOGCAT", "2. 등록된 고객입니다 " +response.body().getUserInfo().getNickname());

                                        try {
                                            String reservTimeUTC = response.body().getOrderInfo().get(0).getReservTime();
                                            reservTime = df.format(sf.parse(reservTimeUTC));

                                            Toast.makeText(CameraActivity.this,
                                                    response.body().getUserInfo().getNickname()+"님. 예약시간: "
                                                            +reservTime+", 예약인원: "
                                                            +response.body().getOrderInfo().get(0).getPeople()+"명", Toast.LENGTH_SHORT).show();

                                        } catch (ParseException e) {
                                            throw new RuntimeException(e);
                                        }





                                    }

                                }else {
                                    try {
                                        JSONObject errorJson = new JSONObject(response.errorBody().string());
                                        String errorMessage = errorJson.getString("error");
                                        Toast toast = Toast.makeText(CameraActivity.this, "" + errorMessage, Toast.LENGTH_SHORT);
                                        toast.show();
                                        Log.i("LOGCAT", "에러 상태코드: " + response.code() + ", 메시지: " + errorMessage);
                                    } catch (IOException | JSONException e) {
                                        e.printStackTrace();
                                    }
                                }

                                Handler handler = new Handler();
                                handler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        cameraProvider.unbindAll();
                                        finish();
                                    }
                                }, 1000); //딜레이 타임 조절

                            }

                            @Override
                            public void onFailure(Call<Res> call, Throwable t) {
                                Log.i("LOGCAT", "Fail");

                            }
                        });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Toast.makeText(CameraActivity.this, "Error saving photo: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                        Log.i("LOGCAT", String.valueOf(exception));
                    }
                }

        );

    }

    // Uri를 파일 경로로 변환하는 메소드
    private String getRealPathFromURI(Uri contentUri) {
        String[] projection = { MediaStore.Images.Media.DATA };
        Cursor cursor = getContentResolver().query(contentUri, projection, null, null, null);
        if (cursor == null) return null;
        int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        String path = cursor.getString(columnIndex);
        cursor.close();
        return path;
    }

}