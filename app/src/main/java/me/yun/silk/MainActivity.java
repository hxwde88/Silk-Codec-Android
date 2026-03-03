package me.yun.silk;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.appcompat.widget.AppCompatTextView;
import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity {

    private AppCompatEditText etInput, etOutput;
    private AppCompatTextView tvLog;
    private AppCompatSpinner spHz;
    private SharedPreferences pref;

    private volatile boolean isDestroyed = false;

    private final String[] HZ_LIST = {
            "8000 Hz（小体积/通话级）",
            "10000 Hz（均衡体积）",
            "12000 Hz（标准音质）",
            "16000 Hz（高音质）",
            "22050 Hz（高清音质）",
            "24000 Hz（超高音质）",
            "32000 Hz（接近无损）",
            "44100 Hz（CD级音质）",
            "48000 Hz（无损级）"
    };
    
    SilkCodec codec = new SilkCodec();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pref = getSharedPreferences("silk_v3_data", Context.MODE_PRIVATE);
        initView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!checkStoragePermission()) {
            showMsg(">>> 警告：请授予文件管理权限，否则无法处理文件！");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isDestroyed = true;
    }

    private void initView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp2px(16);
        root.setPadding(padding, padding, padding, padding);
        
        LinearLayout.LayoutParams rootParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        );
        setContentView(root, rootParams);

        etInput = new AppCompatEditText(this);
        etInput.setHint("输入文件路径");
        etInput.setText(pref.getString("in", ""));
        etInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) clearLog();
        });
        addViewWithParams(root, etInput);

        etOutput = new AppCompatEditText(this);
        etOutput.setHint("输出文件路径");
        etOutput.setText(pref.getString("out", ""));
        etOutput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) clearLog();
        });
        addViewWithParams(root, etOutput);

        spHz = new AppCompatSpinner(this);
        spHz.setAdapter(new ArrayAdapter<>(this, 
                android.R.layout.simple_spinner_item, HZ_LIST));
        int lastHzPos = pref.getInt("hz_pos", HZ_LIST.length - 1);
        spHz.setSelection(lastHzPos);
        addViewWithParams(root, spHz);

        addButton(root, "Silk → MP3", 0);
        addButton(root, "MP3 → Silk", 1);
        //addButton(root, "WAV → Silk", 2);
        //addButton(root, "FLAC → Silk", 3);
        //addButton(root, "OGG → Silk", 4);
        addButton(root, "任意音频 → Silk", 5);
        addButton(root, "任意音频 → PCM", 6);

        tvLog = new AppCompatTextView(this);
        tvLog.setBackgroundColor(0xFFEEEEEE);
        tvLog.setTextColor(0xFF222222);
        tvLog.setPadding(dp2px(2), dp2px(2), dp2px(2), dp2px(2));
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        );
        root.addView(tvLog, logParams);

        showMsg("欢迎使用 Silk 编解码工具");
    }

    private void addViewWithParams(LinearLayout root, View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp2px(8);
        root.addView(view, params);
    }

    private void addButton(LinearLayout root, String text, final int type) {
        AppCompatButton b = new AppCompatButton(this);
        b.setText(text);
        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        b.setBackgroundResource(outValue.resourceId);
        
        b.setOnClickListener(v -> {
            if (!checkStoragePermission()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                return;
            }
            clearLog();
            startTransform(type);
        });
        addViewWithParams(root, b);
    }

    private void startTransform(int type) {
        String inPath = etInput.getText().toString().trim();
        String outPath = etOutput.getText().toString().trim();

        int pos = spHz.getSelectedItemPosition();
        int hz = switch (pos) {
            case 0 -> 8000;
            case 1 -> 10000;
            case 2 -> 12000;
            case 3 -> 16000;
            case 4 -> 22050;
            case 5 -> 24000;
            case 6 -> 32000;
            case 7 -> 44100;
            case 8 -> 48000;
            default -> 48000;
        };

        if (inPath.isEmpty() || outPath.isEmpty()) {
            showMsg(">>> 请输入完整路径！");
            return;
        }

        pref.edit()
                .putString("in", inPath)
                .putString("out", outPath)
                .putInt("hz_pos", pos)
                .apply();

        showMsg("开始处理...");

        WeakReference<MainActivity> weakRef = new WeakReference<>(this);
        new Thread(() -> {
            MainActivity activity = weakRef.get();
            if (activity == null || activity.isDestroyed) {
                return;
            }

            int result = -1;

            try {
                int fileType = codec.getFileType(inPath);
                String typeStr = activity.getFileTypeName(fileType);
                activity.showMsg("真实类型：" + typeStr + "(" + fileType + ")");

                result = switch (type) {
                    case 0 -> codec.silkToMp3(inPath, outPath, hz);
                    case 1 -> codec.mp3ToSilk(inPath, outPath, hz);
                    case 2 -> codec.wavToSilk(inPath, outPath, hz);
                    case 3 -> codec.flacToSilk(inPath, outPath, hz);
                    case 4 -> codec.oggToSilk(inPath, outPath, hz);
                    case 5 -> codec.autoToSilk(inPath, outPath, hz);
                    case 6 -> codec.autoToPcm(inPath, outPath);
                    default -> -1;
                };

                if (result == 0) {
                    activity.showMsg(">>> 处理成功！");
                } else {
                    activity.showMsg(">>> " + activity.getErrorMsg(result));
                }

            } catch (Exception e) {
                if (activity != null) {
                    activity.showMsg(">>> 异常：" + e.getMessage());
                }
            }
        }).start();
    }

    private String getFileTypeName(int type) {
        return switch (type) {
            case 1 -> "Silk";
            case 2 -> "MP3";
            case 3 -> "WAV";
            case 4 -> "FLAC";
            case 5 -> "OGG";
            case 6 -> "PCM";
            case 7 -> "M4A";
            case 8 -> "AAC";
            default -> "未知";
        };
    }

    private String getErrorMsg(int code) {
        return switch (code) {
            case 0 -> "成功";
            case -1 -> "错误码:-1 → 无法获取文件扩展名";
            case -2 -> "错误码:-2 → 不支持的音频格式";
            case -3 -> "错误码:-3 → PCM 转 Silk 需要额外参数";
            case -4 -> "错误码:-4 → 输入已经是 PCM 格式";
            case -5 -> "错误码:-5 → 输入已经是 Silk 格式";
            case -6 -> "错误码:-6 → Silk 转 PCM 请使用 silkToMp3";
            case -10 -> "错误码:-10 → 输出必须是 .silk 或 .slk";
            case -11 -> "错误码:-11 → 输出必须是 .mp3";
            case -12 -> "错误码:-12 → 输出必须是 .pcm 或 .raw";
            case -13 -> "错误码:-13 → 文件格式与方法不匹配";
            case -201, -202 -> "错误码:" + code + " → Silk 转 MP3 文件错误";
            case -301, -302 -> "错误码:" + code + " → MP3 解码错误";
            case -401, -402 -> "错误码:" + code + " → OGG 解码错误";
            case -501, -502 -> "错误码:" + code + " → WAV 解码错误";
            case -601, -602 -> "错误码:" + code + " → FLAC 解码错误";
            case -701, -702, -703 -> "错误码:" + code + " → PCM 参数错误";
            default -> "错误码:" + code + " → 未知错误";
        };
    }

    private void clearLog() {
        runOnUiThread(() -> tvLog.setText("by 雲上升\n"));
    }

    private void showMsg(String msg) {
        if (isDestroyed) return;
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed) {
                tvLog.append(msg + "\n");
            }
        });
    }

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return true;
    }

    private int dp2px(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }
}
