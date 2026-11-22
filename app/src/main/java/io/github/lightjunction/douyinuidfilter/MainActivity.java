package io.github.lightjunction.douyinuidfilter;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** A small native settings screen; no network, analytics or third-party UI runtime. */
public final class MainActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(16, 17, 20);
    private static final int TEXT = Color.rgb(238, 240, 245);
    private static final int SECONDARY = Color.rgb(161, 166, 178);
    private static final int ACCENT = Color.rgb(168, 199, 250);
    private EditText uid;
    private Switch enabled;
    private TextView feedback;
    private TextView status;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(28), dp(44), dp(28), dp(30));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            int bottom = Math.max(insets.getSystemWindowInsetBottom(), 0);
            view.setPadding(0, top, 0, bottom);
            return insets;
        });
        setContentView(scroll);

        TextView brand = text("LOCAL / XPOSED", 12, ACCENT);
        brand.setLetterSpacing(0.16f);
        add(body, brand, 0);
        TextView title = text("只看这一个人", 32, TEXT);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        add(body, title, 22);
        TextView subtitle = text("在抖音评论区，留下你指定的 UID。", 15, SECONDARY);
        subtitle.setLineSpacing(dp(5), 1f);
        add(body, subtitle, 12);

        enabled = new Switch(this);
        enabled.setText("启用评论过滤");
        enabled.setTextColor(TEXT);
        enabled.setTextSize(17);
        enabled.setMinHeight(dp(56));
        enabled.setThumbTintList(new ColorStateList(new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}}, new int[] {ACCENT, SECONDARY}));
        add(body, enabled, 42);
        TextView label = text("目标用户 UID", 13, SECONDARY);
        add(body, label, 28);
        uid = new EditText(this);
        uid.setSingleLine(true);
        uid.setTextColor(TEXT);
        uid.setTextSize(21);
        uid.setHintTextColor(Color.rgb(106, 112, 125));
        uid.setHint("输入数字 UID");
        uid.setInputType(InputType.TYPE_CLASS_NUMBER);
        uid.setFilters(new InputFilter[] {new InputFilter.LengthFilter(30)});
        uid.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        uid.setSelectAllOnFocus(false);
        uid.setPadding(dp(2), dp(14), dp(2), dp(14));
        uid.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        add(body, uid, 4);
        label.setLabelFor(uid.getId() == View.NO_ID ? assignId(uid) : uid.getId());
        TextView hint = text("填写数字 UID，不是昵称、抖音号或 sec_uid。", 12, SECONDARY);
        hint.setLineSpacing(dp(4), 1f);
        add(body, hint, 10);

        Button save = button("保存设置", true);
        save.setOnClickListener(view -> save());
        add(body, save, 28);
        feedback = text("保存后，关闭并重新打开抖音评论区。", 12, SECONDARY);
        feedback.setGravity(Gravity.CENTER_HORIZONTAL);
        feedback.setLineSpacing(dp(4), 1f);
        add(body, feedback, 12);

        status = text("", 13, SECONDARY);
        status.setLineSpacing(dp(6), 1f);
        add(body, status, 38);
        Button open = button("打开抖音", false);
        open.setOnClickListener(view -> openDouyin());
        add(body, open, 18);

        TextView usage = text("首次使用\n在 LSPosed / Vector 中启用本模块，作用域选择「抖音」，再强行停止并重新打开抖音。手机重启后，先打开一次本模块，再打开抖音。", 13, SECONDARY);
        usage.setLineSpacing(dp(7), 1f);
        add(body, usage, 28);
        TextView privacy = text("仅本地展示过滤 · 无联网权限\n无匹配时显示 0；后续加载到的评论也会继续过滤。", 12, SECONDARY);
        privacy.setLineSpacing(dp(6), 1f);
        add(body, privacy, 26);
        TextView version = text("DOUYIN UID FILTER  /  " + BuildConfig.VERSION_NAME, 10, Color.rgb(112, 118, 131));
        version.setLetterSpacing(0.1f);
        add(body, version, 34);

        SharedPreferences prefs = getSharedPreferences(ConfigProvider.PREFS, 0);
        uid.setText(prefs.getString("uid", ""));
        enabled.setChecked(prefs.getBoolean("enabled", false));
        String accessError = grantDouyinReadAccess();
        if (accessError != null) feedback.setText(accessError);
    }

    private int assignId(View view) { int id = View.generateViewId(); view.setId(id); return id; }

    @Override protected void onResume() {
        super.onResume();
        if (status == null) return;
        SharedPreferences diagnostics = getSharedPreferences(ConfigProvider.DIAGNOSTICS, 0);
        long last = diagnostics.getLong("last_seen", 0L);
        if (last == 0L) {
            status.setText("等待接入抖音\n启用作用域后，打开一次抖音以检查模块状态。");
        } else if (diagnostics.getBoolean("ready", false)) {
            status.setText(getString(R.string.status_connected, diagnostics.getString("version", "")));
        } else {
            status.setText("当前抖音版本适配失败\n请查看 Xposed 模块日志，暂勿依赖过滤结果。");
        }
    }

    private void save() {
        String value = uid.getText().toString().trim();
        if ((enabled.isChecked() || !value.isEmpty()) && !FilterConfig.isValidUid(value)) {
            uid.setError("请输入不以 0 开头的数字 UID，最多 30 位");
            uid.requestFocus();
            return;
        }
        SharedPreferences prefs = getSharedPreferences(ConfigProvider.PREFS, 0);
        prefs.edit().putString("uid", value).putBoolean("enabled", enabled.isChecked())
            .putLong("revision", prefs.getLong("revision", 0L) + 1L).apply();
        String accessError = grantDouyinReadAccess();
        feedback.setText(accessError != null ? "已保存。" + accessError : enabled.isChecked()
                ? "已保存。重新打开评论区后，只展示这个 UID。" : "过滤已关闭。重新打开评论区恢复原始展示。");
        feedback.setTextColor(ACCENT);
        uid.clearFocus();
        android.view.inputmethod.InputMethodManager keyboard = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(uid.getWindowToken(), 0);
    }

    private void openDouyin() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(ConfigProvider.TARGET);
        if (intent == null) { Toast.makeText(this, "尚未安装抖音", Toast.LENGTH_SHORT).show(); return; }
        String accessError = grantDouyinReadAccess();
        if (accessError != null) { Toast.makeText(this, accessError, Toast.LENGTH_SHORT).show(); return; }
        try { startActivity(intent); }
        catch (ActivityNotFoundException exception) { Toast.makeText(this, "无法打开抖音", Toast.LENGTH_SHORT).show(); }
    }

    /** Makes this provider visible to the host on Android 11+, without granting write access. */
    private String grantDouyinReadAccess() {
        try {
            getPackageManager().getApplicationInfo(ConfigProvider.TARGET, 0);
            grantUriPermission(ConfigProvider.TARGET, ConfigProvider.URI,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return null;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "尚未安装抖音；安装后重新打开本模块。";
        } catch (SecurityException | IllegalArgumentException exception) {
            Log.w("DouyinUidFilter", "Provider visibility grant failed: "
                    + exception.getClass().getSimpleName());
            return "暂时无法连接抖音；请重新打开本模块后再试。";
        }
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setIncludeFontPadding(false);
        return view;
    }

    private Button button(String value, boolean primary) {
        Button button = new Button(this);
        button.setText(value); button.setTextSize(15); button.setAllCaps(false);
        button.setTextColor(primary ? BACKGROUND : ACCENT);
        GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? ACCENT : Color.rgb(28, 31, 38));
        background.setCornerRadius(dp(28));
        button.setBackground(background);
        button.setMinHeight(dp(54));
        return button;
    }

    private void add(LinearLayout body, View child, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        body.addView(child, params);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
