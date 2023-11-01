package cn.xz.detection.apple;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.xz.detection.apple.util.BitmapUtil;
import cn.xz.detection.apple.util.DisplayUtil;
import cn.xz.detection.apple.util.FileStorageManager;

/**
 * yolo export model=apple_detection_best.pt format=torchscript imgsz=640 optimize=True
 */
public class AppleDetectionActivity extends AppCompatActivity {
    private static final String TAG = "AppleDetectionActivity";

    private static final int REQUEST_CODE_CAMERA_PERMISSION = 200;
    private static final String[] PERMISSIONS = {Manifest.permission.CAMERA};

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();

    private long lastAnalysisResultTime;

    private Module module = null;
    private PreviewView previewView;
    private ResultView resultView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apple_detection);
        previewView = findViewById(R.id.cameraPreview);
        resultView = findViewById(R.id.resultView);

        initViews();

        Log.i(TAG, "loading model");
        long ts = System.currentTimeMillis();
        module = LiteModuleLoader.loadModuleFromAsset(getAssets(), "apple_detection_best.torchscript");
//        String modulePath = FileStorageManager.saveAssetFileToDisk(this, "apple_detection_best.torchscript");
//        if (modulePath == null) {
//            finish();
//            return;
//        }
//        module = Module.load(modulePath);
        Log.i(TAG, "model loaded, cost(seconds): " + ((System.currentTimeMillis() - ts) / 1000F));

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS,
                    REQUEST_CODE_CAMERA_PERMISSION);
        } else {
            setupCameraX();
        }
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

            ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector).build();
            imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
                @Override
                public void analyze(@NonNull ImageProxy image) {
                    Log.d(TAG, "analyze called...");
                    Log.d(TAG, "image format is:" + image.getFormat());
                    // 控制识别频率
                    if (System.currentTimeMillis() - lastAnalysisResultTime < 500) {
                        image.close();
                        return;
                    }

                    Log.i(TAG, "start to analyze");
                    long ts = System.currentTimeMillis();
                    final AnalysisResult result = analyzeImage(image);
                    Log.i(TAG, "analysis finished, cost(seconds): " + ((System.currentTimeMillis() - ts) / 1000F));

                    // call close manually or analyze will not called next time
                    image.close();
                    if (result != null) {
                        lastAnalysisResultTime = System.currentTimeMillis();
                        runOnUiThread(() -> {
                            resultView.setResults(result.mResults);
                            resultView.invalidate();
                        });
                    }
                }
            });

            CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
            Camera camera;
            try {
                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
//                setCameraPreviewFocusOnTap(previewView, camera);
            } catch (Exception e) {
                Toast.makeText(this, "无法绑定相机实例", Toast.LENGTH_LONG).show();
                Log.e(TAG, "无法绑定相机实例", e);
                finish();
            }

        }, ContextCompat.getMainExecutor(this));
    }

    private void saveBitmapForTest(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        String savePath = FileStorageManager.getExternalAppRootPath(this) + "/" + System.currentTimeMillis() + ".jpg";
        Log.d(TAG, savePath);
        File file = new File(savePath);
        if (file.exists())
            file.delete();
        try {
            FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    @WorkerThread
    @Nullable
    protected AnalysisResult analyzeImage(ImageProxy image) {
        // 图片格式需要为 YUV_420_888
        Bitmap bitmap = BitmapUtil.getBitmap(image);
        if (bitmap == null) {
            return null;
        }
//        Bitmap bitmap = imgProxyToBitmap(image);

        // TODO remove test
//        saveBitmapForTest(bitmap);

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
        float ivScaleY = (float)resultView.getHeight() / bitmap.getHeight();

        final ArrayList<PrePostProcessor.Result> results = PrePostProcessor.outputsToNMSPredictions(outputs, imgScaleX, imgScaleY, ivScaleX, ivScaleY, 0, 0);
        return new AnalysisResult(results);
    }

    public void finishActivity(View view) {
        finish();
    }
}
