package cn.xz.classify.fruit;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
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
import org.pytorch.Module;
import org.pytorch.PyTorchAndroid;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.xz.classify.fruit.util.BitmapUtil;
import cn.xz.classify.fruit.util.DisplayUtil;
import cn.xz.classify.fruit.util.MathUtil;

public class FruitClassifyActivity extends AppCompatActivity {
    private static final String TAG = "AppleDetectionActivity";

    private static final int REQUEST_CODE_CAMERA_PERMISSION = 200;
    private static final String[] PERMISSIONS = {Manifest.permission.CAMERA};

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();

    private long lastAnalysisResultTime;

    private Module module = null;
    private PreviewView previewView;
    private TextView resultView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fruit_classify);
        previewView = findViewById(R.id.cameraPreview);
        resultView = findViewById(R.id.resultView);

        initViews();

        Log.i(TAG, "loading model");
        long ts = System.currentTimeMillis();
        module = PyTorchAndroid.loadModuleFromAsset(getAssets(), "fruit_classifier.jit.pt");
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
                            resultView.setText(String.format(Locale.CHINA, "%s of conf %.2f", result.topClassNames[0], result.topScores[0]));
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

    @OptIn(markerClass = ExperimentalGetImage.class)
    protected AnalysisResult analyzeImage(ImageProxy image) {
        // 图片格式需要为 YUV_420_888
        Bitmap bitmap = BitmapUtil.getBitmap(image);
        if (bitmap == null) {
            return null;
        }

        long startTs = System.currentTimeMillis();
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, Constant.SCALE_WIDTH, Constant.SCALE_HEIGHT, true);
        // center crop
        Bitmap croppedBitmap = BitmapUtil.centerCrop(resizedBitmap, Constant.CROP_WIDTH, Constant.CROP_HEIGHT);

        final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(croppedBitmap, TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
                TensorImageUtils.TORCHVISION_NORM_STD_RGB);
        final Tensor outputTensor = module.forward(IValue.from(inputTensor)).toTensor();

        final float[] outputs = outputTensor.getDataAsFloatArray();
        float[] softmaxVals = MathUtil.softmax(outputs);
        List<IndexValPair> res = MathUtil.rankDesc(softmaxVals);

        AnalysisResult analysisResult = new AnalysisResult();
        analysisResult.inferMs = System.currentTimeMillis() - startTs;
        float[] topScores = new float[res.size()];
        String[] topClassNames = new String[res.size()];
        int[] topClassIndexes = new int[res.size()];
        for (int i = 0; i < res.size(); i++) {
            topScores[i] = res.get(i).value;
            topClassIndexes[i] = res.get(i).index;
            topClassNames[i] = Constant.CLASS_NAMES[res.get(i).index];
        }
        analysisResult.topScores = topScores;
        analysisResult.topClassIndexes = topClassIndexes;
        analysisResult.topClassNames = topClassNames;
        return analysisResult;
    }

    public void finishActivity(View view) {
        finish();
    }
}
