
package com.example.myapplication;

import android.app.Activity;
import android.app.AlertDialog;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.MediaType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer, gyroscope, gravitySensor;
    private boolean isCollecting = false;
    private Button startStopButton, uploadButton;
    private TextView timerTextView, accelerationDataTextView, gyroscopeDataTextView;
    private EditText frequencyInput;
    private Button setFrequencyButton;
    private int frequency = 50; // 默认频率为50Hz
    private long startTime;
    private Handler handler = new Handler(Looper.getMainLooper());
//    private OkHttpClient client;

    // 分别存储加速度计和陀螺仪的数据
    private List<String> accelerometerDataList = new ArrayList<>();
    private List<String> gyroscopeDataList = new ArrayList<>();
    private List<String> gravityDataList = new ArrayList<>();

    //存储重力加速度数据
    private float[] gravity = new float[3];

    // 新增的用于存储组合数据的列表
//    private List<String> combinedDataList = new ArrayList<>();

    //用于同步的锁对象
    private final Object sensorLock = new Object();

    private String serverIp = "192.168.137.1"; // 替换为实际IP地址

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);

        startStopButton = findViewById(R.id.start_stop_button);
        uploadButton = findViewById(R.id.upload_button);
        timerTextView = findViewById(R.id.timer_text);
        frequencyInput = findViewById(R.id.frequency_input);
        setFrequencyButton = findViewById(R.id.set_frequency_button);
        accelerationDataTextView = findViewById(R.id.acceleration_data);
        gyroscopeDataTextView = findViewById(R.id.gyroscope_data);

        startStopButton.setOnClickListener(v -> {
            if (isCollecting) {
                stopCollecting();
            } else {
                startCollecting();
            }
        });

        setFrequencyButton.setOnClickListener(v -> {
            String input = frequencyInput.getText().toString();
            if (!input.isEmpty()) {
                frequency = Integer.parseInt(input);
            }
        });

        uploadButton.setOnClickListener(v -> showUploadConfirmationDialog());
    }

    private void startCollecting() {
        isCollecting = true;
        startStopButton.setText("停止");
        uploadButton.setVisibility(View.GONE);


        startTime = System.currentTimeMillis();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_FASTEST);
        handler.post(updateTimer);
        // 延迟1秒后开始采集
//        new Handler(Looper.getMainLooper()).postDelayed(() -> {
//            startTime = System.currentTimeMillis();
//            sensorManager.registerListener(MainActivity.this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
//            sensorManager.registerListener(MainActivity.this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);
//            handler.post(updateTimer);
//        }, 1000); // 延迟1秒 (1000毫秒)

    }

    private void stopCollecting() {
        isCollecting = false;
        startStopButton.setText("开始");
        sensorManager.unregisterListener(this);
        handler.removeCallbacks(updateTimer);
        saveDataToFile();
        runOnUiThread(() -> uploadButton.setVisibility(View.VISIBLE));
    }

    private Runnable updateTimer = new Runnable() {
        @Override
        public void run() {
            long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
            timerTextView.setText("采集时间: " + elapsedTime + "s");
            handler.postDelayed(this, 1000);
        }
    };


    @Override
    public void onSensorChanged(SensorEvent event) {
        long currentTime = System.currentTimeMillis(); // 获取当前时间戳
        double secondsElapsed = (currentTime - startTime) / 1000.0;

        synchronized (sensorLock) { // 使用同步锁确保数据同步采集
            if(event.sensor.getType() == Sensor.TYPE_GRAVITY) {
                // 获取重力传感器的数据
                gravity[0] = event.values[0];
                gravity[1] = event.values[1];
                gravity[2] = event.values[2];
            }
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                // 从加速度计数据中去除重力分量，得到线性加速度
                float linearAccelerationX = event.values[0] - gravity[0];
                float linearAccelerationY = event.values[1] - gravity[1];
                float linearAccelerationZ = event.values[2] - gravity[2];

                // 记录线性加速度和时间戳
                String accedata = currentTime + "," + secondsElapsed + "," + linearAccelerationX + "," + linearAccelerationY + "," + linearAccelerationZ;
                accelerometerDataList.add(accedata);

                if (shouldUpdateUI(currentTime)) {
                    runOnUiThread(() -> accelerationDataTextView.setText("X: " + linearAccelerationX + "\nY: " + linearAccelerationY + "\nZ: " + linearAccelerationZ));
                }


            }


            else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                // 记录陀螺仪数据和时间戳
                String gyrodata = currentTime + "," + secondsElapsed + "," + event.values[0] + "," + event.values[1] + "," + event.values[2];
                gyroscopeDataList.add(gyrodata);
                if (shouldUpdateUI(currentTime)) {
                    runOnUiThread(() -> gyroscopeDataTextView.setText("X: " + event.values[0] + "\nY: " + event.values[1] + "\nZ: " + event.values[2]));
                }
            }

        }
    }

    private boolean shouldUpdateUI(long currentTime) {
        // 每隔500ms更新一次UI
        return (currentTime - startTime) % 500 < 50;
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No implementation needed for this example
    }

    private void saveDataToFile() {
        // 保存加速度计数据
        File accelerometerFile = new File(getExternalFilesDir(null), "accelerometer_data.csv");
        try (FileOutputStream fos = new FileOutputStream(accelerometerFile, false)) {
            fos.write("time,seconds_elapsed,acce_x,acce_y,acce_z\n".getBytes());

            for (String data : accelerometerDataList) {
                fos.write((data + "\n").getBytes());
            }
            accelerometerDataList.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 保存陀螺仪数据
        File gyroscopeFile = new File(getExternalFilesDir(null), "gyroscope_data.csv");

        try (FileOutputStream fos = new FileOutputStream(gyroscopeFile, false)) {

            fos.write("time,seconds_elapsed,gyro_x,gyro_y,gyro_z\n".getBytes());

            for (String data : gyroscopeDataList) {
                fos.write((data + "\n").getBytes());
            }
            gyroscopeDataList.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    // Display confirmation dialog for upload
    private void showUploadConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("确认上传")
                .setMessage("确定要将加速度计和陀螺仪数据上传到服务器吗？")
                .setPositiveButton("确认", (dialog, which) -> uploadDataToServer())
                .setNegativeButton("取消", null)
                .show();
    }

    // Upload the collected data to the server
    private void uploadDataToServer() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        // 上传加速度计数据
        File accelerometerFile = new File(getExternalFilesDir(null), "accelerometer_data.csv");
        if (accelerometerFile.exists()) {
            uploadFileToServer(client, accelerometerFile, "accelerometer_data.csv");
        }

        // 上传陀螺仪数据
        File gyroscopeFile = new File(getExternalFilesDir(null), "gyroscope_data.csv");
        if (gyroscopeFile.exists()) {
            uploadFileToServer(client, gyroscopeFile, "gyroscope_data.csv");
        }


    }

    private void uploadFileToServer(OkHttpClient client, File file, String fileName) {
        RequestBody fileBody = RequestBody.create(file, MediaType.parse("text/csv"));
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, fileBody)
                .build();

        Request request = new Request.Builder()
                .url("http://" + serverIp + ":8080/upload")  // 替换为实际的服务器地址
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "上传失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "数据上传成功: " + fileName, Toast.LENGTH_SHORT).show());
                } else {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "上传失败: " + response.message(), Toast.LENGTH_SHORT).show());
                }
            }
        });
    }
}



