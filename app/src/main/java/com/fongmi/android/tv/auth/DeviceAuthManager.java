package com.fongmi.android.tv.auth;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Prefers;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class DeviceAuthManager {

    private static final String BASE_URL = "https://api.lihansong.top/api/v1/device/";
    private static final String KEY_DEVICE_ID = "fusiontv_device_id";
    private static final String KEY_DEVICE_TOKEN = "fusiontv_device_token";
    private static final long POLL_INTERVAL = 5000L;
    private static final long CODE_REFRESH_INTERVAL = 25 * 60 * 1000L;

    public interface Callback {
        void onApproved();
    }

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final TextView messageView;
    private final AlertDialog dialog;

    private Callback callback;
    private String deviceId;
    private String deviceToken;
    private String deviceCode = "";
    private String lastStatus = "pending";
    private volatile boolean finished;
    private volatile boolean requestInFlight;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            checkStatus();
        }
    };

    private final Runnable refreshCodeRunnable = new Runnable() {
        @Override
        public void run() {
            registerDevice();
            if (!finished) handler.postDelayed(this, CODE_REFRESH_INTERVAL);
        }
    };

    public static DeviceAuthManager create(Activity activity) {
        return new DeviceAuthManager(activity);
    }

    private DeviceAuthManager(Activity activity) {
        this.activity = activity;
        messageView = new TextView(activity);
        messageView.setGravity(Gravity.CENTER);
        messageView.setTextSize(22);
        messageView.setPadding(dp(28), dp(24), dp(28), dp(24));
        messageView.setMinWidth(dp(360));
        dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("设备授权")
                .setView(messageView)
                .setCancelable(false)
                .setPositiveButton("重新检查", null)
                .setNegativeButton("退出应用", (d, which) -> activity.finish())
                .create();
    }

    public void start(Callback callback) {
        this.callback = callback;
        deviceId = Prefers.getString(KEY_DEVICE_ID);
        if (deviceId.isEmpty()) {
            deviceId = "FT-" + UUID.randomUUID().toString().replace("-", "");
            Prefers.put(KEY_DEVICE_ID, deviceId);
        }
        deviceToken = Prefers.getString(KEY_DEVICE_TOKEN);
        showMessage("正在连接授权服务器，请稍候……");
        if (!dialog.isShowing()) {
            dialog.show();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> registerDevice());
            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(
                        (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.58f),
                        WindowManager.LayoutParams.WRAP_CONTENT
                );
            }
        }
        registerDevice();
    }

    private int dp(float value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    private void registerDevice() {
        if (finished || requestInFlight) return;
        requestInFlight = true;
        executor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("device_id", deviceId);
                payload.put("device_name", Build.MODEL == null ? "电视设备" : Build.MODEL);
                payload.put("device_model", (Build.MANUFACTURER == null ? "" : Build.MANUFACTURER + " ") + (Build.MODEL == null ? "Android TV" : Build.MODEL));
                if (deviceToken != null && !deviceToken.isEmpty()) payload.put("device_token", deviceToken);

                JSONObject result = post("register", payload);
                if (!result.optBoolean("ok")) {
                    int httpCode = result.optInt("_http_code", 0);
                    if (httpCode == 409) {
                        showMessage("设备凭证无效或设备记录已变更。\n\n请联系管理员检查设备记录，必要时解绑后重新申请。\n\n设备编号：" + deviceId);
                    } else {
                        showMessage("暂时无法连接授权服务器，正在自动重试。\n\n请检查电视网络连接。");
                        schedulePoll();
                    }
                    return;
                }

                String returnedToken = result.optString("device_token", "");
                if (deviceToken == null || deviceToken.isEmpty()) {
                    if (returnedToken.isEmpty()) {
                        showMessage("服务器未返回设备凭证，请稍后重试。");
                        schedulePoll();
                        return;
                    }
                    deviceToken = returnedToken;
                    Prefers.put(KEY_DEVICE_TOKEN, deviceToken);
                }

                deviceCode = result.optString("device_code", deviceCode);
                String status = result.optString("status", "pending");
                handleStatus(status, result.optString("message", ""));
                if (!"approved".equals(status)) schedulePoll();
            } catch (Exception e) {
                showMessage("暂时无法连接授权服务器，正在自动重试。\n\n请检查电视网络连接。");
                schedulePoll();
            } finally {
                requestInFlight = false;
            }
        });
    }

    private void checkStatus() {
        if (finished || requestInFlight) {
            if (!finished) handler.postDelayed(pollRunnable, POLL_INTERVAL);
            return;
        }
        if (deviceToken == null || deviceToken.isEmpty()) {
            registerDevice();
            return;
        }
        requestInFlight = true;
        executor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("device_id", deviceId);
                payload.put("device_token", deviceToken);
                JSONObject result = post("status", payload);
                if (result.optBoolean("ok")) {
                    handleStatus(result.optString("status", "pending"), result.optString("message", ""));
                } else {
                    showMessage("授权状态暂时无法查询，正在自动重试。\n\n" + currentCodeText());
                }
            } catch (Exception e) {
                showMessage("网络暂时不可用，正在自动重试。\n\n" + currentCodeText());
            } finally {
                requestInFlight = false;
                if (!finished) handler.postDelayed(pollRunnable, POLL_INTERVAL);
            }
        });
    }

    private JSONObject post(String endpoint, JSONObject payload) throws Exception {
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(mediaType, payload.toString());
        Request request = new Request.Builder()
                .url(BASE_URL + endpoint)
                .post(body)
                .build();
        Response response = OkHttp.client(15000).newCall(request).execute();
        try {
            String text = response.body() == null ? "{}" : response.body().string();
            JSONObject result = new JSONObject(text);
            if (!response.isSuccessful()) result.put("_http_code", response.code());
            return result;
        } finally {
            response.close();
        }
    }

    private void handleStatus(String status, String serverMessage) {
        lastStatus = status;
        if ("approved".equals(status)) {
            handler.post(this::approved);
        } else if ("blocked".equals(status)) {
            showMessage("此设备已被管理员远程锁定。\n\n请联系管理员恢复设备使用。\n\n设备编号：" + deviceId);
        } else if ("rejected".equals(status)) {
            showMessage("此设备申请未获批准。\n\n请联系管理员处理后重新检查。\n\n设备编号：" + deviceId);
        } else {
            String message = "请将下面的申请码告知管理员，等待审核通过。\n\n设备申请码：\n" +
                    (deviceCode.isEmpty() ? "正在获取……" : deviceCode) +
                    "\n\n申请码有效期为30分钟。授权通过后，应用会自动进入影视首页。";
            showMessage(message);
        }
    }

    private String currentCodeText() {
        return "设备申请码：" + (deviceCode.isEmpty() ? "获取中" : deviceCode);
    }

    private void showMessage(String message) {
        handler.post(() -> {
            if (!finished && !activity.isFinishing()) messageView.setText(message);
        });
    }

    private void schedulePoll() {
        handler.removeCallbacks(pollRunnable);
        handler.postDelayed(pollRunnable, POLL_INTERVAL);
    }

    private void approved() {
        if (finished) return;
        finished = true;
        handler.removeCallbacks(pollRunnable);
        handler.removeCallbacks(refreshCodeRunnable);
        if (dialog.isShowing()) dialog.dismiss();
        if (callback != null && !activity.isFinishing()) callback.onApproved();
        executor.shutdown();
    }

    public void cancel() {
        finished = true;
        handler.removeCallbacks(pollRunnable);
        handler.removeCallbacks(refreshCodeRunnable);
        if (dialog.isShowing()) dialog.dismiss();
        executor.shutdownNow();
    }
}
