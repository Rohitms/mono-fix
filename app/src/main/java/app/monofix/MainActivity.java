package app.monofix;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.audio.ChannelMixingAudioProcessor;
import androidx.media3.common.audio.ChannelMixingMatrix;
import androidx.media3.transformer.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int INK = 0xff173d39, GREEN = 0xffbbef91, PAPER = 0xfff5f6f0;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout layout;
    private RadioGroup modes;
    private Button pick, convert, play, details;
    private TextView filename, status;
    private ProgressBar progress;
    private final ArrayList<Uri> selection = new ArrayList<>();
    private final ArrayList<Uri> failed = new ArrayList<>();
    private final ArrayList<String> report = new ArrayList<>();
    private int position;
    private Uri result;
    private String name;
    private boolean busy;
    private Transformer exporter;
    private File temporary;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(INK);
        getWindow().setNavigationBarColor(INK);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(PAPER);
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(24), dp(24), dp(24));
        scroll.addView(layout);
        setContentView(scroll);
        label("Mono Fix", 36, true);
        label("Turn one-sided video audio into sound you can hear in both ears.", 17, false);
        section("Source videos");
        filename = label("Choose one video, or long-press to select several.", 16, false);
        pick = button("Choose videos", false);
        pick.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true).setType("video/*").addCategory(Intent.CATEGORY_OPENABLE), 1));
        section("Audio to keep");
        modes = new RadioGroup(this);
        layout.addView(modes);
        for (String text : new String[]{"Either side (one side is silent)", "Left only", "Right only"}) {
            RadioButton option = new RadioButton(this);
            option.setId(View.generateViewId());
            option.setText(text);
            option.setTextSize(16);
            option.setMinHeight(dp(48));
            option.setButtonTintList(android.content.res.ColorStateList.valueOf(INK));
            modes.addView(option);
        }
        modes.check(modes.getChildAt(0).getId());
        convert = button("Save mono video", true);
        convert.setOnClickListener(v -> begin());
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        layout.addView(progress);
        status = label("Saves a new MP4 in Movies/Mono Fix.", 15, false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        play = button("Play saved video", false);
        play.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(result, "video/mp4")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); }
            catch (ActivityNotFoundException e) { status.setText("Open the saved video from your Gallery."); }
        });
        details = button("View results", false);
        details.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Conversion results")
                .setItems(report.toArray(new String[0]), null).setPositiveButton("Close", null).show());
        if (state != null) {
            ArrayList<Uri> restored = state.getParcelableArrayList("selection");
            if (restored != null) selection.addAll(restored);
            ArrayList<String> previous = state.getStringArrayList("report");
            if (previous != null) report.addAll(previous);
            result = state.getParcelable("result");
            filename.setText(state.getCharSequence("caption", filename.getText()));
            status.setText(state.getCharSequence("status", status.getText()));
            modes.check(modes.getChildAt(state.getInt("mode")).getId());
        }
        refresh();
    }

    private int dp(int size) { return Math.round(size * getResources().getDisplayMetrics().density); }
    private TextView label(String text, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(INK);
        view.setTextSize(size);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        layout.addView(view);
        return view;
    }
    private void section(String text) { label(text, 20, true).setPadding(0, dp(24), 0, dp(5)); }
    private Button button(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(INK);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(12));
        shape.setColor(primary ? GREEN : 0xffe3eae1);
        b.setBackground(shape);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(10), 0, dp(10));
        layout.addView(b, params);
        return b;
    }
    private int mode() { return modes.indexOfChild(modes.findViewById(modes.getCheckedRadioButtonId())); }
    private void refresh() {
        pick.setEnabled(!busy);
        convert.setEnabled(!selection.isEmpty() && !busy);
        convert.setText(selection.size() > 1 ? "Save " + selection.size() + " mono videos" : "Save mono video");
        play.setText(report.size() > 1 ? "Play last saved video" : "Play saved video");
        details.setVisibility(!busy && !report.isEmpty() ? View.VISIBLE : View.GONE);
        convert.setAlpha(convert.isEnabled() ? 1f : .45f);
        for (int i = 0; i < modes.getChildCount(); i++) modes.getChildAt(i).setEnabled(!busy);
        play.setVisibility(result != null && !busy ? View.VISIBLE : View.GONE);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    @Override protected void onActivityResult(int request, int code, Intent data) {
        super.onActivityResult(request, code, data);
        if (request != 1 || code != RESULT_OK || data == null || busy) return;
        LinkedHashSet<Uri> chosen = new LinkedHashSet<>();
        ClipData clips = data.getClipData();
        if (clips != null) {
            for (int i = 0; i < clips.getItemCount(); i++) chosen.add(clips.getItemAt(i).getUri());
        }
        if (data.getData() != null) chosen.add(data.getData());
        chosen.remove(null);
        if (chosen.isEmpty()) return;
        selection.clear();
        selection.addAll(chosen);
        report.clear();
        result = null;
        updateCaption();
        status.setText("Saves a new MP4 for each video in Movies/Mono Fix.");
        refresh();
    }
    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getString(0);
        } catch (RuntimeException ignored) {}
        return "Video";
    }
    private void updateCaption() {
        filename.setText(selection.size() == 1 ? displayName(selection.get(0)) : selection.size() + " videos selected");
    }
    private void begin() {
        busy = true;
        result = null;
        position = 0;
        report.clear();
        failed.clear();
        refresh();
        nextVideo();
    }
    private void nextVideo() {
        Uri input = selection.get(position);
        name = displayName(input);
        progress.setIndeterminate(true);
        progress.setProgress(0);
        showStage("Reading");
        worker.execute(() -> {
            try {
                validateSource(input);
                runOnUiThread(() -> { if (!isDestroyed() && !isFinishing()) startExport(input); });
            } catch (Exception e) { runOnUiThread(() -> finishJob(e)); }
        });
    }
    private void showStage(String stage) {
        status.setText(stage + " " + (position + 1) + " of " + selection.size() + " · " + name);
    }
    private void validateSource(Uri input) throws IOException {
        MediaExtractor tracks = new MediaExtractor();
        try {
            tracks.setDataSource(this, input, null);
            int audio = 0;
            boolean video = false;
            for (int i = 0; i < tracks.getTrackCount(); i++) {
                MediaFormat f = tracks.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("video/")) video = true;
                if (!mime.startsWith("audio/")) continue;
                audio++;
                int channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                if (channels == 1) throw new IOException("This video's audio is already mono.");
                if (channels != 2) throw new IOException("Choose a video with two-channel stereo audio.");
            }
            if (!video || audio != 1) throw new IOException("Choose a video with one stereo audio track.");
        } finally { tracks.release(); }
    }
    private void startExport(Uri input) {
        try {
            int mode = mode();
            ChannelMixingAudioProcessor mix = new ChannelMixingAudioProcessor();
            mix.putChannelMixingMatrix(new ChannelMixingMatrix(2, 1,
                    new float[]{mode == 2 ? 0f : 1f, mode == 1 ? 0f : 1f}));
            EditedMediaItem item = new EditedMediaItem.Builder(MediaItem.fromUri(input))
                    .setEffects(new Effects(Collections.singletonList(mix), Collections.emptyList())).build();
            temporary = File.createTempFile("mono-", ".mp4", getCacheDir());
            exporter = new Transformer.Builder(this).setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(new Transformer.Listener() {
                        @Override public void onCompleted(Composition c, ExportResult export) {
                            exporter = null;
                            showStage("Saving");
                            progress.setIndeterminate(true);
                            File ready = temporary;
                            temporary = null;
                            worker.execute(() -> publish(ready));
                        }
                        @Override public void onError(Composition c, ExportResult export, ExportException e) {
                            finishJob(e);
                        }
                    }).build();
            exporter.start(item, temporary.getAbsolutePath());
            showStage("Converting");
            showProgress();
        } catch (Exception e) { finishJob(e); }
    }
    private void showProgress() {
        if (exporter == null) return;
        ProgressHolder p = new ProgressHolder();
        if (exporter.getProgress(p) == Transformer.PROGRESS_STATE_AVAILABLE) {
            progress.setIndeterminate(false);
            progress.setProgress(p.progress);
        }
        handler.postDelayed(this::showProgress, 500);
    }
    private void publish(File ready) {
        Uri destination = null;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, name.replaceFirst("\\.[^.]+$", "") + "-mono.mp4");
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Mono Fix/");
            values.put(MediaStore.Video.Media.IS_PENDING, 1);
            ContentResolver resolver = getContentResolver();
            destination = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            if (destination == null) throw new IOException("Could not create the output file.");
            try (InputStream source = new FileInputStream(ready); OutputStream out = resolver.openOutputStream(destination)) {
                if (out == null) throw new IOException("Could not open the output file.");
                byte[] buffer = new byte[131072];
                int n;
                while ((n = source.read(buffer)) != -1) out.write(buffer, 0, n);
            }
            values.clear();
            values.put(MediaStore.Video.Media.IS_PENDING, 0);
            if (resolver.update(destination, values, null, null) != 1) throw new IOException("Could not publish the output file.");
            Uri saved = destination;
            runOnUiThread(() -> { result = saved; finishJob(null); });
        } catch (Exception e) {
            if (destination != null) getContentResolver().delete(destination, null, null);
            runOnUiThread(() -> finishJob(e));
        } finally { ready.delete(); }
    }
    private void finishJob(Exception e) {
        exporter = null;
        if (temporary != null) { temporary.delete(); temporary = null; }
        handler.removeCallbacksAndMessages(null);
        if (isDestroyed() || isFinishing()) return;
        if (e == null) {
            report.add(name + " — Saved");
        } else {
            failed.add(selection.get(position));
            report.add(name + " — " + e.getLocalizedMessage());
        }
        position++;
        if (position < selection.size()) {
            nextVideo();
            return;
        }
        busy = false;
        int saved = position - failed.size();
        String summary = "Saved " + saved + " of " + position + " in Movies/Mono Fix.";
        if (!failed.isEmpty()) {
            selection.clear();
            selection.addAll(failed);
            updateCaption();
            summary += " Only unsuccessful clips are selected for retry.";
        }
        status.setText(summary);
        refresh();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        ArrayList<Uri> remaining = new ArrayList<>();
        if (busy) {
            remaining.addAll(failed);
            remaining.addAll(selection.subList(position, selection.size()));
        } else {
            remaining.addAll(selection);
        }
        state.putParcelableArrayList("selection", remaining);
        state.putStringArrayList("report", new ArrayList<>(report));
        state.putParcelable("result", result);
        state.putCharSequence("caption", busy ? remaining.size() + " videos selected" : filename.getText());
        state.putCharSequence("status", busy ? "Conversion was interrupted. Check saved files before retrying." : status.getText());
        state.putInt("mode", mode());
    }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (exporter != null) exporter.cancel();
        if (temporary != null) temporary.delete();
        worker.shutdown();
        super.onDestroy();
    }
}
