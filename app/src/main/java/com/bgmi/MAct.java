package com.bgmi;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bgmi.utils.AppManager;
import com.mundo.MundoCore;
import com.mundo.entity.pm.InstallResult;

import org.lsposed.lsparanoid.Obfuscate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

@Obfuscate
public class MAct extends AppCompatActivity {
    static {
        try {
            System.loadLibrary("zenin");
        } catch (UnsatisfiedLinkError ignored) {}
    }

    private static final String PKG_BGMI = "com.pubg.imobile";
    private static final int USER_ID = 0;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private boolean doubleBackExit = false;

    public static native String exdate();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Fullscreen/Transparent status bar look
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            getWindow().setStatusBarColor(Color.TRANSPARENT);
        }

        doCountTimerAccount();

        // Naye Start Button ki ID
        findViewById(R.id.btnStart).setOnClickListener(v -> handleStart());
        
        // btnStop remove kar diya hai layout se isliye yahan zaroorat nahi
    }

    private void handleStart() {
        if (MundoCore.get() == null) {
            Toast.makeText(this, "Core is null!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!MundoCore.get().isInstalled(PKG_BGMI, USER_ID)) {
            Toast.makeText(this, "Installing BGMI in Virtual Space...", Toast.LENGTH_SHORT).show();
            InstallResult res = MundoCore.get().installPackageAsUser(PKG_BGMI, USER_ID);
            if (res.success) {
                forceAutoCopyObb();
            } else {
                Toast.makeText(this, "Install Failed: " + res.msg, Toast.LENGTH_SHORT).show();
            }
        } else {
            forceAutoCopyObb();
        }
    }

    private void forceAutoCopyObb() {
        String internalRoot = Environment.getExternalStorageDirectory().getAbsolutePath();
        File sourceFolder = new File(internalRoot + "/Android/obb/" + PKG_BGMI);
        File destFolder = new File(internalRoot + "/Sdcard/Android/obb/" + PKG_BGMI);

        if (!destFolder.exists()) destFolder.mkdirs();

        File[] existingFiles = destFolder.listFiles((dir, name) -> name.endsWith(".obb"));
        if (existingFiles != null && existingFiles.length > 0) {
            launchGame();
            return;
        }

        Toast.makeText(this, "OBB Copying... Wait 1m", Toast.LENGTH_SHORT).show();
        AtomicBoolean isFinished = new AtomicBoolean(false);

        timerHandler.postDelayed(() -> {
            if (!isFinished.get()) {
                isFinished.set(true);
                Toast.makeText(MAct.this, "Copy Timeout! Check manually.", Toast.LENGTH_LONG).show();
            }
        }, 60000);

        new Thread(() -> {
            try {
                File[] sourceFiles = sourceFolder.listFiles((dir, name) -> name.endsWith(".obb"));
                if (sourceFiles == null || sourceFiles.length == 0) {
                    if (!isFinished.get()) {
                        isFinished.set(true);
                        runOnUiThread(() -> Toast.makeText(MAct.this, "Source OBB missing!", Toast.LENGTH_LONG).show());
                    }
                    return;
                }

                File srcFile = sourceFiles[0];
                File destFile = new File(destFolder, srcFile.getName());

                try (FileChannel srcChannel = new FileInputStream(srcFile).getChannel();
                     FileChannel destChannel = new FileOutputStream(destFile).getChannel()) {
                    srcChannel.transferTo(0, srcChannel.size(), destChannel);
                }

                if (!isFinished.get()) {
                    isFinished.set(true);
                    runOnUiThread(() -> {
                        Toast.makeText(MAct.this, "OBB Ready!", Toast.LENGTH_SHORT).show();
                        launchGame();
                    });
                }
            } catch (Exception e) {
                if (!isFinished.get()) {
                    isFinished.set(true);
                    runOnUiThread(() -> Toast.makeText(MAct.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }
        }).start();
    }

    private void launchGame() {
        try {
            MundoCore.get().launchApk(PKG_BGMI, USER_ID);
        } catch (Exception e) {
            Toast.makeText(this, "Launch Error", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (doubleBackExit) { finishAffinity(); return; }
        this.doubleBackExit = true;
        Toast.makeText(this, "Press again to exit", Toast.LENGTH_SHORT).show();
        timerHandler.postDelayed(() -> doubleBackExit = false, 2000);
    }

    private void doCountTimerAccount() {
        timerHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    Date expiry = sdf.parse(exdate());
                    long diff = expiry.getTime() - System.currentTimeMillis();
                    
                    if (diff > 0) {
                        // Naye Layout ki single ID: tvExpires
                        long d = diff / 86400000;
                        long h = (diff / 3600000) % 24;
                        long m = (diff / 60000) % 60;
                        long s = (diff / 1000) % 60;
                        
                        String timeLeft = String.format("%dd %dh %dm %ds", d, h, m, s);
                        ((TextView) findViewById(R.id.tvExpires)).setText(timeLeft);
                        
                        timerHandler.postDelayed(this, 1000);
                    } else {
                        Toast.makeText(MAct.this, "Subscription Expired!", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                } catch (Exception ignored) {}
            }
        });
    }
}