package com.example.libtest;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import android.widget.ToggleButton;

import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.gl.SurfaceView;
import net.majorkernelpanic.streaming.rtsp.RtspClient;

import org.taktik.mpegts.Streamer;
import org.taktik.mpegts.sinks.MTSSink;
import org.taktik.mpegts.sinks.UDPTransport;
import org.taktik.mpegts.sources.InputStreamMTSSource;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.Manifest.permission.CAMERA;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    // log tag
    public final static String TAG = MainActivity.class.getSimpleName();

    // surfaceview
    private static SurfaceView mSurfaceView;

    // Rtsp session
    private Session mSession;
    private static RtspClient mClient;
    private boolean mInitSuccesful;
    private MediaRecorder mMediaRecorder;
    private Camera mCamera;
    private ToggleButton mToggleButton;
    ParcelFileDescriptor[] mParcelFileDescriptors;
    ParcelFileDescriptor mParcelRead = null;
    ParcelFileDescriptor mParcelWrite = null;
    private FileDescriptor fd;
    InputStream is = null;
    File mfile = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_main);

        mSurfaceView = (SurfaceView) findViewById(R.id.surface);

        mSurfaceView.getHolder().addCallback(this);

        verifyStoragePermissions(this);

//        assert file != null;
//        file.delete();

        mToggleButton = (ToggleButton) findViewById(R.id.toggleRecordingButton);
        mToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            // toggle video recording
            public void onClick(View v) {
                if (((ToggleButton) v).isChecked()) {
                    mMediaRecorder.start();
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
//                    startStream();
                } else {
                    mMediaRecorder.stop();
                    mMediaRecorder.reset();
//                    startCamera();
                }
            }
        });
    }

    public void startCamera() throws IOException {

        mCamera = Camera.open(0);
        mCamera.setDisplayOrientation(0);
        mCamera.unlock();
//        try {
//            mCamera.setPreviewDisplay(mSurfaceView.getHolder());
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        mCamera.startPreview();

        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.reset();
        mMediaRecorder.setCamera(mCamera);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_2_TS);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setPreviewDisplay(mSurfaceView.getHolder().getSurface());
        mMediaRecorder.setVideoSize(1920, 1080);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setMaxDuration(-1);

        // The bandwidth actually consumed is often above what was requested
        mMediaRecorder.setVideoEncodingBitRate((int)(3000000*0.8));

        // We write the output of the camera in a local socket instead of a file !
        // This one little trick makes streaming feasible quiet simply: data from the camera
        // can then be manipulated at the other end of the socket
        mParcelFileDescriptors = ParcelFileDescriptor.createPipe();
        mParcelRead = new ParcelFileDescriptor(mParcelFileDescriptors[0]);
        mParcelWrite = new ParcelFileDescriptor(mParcelFileDescriptors[1]);

        fd = null;
        fd = mParcelWrite.getFileDescriptor();

        initDirs();

//        InputStream is2 = new FileInputStream(getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_MOVIES) + "/test_movie.ts");

        mMediaRecorder.setOutputFile(fd);
//        mMediaRecorder.setOutputFile(getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_MOVIES) + "/test_movie.ts");
//        mMediaRecorder.setOutputFile(is2);

        mMediaRecorder.prepare();

        is = new ParcelFileDescriptor.AutoCloseInputStream(mParcelRead);

        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buffer = new byte[256];
                while(true) {
                    try {
                        Log.i(TAG, String.valueOf(is.read(buffer, 0, 255)));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        th.start();

        mInitSuccesful = true;

//        File file = initFile();
//
//        mMediaRecorder.setOutputFile(getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_MOVIES) + "/test_movie.ts");
//        Log.d(TAG, String.valueOf(getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_MOVIES) + "/test_movie.ts"));
//        try {
//            mMediaRecorder.prepare();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

    }

    private void initDirs() {
        // File dir = new
        // File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
        // this
        File dir = new File(getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_MOVIES).toString());

        if (!dir.exists() && !dir.mkdirs()) {
            Log.wtf(TAG,
                    "Failed to create storage directory: "
                            + dir.getAbsolutePath());
            Toast.makeText(this, "not record", Toast.LENGTH_SHORT).show();
            mfile = null;
        }
    }

    private void startStream() {
        InputStreamMTSSource.InputStreamMTSSourceBuilder isStreamBuilder = InputStreamMTSSource.builder();
        InputStreamMTSSource mtsSource = null;
        try {
            Log.d(TAG, "IS = " + is.toString());
            mtsSource = isStreamBuilder.setInputStream(is).build();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Set up packet sink. We will send packets directly in UDP
        MTSSink transport = null;
        try {
            transport = UDPTransport.builder()
                    .setAddress("192.168.4.112") // Can be a multicast address
                    .setPort(8554)
                    .setSoTimeout(5000)
                    .setTtl(1)
                    .build();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Build streamer
        Streamer streamer = Streamer.builder()
                .setSource(mtsSource) // We will stream this source
                .setSink(transport) // We will send packets to this sink
                .build();

        // Start streaming
        streamer.stream();
    }

    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            READ_EXTERNAL_STORAGE,
            WRITE_EXTERNAL_STORAGE,
            RECORD_AUDIO,
            CAMERA
    };

    /**
     * Checks if the app has permission to write to device storage
     *
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity
     */
    public static void verifyStoragePermissions(AppCompatActivity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, WRITE_EXTERNAL_STORAGE);

        if(permission == PackageManager.PERMISSION_GRANTED)
            Log.d(TAG, "We have storage permission!");

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
        if(!mInitSuccesful) {
            try {
                startCamera();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
        // Release MediaRecorder and especially the Camera as it's a shared
        // object that can be used by other applications
        mMediaRecorder.reset();
        mMediaRecorder.release();
        mCamera.release();

        // once the objects have been released they can't be reused
        mMediaRecorder = null;
        mCamera = null;
    }
}