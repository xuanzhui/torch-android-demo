package cn.xz.detection.apple.util;


import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.ColorInt;

public class DisplayUtil {
    /**
     * 设置添加屏幕的背景透明度
     */
    public static void setWindowBackgroundAlpha(Activity activity, float bgAlpha) {
        WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
        lp.alpha = bgAlpha; //0.0-1.0
        activity.getWindow().setAttributes(lp);
    }

    public static float[] getScreenDipDimen(Context context) {
        if (context == null) {
            Log.e("DisplayUtils", "context NPE");
            return null;
        }

        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();

        float dpHeight = displayMetrics.heightPixels / displayMetrics.density;
        float dpWidth = displayMetrics.widthPixels / displayMetrics.density;

        return new float[]{dpWidth, dpHeight};
    }

    public static int[] getScreenPixDimen(Context context) {
        if (context == null) {
            Log.e("DisplayUtils", "context NPE");
            return null;
        }

        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();

        return new int[]{displayMetrics.widthPixels, displayMetrics.heightPixels};
    }

    public static int getScreenPXWidth(Context context) {
        if (context == null) {
            Log.e("DisplayUtils", "context NPE");
            return 0;
        }

        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();

        return displayMetrics.widthPixels;
    }

    public static int getScreenPXHeight(Context context) {
        if (context == null) {
            Log.e("DisplayUtils", "context NPE");
            return 0;
        }

        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();

        return displayMetrics.heightPixels;
    }

    public static float px2dp(final Context context, final float px) {
        return px / context.getResources().getDisplayMetrics().density;
    }

    public static float dp2px(final Context context, final float dp) {
        return dp * context.getResources().getDisplayMetrics().density;
    }

    /**
     * 将px值转换为sp值，保证文字大小不变
     */
    public static int px2sp(Context context, float pxValue) {
        final float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
        return (int) (pxValue / fontScale + 0.5f);
    }

    /**
     * 将sp值转换为px值，保证文字大小不变
     */
    public static int sp2px(Context context, float spValue) {
        final float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
        return (int) (spValue * fontScale + 0.5f);
    }

    public static void setStatusBarTransparent(Activity activity) {
        Window window = activity.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
    }

    public static void setStatusBarBackground(Activity activity, @ColorInt int color) {
        Window window = activity.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.setStatusBarColor(color);
    }

    public static void setStatusBarTheme(final Activity activity, final boolean needDarkFont) {
        // fetch the current flags
        final int lFlags = activity.getWindow().getDecorView().getSystemUiVisibility();

        // View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR requests the status bar to draw in a mode
        // that is compatible with light status bar backgrounds

        // For this to take effect, the window must request FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
        // but not FLAG_TRANSLUCENT_STATUS
        activity.getWindow().getDecorView().setSystemUiVisibility(
                needDarkFont ?
                        (lFlags | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) :
                        (lFlags & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR));
    }

    public static void setStatusBarBackgroundTranslate(final Activity activity){
        Window window = activity.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
    }
}
