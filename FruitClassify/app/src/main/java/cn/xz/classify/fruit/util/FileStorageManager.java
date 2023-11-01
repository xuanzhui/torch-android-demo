package cn.xz.classify.fruit.util;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

public class FileStorageManager {
    // Returns absolute paths to application-specific directories on all shared/external storage devices
    // where the application can place persistent files it owns.
    // These files are internal to the application, and not typically visible to the user as media.
    // 该目录下面的文件会随app卸载时删除，并且在media不可见
    public static String getExternalAppRootPath(Context context) {
        File file = context.getExternalFilesDir(null);
        if (file != null) {
            return file.getAbsolutePath();
        }
        return null;
    }

    public static String saveAssetFileToDisk(Context context, String assetName) {
        File file = new File(getExternalAppRootPath(context), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = Files.newOutputStream(file.toPath())) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        } catch (IOException e) {
            Log.e("FileStorageManager", "Error process asset " + assetName + " to file path");
        }
        return null;
    }
}
