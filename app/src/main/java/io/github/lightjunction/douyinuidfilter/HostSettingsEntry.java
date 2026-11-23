package io.github.lightjunction.douyinuidfilter;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

/** Optional entry in an existing platform options menu; no overlay or proprietary UI traversal. */
final class HostSettingsEntry {
    private static final int ITEM_ID = View.generateViewId();

    static boolean addTo(Activity activity, Menu menu) {
        if (!ConfigProvider.TARGET.equals(activity.getPackageName()) || menu == null
                || menu.size() == 0 || menu.findItem(ITEM_ID) != null) return false;
        MenuItem item = menu.add(Menu.NONE, ITEM_ID, Menu.NONE, "UID 评论过滤");
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        item.setOnMenuItemClickListener(selected -> {
            try {
                activity.startActivity(new Intent().setClassName(
                        "io.github.lightjunction.douyinuidfilter",
                        "io.github.lightjunction.douyinuidfilter.MainActivity"));
            } catch (ActivityNotFoundException | SecurityException unavailable) {
                Toast.makeText(activity, "无法打开 UID 评论过滤，请从桌面打开模块设置。", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        return true;
    }
}
