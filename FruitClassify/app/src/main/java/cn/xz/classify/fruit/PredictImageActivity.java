package cn.xz.classify.fruit;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.PyTorchAndroid;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import cn.xz.classify.fruit.util.BitmapUtil;
import cn.xz.classify.fruit.util.DisplayUtil;
import cn.xz.classify.fruit.util.FileStorageManager;
import cn.xz.classify.fruit.util.MathUtil;

public class PredictImageActivity extends AppCompatActivity {
    private static final String TAG = "PredictImageActivity";

    private static Executor executor = Executors.newSingleThreadExecutor();

    private Module module = null;

    private ImageView imageView;
    private TextView resultView1;
    private TextView resultView2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_predict_image);
        imageView = findViewById(R.id.imageView);
        resultView1 = findViewById(R.id.result1);
        resultView2 = findViewById(R.id.result2);

//        module = LiteModuleLoader.loadModuleFromAsset(getAssets(), "fruit_classifier.pt");
//        String modulePath = FileStorageManager.saveAssetFileToDisk(this, "fruit_classifier.jit.pt");
//        if (modulePath == null) {
//            finish();
//            return;
//        }
//        module = Module.load(modulePath);
        module = PyTorchAndroid.loadModuleFromAsset(getAssets(), "fruit_classifier.jit.pt");

        executor.execute(() -> {
            Bitmap bitmap1 = BitmapFactory.decodeResource(getResources(),
                    R.drawable.banana);
            AnalysisResult result1 = analyzeImage(bitmap1);
            runOnUiThread(() -> {
                Log.i(TAG, "analysis cost in ms:" + result1.inferMs);
                resultView1.setText(String.format(Locale.CHINA, "top1: %s, score %.2f", result1.topClassNames[0], result1.topScores[0]));
            });
        });

        executor.execute(() -> {
            Bitmap bitmap2 = BitmapFactory.decodeResource(getResources(),
                    R.drawable.orange);
            AnalysisResult result2 = analyzeImage(bitmap2);
            runOnUiThread(() -> {
                Log.i(TAG, "analysis cost in ms:" + result2.inferMs);
                resultView2.setText(String.format(Locale.CHINA, "top1: %s, score %.2f", result2.topClassNames[0], result2.topScores[0]));
            });
        });

    }

    protected AnalysisResult analyzeImage(Bitmap bitmap) {
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