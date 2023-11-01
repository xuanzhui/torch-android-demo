package cn.xz.detection.apple;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.xz.detection.apple.util.DisplayUtil;
import cn.xz.detection.apple.util.FileStorageManager;

public class AppleDetectByCaptureActivity extends AppCompatActivity {

    private static final String TAG = "AbstractCameraXActivity";

    private PreviewView previewView;
    private ResultView resultView;
    private Button button;

    private Module module = null;
    private static final int REQUEST_CODE_CAMERA_PERMISSION = 200;
    private static final String[] PERMISSIONS = {android.Manifest.permission.CAMERA};

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private ExecutorService analyzeExecutor = Executors.newSingleThreadExecutor();

    private long lastAnalysisResultTime;
    private ImageCapture imageCapture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apple_detect_by_capture);
        previewView = findViewById(R.id.cameraPreview);
        resultView = findViewById(R.id.resultView);
        button = findViewById(R.id.button);

        initViews();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS,
                    REQUEST_CODE_CAMERA_PERMISSION);
        } else {
            setupCameraX();
        }

        module = LiteModuleLoader.loadModuleFromAsset(getAssets(), "apple_detection_best.torchscript");
//        String modulePath = FileStorageManager.saveAssetFileToDisk(this, "apple_detection_best.torchscript");
//        if (modulePath == null) {
//            finish();
//            return;
//        }
//        module = Module.load(modulePath);

        button.setOnClickListener(view -> {
            try {
                takePhoto();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    // 将preview 和 result view 按 3：4 的比例重新设置
    private void initViews() {
        int screenWidth = DisplayUtil.getScreenPXWidth(this);
        int previewHeight = Math.round(screenWidth * 4 / 3.0F);
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) previewView.getLayoutParams();
        layoutParams.width = screenWidth;
        layoutParams.height = previewHeight;
        previewView.setLayoutParams(layoutParams);

        RelativeLayout.LayoutParams resultViewLayoutParams = (RelativeLayout.LayoutParams) resultView.getLayoutParams();
        resultViewLayoutParams.width = screenWidth;
        resultViewLayoutParams.height = previewHeight;
        resultView.setLayoutParams(resultViewLayoutParams);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(
                                this,
                                "目标检测需要开启相机权限",
                                Toast.LENGTH_LONG)
                        .show();
                finish();
            } else {
                setupCameraX();
            }
        }
    }

    private void setupCameraX() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            ProcessCameraProvider cameraProvider;
            try {
                cameraProvider = cameraProviderFuture.get();

            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "无法获取相机实例", Toast.LENGTH_LONG).show();
                Log.e(TAG, "无法获取相机实例");
                finish();
                return;
            }

            ResolutionSelector resolutionSelector = new ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build();
            Preview preview = new Preview.Builder()
                    .setResolutionSelector(resolutionSelector).build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            imageCapture = new ImageCapture.Builder()
                    .setResolutionSelector(resolutionSelector).build();
            CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
            Camera camera;
            try {
                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
            } catch (Exception e) {
                Toast.makeText(this, "无法绑定相机实例", Toast.LENGTH_LONG).show();
                Log.e(TAG, "无法绑定相机实例", e);
                finish();
            }

        }, ContextCompat.getMainExecutor(this));
    }

    private String takePhoto() throws IOException {
        String savePath = FileStorageManager.getExternalAppRootPath(this) + "/" + System.currentTimeMillis() + ".jpg";
        File photoFile = File.createTempFile(
                String.valueOf(System.currentTimeMillis()),
                ".jpg",
                new File(FileStorageManager.getExternalAppRootPath(this))
        );
        Log.w(TAG, "save to:" + photoFile);
        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();
        imageCapture.takePicture(outputFileOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                analyzeExecutor.submit(() -> {
                    Bitmap bitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
                    AnalysisResult result = analyzeImage(bitmap);
                    runOnUiThread(() -> {
                        resultView.setResults(result.mResults);
                        resultView.invalidate();
                    });

                });

            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {

            }
        });
        return savePath;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }

    protected AnalysisResult analyzeImage(Bitmap bitmap) {

//        Matrix matrix = new Matrix();
//        matrix.postRotate(90.0f);
//        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, PrePostProcessor.mInputWidth, PrePostProcessor.mInputHeight, true);

        final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(resizedBitmap, PrePostProcessor.NO_MEAN_RGB, PrePostProcessor.NO_STD_RGB);
        // only one type (apple), here to tensor, if several types (apple, banana ...) to Tuple
//        IValue[] outputTuple = module.forward(IValue.from(inputTensor)).toTuple();
//        final Tensor outputTensor = outputTuple[0].toTensor();
        final Tensor outputTensor = module.forward(IValue.from(inputTensor)).toTensor();

        final float[] outputs = outputTensor.getDataAsFloatArray();

        float imgScaleX = (float)bitmap.getWidth() / PrePostProcessor.mInputWidth;
        float imgScaleY = (float)bitmap.getHeight() / PrePostProcessor.mInputHeight;
        float ivScaleX = (float)resultView.getWidth() / bitmap.getWidth();
//        float ivScaleY = (float)resultView.getHeight() / bitmap.getHeight();
        float ivScaleY = ivScaleX;

        final ArrayList<PrePostProcessor.Result> results = PrePostProcessor.outputsToNMSPredictions(outputs, imgScaleX, imgScaleY, ivScaleX, ivScaleY, 0, 0);
        return new AnalysisResult(results);
    }

    public void finishActivity(View view) {
        finish();
    }
}