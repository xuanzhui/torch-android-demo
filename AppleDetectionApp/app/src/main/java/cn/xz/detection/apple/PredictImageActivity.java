package cn.xz.detection.apple;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageProxy;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import cn.xz.detection.apple.util.FileStorageManager;

public class PredictImageActivity extends AppCompatActivity {

    private static Executor executor = Executors.newSingleThreadExecutor();

    private Module module = null;

    private ImageView imageView;
    private ResultView resultView;

    static class AnalysisResult {
        private final ArrayList<PrePostProcessor.Result> mResults;

        public AnalysisResult(ArrayList<PrePostProcessor.Result> results) {
            mResults = results;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_predict_image);
        imageView = findViewById(R.id.imageView);
        resultView = findViewById(R.id.resultView);

        module = LiteModuleLoader.loadModuleFromAsset(getAssets(), "apple_detection_best.torchscript");
//        String modulePath = FileStorageManager.saveAssetFileToDisk(this, "apple_detection_best.torchscript");
//        if (modulePath == null) {
//            finish();
//            return;
//        }
//        module = Module.load(modulePath);

        Bitmap bitmap = BitmapFactory.decodeResource(getResources(),
                R.drawable.apple1);
//        Bitmap bitmap = BitmapFactory.decodeFile("/storage/emulated/0/Android/data/cn.xz.detection.apple/files/16977933107391281566746818466597.jpg");
        imageView.setImageBitmap(bitmap);
        executor.execute(() -> {

            AnalysisResult result = analyzeImage(bitmap);
            runOnUiThread(() -> {
                resultView.setResults(result.mResults);
                resultView.invalidate();
            });
        });
    }

    @WorkerThread
    @Nullable
    protected AnalysisResult analyzeImage(Bitmap bitmap) {
//        Bitmap bitmap = imgToBitmap(image);
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

        float imgScaleX = (float) bitmap.getWidth() / PrePostProcessor.mInputWidth;
        float imgScaleY = (float) bitmap.getHeight() / PrePostProcessor.mInputHeight;
        // 此处的缩放比例需要根据 image view 和 图片 的大小妥善处理，此处示例按宽缩放
        float ivScaleX = (float) resultView.getWidth() / bitmap.getWidth();
//        float ivScaleY = (float) resultView.getHeight() / bitmap.getHeight();
        float ivScaleY = ivScaleX;

        final ArrayList<PrePostProcessor.Result> results = PrePostProcessor.outputsToNMSPredictions(outputs, imgScaleX, imgScaleY, ivScaleX, ivScaleY, 0, 0);
        return new AnalysisResult(results);
    }

    public void finishActivity(View view) {
        finish();
    }
}