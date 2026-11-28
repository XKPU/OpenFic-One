package cc.openfic.one;

import static cc.openfic.one.Cfg.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.text.InputType;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;


/** 更新确认面板。 */
class UpdatePanel {
    private final MainActivity act;

    UpdatePanel(MainActivity act) {
        this.act = act;
    }

    int askUpdate(final String title, final String notes, final String installed,
                          final String latest, final String snoozeKey) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Integer> result = new AtomicReference<Integer>(UPDATE_LATER);
        act.ui.post(new Runnable() {
            @Override
            public void run() {
                act.overlay.removeAllViews();
                act.overlay.addView(buildUpdatePanel(latch, result, title, notes, installed, latest, snoozeKey),
                        new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                act.overlay.setVisibility(View.VISIBLE);
                act.overlay.setFocusable(true);
                act.overlay.setFocusableInTouchMode(true);
                act.overlay.requestFocus();
                act.overlay.setOnKeyListener(new View.OnKeyListener() {
                    @Override
                    public boolean onKey(View v, int keyCode, KeyEvent event) {
                        if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
                            result.set(UPDATE_LATER);
                            act.console.dismissOverlay();
                            latch.countDown();
                        }
                        return true;
                    }
                });
            }
        });
        latch.await();
        return result.get();
    }

    View buildUpdatePanel(final CountDownLatch latch, final AtomicReference<Integer> result,
                                  String title, String notes, String installed, String latest,
                                  final String snoozeKey) {
        final Palette pal = Palette.of(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(pal.bg);
        box.setPadding(act.console.dp(24), act.console.dp(36), act.console.dp(24), act.console.dp(28));

        TextView head = new TextView(act);
        head.setText(title);
        head.setTextColor(pal.text);
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(head);

        TextView body = new TextView(act);
        body.setText(Txt.f(act, "当前版本 %s，最新版本 %s。", installed, latest));
        body.setTextColor(pal.muted);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        body.setPadding(0, act.console.dp(10), 0, notes == null || notes.isEmpty() ? act.console.dp(18) : act.console.dp(8));
        box.addView(body);

        if (notes != null && !notes.isEmpty()) {
            TextView noteView = new TextView(act);
            noteView.setText(notes);
            noteView.setTextColor(pal.muted);
            noteView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            noteView.setPadding(0, 0, 0, act.console.dp(18));
            box.addView(noteView);
        }

        Button update = new Button(act);
        update.setText(Txt.t(act, "立即更新"));
        update.setAllCaps(false);
        update.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                result.set(UPDATE_ACCEPT);
                act.console.dismissOverlay();
                latch.countDown();
            }
        });
        box.addView(update, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button skip = new Button(act);
        skip.setText(Txt.f(act, "跳过此版本（%s）", latest));
        skip.setAllCaps(false);
        skip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                result.set(UPDATE_SKIP_VERSION);
                act.console.dismissOverlay();
                latch.countDown();
            }
        });
        box.addView(skip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout snoozeRow = new LinearLayout(act);
        snoozeRow.setOrientation(LinearLayout.HORIZONTAL);
        snoozeRow.setGravity(Gravity.CENTER_VERTICAL);
        snoozeRow.setPadding(0, act.console.dp(10), 0, act.console.dp(10));

        TextView snoozeHint = new TextView(act);
        snoozeHint.setText(Txt.t(act, "在"));
        snoozeHint.setTextColor(pal.muted);
        snoozeHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        snoozeRow.addView(snoozeHint);

        final EditText days = new EditText(act);
        days.setInputType(InputType.TYPE_CLASS_NUMBER);
        days.setText("7");
        days.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        days.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(act.console.dp(64), ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.setMargins(act.console.dp(6), 0, act.console.dp(6), 0);
        snoozeRow.addView(days, dlp);

        TextView tail = new TextView(act);
        tail.setText(Txt.t(act, "日内不再询问"));
        tail.setTextColor(pal.muted);
        tail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        snoozeRow.addView(tail);

        Button snooze = new Button(act);
        snooze.setText(Txt.t(act, "确定"));
        snooze.setAllCaps(false);
        snooze.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int d = 7;
                try {
                    d = Integer.parseInt(days.getText().toString().trim());
                } catch (NumberFormatException ignored) {
                }
                if (d < 1) d = 1;
                act.prefs.edit().putLong(snoozeKey, System.currentTimeMillis() + d * 86400000L).apply();
                result.set(UPDATE_SNOOZE);
                act.console.dismissOverlay();
                act.console.logT("已设置 %d 天内不再检查更新", d);
                latch.countDown();
            }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.setMargins(act.console.dp(8), 0, 0, 0);
        snoozeRow.addView(snooze, slp);
        box.addView(snoozeRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button never = new Button(act);
        never.setText(Txt.t(act, "永不更新（关闭更新检查）"));
        never.setAllCaps(false);
        never.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                result.set(UPDATE_NEVER);
                act.console.dismissOverlay();
                latch.countDown();
            }
        });
        box.addView(never, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView sc = new ScrollView(act);
        sc.setBackgroundColor(pal.bg);
        sc.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sc;
    }
}
