package ucla.cs211.camencoder;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.widget.Button;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private final static String TAG = MainActivity.class.getSimpleName();

    private static int SP_CAM_WIDTH = 0;
    private static int SP_CAM_HEIGHT = 0;

    private final static int DEFAULT_FRAME_RATE = 15;
    private final static int DEFAULT_BIT_RATE = 500000;

    Camera camera;
    Context mContext;
    SurfaceHolder previewHolder;
    SurfaceView svCameraPreview;
    byte[] previewBuffer;
    boolean isStreaming = false;
    AvcEncoder encoder;
    ArrayList<byte[]> encDataList = new ArrayList<byte[]>();

    //TODO: Debugging, remove before final release
    File file;
    FileOutputStream fos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.setContentView(R.layout.activity_main);

        mContext = this;
        this.findViewById(R.id.btnStream).setOnClickListener(
                v -> {
                    if (!isStreaming) {
                        isStreaming = true;
                        ((Button)v).setText("Stop Stream");
                        try {
                            startStream();
                        } catch (FileNotFoundException e) {
                            Log.e(TAG, "Start streaming failed");
                            e.printStackTrace();
                        }
                        // startCamera();
                    } else {
                        isStreaming = false;
                        ((Button)v).setText("Start Stream");
                        try {
                            stopStream();
                        } catch (IOException e) {
                            Log.e(TAG, "Stop streaming failed");
                            e.printStackTrace();
                        }
                    }
                });

        svCameraPreview = (SurfaceView) this.findViewById(R.id.svCameraPreview);
        this.previewHolder = svCameraPreview.getHolder();
        this.previewHolder.addCallback(this);
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        // Camera size width: 1920, height 932
        this.camera.addCallbackBuffer(this.previewBuffer);

        if (this.isStreaming){
            Log.i(TAG, "streaming; byte size = " + bytes.length);
            byte[] encData = this.encoder.offerEncoder(bytes);
            Log.i(TAG, "Encoded size = " + encData.length);
            if (encData.length > 0)
            {
                synchronized(this.encDataList)
                {
                    try {
                        fos.write(encData);
                    } catch (IOException e) {
                        Log.e(TAG, "[onPreviewFrame]: write to file failed");
                        e.printStackTrace();
                    }
                    this.encDataList.add(encData);
                }
            }
        }
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
        startCamera();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {}

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
        stopCamera();
    }

    // TODO: bluetooth streaming
    private void startStream() throws FileNotFoundException, RuntimeException {
        this.encoder = new AvcEncoder();
        this.encoder.init(SP_CAM_WIDTH, SP_CAM_HEIGHT, DEFAULT_FRAME_RATE, DEFAULT_BIT_RATE);

        // TODO: to be deleted when final releasing
        // save to stream video
        File path = mContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES).getAbsoluteFile();
        if (!path.mkdirs()) {
            Log.e(TAG, "Directory not created");
            return;
        }
        file = new File(path, "encoded.h264");
        if (fos == null){
            fos = new FileOutputStream(file, false);
        }else{
            Log.e(TAG, "[startStream]: fos existed");
            throw new RuntimeException("[startStream]: fos existed");
        }

    }

    private void stopStream() throws IOException {
        fos.close();
        encoder.close();
    }

    private void startCamera() {
        if (SP_CAM_WIDTH == 0) {
            Camera tmpCam = Camera.open();
            Camera.Parameters params = tmpCam.getParameters();
            final List<Camera.Size> prevSizes = params.getSupportedPreviewSizes();
            int i = prevSizes.size()-1;
            SP_CAM_WIDTH = prevSizes.get(i).width;
            SP_CAM_HEIGHT = prevSizes.get(i).height;
            Log.i(TAG, "width is: "+SP_CAM_HEIGHT);
            Log.i(TAG, "height is: "+SP_CAM_WIDTH);

            tmpCam.release();
            tmpCam = null;
        }

        // Preview holder need swap the height and width because of the orientation
        this.previewHolder.setFixedSize(SP_CAM_HEIGHT, SP_CAM_WIDTH);
        Log.i(TAG, "previewHolder: width is: "+SP_CAM_HEIGHT + " height is: " + SP_CAM_WIDTH);

        int stride = (int) Math.ceil(SP_CAM_WIDTH/16.0f) * 16;
        int cStride = (int) Math.ceil(SP_CAM_WIDTH/32.0f)  * 16;
        final int frameSize = stride * SP_CAM_HEIGHT;
        final int qFrameSize = cStride * SP_CAM_HEIGHT / 2;

        this.previewBuffer = new byte[frameSize + qFrameSize * 2];

        try {
            camera = Camera.open();
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(this.previewHolder);
            Camera.Parameters params = camera.getParameters();
            params.setPreviewSize(SP_CAM_WIDTH, SP_CAM_HEIGHT);
            params.setPreviewFormat(ImageFormat.YV12);
            camera.setParameters(params);
            camera.addCallbackBuffer(previewBuffer);
            camera.setPreviewCallbackWithBuffer(this);
            camera.startPreview();
        }
        catch (IOException e) {
            Log.e(TAG, String.valueOf(e));
        }
        catch (RuntimeException e) {
            Log.e(TAG, String.valueOf(e));
        }
    }

    private void stopCamera() {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }
}