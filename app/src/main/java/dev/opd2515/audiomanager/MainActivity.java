// SPDX-License-Identifier: GPL-3.0-only
package dev.opd2515.audiomanager;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
  final ExecutorService worker = Executors.newSingleThreadExecutor();
  final RootStore store = new RootStore();
  final List<ResolveInfo> apps = new ArrayList<>(), shown = new ArrayList<>();
  TextView status;
  EditText search;
  AppAdapter adapter;
  Policy policy;
  boolean busy = true;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(24, 48, 24, 24);
    layout.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
          v.setPadding(bars.left + 24, bars.top + 24, bars.right + 24, bars.bottom + 24);
          return insets;
        });
    TextView title = new TextView(this);
    title.setText("OPD2515 Audio Manager · TEST");
    title.setTextSize(24);
    layout.addView(title);
    TextView help = new TextView(this);
    help.setText(
        "Per-app eligibility for AAudio MMAP and raw ULL. Clients choose their supported path."
            + " Saving briefly interrupts sound. Raw changes apply after audio restart; MMAP"
            + " changes require reboot. No reboot is performed here.");
    layout.addView(help);
    status = new TextView(this);
    layout.addView(status);
    Button reload = new Button(this);
    reload.setText("Reload policy / request root");
    reload.setOnClickListener(v -> load());
    layout.addView(reload);
    Button restore = new Button(this);
    restore.setText("Restore Stock…");
    restore.setOnClickListener(
        v -> {
          if (!busy)
            confirm(
                "Restore Stock",
                "Remove this manager's override and restart audio? Refused if an override existed"
                    + " before the manager, to preserve that configuration.",
                () -> perform(() -> store.write(null, true)));
        });
    layout.addView(restore);
    Button original = new Button(this);
    original.setText("Restore pre-manager backup…");
    original.setOnClickListener(
        v -> {
          if (!busy)
            confirm(
                "Restore original override",
                "Restore the exact override saved before this manager's first write? This undoes"
                    + " all manager edits and briefly stops audio. External changes still block"
                    + " restoration.",
                () -> perform(store::restoreOriginal));
        });
    layout.addView(original);
    search = new EditText(this);
    search.setSingleLine(true);
    search.setHint("Search app name or package");
    layout.addView(search);
    ListView list = new ListView(this);
    adapter = new AppAdapter();
    list.setAdapter(adapter);
    layout.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    setContentView(layout);
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

          public void onTextChanged(CharSequence s, int st, int before, int count) {
            filter();
          }

          public void afterTextChanged(Editable e) {}
        });
    load();
  }

  interface Job {
    void run() throws Exception;
  }

  void confirm(String title, String message, Runnable yes) {
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Continue", (d, w) -> yes.run())
        .show();
  }

  void perform(Job job) {
    if (busy) return;
    busy = true;
    adapter.notifyDataSetChanged();
    status.setText("Working…");
    worker.execute(
        () -> {
          try {
            job.run();
            Policy p = new Policy(store.current == null ? store.stock : store.current);
            runOnUiThread(
                () -> {
                  policy = p;
                  busy = false;
                  status.setText(
                      "Policy saved and read back. Raw eligibility updated; reboot needed for MMAP"
                          + " changes.");
                  adapter.notifyDataSetChanged();
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  busy = true;
                  status.setText(
                      "Stopped: " + e.getMessage() + ". Reload to inspect before retrying.");
                  adapter.notifyDataSetChanged();
                });
          }
        });
  }

  void load() {
    busy = true;
    status.setText("Reading device policy and launchable apps…");
    adapter.notifyDataSetChanged();
    worker.execute(
        () -> {
          try {
            store.load();
            Policy p = new Policy(store.current == null ? store.stock : store.current);
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> found = getPackageManager().queryIntentActivities(intent, 0);
            Set<String> seen = new HashSet<>();
            List<ResolveInfo> unique = new ArrayList<>();
            for (ResolveInfo r : found) if (seen.add(r.activityInfo.packageName)) unique.add(r);
            unique.sort(
                Comparator.comparing(
                    r -> r.loadLabel(getPackageManager()).toString().toLowerCase(Locale.ROOT)));
            runOnUiThread(
                () -> {
                  policy = p;
                  apps.clear();
                  apps.addAll(unique);
                  busy = false;
                  status.setText(
                      store.current == null
                          ? "Stock policy loaded. Root available."
                          : "Existing override loaded. Changes preserve all other entries. Baseline"
                                + " backup is retained on device.");
                  filter();
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  status.setText("Unavailable: " + e.getMessage());
                  busy = true;
                  adapter.notifyDataSetChanged();
                });
          }
        });
  }

  void filter() {
    shown.clear();
    String q = search.getText().toString().toLowerCase(Locale.ROOT);
    for (ResolveInfo r : apps)
      if ((r.loadLabel(getPackageManager()) + " " + r.activityInfo.packageName)
          .toLowerCase(Locale.ROOT)
          .contains(q)) shown.add(r);
    adapter.notifyDataSetChanged();
  }

  class AppAdapter extends BaseAdapter {
    public int getCount() {
      return shown.size();
    }

    public Object getItem(int p) {
      return shown.get(p);
    }

    public long getItemId(int p) {
      return p;
    }

    public View getView(int position, View old, ViewGroup parent) {
      ResolveInfo r = shown.get(position);
      String pkg = r.activityInfo.packageName;
      LinearLayout row = new LinearLayout(MainActivity.this);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setPadding(4, 12, 4, 12);
      ImageView icon = new ImageView(MainActivity.this);
      icon.setImageDrawable(r.loadIcon(getPackageManager()));
      row.addView(icon, new LinearLayout.LayoutParams(64, 64));
      int n = 0;
      try {
        if (policy != null) n = policy.eligibility(pkg);
      } catch (Exception ignored) {
      }
      TextView label = new TextView(MainActivity.this);
      label.setText(
          r.loadLabel(getPackageManager())
              + "\n"
              + pkg
              + (n == 1 ? "\nPartial eligibility (one list)" : ""));
      row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
      Switch toggle = new Switch(MainActivity.this);
      toggle.setContentDescription("Low latency eligibility for " + pkg);
      toggle.setChecked(n == 2);
      toggle.setEnabled(!busy && policy != null);
      row.addView(toggle);
      toggle.setOnCheckedChangeListener(
          (button, on) -> {
            adapter.notifyDataSetChanged();
            confirm(
                on ? "Enable eligibility" : "Remove eligibility",
                "Update both audio lists for "
                    + pkg
                    + "? Other packages and policy sections are preserved. An existing override is"
                    + " backed up and adopted on the first save. Audio will briefly stop. MMAP"
                    + " changes need a later reboot.",
                () ->
                    perform(
                        () -> {
                          String xml =
                              new Policy(store.current == null ? store.stock : store.current)
                                  .change(pkg, on, new Policy(store.stock).version());
                          store.write(xml, false);
                        }));
          });
      return row;
    }
  }
}
