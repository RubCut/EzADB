package dev.ezadb.helper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Tiny on-device agent, pushed to /data/local/tmp and executed with app_process.
 *
 * It answers the questions the plain adb shell cannot answer cheaply:
 * human readable application labels and real launcher icons.
 *
 * Everything is done through reflection so this file compiles with a plain JDK
 * (no android.jar on the compile classpath) and stays compatible across API levels.
 */
public final class Helper {

    private static final int FLAG_SYSTEM = 0x1;
    private static final int FLAG_UPDATED_SYSTEM_APP = 0x80;

    private Object packageManager;
    private Class<?> applicationInfoClass;
    private Class<?> packageInfoClass;

    public static void main(String[] args) {
        String command = args.length > 0 ? args[0] : "apps";
        try {
            Helper helper = new Helper();
            helper.init();
            if ("apps".equals(command)) {
                helper.apps();
            } else if ("labels".equals(command)) {
                helper.labels();
            } else if ("label".equals(command) && args.length > 1) {
                helper.label(args[1]);
            } else if ("icon".equals(command) && args.length > 1) {
                System.out.println(helper.iconBase64(args[1], 96));
            } else if ("icons".equals(command) && args.length > 1) {
                helper.icons(args);
            } else if ("ping".equals(command)) {
                System.out.println("pong");
            } else {
                System.out.println("#unknown-command");
            }
            System.out.flush();
        } catch (Throwable error) {
            System.out.println("#error " + error.getClass().getSimpleName() + ": " + error.getMessage());
            System.out.flush();
        }
    }

    private void init() throws Exception {
        try {
            Class<?> looper = Class.forName("android.os.Looper");
            if (looper.getMethod("myLooper").invoke(null) == null) {
                looper.getMethod("prepare").invoke(null);
            }
        } catch (Throwable ignored) {
            // A looper is not required on every API level.
        }
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object thread;
        try {
            thread = activityThread.getMethod("systemMain").invoke(null);
        } catch (Throwable error) {
            thread = activityThread.getMethod("currentActivityThread").invoke(null);
        }
        Object context = activityThread.getMethod("getSystemContext").invoke(thread);
        packageManager = context.getClass().getMethod("getPackageManager").invoke(context);
        applicationInfoClass = Class.forName("android.content.pm.ApplicationInfo");
        packageInfoClass = Class.forName("android.content.pm.PackageInfo");
    }

    private void apps() throws Exception {
        Method getInstalledPackages = packageManager.getClass().getMethod("getInstalledPackages", int.class);
        Object result = getInstalledPackages.invoke(packageManager, 0);
        List<?> packages = (List<?>) result;
        System.out.println("#ezadb-helper-v1");
        for (Object info : packages) {
            try {
                String name = string(field(info, "packageName"));
                if (name == null || name.isEmpty()) continue;
                Object appInfo = field(info, "applicationInfo");
                String label = labelOf(appInfo, name);
                String versionName = string(field(info, "versionName"));
                long versionCode = versionCodeOf(info);
                int flags = intValue(field(appInfo, "flags"));
                boolean system = (flags & FLAG_SYSTEM) != 0 || (flags & FLAG_UPDATED_SYSTEM_APP) != 0;
                boolean enabled = enabledOf(name);
                String sourceDir = string(field(appInfo, "sourceDir"));
                long size = sourceDir == null ? 0L : new File(sourceDir).length();
                int uid = intValue(field(appInfo, "uid"));
                StringBuilder line = new StringBuilder();
                line.append(clean(name)).append('\t')
                    .append(clean(label)).append('\t')
                    .append(clean(versionName == null ? "" : versionName)).append('\t')
                    .append(versionCode).append('\t')
                    .append(system ? '1' : '0').append('\t')
                    .append(enabled ? '1' : '0').append('\t')
                    .append(size).append('\t')
                    .append(clean(sourceDir == null ? "" : sourceDir)).append('\t')
                    .append(uid);
                System.out.println(line);
            } catch (Throwable ignored) {
                // A single broken package must not break the whole listing.
            }
        }
    }

    private void labels() throws Exception {
        Method getInstalledPackages = packageManager.getClass().getMethod("getInstalledPackages", int.class);
        List<?> packages = (List<?>) getInstalledPackages.invoke(packageManager, 0);
        System.out.println("#ezadb-helper-v1");
        for (Object info : packages) {
            try {
                String name = string(field(info, "packageName"));
                Object appInfo = field(info, "applicationInfo");
                System.out.println(clean(name) + "\t" + clean(labelOf(appInfo, name)));
            } catch (Throwable ignored) {
            }
        }
    }

    private void label(String packageName) throws Exception {
        Object appInfo = applicationInfoOf(packageName);
        System.out.println(clean(labelOf(appInfo, packageName)));
    }

    private void icons(String[] args) {
        System.out.println("#ezadb-helper-v1");
        for (int index = 1; index < args.length; index++) {
            String packageName = args[index];
            String data;
            try {
                data = iconBase64(packageName, 96);
            } catch (Throwable ignored) {
                data = "";
            }
            System.out.println(clean(packageName) + "\t" + data);
        }
    }

    private String iconBase64(String packageName, int size) throws Exception {
        if (size < 24) size = 24;
        if (size > 256) size = 256;
        Object appInfo = applicationInfoOf(packageName);
        Object drawable = packageManager.getClass()
            .getMethod("getApplicationIcon", applicationInfoClass)
            .invoke(packageManager, appInfo);

        Class<?> bitmapClass = Class.forName("android.graphics.Bitmap");
        Class<?> configClass = Class.forName("android.graphics.Bitmap$Config");
        Object config = enumValue(configClass, "ARGB_8888");
        Object bitmap = bitmapClass
            .getMethod("createBitmap", int.class, int.class, configClass)
            .invoke(null, size, size, config);

        Class<?> canvasClass = Class.forName("android.graphics.Canvas");
        Object canvas = canvasClass.getConstructor(bitmapClass).newInstance(bitmap);
        Class<?> drawableClass = Class.forName("android.graphics.drawable.Drawable");
        drawableClass.getMethod("setBounds", int.class, int.class, int.class, int.class)
            .invoke(drawable, 0, 0, size, size);
        drawableClass.getMethod("draw", canvasClass).invoke(drawable, canvas);

        Class<?> formatClass = Class.forName("android.graphics.Bitmap$CompressFormat");
        Object png = enumValue(formatClass, "PNG");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        bitmapClass.getMethod("compress", formatClass, int.class, OutputStream.class)
            .invoke(bitmap, png, 100, buffer);
        bitmapClass.getMethod("recycle").invoke(bitmap);
        return base64(buffer.toByteArray());
    }

    private Object applicationInfoOf(String packageName) throws Exception {
        return packageManager.getClass()
            .getMethod("getApplicationInfo", String.class, int.class)
            .invoke(packageManager, packageName, 0);
    }

    private String labelOf(Object applicationInfo, String fallback) {
        try {
            Object label = packageManager.getClass()
                .getMethod("getApplicationLabel", applicationInfoClass)
                .invoke(packageManager, applicationInfo);
            String text = string(label);
            if (text != null && !text.trim().isEmpty()) return text.trim();
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private boolean enabledOf(String packageName) {
        try {
            Object state = packageManager.getClass()
                .getMethod("getApplicationEnabledSetting", String.class)
                .invoke(packageManager, packageName);
            int value = state instanceof Integer ? (Integer) state : 0;
            return value != 2 && value != 3 && value != 4;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private long versionCodeOf(Object packageInfo) {
        try {
            Method method = packageInfoClass.getMethod("getLongVersionCode");
            Object value = method.invoke(packageInfo);
            if (value instanceof Long) return (Long) value;
        } catch (Throwable ignored) {
        }
        return intValue(field(packageInfo, "versionCode"));
    }

    private static Object field(Object target, String name) {
        if (target == null) return null;
        try {
            return target.getClass().getField(name).get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int intValue(Object value) {
        return value instanceof Integer ? (Integer) value : 0;
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> type, String name) {
        return Enum.valueOf((Class<Enum>) type, name);
    }

    private static final char[] ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    private static String base64(byte[] data) {
        StringBuilder out = new StringBuilder(((data.length + 2) / 3) * 4);
        int index = 0;
        while (index + 2 < data.length) {
            int block = ((data[index] & 0xFF) << 16) | ((data[index + 1] & 0xFF) << 8) | (data[index + 2] & 0xFF);
            out.append(ALPHABET[(block >> 18) & 63]).append(ALPHABET[(block >> 12) & 63])
               .append(ALPHABET[(block >> 6) & 63]).append(ALPHABET[block & 63]);
            index += 3;
        }
        int remaining = data.length - index;
        if (remaining == 1) {
            int block = (data[index] & 0xFF) << 16;
            out.append(ALPHABET[(block >> 18) & 63]).append(ALPHABET[(block >> 12) & 63]).append("==");
        } else if (remaining == 2) {
            int block = ((data[index] & 0xFF) << 16) | ((data[index + 1] & 0xFF) << 8);
            out.append(ALPHABET[(block >> 18) & 63]).append(ALPHABET[(block >> 12) & 63])
               .append(ALPHABET[(block >> 6) & 63]).append('=');
        }
        return out.toString();
    }
}
