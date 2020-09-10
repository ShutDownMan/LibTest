package com.example.libtest;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.libtest.gl.SurfaceView;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.FFmpeg;
import com.arthenica.mobileffmpeg.Level;
import com.example.libtest.mpegts.sources.InputStreamMTSSource;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Objects;
import java.util.concurrent.Semaphore;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.Manifest.permission.CAMERA;
import static com.arthenica.mobileffmpeg.Config.RETURN_CODE_CANCEL;
import static com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    // log tag
    public final static String TAG = MainActivity.class.getSimpleName();

    // surfaceview
    private static SurfaceView mSurfaceView;

    // Rtsp session
    private boolean mInitSuccesful = false;
    private MediaRecorder mMediaRecorder;
    private Camera mCamera;
    private ToggleButton mToggleButton;
    ParcelFileDescriptor[] mParcelFileDescriptors;
    ParcelFileDescriptor mParcelRead = null;
    ParcelFileDescriptor mParcelWrite = null;
    private FileDescriptor fd;
    MediaCodecInputStream is = null;
    private String mParcelReadFD;
    private MediaCodec mMediaCodec;
    private String mParcelWriteFD;
    private OutputStream outStream;
    private boolean mUnlocked;
    private boolean mPreviewStarted;
    private boolean mSurfaceReady;
    private Thread mCameraThread;
    private Looper mCameraLooper;
    EncoderDebugger debugger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

//        EncoderDebugger.asyncDebug(getApplicationContext(), 1920, 1080);

        setContentView(R.layout.activity_main);

        mSurfaceView = (SurfaceView) findViewById(R.id.surface);

        mSurfaceView.getHolder().addCallback(this);

        verifyStoragePermissions(this);

        mToggleButton = (ToggleButton) findViewById(R.id.toggleRecordingButton);
        // toggle video recording
        mToggleButton.setOnClickListener((v) -> {
            if (((ToggleButton) v).isChecked()) {
                Thread streamTh2 = new Thread(() -> {
                    try {
//                        Thread.sleep(6000);
                        encodeWithMediaCodec();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                streamTh2.start();

            } else {
                mMediaCodec.stop();
                mMediaCodec.reset();
            }
        });

    }

    private void encodeWithMediaCodec() throws IOException {

    //        updateCamera();
        debugger = EncoderDebugger.debug(getApplicationContext(), 1920, 1080);

        Log.d(TAG,"Video encoded using the MediaCodec API with a surface");
        Log.d(TAG, debugger.getEncoderName());

        mMediaCodec = MediaCodec.createByCodecName(debugger.getEncoderName());
        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", 1920, 1080);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 4000000);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, debugger.getEncoderColorFormat());
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
//        mediaFormat.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.H263Level10);
//        mediaFormat.setInteger(MediaFormat.KEY_LATENCY, 1);
//        mediaFormat.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
        mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        Surface surface = mMediaCodec.createInputSurface();
        ((SurfaceView)mSurfaceView).addMediaCodecSurface(surface);
        mMediaCodec.start();

        is = (new MediaCodecInputStream(mMediaCodec));

        Thread writeToFile = new Thread(() -> {
            try {
                int length;
                byte[] bytes = new byte[1024];
//                WritableByteChannel byteChannel = Channels.newChannel(outStream);
                while (true) {
                    if(is.available() != 0) {
                        length = is.read(bytes);
                        outStream.write(bytes, 0, length);
//                        is.writeToChannel(byteChannel);
                    } else {
                        Log.i(TAG, "Not Available");
                        Thread.sleep(1000);
                    }
                }

            } catch (IOException | InterruptedException ex) {
                ex.printStackTrace();
            }
        });
        writeToFile.start();
    }

    private String getFileDescriptorPath(int fd) {
        return "/proc/self/fd/" + String.valueOf(fd);
    }

    private void startStream() {
        final String command = " -i " + mParcelReadFD + " -b:v 10M -f rtsp \"rtsp://192.168.4.115:8554/live\"";
//        final String command = "-f android_camera -i 0 -r 1 -input_queue_size 150 -video_size 1920x1080 -pixel_format bgr0 -f rtsp -b:v 5M \"rtsp://192.168.4.115:8554/live\"";

        Config.setLogLevel(Level.AV_LOG_DEBUG);

        long executionId = FFmpeg.executeAsync(command, (executionId1, returnCode) -> {
            if (returnCode == RETURN_CODE_SUCCESS) {
                Log.i(Config.TAG, "Async command execution completed successfully.");
            } else if (returnCode == RETURN_CODE_CANCEL) {
                Log.i(Config.TAG, "Async command execution cancelled by user.");
            } else {
                Log.i(Config.TAG, String.format("Async command execution failed with rc=%d.", returnCode));
            }
        });
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
        mSurfaceReady = true;
        try {
            createCamera();
            startFiles();
            Thread streamTh = new Thread(this::startStream);
            streamTh.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mInitSuccesful = true;
    }

    private void startFiles() throws IOException {

        mParcelFileDescriptors = ParcelFileDescriptor.createPipe();

        mParcelRead = new ParcelFileDescriptor(mParcelFileDescriptors[0]);
        mParcelWrite = new ParcelFileDescriptor(mParcelFileDescriptors[1]);

        mParcelReadFD = getFileDescriptorPath(mParcelRead.getFd());
        mParcelWriteFD = getFileDescriptorPath(mParcelWrite.getFd());

        outStream = new ParcelFileDescriptor.AutoCloseOutputStream(mParcelWrite);
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
        // Release MediaRecorder and especially the Camera as it's a shared
        // object that can be used by other applications
        mMediaCodec.reset();
        mMediaCodec.release();
        mCamera.release();

        // once the objects have been released they can't be reused
        mMediaCodec = null;
        mCamera = null;
    }

    private void openCamera() throws RuntimeException {
        final Semaphore lock = new Semaphore(0);
        final RuntimeException[] exception = new RuntimeException[1];

        mCameraThread = new Thread(() -> {
            Looper.prepare();
            mCameraLooper = Looper.myLooper();
            try {
                Log.i(TAG, "Open Camera");
                mCamera = Camera.open(1);
                mCamera.setDisplayOrientation(0);
//                mCamera.lock();
            } catch (RuntimeException e) {
                exception[0] = e;
            } finally {
                lock.release();
                Looper.loop();
            }
        });
        mCameraThread.start();
        lock.acquireUninterruptibly();

        if (exception[0] != null) throw new CameraInUseException(exception[0].getMessage());
    }

    protected synchronized void createCamera() throws RuntimeException {
        if (mSurfaceView == null)
            throw new InvalidSurfaceException("Invalid surface !");
        if (mSurfaceView.getHolder() == null || !mSurfaceReady)
            throw new InvalidSurfaceException("Invalid surface !");

        if (mCamera == null) {
            openCamera();
            mUnlocked = false;

            try {

                try {
                    mSurfaceView.startGLThread();
                    mCamera.setPreviewTexture(mSurfaceView.getSurfaceTexture());
                    mCamera.startPreview();
                } catch (IOException e) {
                    Log.e(TAG, Objects.requireNonNull(e.getMessage()));
                    throw new InvalidSurfaceException("Invalid surface !");
                }

            } catch (RuntimeException e) {
                destroyCamera();
                throw e;
            }

        }
    }

    /** Stops the stream. */
    public synchronized void stop() {

    }

    protected synchronized void destroyCamera() {
        if (mCamera != null) {
            lockCamera();
            mCamera.stopPreview();
            try {
                mCamera.release();
            } catch (Exception e) {
                Log.e(TAG,e.getMessage()!=null?e.getMessage():"unknown error");
            }
            mCamera = null;
            mCameraLooper.quit();
            mUnlocked = false;
            mPreviewStarted = false;
        }
    }

    protected void lockCamera() {
        if (mUnlocked) {
            Log.d(TAG,"Locking camera");
            try {
                mCamera.reconnect();
            } catch (Exception e) {
                Log.e(TAG, Objects.requireNonNull(e.getMessage()));
            }
            mUnlocked = false;
        }
    }

    protected void unlockCamera() {
        if (!mUnlocked) {
            Log.d(TAG,"Unlocking camera");
            try {
                mCamera.unlock();
            } catch (Exception e) {
                Log.e(TAG, Objects.requireNonNull(e.getMessage()));
            }
            mUnlocked = true;
        }
    }

}