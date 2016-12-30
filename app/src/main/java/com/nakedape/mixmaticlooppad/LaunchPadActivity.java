package com.nakedape.mixmaticlooppad;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.*;
import android.os.Process;
import android.preference.PreferenceManager;
import android.support.annotation.IntegerRes;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.content.res.AppCompatResources;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.SparseArray;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AnticipateOvershootInterpolator;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.BaseExpandableListAdapter;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import IABUtils.IabHelper;
import IABUtils.IabResult;
import IABUtils.Inventory;
import javazoom.jl.converter.WaveFile;


public class LaunchPadActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener{

    private static final String LOG_TAG = "LaunchPadActivity";

    public static String TOUCHPAD_ID = "com.nakedape.mixmaticlooppad.touchpadid";
    public static String SAMPLE_PATH = "com.nakedape.mixmaticlooppad.samplepath";
    public static String COLOR = "com.nakedape.mixmaticlooppad.color";
    public static String LOOPMODE = "com.nakedape.mixmaticlooppad.loop";
    public static String LAUNCHMODE = "com.nakedape.mixmaticlooppad.launchmode";
    public static String SAMPLE_VOLUME = "com.nakedape.mixmaticlooppad.volume";
    public static String QUANTIZE_MODE = "com.nakedape.mixmaticlooppad.quantize_mode";
    private static int GET_SAMPLE = 0;

    private boolean isEditMode = false;
    private boolean isRecording = false;
    private boolean isPlaying = false;
    private boolean isMicRecording = false;
    private boolean dialogCanceled = false;
    private boolean stopCounterThread = false;
    private boolean stopPlaybackThread = false;


    // Counter
    private TextView counterTextView;
    private long counter;
    private int bpm = 120;
    private int timeSignature = 4;
    private long recordingEndTime;
    private ArrayList<LaunchEvent> launchEvents = new ArrayList<LaunchEvent>(50);
    private int playEventIndex = 0;
    private ArrayList<LaunchEvent> loopingSamplesPlaying = new ArrayList<LaunchEvent>(5);
    private ArrayList<Integer> activePads;

    private Context context;
    private RelativeLayout rootLayout;
    private ProgressDialog progressDialog;
    private SparseArray<Sample> samples;
    private File homeDirectory, sampleDirectory, samplePackDirectory;
    private AudioManager am;
    private SharedPreferences launchPadprefs; // Stores setting for each launchpad
    private SharedPreferences activityPrefs; // Stores app wide preferences
    private LaunchPadData savedData;
    private View listItemPauseButton;

    private int selectedSampleID;
    private int[] touchPadIds = {R.id.touchPad1, R.id.touchPad2, R.id.touchPad3, R.id.touchPad4, R.id.touchPad5, R.id.touchPad6,
            R.id.touchPad7, R.id.touchPad8, R.id.touchPad9, R.id.touchPad10, R.id.touchPad11, R.id.touchPad12,
            R.id.touchPad13, R.id.touchPad14, R.id.touchPad15, R.id.touchPad16, R.id.touchPad17, R.id.touchPad18,
            R.id.touchPad19, R.id.touchPad20, R.id.touchPad21, R.id.touchPad22, R.id.touchPad23, R.id.touchPad24};
    private Menu actionBarMenu;
    private boolean isSampleLibraryShowing = false;
    private SampleListAdapter sampleListAdapter;
    private SamplePackListAdapter samplePackListAdapter;
    private int sampleLibraryIndex = -1;
    private MediaPlayer samplePlayer;
    private Runtime runtime;

    // Activity overrides
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.loading_screen);
        context = this;

        // Find the retained fragment on activity restarts
        FragmentManager fm = getFragmentManager();
        savedData = (LaunchPadData) fm.findFragmentByTag("data");

        new Thread(new Runnable() {
            @Override
            public void run() {
                loadInBackground();
            }
        }).start();
    }
    @Override
    public void onPause(){
        super.onPause();
        // Logs 'app deactivate' App Event to Facebook.
        //AppEventsLogger.deactivateApp(this);

        if (samplePlayer != null){
            if (samplePlayer.isPlaying()) {
                samplePlayer.stop();
                if (listItemPauseButton != null)
                    listItemPauseButton.setSelected(false);
            }
            samplePlayer.release();
            samplePlayer = null;
        }
    }
    @Override
    public void onResume() {
        super.onResume();
        // Logs 'install' and 'app activate' App Events to Facebook.
        //AppEventsLogger.activateApp(this);
        if (rootLayout != null) {
            int newBpm = activityPrefs.getInt(LaunchPadPreferencesFragment.PREF_BPM, 120);
            int newTimeSignature = Integer.parseInt(activityPrefs.getString(LaunchPadPreferencesFragment.PREF_TIME_SIG, "4"));

            if (newBpm != bpm || newTimeSignature != timeSignature) {
                counter = 0;
                bpm = newBpm;
                timeSignature = newTimeSignature;
            }
            if (counterTextView != null)
                updateCounterMessage();
        }
    }
    @Override
    protected void onDestroy(){
        super.onDestroy();
        if (progressDialog != null)
            if (progressDialog.isShowing())
                progressDialog.cancel();
        stopCounterThread = true;
        stopPlaybackThread = true;
        if (rootLayout != null) {
            if (isFinishing()) {
                // Release audiotrack resources
                for (Integer i : activePads) {
                    Sample s = samples.get(i);
                    isPlaying = false;
                    if (s.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        s.stop();
                    }
                    s.audioTrack.release();
                }
                unregisterNetworkListener();
            } else {
                savedData.setSamples(samples);
                savedData.setCounter(counter);
                savedData.setEditMode(isEditMode);
                savedData.setActivePads(activePads);
                savedData.setPlaying(isPlaying);
                savedData.setRecording(isRecording);
                savedData.setRecordingEndTime(recordingEndTime);
                savedData.setLaunchEvents(launchEvents);
                savedData.setLaunchQueue(launchQueue);
                savedData.setPlayEventIndex(playEventIndex);
                savedData.sampleFiles = sampleListAdapter.sampleFiles;
                savedData.sampleLengths = sampleListAdapter.sampleLengths;
            }
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_launch_pad_activity, menu);
        actionBarMenu = menu;
        if (isPlaying || isRecording){
            MenuItem playButton = menu.findItem(R.id.action_play);
            playButton.setIcon(R.drawable.ic_action_av_pause);
        }
        return true;
    }
    @Override
    public boolean onMenuOpened(int featureId, Menu menu){
        stopPlayBack();
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_settings:
                isRecording = false;
                Intent intent = new Intent(EditPreferencesActivity.LAUNCHPAD_PREFS, null, context, EditPreferencesActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_edit_mode:
                if (!isEditMode) {
                    isRecording = false;
                    gotoEditMode();
                } else {
                    gotoPlayMode();
                }
                return true;
            case R.id.action_play:
                if (isPlaying || isRecording) {
                    item.setIcon(R.drawable.ic_action_av_play_arrow);
                    stopPlayBack();
                    showInterstitialAd();
                }
                else if (launchEvents.size() > 0 && playEventIndex < launchEvents.size()){
                    item.setIcon(R.drawable.ic_action_av_pause);
                    playMix();
                } else if (launchEvents.size() > 0){
                    RewindButtonClick(null);
                    item.setIcon(R.drawable.ic_action_av_pause);
                    playMix();
                }
                return true;
            case R.id.action_rewind:
                RewindButtonClick(null);
                return true;
            case R.id.action_fast_forward:
                FastForwardButtonClick(null);
                return true;
            case R.id.action_reset:
                resetRecording();
                return true;
            case R.id.action_clear_pads:
                removeAllSamples();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        if (requestCode == GET_SAMPLE && resultCode == RESULT_OK) {
            String path = data.getData().getPath();
            // Create a new file to contain the new sample
            File sampleFile = new File(path);
            if (sampleFile.exists() && sampleListAdapter != null)
                sampleListAdapter.add(sampleFile);
            // If the file exists and a pad was selected, prepare touchpad
            int id = data.getIntExtra(TOUCHPAD_ID, -1);
            TouchPad t = (TouchPad) findViewById(id);
            if (sampleFile.exists() && t != null) {
                selectedSampleID = id;
                Sample sample = new Sample(sampleFile.getAbsolutePath(), id);
                if (sample.isReady()) {
                    sample.setOnPlayFinishedListener(samplePlayListener);
                    String padNumber = (String) t.getTag();
                    // Set sample properties and save to shared preferences
                    SharedPreferences.Editor editor = launchPadprefs.edit();
                    sample.setLaunchMode(launchPadprefs.getInt(padNumber + LAUNCHMODE, Sample.LAUNCHMODE_GATE));
                    sample.setLoopMode(launchPadprefs.getBoolean(padNumber + LOOPMODE, false));
                    sample.setVolume(launchPadprefs.getFloat(padNumber + SAMPLE_VOLUME, 0.5f * AudioTrack.getMaxVolume()));
                    samples.put(id, sample);
                    if (!activePads.contains((Integer) id)) activePads.add(id);
                    // Set and save color
                    int colorIndex = data.getIntExtra(COLOR, 0);
                    t.setBackgroundResource(padColorDrawables[colorIndex]);
                    editor.putInt(padNumber + COLOR, colorIndex);
                    editor.apply();
                } else {
                    notifySampleLoadError(sample);
                }
            }
        } else if (requestCode == STORE_RESULT){
            samplePackListAdapter.refresh();
        }
    }
    @Override
    public boolean onKeyDown(int keycode, KeyEvent e) {
        switch (keycode) {
            case KeyEvent.KEYCODE_BACK:
                if (isSampleLibraryShowing){
                    hideSampleLibrary();
                    return true;
                }
                if (isEditMode){
                    gotoPlayMode();
                    return true;
                }
            default:
                setResult(Activity.RESULT_CANCELED);
                return super.onKeyDown(keycode, e);
        }
    }
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key){
            case ALWAYS_SHOW_SAMPLE_NAME_OVERLAY:
                if (activityPrefs.getBoolean(ALWAYS_SHOW_SAMPLE_NAME_OVERLAY, false)) {
                    rootLayout.findViewById(R.id.edit_mode_overlay).setVisibility(View.VISIBLE);
                    rootLayout.findViewById(R.id.edit_mode_overlay).setAlpha(1f);
                }
                else
                    rootLayout.findViewById(R.id.edit_mode_overlay).setVisibility(View.GONE);
                break;
            case PREF_BPM:
                bpm = activityPrefs.getInt(PREF_BPM, 120);
                if (getSupportActionBar() != null)
                    ((TextView)getSupportActionBar().getCustomView().findViewById(R.id.bpm_view)).setText(getString(R.string.bpm_display,
                            bpm));

                break;
            case PREF_TIME_SIGNATURE:
                timeSignature = Integer.valueOf(activityPrefs.getString(PREF_TIME_SIGNATURE, "4"));
                if (getSupportActionBar() != null)
                    ((TextView)getSupportActionBar().getCustomView().findViewById(R.id.time_sig_view)).setText(getString(R.string.time_sig_display,
                            timeSignature));
                break;
        }
    }

    // Initialization methods
    private BroadcastReceiver br;
    private void loadInBackground(){
        // Prepare shared preferences
        launchPadprefs = getPreferences(MODE_PRIVATE);
        PreferenceManager.setDefaultValues(this, R.xml.sample_edit_preferences, true);
        activityPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        activityPrefs.registerOnSharedPreferenceChangeListener(this);

        // Instance of runtime to check memory use
        runtime = Runtime.getRuntime();

        // Initialize Facebook SDK
        //FacebookSdk.sdkInitialize(getApplicationContext());

        // Set up audio control
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);

        // Prepare stoarage directories
        if (Utils.isExternalStorageWritable()){
            sampleDirectory = new File(getExternalFilesDir(null), "Samples");
            if (!sampleDirectory.exists())
                if (!sampleDirectory.mkdir()) Log.e(LOG_TAG, "error creating external files directory");
            samplePackDirectory = new File(getExternalFilesDir(null), "Sample Packs");
        } else {
            sampleDirectory = new File(getFilesDir(), "Samples");
            if (!sampleDirectory.exists())
                if (!sampleDirectory.mkdir()) Log.e(LOG_TAG, "error creating internal files directory");
            samplePackDirectory = new File(getFilesDir(), "Sample Packs");
        }

        // Copy files over from directory used in previous app versions and delete the directories
        cleanUpStorageDirs();

        // Prepare sampleListAdapter and samplePackListAdapter
        sampleListAdapter = new SampleListAdapter(context, R.layout.sample_list_item);
        samplePackListAdapter = new SamplePackListAdapter(context, R.layout.sample_pack_list_item);
        new Thread(new Runnable() {
            @Override
            public void run() {
                samplePackListAdapter.refresh();
                File[] sampleFiles = sampleDirectory.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        return pathname.getName().endsWith(".wav");
                    }
                });
                sampleListAdapter.addAll(sampleFiles);
            }
        }).start();

        // Load UI
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                loadUi();
            }
        });
    }
    private void loadUi(){
        setContentView(R.layout.activity_launch_pad);

        rootLayout = (RelativeLayout)findViewById(R.id.launch_pad_root_layout);
        LinearLayout library = (LinearLayout)findViewById(R.id.sample_library);
        library.setOnDragListener(new LibraryDragEventListener());
        library.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                hideSampleLibrary();
                return true;
            }
        });

        // Setup sample listview
        ListView sampleListView = (ListView)library.findViewById(R.id.sample_listview);
        sampleListView.setAdapter(sampleListAdapter);
        sampleListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                sampleLibraryIndex = position;
            }
        });
        sampleListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                // Goto edit mode
                if (!isEditMode)
                    gotoEditMode();

                // Start drag
                ClipData.Item pathItem = new ClipData.Item(sampleListAdapter.getItem(position).getAbsolutePath());
                String[] mime_type = {ClipDescription.MIMETYPE_TEXT_PLAIN};
                ClipData dragData = new ClipData(SAMPLE_PATH, mime_type, pathItem);
                Random random = new Random();
                int index = random.nextInt(4);
                Drawable drawable = AppCompatResources.getDrawable(context, padColorDrawables[index]);
                dragData.addItem(new ClipData.Item(String.valueOf(index)));
                View.DragShadowBuilder myShadow = new SampleDragShadowBuilder(view, drawable);
                view.startDrag(dragData,  // the data to be dragged
                        myShadow,  // the drag shadow builder
                        null,      // no need to use local data
                        0          // flags (not currently used, set to 0)
                );
                return true;
            }
        });


        // Setup sample pack listviews
        ListView samplePackListView = (ListView)library.findViewById(R.id.sample_pack_listview);
        samplePackListView.setAdapter(samplePackListAdapter);

        // Handle background touch for toolbar
        rootLayout.findViewById(R.id.pad_toolbar).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return true;
            }
        });

        if (activityPrefs.getBoolean(ALWAYS_SHOW_SAMPLE_NAME_OVERLAY, false))
            rootLayout.findViewById(R.id.edit_mode_overlay).setVisibility(View.VISIBLE);

        // Setup App bar
        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        myToolbar.setOverflowIcon(AppCompatResources.getDrawable(this, R.drawable.ic_action_navigation_more_vert));
        setSupportActionBar(myToolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null){
            actionBar.setDisplayShowHomeEnabled(false);
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setCustomView(R.layout.counter_bar);
            actionBar.setDisplayShowCustomEnabled(true);
            counterTextView = (TextView)actionBar.getCustomView().findViewById(R.id.counter_view);
            bpm = activityPrefs.getInt(LaunchPadPreferencesFragment.PREF_BPM, 120);
            ((TextView)actionBar.getCustomView().findViewById(R.id.bpm_view)).setText(getString(R.string.bpm_display, bpm));
            timeSignature = Integer.parseInt(activityPrefs.getString(LaunchPadPreferencesFragment.PREF_TIME_SIG, "4"));
            updateCounterMessage();
            actionBar.setDisplayShowCustomEnabled(true);
        }

        // Initialize Ads
        initializeAds();

        // Check for network connectivity
        checkInternetConnection();

        if (savedData != null){
            samples = savedData.getSamples();
            counter = savedData.getCounter();
            double sec = (double)counter / 1000;
            int min = (int)Math.floor(sec / 60);
            counterTextView.setText(String.format(Locale.US, "%2d : %.2f", min, sec % 60));
            isEditMode = savedData.isEditMode();
            activePads = savedData.getActivePads();
            isRecording = savedData.isRecording();
            isPlaying = savedData.isPlaying();
            recordingEndTime = savedData.getRecordingEndTime();
            if (isPlaying)
                disconnectTouchListeners();
            launchEvents = savedData.getLaunchEvents();
            playEventIndex = savedData.getPlayEventIndex();
            sampleListAdapter.sampleFiles = savedData.sampleFiles;
            sampleListAdapter.sampleLengths = savedData.sampleLengths;
            launchQueue = savedData.getLaunchQueue();
            // Setup touch pads from retained fragment
            setupPadsFromFrag();
            updatePadOverlay();
        }
        else{
            FragmentManager fm = getFragmentManager();
            savedData = new LaunchPadData();
            fm.beginTransaction().add(savedData, "data").commit();
            // Setup touch pads from files
            new Thread(new Runnable() {
                @Override
                public void run() {
                    setupPadsFromFile();
                    // Prepare Sample Library list adapter
                    File[] sampleFiles = sampleDirectory.listFiles(new FileFilter() {
                        @Override
                        public boolean accept(File pathname) {
                            return pathname.getName().endsWith(".wav");
                        }
                    });
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updatePadOverlay();
                        }
                    });
                    //sampleListAdapter.addAll(sampleNames);
                }
            }).start();
        }
    }
    private void cleanUpStorageDirs(){
        File oldHomeDirectory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath() + "/Mixmatic");
        if (oldHomeDirectory.exists() && oldHomeDirectory.isDirectory()) {
            File oldSampleDirectory = new File(oldHomeDirectory.getAbsolutePath() + "/Sample_Data");
            if (oldSampleDirectory.exists() && oldSampleDirectory.isDirectory()){
                File[] files = oldSampleDirectory.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File file) {
                        return file.getPath().endsWith("wav");
                    }
                });
                int padInt = 1;
                SharedPreferences.Editor editor = launchPadprefs.edit();
                for (File file : files){
                    String fileName = file.getName();
                    if (fileName.contains("Mixmatic_Touch_Pad_")) {
                        try {
                            String padNumber = String.valueOf(padInt++);
                            File newSampleFile = new File(sampleDirectory, "Sample" + padNumber + ".wav");
                            Utils.CopyFile(file, newSampleFile);
                            fileName = fileName.replace("Mixmatic_Touch_Pad_", "");
                            fileName = fileName.replace(".wav", "");
                            editor.putString(padNumber + SAMPLE_PATH, newSampleFile.getAbsolutePath());
                            editor.putInt(padNumber + LAUNCHMODE, launchPadprefs.getInt(fileName + LAUNCHMODE, Sample.LAUNCHMODE_GATE));
                            editor.remove(fileName + LAUNCHMODE);
                            editor.putBoolean(padNumber + LOOPMODE, launchPadprefs.getBoolean(fileName + LOOPMODE, false));
                            editor.remove(fileName + LOOPMODE);
                            editor.putFloat(padNumber + SAMPLE_VOLUME, launchPadprefs.getFloat(fileName + SAMPLE_VOLUME, 0.5f * AudioTrack.getMaxVolume()));
                            editor.remove(fileName + SAMPLE_VOLUME);
                            editor.putInt(padNumber + COLOR, launchPadprefs.getInt(fileName + COLOR, 0));
                            editor.remove(fileName + COLOR);
                            file.delete();
                        } catch (IOException e) {e.printStackTrace(); }
                    } else {
                        try {
                            Utils.CopyFile(file, new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), file.getName()));
                            file.delete();
                        } catch (IOException e) {e.printStackTrace();}
                    }
                }
                editor.apply();
                oldSampleDirectory.delete();
            }
            // Move or delete files in oldHomeDirectory
            File[] miscFiles = oldHomeDirectory.listFiles();
            for (File file : miscFiles){
                if (file.getName().endsWith(".wav")) {
                    try {
                        Utils.CopyFile(file, new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), file.getName()));
                        file.delete();
                    } catch (IOException e) {e.printStackTrace(); }
                } else {
                    file.delete();
                }
            }
            oldHomeDirectory.delete();
        }
    }
    private void setupPadsFromFile() {
        samples = new SparseArray<Sample>(24);
        activePads = new ArrayList<Integer>(24);
        int padInt = 1;
        for (int id : touchPadIds){
            final TouchPad pad = (TouchPad)findViewById(id);
            pad.setTag(String.valueOf(padInt++));
            pad.setOnTouchListener(TouchPadTouchListener);
            pad.setOnDragListener(new PadDragEventListener());
            String path = launchPadprefs.getString(pad.getTag().toString() + SAMPLE_PATH, null);
            if (path != null) {
                File sampleFile = new File(path);
                if (sampleFile.isFile()) {
                    // Configure pad settings
                    pad.setOnTouchListener(TouchPadTouchListener);
                    Sample s = new Sample(path, id);
                    if (s.isReady()) {
                        s.setOnPlayFinishedListener(samplePlayListener);
                        samples.put(id, s);
                        String padNumber = (String) pad.getTag();
                        s.setLoopMode(launchPadprefs.getBoolean(padNumber + LOOPMODE, false));
                        s.setLaunchMode(launchPadprefs.getInt(padNumber + LAUNCHMODE, Sample.LAUNCHMODE_TRIGGER));
                        s.setVolume(launchPadprefs.getFloat(padNumber + SAMPLE_VOLUME, 0.5f * AudioTrack.getMaxVolume()));
                        s.setQuantizationMode(launchPadprefs.getInt(padNumber + QUANTIZE_MODE, Sample.Q_NONE));
                        final int color = launchPadprefs.getInt(padNumber + COLOR, 0);
                        activePads.add(pad.getId());
                        // Animate the loading of the pad
                        rootLayout.post(new Runnable() {
                            @Override
                            public void run() {
                                setPadColor(color, pad);
                                // Start the animation
                                AnimatorSet set = new AnimatorSet();
                                ObjectAnimator popIn = ObjectAnimator.ofFloat(pad, "ScaleX", 0f, 1f);
                                set.setInterpolator(new AnticipateOvershootInterpolator());
                                set.play(popIn);
                                set.setTarget(pad);
                                set.start();
                            }
                        });
                    } else {
                        notifySampleLoadError(s);
                    }
                }
            } else {
                pad.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        emptyPadLongClick(v);
                        return true;
                    }
                });
            }
        }
    }
    private void setupPadsFromFrag(){
        if (samples == null)
            // Setup touch pads from files
            new Thread(new Runnable() {
                @Override
                public void run() {
                    setupPadsFromFile();
                }
            }).start();
        else {
            int padIndex = 1;
            for (int id : touchPadIds) {
                Sample sample;
                String padNumber = String.valueOf(padIndex++);
                TouchPad pad = (TouchPad) findViewById(id);
                pad.setTag(padNumber);
                pad.setOnTouchListener(TouchPadTouchListener);
                pad.setOnDragListener(new PadDragEventListener());
                if (samples.indexOfKey(pad.getId()) >= 0) {
                    setPadColor(launchPadprefs.getInt(padNumber + COLOR, 0), pad);
                    sample = samples.get(pad.getId());
                    sample.setOnPlayFinishedListener(samplePlayListener);
                    sample.setLoopMode(sample.getLoopMode());
                    pad.setPressed(sample.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
                }
                else {
                    pad.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            emptyPadLongClick(v);
                            return true;
                        }
                    });
                }
            }
        }
    }
    private void checkInternetConnection() {
        if (activityPrefs.getBoolean(SHOW_ADS, true) && br == null) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            //should check null because in airplane mode it will be null
            if (netInfo == null || !netInfo.isConnected()) disableApp();

            br = new BroadcastReceiver() {

                @Override
                public void onReceive(Context context, Intent intent) {

                    Bundle extras = intent.getExtras();

                    NetworkInfo info = (NetworkInfo) extras
                            .getParcelable("networkInfo");

                    if (info != null) {
                        NetworkInfo.State state = info.getState();

                        if (state == NetworkInfo.State.CONNECTED) {
                            enableApp();
                            initializeIAB();
                            //Toast.makeText(getApplicationContext(), "Internet connection is on", Toast.LENGTH_LONG).show();

                        } else if(activityPrefs.getBoolean(SHOW_ADS, true)) {
                            disableApp();
                            //Toast.makeText(getApplicationContext(), "Internet connection is Off", Toast.LENGTH_LONG).show();
                        }
                    }

                }
            };

            final IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            registerReceiver((BroadcastReceiver) br, intentFilter);
        }
    }
    private void unregisterNetworkListener(){
        if (br != null)
            unregisterReceiver(br);
    }
    private void disableApp(){
        if (rootLayout.findViewById(R.id.no_network_overlay) == null) {
            LayoutInflater inflater = getLayoutInflater();
            View overlay = inflater.inflate(R.layout.no_network_overlay, null);
            overlay.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    return true;
                }
            });
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            overlay.setLayoutParams(params);
            rootLayout.addView(overlay);
            if (isPlaying || isRecording) stopPlayBack();
        }
    }
    private void enableApp(){
        View overlay = rootLayout.findViewById(R.id.no_network_overlay);
        if (overlay != null)
            rootLayout.removeView(overlay);
    }

    // Touch pad helper methods
    private void preparePad(String path, int id, int color) {
        File f = new File(path); // File to contain the new sample
        // Create a new file to contain the new sample
        File sampleFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(id) + ".wav");
        // If the file already exists, delete, but remember to keep its configuration
        boolean keepSettings = false;
        if (sampleFile.isFile()) {
            sampleFile.delete();
            keepSettings = true;
        }
        // Copy new sample over
        try {
            CopyFile(f, sampleFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (sampleFile.isFile()) { // If successful, prepare touchpad
            Sample sample = new Sample(sampleFile.getAbsolutePath(), id);
            sample.setOnPlayFinishedListener(samplePlayListener);
            TouchPad t = (TouchPad) findViewById(id);
            String padNumber = (String)t.getTag();
            // Set sample properties and save to shared preferences
            SharedPreferences.Editor editor = launchPadprefs.edit();
            if (keepSettings) {
                sample.setLaunchMode(launchPadprefs.getInt(padNumber + LAUNCHMODE, Sample.LAUNCHMODE_GATE));
                sample.setLoopMode(launchPadprefs.getBoolean(padNumber + LOOPMODE, false));
                sample.setVolume(launchPadprefs.getFloat(padNumber + SAMPLE_VOLUME, 0.5f * AudioTrack.getMaxVolume()));
            } else { // Default properties
                sample.setLaunchMode(Sample.LAUNCHMODE_GATE);
                editor.putInt(padNumber + LAUNCHMODE, Sample.LAUNCHMODE_GATE);
                editor.putBoolean(padNumber + LOOPMODE, false);
                editor.putFloat(padNumber + SAMPLE_VOLUME, 0.5f * AudioTrack.getMaxVolume());
            }
            samples.put(id, sample);
            activePads.add(id);
            t.setBackgroundResource(padColorDrawables[color]);
            editor.putInt(padNumber + COLOR, color);
            editor.apply();
        }
    }
    private void loadSample(Sample s, TouchPad pad){
        int id = pad.getId();
        pad.setOnTouchListener(TouchPadTouchListener);
        s.setOnPlayFinishedListener(samplePlayListener);
        samples.put(id, s);
        String padNumber = (String)pad.getTag();
        s.setLoopMode(launchPadprefs.getBoolean(padNumber + LOOPMODE, activityPrefs.getBoolean(PREF_LOOP_MODE, false)));
        s.setLaunchMode(launchPadprefs.getInt(padNumber + LAUNCHMODE, Integer.valueOf(activityPrefs.getString(PREF_LAUNCH_MODE, "0"))));
        s.setVolume(launchPadprefs.getFloat(padNumber + SAMPLE_VOLUME, 0.5f * AudioTrack.getMaxVolume()));
        s.setQuantizationMode(launchPadprefs.getInt(padNumber + QUANTIZE_MODE, Integer.valueOf(activityPrefs.getString(PREF_QUANTIZATION, "0"))));
        pad.setBackgroundResource(padColorDrawables[launchPadprefs.getInt(padNumber + COLOR, 0)]);
    }
    private void setPadColor(int color, TouchPad pad){
        switch (color){ // Load and set color
            case 0:
                pad.setBackgroundResource(R.drawable.launch_pad_blue);
                break;
            case 1:
                pad.setBackgroundResource(R.drawable.launch_pad_red);
                break;
            case 2:
                pad.setBackgroundResource(R.drawable.launch_pad_green);
                break;
            case 3:
                pad.setBackgroundResource(R.drawable.launch_pad_orange);
                break;
        }
    }
    private void removeSample(int sampleId){
        Sample s = samples.get(sampleId);
        // Remove the sample from the list of active samples
        samples.remove(sampleId);
        activePads.remove((Integer)sampleId);
        // Set the launchpad background to empty
        View pad = findViewById(sampleId);
        pad.setBackgroundResource(R.drawable.launch_pad_empty);
        String padNumber = (String)pad.getTag();
        // Remove settings from shared preferences
        SharedPreferences.Editor editor = launchPadprefs.edit();
        editor.remove(padNumber + SAMPLE_PATH);
        editor.remove(padNumber + SAMPLE_VOLUME);
        editor.remove(padNumber + LAUNCHMODE);
        editor.remove(padNumber + LOOPMODE);
        editor.remove(padNumber + COLOR);
        editor.apply();
        updatePadOverlay();
    }
    private void removeAllSamples(){
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.MyAlertDialogStyle);
        builder.setMessage(R.string.clear_pads_prompt);
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ArrayList<Integer> temp = (ArrayList<Integer>)activePads.clone();
                for (int i : temp)
                    removeSample(i);
                launchEvents = new ArrayList<>(20);
                launchQueue = new ArrayList<>(5);
            }
        });
        builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    private void notifySampleLoadError(Sample s){
        String error = getString(R.string.error_sample_load) + " " + new File(s.getPath()).getName();
        if (s.getState() == Sample.ERROR_OUT_OF_MEMORY)
            error = error + "\n" + getString(R.string.error_out_of_memory);
        else if (s.getState() == Sample.ERROR_FILE_IO)
            error = error + "\n" + getString(R.string.error_file_read);
        final String message = error;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            }
        });
    }
    private void recordAudio(File recordingFile){
        int bufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        isMicRecording = true;
        short[] audioShorts = new short[bufferSize / 2];
        WaveFile waveFile = new WaveFile();
        waveFile.OpenForWrite(recordingFile.getAbsolutePath(), 44100, (short) 16, (short) 2);
        recorder.startRecording();
        while (isMicRecording){
            recorder.read(audioShorts, 0, audioShorts.length);
            waveFile.WriteData(audioShorts, audioShorts.length);
        }
        waveFile.Close();
    }

    /** Edit Mode **/
    public static final String ALWAYS_SHOW_SAMPLE_NAME_OVERLAY = "always_show_sample_name_overlay";
    public static final String PREF_BPM = "pref_bpm";
    public static final String PREF_TIME_SIGNATURE = "pref_time_signature";
    public static final String PREF_LAUNCH_MODE = LaunchPadPreferencesFragment.PREF_LAUNCH_MODE;
    public static final String PREF_LOOP_MODE = LaunchPadPreferencesFragment.PREF_LOOP_MODE;
    public static final String PREF_QUANTIZATION = LaunchPadPreferencesFragment.PREF_QUANTIZATION;
    private int[] padTextViewIds = {R.id.touchPad1_textview, R.id.touchPad2_textview, R.id.touchPad3_textview, R.id.touchPad4_textview,
            R.id.touchPad5_textview, R.id.touchPad6_textview, R.id.touchPad7_textview, R.id.touchPad8_textview, R.id.touchPad9_textview,
            R.id.touchPad10_textview, R.id.touchPad11_textview, R.id.touchPad12_textview, R.id.touchPad13_textview, R.id.touchPad14_textview,
            R.id.touchPad15_textview, R.id.touchPad16_textview, R.id.touchPad17_textview, R.id.touchPad18_textview, R.id.touchPad19_textview,
            R.id.touchPad20_textview, R.id.touchPad21_textview, R.id.touchPad22_textview, R.id.touchPad23_textview, R.id.touchPad24_textview};
    private int[] padColorDrawables = {R.drawable.launch_pad_blue, R.drawable.launch_pad_red, R.drawable.launch_pad_green, R.drawable.launch_pad_orange};
    private void gotoEditMode(){
        isEditMode = true;
        showToolBar();
        updatePadOverlay();
        for (int i = 0; i < touchPadIds.length; i++){
            View pad = findViewById(touchPadIds[i]);
            pad.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    touchPadEditLongClick(v);
                    return true;
                }
            });
        }
        stopPlayBack();
        if (activePads.size() > 0){
            findViewById(activePads.get(0)).callOnClick();
        }
    }
    private void updatePadOverlay(){
        TextView textView;
        String path;
        for (int i = 0; i < touchPadIds.length; i++){
            textView = (TextView)rootLayout.findViewById(padTextViewIds[i]);
            if (activePads.contains(touchPadIds[i])){
                path = samples.get(touchPadIds[i]).path;
                textView.setText(path.substring(path.lastIndexOf("/") + 1).replace(".wav", ""));
            } else {
                textView.setText("Empty");
            }
        }
    }
    public void DoneButtonClick(View v){
        gotoPlayMode();
    }
    private void gotoPlayMode(){
        for (int id : touchPadIds){
            TouchPad pad = (TouchPad)findViewById(id);
            pad.setSelected(false);
            if (activePads.contains(id))
                pad.setOnLongClickListener(null);
            else
                pad.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        emptyPadLongClick(v);
                        return true;
                    }
                });
        }
        hideToolBar();
        hideSampleLibrary();
        isEditMode = false;
    }
    public void touchPadClick(View v) {
        if (isEditMode) {
            // Deselect the current touchpad
            View oldView = findViewById(selectedSampleID);
            if (oldView != null)
                oldView.setSelected(false);
            // Select the new touchpad
            v.setSelected(true);
            selectedSampleID = v.getId();
            if (samples.indexOfKey(v.getId()) >= 0) {
                String padNumber = (String)findViewById(selectedSampleID).getTag();

                // Update toolbar
                updateVolumeDisplay();

                if (launchPadprefs.getInt(padNumber + LAUNCHMODE, Sample.LAUNCHMODE_TRIGGER) == Sample.LAUNCHMODE_GATE)
                    rootLayout.findViewById(R.id.action_launch_mode).setBackgroundResource(R.drawable.ic_keyboard);
                else
                    rootLayout.findViewById(R.id.action_launch_mode).setBackgroundResource(R.drawable.ic_album);

                if (launchPadprefs.getBoolean(padNumber + LOOPMODE, false))
                    rootLayout.findViewById(R.id.action_loop_mode).setBackgroundResource(R.drawable.ic_av_loop_selected);
                else
                    rootLayout.findViewById(R.id.action_loop_mode).setBackgroundResource(R.drawable.ic_av_loop);

                switch (launchPadprefs.getInt(padNumber + QUANTIZE_MODE, Sample.Q_NONE)){
                    case Sample.Q_BEAT:
                        rootLayout.findViewById(R.id.action_quantize).setBackgroundResource(R.drawable.ic_q_beat);
                        break;
                    case Sample.Q_HALF_BEAT:
                        rootLayout.findViewById(R.id.action_quantize).setBackgroundResource(R.drawable.ic_q_half_beat);
                        break;
                    case Sample.Q_BAR:
                        rootLayout.findViewById(R.id.action_quantize).setBackgroundResource(R.drawable.ic_q_bar);
                        break;
                    default:
                        rootLayout.findViewById(R.id.action_quantize).setBackgroundResource(R.drawable.ic_q_none);
                        break;
                }
            }
            else {
                hideVolumePopup();
                showSampleLibrary();
            }
        }
    }
    public void emptyPadLongClick(View v){
        // Deselect the current touchpad
        View oldView = findViewById(selectedSampleID);
        if (oldView != null)
            oldView.setSelected(false);
        selectedSampleID = v.getId();
        v.setSelected(true);
        gotoEditMode();
        showSampleLibrary();
    }
    public void touchPadEditLongClick(View v){
        if (activePads.contains(v.getId())) {
            // Deselect the previous touchpad
            View oldView = findViewById(selectedSampleID);
            if (oldView != null)
                oldView.setSelected(false);
            selectedSampleID = v.getId();
            v.setSelected(true);
            ClipData.Item pathItem = new ClipData.Item(String.valueOf(v.getId()));
            String[] mime_type = {ClipDescription.MIMETYPE_TEXT_PLAIN};
            ClipData dragData = new ClipData(TOUCHPAD_ID, mime_type, pathItem);
            View.DragShadowBuilder myShadow = new TouchPadDragShadowBuilder(v);
            v.startDrag(dragData,  // the data to be dragged
                    myShadow,  // the drag shadow builder
                    null,      // no need to use local data
                    0          // flags (not currently used, set to 0)
            );
            v.setSelected(true);
        }
    }
    private void mySamplesListItemEditClick(int index){
            Intent intent = new Intent(Intent.ACTION_SEND, null, context, SampleEditActivity.class);
                intent.putExtra(SAMPLE_PATH, sampleListAdapter.getItem(index).getAbsolutePath());
            startActivityForResult(intent, GET_SAMPLE);
    }
    private void samplePackListItemEditClick(String path){
        Intent intent = new Intent(Intent.ACTION_SEND, null, context, SampleEditActivity.class);
        intent.putExtra(SAMPLE_PATH, path);
        startActivityForResult(intent, GET_SAMPLE);
    }
    protected class PadDragEventListener implements View.OnDragListener {
        public boolean onDrag(View v, DragEvent event) {
            switch (event.getAction()){
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    return true;
                case DragEvent.ACTION_DRAG_LOCATION:
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    return true;
                case DragEvent.ACTION_DROP:
                    ClipData data = event.getClipData();
                    if (data.getDescription().getLabel().equals(TOUCHPAD_ID)) {
                        int pad1Id = Integer.parseInt((String) data.getItemAt(0).getText());
                        swapPads(pad1Id, v.getId());
                        resetRecording();
                    } else if (data.getDescription().getLabel().equals(SAMPLE_PATH)){
                        loadPadFromDrop(data.getItemAt(0).getText().toString(), v.getId(), Integer.parseInt(data.getItemAt(1).getText().toString()));
                    }
                    return true;
                default:
                    return false;
            }
        }
    }
    private void swapPads(int pad1Id, int pad2Id){
        Sample sample1 = samples.get(pad1Id);
        TouchPad pad1 = (TouchPad)findViewById(pad1Id);
        String pad1Number = (String)pad1.getTag();
        int pad1Color = launchPadprefs.getInt((String)pad1.getTag() + COLOR, 0);
        TouchPad pad2 = (TouchPad) findViewById(pad2Id);
        String pad2Number = (String)pad2.getTag();

        // Move pad1 to pad2
        Sample sample2 = null;
        if (activePads.contains((Integer)pad2Id)) {
            sample2 = samples.get(pad2Id);
        }
        else activePads.add(pad2Id);
        sample1.id = pad2Id;
        samples.remove(pad1Id);
        samples.put(pad2Id, sample1);
        SharedPreferences.Editor editor = launchPadprefs.edit();
        editor.putString(pad2Number + SAMPLE_PATH, sample1.getPath());
        editor.putInt(pad2Number + LAUNCHMODE, sample1.getLaunchMode());
        editor.putBoolean(pad2Number + LOOPMODE, sample1.getLoopMode());
        editor.putFloat(pad2Number + SAMPLE_VOLUME, sample1.getVolume());
        editor.putInt(pad2Number + COLOR, pad1Color);
        setPadColor(pad1Color, pad2);
        sample1.release();
        sample1.reloadAudioTrack();

        if (sample2 != null) { // Move pad2 to pad1
            int pad2Color = launchPadprefs.getInt(pad2Number + COLOR, 0);
            sample2.id = pad1Id;
            samples.put(pad1Id, sample2);
            editor.putString(pad1Number + SAMPLE_PATH, sample2.getPath());
            editor.putInt(pad1Number + LAUNCHMODE, sample2.getLaunchMode());
            editor.putBoolean(pad1Number + LOOPMODE, sample2.getLoopMode());
            editor.putFloat(pad1Number + SAMPLE_VOLUME, sample2.getVolume());
            editor.putInt(pad1Number + COLOR, pad2Color);
            setPadColor(pad2Color, pad1);
            sample2.release();
            sample2.reloadAudioTrack();
        } else { // Remove pad1
            activePads.remove((Integer)pad1Id);
            pad1.setBackgroundResource(R.drawable.launch_pad_empty);
            pad1.setOnLongClickListener(null);
            editor.remove(pad1Number + SAMPLE_PATH);
            editor.remove(pad1Number + LAUNCHMODE);
            editor.remove(pad1Number + LOOPMODE);
            editor.remove(pad1Number + SAMPLE_VOLUME);
            editor.remove(pad1Number + COLOR);
        }
        editor.apply();
        updatePadOverlay();
        pad2.callOnClick();
    }
    private void loadPadFromDrop(String path, int padId, int color){
        TouchPad pad = (TouchPad)findViewById(padId);
        SharedPreferences.Editor editor = launchPadprefs.edit();
        if (activePads.contains((Integer)padId)) {
            samples.get(padId).release();
            samples.remove(padId);
        }
        Sample sample = new Sample(path, padId);
        if (sample.isReady()) {
            activePads.add(padId);
            editor.putInt(pad.getTag().toString() + COLOR, color);
            editor.putString(pad.getTag().toString() + SAMPLE_PATH, path);
            int launchMode = Integer.valueOf(activityPrefs.getString(PREF_LAUNCH_MODE, String.valueOf(Sample.LAUNCHMODE_TRIGGER)));
            editor.putInt(pad.getTag().toString() + LAUNCHMODE, launchMode);
            editor.putBoolean(pad.getTag().toString() + LOOPMODE, activityPrefs.getBoolean(PREF_LOOP_MODE, false));
            if (launchMode == Sample.LAUNCHMODE_TRIGGER)
                editor.putInt(pad.getTag().toString() + QUANTIZE_MODE, Integer.valueOf(activityPrefs.getString(PREF_QUANTIZATION, String.valueOf(Sample.Q_NONE))));
            else
                editor.putInt(pad.getTag().toString() + QUANTIZE_MODE, Sample.Q_NONE);
            editor.commit();
            loadSample(sample, pad);
            pad.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    touchPadEditLongClick(v);
                    return true;
                }
            });
            pad.callOnClick();
            setBpm(path);
        } else {
            notifySampleLoadError(sample);
        }
        updatePadOverlay();
    }
    private void setBpm(String path){
        String filename = path.substring(path.lastIndexOf(File.pathSeparatorChar) + 1).toLowerCase();
        String bpmString = "";
        int bpmIndex = filename.indexOf("bpm");
        for (int i = bpmIndex - 5; i < bpmIndex; i++){
            if (Character.isDigit(filename.charAt(i)))
                bpmString += filename.charAt(i);
        }
        Log.d(LOG_TAG, "bpm from filename: " + bpmString);

        if (!bpmString.equals("")){
            int newBpm = Integer.valueOf(bpmString);
            if (activePads.size() == 1) {
                bpm = newBpm;
                SharedPreferences.Editor editor = activityPrefs.edit();
                editor.putInt(LaunchPadPreferencesFragment.PREF_BPM, bpm);
                editor.apply();
                ActionBar actionBar = getSupportActionBar();
                if (actionBar != null){
                    ((TextView)actionBar.getCustomView().findViewById(R.id.bpm_view)).setText(getString(R.string.bpm_display, bpm));
                }
            } else if (newBpm != bpm) {
                Toast.makeText(context, "This samples bpm doesn't match", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Tool bar
    private void showToolBar(){
        View toolBar = rootLayout.findViewById(R.id.pad_toolbar);
        View doneButton = rootLayout.findViewById(R.id.done_button);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)doneButton.getLayoutParams();
        // Start the animation
        AnimatorSet set = new AnimatorSet();
        ObjectAnimator slideLeft = ObjectAnimator.ofFloat(toolBar, "TranslationX", -toolBar.getWidth(), 0);
        slideLeft.setTarget(toolBar);
        ObjectAnimator slideUp = ObjectAnimator.ofFloat(doneButton, "TranslationY", doneButton.getHeight() + params.bottomMargin, 0);
        slideUp.setTarget(doneButton);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        if (activityPrefs.getBoolean(ALWAYS_SHOW_SAMPLE_NAME_OVERLAY, false)){
            set.playTogether(slideLeft, slideUp);
        } else {
            View overlay = rootLayout.findViewById(R.id.edit_mode_overlay);
            overlay.setVisibility(View.VISIBLE);
            AnimatorSet fadeIn = Animations.fadeIn(overlay, 200, 0);
            set.playTogether(slideLeft, slideUp, fadeIn);
        }
        set.start();
    }
    private void hideToolBar(){
        View toolBar = rootLayout.findViewById(R.id.pad_toolbar);
        View doneButton = rootLayout.findViewById(R.id.done_button);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)doneButton.getLayoutParams();
        // Start the animation
        rootLayout.findViewById(R.id.volume_popup).setVisibility(View.GONE);
        rootLayout.findViewById(R.id.color_popup).setVisibility(View.GONE);
        AnimatorSet set = new AnimatorSet();
        ObjectAnimator slideLeft = ObjectAnimator.ofFloat(toolBar, "TranslationX", 0, -toolBar.getWidth());
        slideLeft.setTarget(toolBar);
        ObjectAnimator slideUp = ObjectAnimator.ofFloat(doneButton, "TranslationY", 0, doneButton.getHeight() + params.bottomMargin);
        slideUp.setTarget(doneButton);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        if (activityPrefs.getBoolean(ALWAYS_SHOW_SAMPLE_NAME_OVERLAY, false)){
            set.playTogether(slideLeft, slideUp);
        } else {
            View overlay = rootLayout.findViewById(R.id.edit_mode_overlay);
            overlay.setVisibility(View.GONE);
            AnimatorSet fadeOut = Animations.fadeOut(overlay, 200, 0);
            set.playTogether(slideLeft, slideUp, fadeOut);
        }
        // Show add after animation
        set.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {

            }

            @Override
            public void onAnimationEnd(Animator animator) {
                showInterstitialAd();
            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });
        set.start();
    }
    public void toolBarClick(View v){
        SharedPreferences.Editor prefEditor = launchPadprefs.edit();
        String padNumber = (String)findViewById(selectedSampleID).getTag();
        switch (v.getId()){
            case R.id.action_edit_sample:
                editSample();
                return;
            case R.id.action_loop_mode:
                if (samples.indexOfKey(selectedSampleID) >= 0 && launchPadprefs.getBoolean(padNumber + LOOPMODE, false)) {
                    rootLayout.findViewById(R.id.action_loop_mode).setBackgroundResource(R.drawable.ic_av_loop);
                    Sample s = samples.get(selectedSampleID);
                    s.setLoopMode(false);
                    prefEditor.putBoolean(padNumber + LOOPMODE, false);
                    prefEditor.apply();
                }
                else if (samples.indexOfKey(selectedSampleID) >= 0) {
                    rootLayout.findViewById(R.id.action_loop_mode).setBackgroundResource(R.drawable.ic_av_loop_selected);
                    Sample s = samples.get(selectedSampleID);
                    s.setLoopMode(true);
                    prefEditor.putBoolean(padNumber + LOOPMODE, true);
                    prefEditor.apply();
                }
                return;
            case R.id.action_remove_sample:
                if (samples.indexOfKey(selectedSampleID) >= 0){
                    removeSample(selectedSampleID);
                }
                return;
            case R.id.action_launch_mode:
                if (samples.indexOfKey(selectedSampleID) >= 0 && launchPadprefs.getInt(padNumber + LAUNCHMODE, Sample.LAUNCHMODE_TRIGGER) == Sample.LAUNCHMODE_TRIGGER){
                    rootLayout.findViewById(R.id.action_launch_mode).setBackgroundResource(R.drawable.ic_keyboard);
                    rootLayout.findViewById(R.id.action_quantize).setBackgroundResource(R.drawable.ic_q_none);
                    Sample s = samples.get(selectedSampleID);
                    s.setLaunchMode(Sample.LAUNCHMODE_GATE);
                    prefEditor.putInt(padNumber + LAUNCHMODE, Sample.LAUNCHMODE_GATE);
                    prefEditor.putInt(padNumber + QUANTIZE_MODE, Sample.Q_NONE);
                    prefEditor.apply();
                } else if (samples.indexOfKey(selectedSampleID) >= 0){
                    rootLayout.findViewById(R.id.action_launch_mode).setBackgroundResource(R.drawable.ic_album);
                    Sample s = samples.get(selectedSampleID);
                    s.setOnPlayFinishedListener(samplePlayListener);
                    s.setLaunchMode(Sample.LAUNCHMODE_TRIGGER);
                    prefEditor.putInt(padNumber + LAUNCHMODE, Sample.LAUNCHMODE_TRIGGER);
                    prefEditor.apply();
                }
                return;
            case R.id.action_pick_color:
                if (rootLayout.findViewById(R.id.color_popup).getVisibility() != View.VISIBLE && samples.indexOfKey(selectedSampleID) >= 0)
                    showColorPopup();
                else
                    hideColorPopup();
                return;
            case R.id.action_set_volume:
                if (rootLayout.findViewById(R.id.volume_popup).getVisibility() != View.VISIBLE && samples.indexOfKey(selectedSampleID) >= 0)
                    showVolumePopup();
                else
                    hideVolumePopup();
                return;
            case R.id.action_quantize:
                if (samples.indexOfKey(selectedSampleID) >= 0) {
                    if (samples.get(selectedSampleID).getLaunchMode() == Sample.LAUNCHMODE_GATE){
                        Toast.makeText(context, R.string.gate_mode_quantize_toast, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    switch (launchPadprefs.getInt(padNumber + QUANTIZE_MODE, Sample.Q_NONE)) {
                        case Sample.Q_NONE:
                            samples.get(selectedSampleID).setQuantizationMode(Sample.Q_BEAT);
                            prefEditor.putInt(padNumber + QUANTIZE_MODE, Sample.Q_BEAT);
                            v.setBackgroundResource(R.drawable.ic_q_beat);
                            Log.d(LOG_TAG, "Beat");
                            break;
                        case Sample.Q_BEAT:
                            samples.get(selectedSampleID).setQuantizationMode(Sample.Q_HALF_BEAT);
                            prefEditor.putInt(padNumber + QUANTIZE_MODE, Sample.Q_HALF_BEAT);
                            v.setBackgroundResource(R.drawable.ic_q_half_beat);
                            Log.d(LOG_TAG, "Half beat");
                            break;
                        case Sample.Q_HALF_BEAT:
                            samples.get(selectedSampleID).setQuantizationMode(Sample.Q_BAR);
                            prefEditor.putInt(padNumber + QUANTIZE_MODE, Sample.Q_BAR);
                            v.setBackgroundResource(R.drawable.ic_q_bar);
                            Log.d(LOG_TAG, "Bar");
                            break;
                        case Sample.Q_BAR:
                            samples.get(selectedSampleID).setQuantizationMode(Sample.Q_NONE);
                            prefEditor.putInt(padNumber + QUANTIZE_MODE, Sample.Q_NONE);
                            v.setBackgroundResource(R.drawable.ic_q_none);
                            Log.d(LOG_TAG, "None");
                            break;
                    }
                    prefEditor.apply();
                    return;
                }
            default:
                return;
        }
    }
    private void showVolumePopup(){
        final View popup = rootLayout.findViewById(R.id.volume_popup);
        if (popup.getVisibility() != View.VISIBLE) {
            hideColorPopup();
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) popup.getLayoutParams();
            params.topMargin = rootLayout.findViewById(R.id.pad_toolbar).getTop() + rootLayout.findViewById(R.id.action_set_volume).getTop();
            popup.setLayoutParams(params);

            final TextView volumeView = (TextView) popup.findViewById(R.id.volume_view);
            volumeView.setText(String.valueOf((int)(samples.get(selectedSampleID).getVolume() * 100)));

            final SeekBar volumeBar = (SeekBar) popup.findViewById(R.id.volume_slider_view);
            volumeBar.setProgress((int)(samples.get(selectedSampleID).getVolume() * 100));
            volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                    volumeView.setText(String.valueOf(i));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    float volume = (float)seekBar.getProgress() / 100;
                    samples.get(selectedSampleID).setVolume(volume);
                    TouchPad pad = (TouchPad)findViewById(selectedSampleID);
                    String padNumber = (String)pad.getTag();
                    SharedPreferences.Editor editor = launchPadprefs.edit();
                    editor.putFloat(padNumber + SAMPLE_VOLUME, volume);
                    editor.apply();
                }
            });

            // Start the animation
            popup.setVisibility(View.VISIBLE);
            popup.setAlpha(0f);
            AnimatorSet set = Animations.fadeIn(popup, 200, 0);
            set.start();
        }
    }
    private void updateVolumeDisplay(){
        View popup = rootLayout.findViewById(R.id.volume_popup);
        if (popup.getVisibility() == View.VISIBLE) {
            TextView volumeView = (TextView) popup.findViewById(R.id.volume_view);
            volumeView.setText(String.valueOf((int) (samples.get(selectedSampleID).getVolume() * 100)));

            SeekBar volumeBar = (SeekBar) popup.findViewById(R.id.volume_slider_view);
            volumeBar.setProgress((int) (samples.get(selectedSampleID).getVolume() * 100));
        }
    }
    private void hideVolumePopup(){
        final View popup = rootLayout.findViewById(R.id.volume_popup);
        if (popup.getVisibility() != View.GONE) {
            // Start the animation
            AnimatorSet set = Animations.fadeOut(popup, 200, 0);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {

                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    popup.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationCancel(Animator animator) {

                }

                @Override
                public void onAnimationRepeat(Animator animator) {

                }
            });
            set.start();
        }
    }
    private void showColorPopup(){
        final View popup = rootLayout.findViewById(R.id.color_popup);
        if (popup.getVisibility() != View.VISIBLE) {
            hideVolumePopup();
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) popup.getLayoutParams();
            params.topMargin = rootLayout.findViewById(R.id.pad_toolbar).getTop() + rootLayout.findViewById(R.id.action_pick_color).getTop();
            popup.setLayoutParams(params);

            // Start the animation
            popup.setVisibility(View.VISIBLE);
            popup.setAlpha(0f);
            AnimatorSet set = Animations.fadeIn(popup, 200, 0);
            set.start();
        }
    }
    public void ColorClick(View v){
        TouchPad pad = (TouchPad)rootLayout.findViewById(selectedSampleID);
        String padNumber = (String)pad.getTag();
        SharedPreferences.Editor editor = launchPadprefs.edit();
        switch (v.getId()){
            case R.id.blue:
                pad.setBackgroundResource(R.drawable.launch_pad_blue);
                editor.putInt(padNumber + COLOR, 0);
                break;
            case R.id.red:
                pad.setBackgroundResource(R.drawable.launch_pad_red);
                editor.putInt(padNumber + COLOR, 1);
                break;
            case R.id.green:
                pad.setBackgroundResource(R.drawable.launch_pad_green);
                editor.putInt(padNumber + COLOR, 2);
                break;
            case R.id.orange:
                pad.setBackgroundResource(R.drawable.launch_pad_orange);
                editor.putInt(padNumber + COLOR, 3);
                break;
        }
        editor.apply();
        hideColorPopup();
    }
    private void hideColorPopup(){
        final View popup = rootLayout.findViewById(R.id.color_popup);
        if (popup.getVisibility() != View.GONE) {
            // Start the animation
            AnimatorSet set = Animations.fadeOut(popup, 200, 0);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {

                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    popup.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationCancel(Animator animator) {

                }

                @Override
                public void onAnimationRepeat(Animator animator) {

                }
            });
            set.start();
        }
    }

    // Sample Library Methods
    private boolean hasLibShown = false;
    private class SampleListAdapter extends BaseAdapter {
        public ArrayList<File> sampleFiles;
        public ArrayList<String> sampleLengths;
        private Context mContext;
        private int resource_id;
        private LayoutInflater mInflater;

        public SampleListAdapter(Context context, int resource_id) {
            this.mContext = context;
            this.resource_id = resource_id;
            mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            sampleFiles = new ArrayList<>();
            sampleLengths = new ArrayList<>();
        }

        public void add(File file){
            if (!sampleFiles.contains(file)) {
                sampleFiles.add(file);
                // Determine length of wav file
                float length = Utils.getWavLengthInSeconds(file, 44100);
                sampleLengths.add(String.valueOf((int) Math.floor(length / 60)) + ":" + String.format("%.2f", length % 60));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        notifyDataSetChanged();
                    }
                });
            }
        }
        public void addAll(File[] files){
            for (File f : files){
                add(f);
            }
        }
        public void remove(File file){
            if (sampleFiles.contains(file)) {
                sampleLengths.remove(sampleFiles.indexOf(file));
                sampleFiles.remove(file);
                notifyDataSetChanged();
            }
        }
        @Override
        public int getCount() {
            if (sampleFiles != null)
                return sampleFiles.size();
            else return 0;
        }

        @Override
        public File getItem(int position) {
            return sampleFiles.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, final ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(resource_id, null);
            }
            TextView nameText = (TextView)convertView.findViewById(R.id.sample_file_name);
            nameText.setText(sampleFiles.get(position).getName().replace(".wav", ""));
            TextView timeText = (TextView)convertView.findViewById(R.id.sample_length);
            timeText.setText(sampleLengths.get(position));
            Bitmap wavBitmap = Utils.decodedSampledBitmapFromFile(new File(sampleFiles.get(position).getAbsolutePath().replace(".wav", ".png")), 80, 80);
            ImageView wavIcon = (ImageView)convertView.findViewById(R.id.wave_icon);
            if (wavBitmap != null){
                wavIcon.setBackground(new BitmapDrawable(getResources(), wavBitmap));
            } else {
                wavIcon.setBackground(getResources().getDrawable(R.drawable.ic_sound_wave_icon_green));
            }
            ImageView pauseButton = (ImageView)convertView.findViewById(R.id.pause_icon);
            pauseButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    if (v.isSelected()){
                        v.setSelected(false);
                        if (samplePlayer != null)
                            samplePlayer.stop();
                    } else {
                        v.setSelected(true);
                        if (samplePlayer != null)
                            if (samplePlayer.isPlaying())
                                samplePlayer.stop();
                        samplePlayer = new MediaPlayer();
                        try {
                            samplePlayer.setDataSource(sampleFiles.get(position).getAbsolutePath());
                            samplePlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                @Override
                                public void onCompletion(MediaPlayer mp) {
                                    v.setSelected(false);
                                    samplePlayer.release();
                                    samplePlayer = null;
                                }
                            });
                            samplePlayer.prepare();
                            samplePlayer.start();
                            listItemPauseButton = v;
                        } catch (IOException e){e.printStackTrace();}
                    }
                }
            });
            ImageView editButton = (ImageView)convertView.findViewById(R.id.item_edit_icon);
            editButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (v.isActivated() || v.isSelected())
                        mySamplesListItemEditClick(position);
                }
            });
            return  convertView;
        }
    }
    private class SamplePackListAdapter extends BaseAdapter {
        private ArrayList<String> names;
        private ArrayList<String> titles;
        private ArrayList<String> genres;
        private ArrayList<Bitmap> images;
        private Context mContext;
        private int resource_id;
        private LayoutInflater mInflater;

        private SamplePackListAdapter(Context context, int resource_id) {
            this.mContext = context;
            this.resource_id = resource_id;
            mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            names = new ArrayList<>();
            titles = new ArrayList<>();
            genres = new ArrayList<>();
            images = new ArrayList<>();
        }

        private void refresh(){
            if (samplePackDirectory.exists()) {
                File[] packFolders = samplePackDirectory.listFiles();
                for (File folder : packFolders) {
                    if (folder.isDirectory() && !names.contains(folder.getName())) {
                        names.add(folder.getName());
                        try {
                            BufferedReader br = new BufferedReader(new FileReader(new File(folder, folder.getName() + ".txt")));
                            titles.add(br.readLine());
                            genres.add(br.readLine());
                            br.close();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    notifyDataSetChanged();
                                }
                            });
                        } catch (IOException e) {
                            //You'll need to add proper error handling here
                            e.printStackTrace();
                        }
                        File image = new File(new File(samplePackDirectory, folder.getName()), folder.getName() + ".png");
                        if (image.exists()) {
                            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                            images.add(BitmapFactory.decodeFile(image.getAbsolutePath(), bmOptions));
                        }
                    }
                }
            }
        }

        @Override
        public int getCount() {
            if (names != null)
                return names.size();
            else return 0;
        }

        @Override
        public String getItem(int position) {
            return names.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, final ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(resource_id, null);
            }

            TextView titleView = (TextView)convertView.findViewById(R.id.title_view);
            titleView.setText(titles.get(position));

            TextView genreView = (TextView)convertView.findViewById(R.id.genre_view);
            genreView.setText(genres.get(position));

            ImageView imageView = (ImageView)convertView.findViewById(R.id.image_view);
            imageView.setImageBitmap(images.get(position));

            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Prepare selected pack view
                    final View selectedView = rootLayout.findViewById(R.id.selected_pack_view);
                    selectedView.setVisibility(View.VISIBLE);
                    TextView titleView = (TextView) selectedView.findViewById(R.id.title_view);
                    titleView.setText(titles.get(position));

                    TextView genreView = (TextView) selectedView.findViewById(R.id.genre_view);
                    genreView.setText(genres.get(position));

                    ImageView imageView = (ImageView) selectedView.findViewById(R.id.image_view);
                    imageView.setImageBitmap(images.get(position));

                    selectedView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            SamplePacksClick(v);
                        }
                    });

                    // Animate transition

                    int dy = v.getTop();
                    ObjectAnimator slideUp = ObjectAnimator.ofFloat(selectedView, "TranslationY", dy, 0);
                    slideUp.setInterpolator(new AccelerateDecelerateInterpolator());
                    AnimatorSet set = new AnimatorSet();
                    set.play(slideUp);
                    set.setDuration(dy);
                    showSamplePackFiles(names.get(position), set);

                }
            });

            return  convertView;
        }
    }
    private class SamplePackFilesListAdapter extends BaseExpandableListAdapter {
        private LayoutInflater mInflater;
        private Context context;
        private int groupResourceId, childResourceId;
        private ArrayList<String> folderNames;
        private HashMap<String, ArrayList<String>> fileNames;
        private String samplePackName;
        private View currentlyPlayingButton, previousView;

        public SamplePackFilesListAdapter(Context context, int groupResourceId, int childResourceId){
            this.context = context;
            this.groupResourceId = groupResourceId;
            this.childResourceId = childResourceId;
            folderNames = new ArrayList<>();
            fileNames = new HashMap<>();
        }

        private void loadPack(String name){
            samplePackName = name;
            File sampleFolder = new File(samplePackDirectory, name);
            if (sampleFolder.exists() && sampleFolder.isDirectory()){
                for (File folder : sampleFolder.listFiles()){
                    if (folder.isDirectory()) {
                        folderNames.add(folder.getName());
                        ArrayList<String> names = new ArrayList<>(Arrays.asList(folder.list()));
                        fileNames.put(folder.getName(), names);
                    }
                }
                notifyDataSetChanged();
            }
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }

        @Override
        public String getChild(int groupPosition, int childPosititon) {
            String folderName = folderNames.get(groupPosition);
            String path = samplePackDirectory.getAbsolutePath() + "/" + samplePackName + "/" + folderName + "/" + fileNames.get(folderName).get(childPosititon);
            return path;
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            return fileNames.get(folderNames.get(groupPosition)).size();
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        @Override
        public View getChildView(final int groupPosition, final int childPosition,
                                 boolean isLastChild, View convertView, ViewGroup parent) {
            if (convertView == null) {
                mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = mInflater.inflate(childResourceId, null);
            }

            TextView filenameView = (TextView)convertView.findViewById(R.id.sample_file_name);
            filenameView.setText(fileNames.get(folderNames.get(groupPosition)).get(childPosition));
            ImageView pauseButton = (ImageView)convertView.findViewById(R.id.pause_icon);
            pauseButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    if (currentlyPlayingButton != null && !currentlyPlayingButton.equals(v)) currentlyPlayingButton.setSelected(false);
                    currentlyPlayingButton = v;
                    if (previousView != null) v.setActivated(false);
                    if (v.isSelected()){
                        v.setSelected(false);
                        if (samplePlayer != null) {
                            samplePlayer.stop();
                            samplePlayer.release();
                            samplePlayer = null;
                        }
                    } else {
                        v.setSelected(true);
                        if (samplePlayer != null) {
                            if (samplePlayer.isPlaying())
                                samplePlayer.stop();
                            samplePlayer.release();
                            samplePlayer = null;
                        }
                        samplePlayer = new MediaPlayer();
                        try {
                            samplePlayer.setDataSource(getChild(groupPosition, childPosition));
                            samplePlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                @Override
                                public void onCompletion(MediaPlayer mp) {
                                    currentlyPlayingButton.setSelected(false);
                                    samplePlayer.release();
                                    samplePlayer = null;
                                }
                            });
                            samplePlayer.prepare();
                            samplePlayer.start();
                            listItemPauseButton = v;
                        } catch (IOException e){e.printStackTrace();}
                    }
                }
            });

            ImageView editButton = (ImageView)convertView.findViewById(R.id.item_edit_icon);
            editButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (v.isActivated() || v.isSelected())
                        samplePackListItemEditClick(getChild(groupPosition, childPosition));
                }
            });

            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (previousView != null && !previousView.equals(view))
                        previousView.setActivated(false);
                    view.setActivated(true);
                    previousView = view;
                }
            });

            convertView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    // Goto edit mode
                    if (!isEditMode)
                        gotoEditMode();

                    // Start drag
                    ClipData.Item pathItem = new ClipData.Item(getChild(groupPosition, childPosition));
                    String[] mime_type = {ClipDescription.MIMETYPE_TEXT_PLAIN};
                    ClipData dragData = new ClipData(SAMPLE_PATH, mime_type, pathItem);
                    Drawable drawable = AppCompatResources.getDrawable(context, padColorDrawables[groupPosition % 4]);
                    dragData.addItem(new ClipData.Item(String.valueOf(groupPosition % 4)));
                    View.DragShadowBuilder myShadow = new SampleDragShadowBuilder(v, drawable);
                    v.startDrag(dragData,  // the data to be dragged
                            myShadow,  // the drag shadow builder
                            null,      // no need to use local data
                            0          // flags (not currently used, set to 0)
                    );
                    return true;
                }
            });

            return convertView;
        }

        @Override
        public int getGroupCount() {
            return folderNames.size();
        }

        @Override
        public String getGroup(int position) {
            return folderNames.get(position);
        }

        @Override
        public long getGroupId(int position) {
            return position;
        }

        @Override
        public View getGroupView(int position, boolean isExpanded, View convertView, final ViewGroup parent) {
            if (convertView == null) {
                mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = mInflater.inflate(groupResourceId, null);
            }

            TextView folderNameView = (TextView)convertView.findViewById(R.id.textView);
            folderNameView.setText(folderNames.get(position));

            return convertView;
        }
    }
    protected class LibraryDragEventListener implements View.OnDragListener {
        public boolean onDrag(View v, DragEvent event) {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    showSampleLibrary();
                    return true;
                case DragEvent.ACTION_DRAG_LOCATION:
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    hideSampleLibrary();
                    return true;
                case DragEvent.ACTION_DROP:
                    ClipData data = event.getClipData();
                    if (data.getDescription().getLabel().equals(TOUCHPAD_ID)) {
                        int padId = Integer.parseInt((String) data.getItemAt(0).getText());
                        removeSample(padId);
                        resetRecording();
                    }
                    return true;
                default:
                    return false;
            }
        }
    }
    public void SampleLibTabClick(View v){
        if (isSampleLibraryShowing) {
            hideSampleLibrary();
        }
        else if (!(isPlaying || isRecording)) showSampleLibrary();
        else {
            isRecording = false;
            boolean isTouched = false;
            for (int i : activePads){
                isTouched = isTouched || samples.get(i).audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING;
            }
            if (!isTouched) showSampleLibrary();
        }
    }
    private void showSampleLibrary(){
        if (!isSampleLibraryShowing) {
            if (!isEditMode) gotoEditMode();
            final LinearLayout library = (LinearLayout)findViewById(R.id.sample_library);
            ListView sampleListView = (ListView)library.findViewById(R.id.sample_listview);

            if (!hasLibShown && sampleListAdapter.getCount() < 1 && samplePackListAdapter.getCount() < 2) SamplePacksClick(null);
            hasLibShown = true;
            // Start the animation
            AnimatorSet set = new AnimatorSet();
            ObjectAnimator slideLeft = ObjectAnimator.ofFloat(library, "TranslationX", sampleListView.getWidth(), 0);
            set.setInterpolator(new AccelerateDecelerateInterpolator());
            set.play(slideLeft);
            set.setTarget(library);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    isSampleLibraryShowing = true;
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
            set.start();
        }
    }
    private void hideSampleLibrary(){
        if (isSampleLibraryShowing) {
            LinearLayout library = (LinearLayout) findViewById(R.id.sample_library);
            ListView sampleListView = (ListView) library.findViewById(R.id.sample_listview);
            sampleLibraryIndex = -1;

            // Start the animation
            AnimatorSet set = new AnimatorSet();
            ObjectAnimator slideRight = ObjectAnimator.ofFloat(library, "TranslationX", 0, sampleListView.getWidth());
            set.setInterpolator(new AccelerateDecelerateInterpolator());
            set.play(slideRight);
            set.setTarget(library);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    isSampleLibraryShowing = false;
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
            set.start();
        }
    }
    public void AddSampleClick(View v){
        Intent intent = new Intent(Intent.ACTION_SEND, null, this, SampleEditActivity.class);
        startActivityForResult(intent, GET_SAMPLE);
    }
    private void editSample(){
        Intent intent = new Intent(Intent.ACTION_SEND, null, this, SampleEditActivity.class);
        if (isEditMode) {
            intent.putExtra(TOUCHPAD_ID, selectedSampleID);
            if (samples.indexOfKey(selectedSampleID) >= 0) {
                intent.putExtra(SAMPLE_PATH, samples.get(selectedSampleID).getPath());
                String padNumber = (String) findViewById(selectedSampleID).getTag();
                intent.putExtra(COLOR, launchPadprefs.getInt(padNumber + COLOR, 0));
            }
        }
        startActivityForResult(intent, GET_SAMPLE);
    }
    public void DeleteSampleClick(View v){
        // Determine if a sample is selected
        File file = null;
        if (sampleLibraryIndex > -1) {
            file = sampleListAdapter.getItem(sampleLibraryIndex);
        } else if (selectedSampleID > -1) {
            if (activePads.contains((Integer)selectedSampleID))
                file = new File(samples.get(selectedSampleID).getPath());
        }

        if (file != null) {
            final File sampleFile = file;
            // Determine what touchpads this sample is assigned to
            final ArrayList<Integer> padIds = new ArrayList<>();
            for (int i : activePads) {
                String path = samples.get(i).getPath();
                if (path.equals(sampleFile.getAbsolutePath()))
                    padIds.add(i);
            }

            // Show dialog to alert user that file will be permanently deleted
            AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.MyAlertDialogStyle);
            builder.setTitle(R.string.remove_sample_dialog_title);
            if (padIds.size() > 0)
                builder.setMessage(R.string.remove_samples_in_use_dialog_message);
            else
                builder.setMessage(R.string.remove_sample_dialog_message);
            builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    for (int i : padIds)
                        removeSample(i);
                    if (sampleFile.delete())
                        Toast.makeText(context, getString(R.string.sample_deleted_toast), Toast.LENGTH_SHORT).show();
                    else
                        Toast.makeText(context, getString(R.string.sample_delete_error_toast), Toast.LENGTH_SHORT).show();
                    if (sampleListAdapter != null)
                        sampleListAdapter.remove(sampleFile);
                }
            });
            builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                }
            });
            AlertDialog dialog = builder.create();
            dialog.show();
        } else {
            Toast.makeText(context, getString(R.string.no_sample_selected_toast), Toast.LENGTH_SHORT).show();
        }
    }
    public void MySamplesClick(View v){
        TextView mySamplesTextView = (TextView)findViewById(R.id.my_samples_button);
        mySamplesTextView.setText(R.string.my_samples_underlined);

        TextView samplePacksTextView = (TextView)findViewById(R.id.sample_packs_button);
        samplePacksTextView.setText(R.string.sample_packs);

        rootLayout.findViewById(R.id.sample_listview).setVisibility(View.VISIBLE);
        rootLayout.findViewById(R.id.sample_pack_listview).setVisibility(View.GONE);
        rootLayout.findViewById(R.id.sample_pack_files_listview).setVisibility(View.GONE);
        rootLayout.findViewById(R.id.selected_pack_view).setVisibility(View.GONE);
        rootLayout.findViewById(R.id.sample_pack_empty_view).setVisibility(View.GONE);
        rootLayout.findViewById(R.id.new_sample_button).setVisibility(View.VISIBLE);
        Animations.fadeIn(rootLayout.findViewById(R.id.sample_listview), 200, 0).start();
    }
    public void SamplePacksClick(View v){
        TextView mySamplesTextView = (TextView)findViewById(R.id.my_samples_button);
        mySamplesTextView.setText(R.string.my_samples);

        TextView samplePacksTextView = (TextView)findViewById(R.id.sample_packs_button);
        samplePacksTextView.setText(R.string.sample_packs_underlined);

        rootLayout.findViewById(R.id.sample_listview).setVisibility(View.GONE);
        rootLayout.findViewById(R.id.sample_pack_files_listview).setVisibility(View.GONE);
        rootLayout.findViewById(R.id.sample_pack_listview).setVisibility(View.VISIBLE);
        rootLayout.findViewById(R.id.selected_pack_view).setVisibility(View.GONE);
        rootLayout.findViewById(R.id.sample_pack_empty_view).setVisibility(View.VISIBLE);
        rootLayout.findViewById(R.id.new_sample_button).setVisibility(View.GONE);
        Animations.fadeIn(findViewById(R.id.sample_pack_listview), 200, 0).start();
    }
    private void showSamplePackFiles(String name, AnimatorSet anim){
        ExpandableListView packListView = (ExpandableListView)rootLayout.findViewById(R.id.sample_pack_files_listview);
        SamplePackFilesListAdapter filesListAdapter = new SamplePackFilesListAdapter(context, R.layout.sample_pack_folder_list_item, R.layout.sample_pack_file_list_item);
        packListView.setAdapter(filesListAdapter);
        filesListAdapter.loadPack(name);

        rootLayout.findViewById(R.id.sample_pack_listview).setVisibility(View.GONE);
        packListView.setVisibility(View.VISIBLE);
        AnimatorSet fadeIn = Animations.fadeIn(packListView, 200, (int)anim.getDuration());
        AnimatorSet set = new AnimatorSet();
        set.playTogether(anim, fadeIn);
        set.start();
    }


    /** Monetization **/
    // In-app Store
    public static final int STORE_RESULT = 1001;
    private IabHelper mBillingHelper;
    private static String RSA_STRING_1 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAjcQ7YSSmv5GSS3FrQ801508P/r5laGtv7GBG2Ax9ql6ZAJZI6UPrJIvN9gXjoRBnH",
            RSA_STRING_2 = "OIphIg9HycJRxBwGfgcpEQ3F47uWJ/UvmPeQ3cVffFKIb/cAUqCS4puEtcDL2yDXoKjagsJNBjbRWz6tqDvzH5BtvdYoy4QUf8NqH8wd3/2R/m3PAVIr+lRlUAc1Dj2y40uOEdluDW+i9kbkMD8vrLKr+DGnB7JrKFAPaqxBNTeogv",
            RSA_STRING_3 = "0vGNOWwJd3Tgx7VDm825Op/vyG9VQSM7W53TsyJE8NdwP8Q59B/WRlcsr+tHCyoQcjscrgVegiOyME1DfEUrQk/SPzr5AlCqa2AZ//wIDAQAB";
    private static final String SKU_REMOVE_ADS = "remove_ads";
    private void initializeIAB(){
        // In-app billing init
        String base64EncodedPublicKey = RSA_STRING_1 + RSA_STRING_2 + RSA_STRING_3;
        mBillingHelper = new IabHelper(this, base64EncodedPublicKey);
        mBillingHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    // Oh no, there was a problem.
                    Log.d(LOG_TAG, "Problem setting up In-app Billing: " + result);
                }
                Log.d(LOG_TAG, "In-app billing initialized");
                checkForUnconsumedPurchases();
            }
        });
    }
    public void OpenStore(View v){
        Intent intent = new Intent(context, StoreActivity.class);
        startActivityForResult(intent, STORE_RESULT);
    }
    private void checkForUnconsumedPurchases(){
        try {
            mBillingHelper.queryInventoryAsync(new IabHelper.QueryInventoryFinishedListener() {
                @Override
                public void onQueryInventoryFinished(IabResult result, Inventory inv) {
                    if (!result.isSuccess()) return;

                    if (inv.hasPurchase(SKU_REMOVE_ADS)){
                        SharedPreferences.Editor editor = activityPrefs.edit();
                        editor.putBoolean(SHOW_ADS, false);
                        editor.apply();
                    }

                }
            });
        } catch (IabHelper.IabAsyncInProgressException e) { e.printStackTrace(); }
    }

    // Admob Advertising
    public static final String SHOW_ADS = "SHOW_ADS";
    private static final String SHOW_REMOVE_ADS_PROMPT = "SHOW_REMOVE_ADS_PROMPT";
    private boolean showRemoveAdsPrompt = true;

    private int interactionCount = 5;
    private InterstitialAd mInterstitialAd;
    private void initializeAds(){
        if (activityPrefs.getBoolean(SHOW_ADS, true)) {
            MobileAds.initialize(getApplicationContext(), getString(R.string.admob_id));
            mInterstitialAd = new InterstitialAd(this);
            mInterstitialAd.setAdUnitId(getString(R.string.launch_pad_activity_interstitial_ad_id));

            requestNewInterstitial();
        }
    }
    private void requestNewInterstitial() {
        if (activityPrefs.getBoolean(SHOW_ADS, true)) {
            AdRequest adRequest = new AdRequest.Builder()
                    .addTestDevice("19BA58A88672F3F9197685FEEB600EA7")
                    .addTestDevice("84217760FD1D092D92F5FE072A2F1861")
                    .build();

            mInterstitialAd.setAdListener(new AdListener() {
                @Override
                public void onAdClosed() {
                    if (activityPrefs.getBoolean(SHOW_REMOVE_ADS_PROMPT, true) && showRemoveAdsPrompt) {
                        Snackbar snackbar = Snackbar.make(findViewById(R.id.coordinator_layout), R.string.stop_ads_prompt, 5000);
                        snackbar.setAction(R.string.yes, new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                SharedPreferences.Editor editor = activityPrefs.edit();
                                editor.putBoolean(SHOW_REMOVE_ADS_PROMPT, false);
                                editor.apply();
                                Intent intent = new Intent(context, StoreActivity.class);
                                intent.putExtra(StoreActivity.SHOW_APP_EXTRAS, true);
                                startActivityForResult(intent, STORE_RESULT);
                            }
                        });
                        snackbar.setCallback(new Snackbar.Callback() {
                            @Override
                            public void onDismissed(Snackbar snackbar, int event) {
                                super.onDismissed(snackbar, event);
                                showRemoveAdsPrompt = false;

                            }
                        });
                        snackbar.show();
                    }
                    requestNewInterstitial();
                }
            });
            mInterstitialAd.loadAd(adRequest);
        }
    }
    private void showInterstitialAd(){
        interactionCount++;
        if (activityPrefs.getBoolean(SHOW_ADS, true)
                && mInterstitialAd != null
                && mInterstitialAd.isLoaded()
                && interactionCount > 4) {
            mInterstitialAd.show();
            interactionCount = 0;
        }
    }


    /** Recording and playback **/
    private ArrayList<LaunchEvent> launchQueue;
    private double beatsPerSec, sec, beats;
    private Timer timeOutTimer;

    // Handles touch events in record mode;
    private View.OnTouchListener TouchPadTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            return handlePlayTouch(v, event);
        }
    };
    private boolean handlePlayTouch(View v, MotionEvent event){
        if (!isEditMode && !isPlaying && samples.indexOfKey(v.getId()) >= 0) {
            if (!isRecording){ // Start counter thread
                resetRecording();
                isRecording = true;
                launchQueue = new ArrayList<>(10);
                //counter = recordingEndTime;
                actionBarMenu.findItem(R.id.action_play).setIcon(R.drawable.ic_action_av_pause);
                new Thread(new CounterThread()).start();
            }
            if (timeOutTimer == null) {
                timeOutTimer = new Timer();
                timeOutTimer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        boolean isTouched = false;
                        for (int i : activePads) {
                            isTouched = isTouched || samples.get(i).audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING;
                        }
                        if (!isTouched) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    stopPlayBack();
                                    timeOutTimer.cancel();
                                    timeOutTimer = null;
                                }
                            });
                        }
                    }
                }, 8000, 8000);
            }
            Sample s = samples.get(v.getId());
            switch (event.getAction()) {
                case MotionEvent.ACTION_UP:
                    if (s.getLaunchMode() == Sample.LAUNCHMODE_GATE) {// Stop sound and deselect pad
                        s.stop();
                        v.setPressed(false);
                        launchEvents.add(new LaunchEvent(counter, LaunchEvent.PLAY_STOP, v.getId()));
                    }
                    return true;
                case MotionEvent.ACTION_DOWN:
                    // If the sample is queued to launch, cancel it
                    if (v.isActivated()){
                        v.setActivated(false);
                        cancelLaunchEvent(s);
                        return true;
                    }
                    // If the sound is already playing, stop it
                    if (s.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        stopSample(s, v);
                        return true;
                    }
                    // Otherwise launch the sample
                    launchSample(s, v);
                    return true;
                default:
                    return false;
            }
        }
        return false;
    }
    private void launchSample(Sample s, View v){
        if (s.hasPlayed())
            s.reset();
        LaunchEvent launchEvent;
        if (s.getLaunchMode() == Sample.LAUNCHMODE_GATE){
            s.play();
            v.setPressed(true);
            launchEvents.add(new LaunchEvent(counter, LaunchEvent.PLAY_START, v.getId()));
        } else {
            switch (s.getQuantizationMode()) {
                case Sample.Q_BAR:
                    launchEvent = new LaunchEvent((Math.floor(beats) + timeSignature - Math.floor(beats) % timeSignature) * 1000 / beatsPerSec, LaunchEvent.PLAY_START, v.getId());
                    launchEvents.add(launchEvent);
                    launchQueue.add(launchEvent);
                    v.setActivated(true);
                    break;
                case Sample.Q_BEAT:
                    launchEvent = new LaunchEvent((Math.floor(beats) + 1) * 1000 / beatsPerSec, LaunchEvent.PLAY_START, v.getId());
                    launchEvents.add(launchEvent);
                    launchQueue.add(launchEvent);
                    v.setActivated(true);
                    break;
                case Sample.Q_HALF_BEAT:
                    launchEvent = new LaunchEvent((Math.floor(beats) + 0.5) * 1000 / beatsPerSec, LaunchEvent.PLAY_START, v.getId());
                    launchEvents.add(launchEvent);
                    launchQueue.add(launchEvent);
                    v.setActivated(true);
                    break;
                case Sample.Q_QUART_BEAT:
                    launchEvent = new LaunchEvent((Math.floor(beats) + 0.25) * 1000 / beatsPerSec, LaunchEvent.PLAY_START, v.getId());
                    launchEvents.add(launchEvent);
                    launchQueue.add(launchEvent);
                    v.setActivated(true);
                    break;
                case Sample.Q_NONE:
                    s.play();
                    v.setPressed(true);
                    launchEvents.add(new LaunchEvent(counter, LaunchEvent.PLAY_START, v.getId()));
                    break;
            }
        }
    }
    private void stopSample(Sample s, View v){
        LaunchEvent launchEvent;
        if (s.getLaunchMode() == Sample.LAUNCHMODE_GATE){
            s.play();
            v.setPressed(true);
            launchEvents.add(new LaunchEvent(counter, LaunchEvent.PLAY_START, v.getId()));
        } else {
            switch (s.getQuantizationMode()) {
                case Sample.Q_BAR:
                    launchEvent = new LaunchEvent((Math.floor(beats) + timeSignature - Math.floor(beats) % timeSignature) * 1000 / beatsPerSec, LaunchEvent.PLAY_STOP, v.getId());
                    launchEvents.add(launchEvent);
                    launchQueue.add(launchEvent);
                    v.setActivated(true);
                    break;
                case Sample.Q_BEAT:
                    launchEvent = new LaunchEvent((Math.floor(beats) + 1) * 1000 / beatsPerSec, LaunchEvent.PLAY_STOP, v.getId());
                    launchEvents.add(launchEvent);
                    launchQueue.add(launchEvent);
                    v.setActivated(true);
                    break;
                case Sample.Q_HALF_BEAT:
                    launchEvent = new LaunchEvent((Math.floor(beats) + 0.5) * 1000 / beatsPerSec, LaunchEvent.PLAY_STOP, v.getId());
                    launchEvents.add(launchEvent);
                    launchQueue.add(launchEvent);
                    v.setActivated(true);
                    break;
                case Sample.Q_QUART_BEAT:
                    launchEvent = new LaunchEvent((Math.floor(beats) + 0.25) * 1000 / beatsPerSec, LaunchEvent.PLAY_STOP, v.getId());
                    launchEvents.add(launchEvent);
                    launchQueue.add(launchEvent);
                    v.setActivated(true);
                    break;
                case Sample.Q_NONE:
                    s.stop();
                    v.setPressed(false);
                    launchEvents.add(new LaunchEvent(counter, LaunchEvent.PLAY_STOP, v.getId()));
                    break;
            }
        }
    }
    private void cancelLaunchEvent(Sample s){
        for (int i = launchEvents.size() - 1; i > launchEvents.size() - 10; i--){
            if (launchEvents.get(i).getSampleId() == s.getViewId()){
                launchEvents.remove(i);
                break;
            }
        }
        for (LaunchEvent e : launchQueue){
            if (e.getSampleId() == s.getViewId()){
                launchQueue.remove(e);
                break;
            }
        }
    }

    // Helper methods for menu commands
    private void matchTempo(){
        // Determine tempo of sample and re-sample scale factor
        AudioProcessor processor = new AudioProcessor(samples.get(selectedSampleID).getPath());
        ArrayList<BeatInfo> beats = processor.detectBeats();
        double sampleTempo = 60 * beats.size() / (samples.get(selectedSampleID).getSampleLengthMillis() / 1000);
        final double ratio = (double)bpm / sampleTempo;

        // Show re-sample dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Re-sample");
        LayoutInflater inflater = getLayoutInflater();
        final View view = inflater.inflate(R.layout.resample_dialog, null);
        TextView textGlobalTempo = (TextView)view.findViewById(R.id.textGlobalTempo);
        textGlobalTempo.setText(getString(R.string.global_tempo_msg, bpm));
        TextView textSampleTempo = (TextView)view.findViewById(R.id.textSampleTempo);
        textSampleTempo.setText(getString(R.string.sample_tempo_msg, (int)sampleTempo));
        TextView textRatio = (TextView)view.findViewById(R.id.textSuggestedRatio);
        textRatio.setText(getString(R.string.resample_suggested_ratio, ratio));
        builder.setView(view);
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                progressDialog = new ProgressDialog(context);
                progressDialog.setMessage(getString(R.string.resample_msg));
                progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                progressDialog.setIndeterminate(true);
                progressDialog.setCancelable(false);
                progressDialog.setCanceledOnTouchOutside(false);
                dialogCanceled = false;
                progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        progressDialog.dismiss();
                        dialogCanceled = true;
                    }
                });
                progressDialog.show();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final String stretchedSamplePath = samples.get(selectedSampleID).matchTempo(ratio);
                        view.post(new Runnable() {
                            @Override
                            public void run() {
                                progressDialog.dismiss();
                                playSampleDialog(stretchedSamplePath);
                            }
                        });
                    }
                }).start();
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    private void playSampleDialog(final String samplePath){
        final SamplePlayerFragment playerFragment = new SamplePlayerFragment();
        SamplePlayerFragment.SamplePlayerListener listener = new SamplePlayerFragment.SamplePlayerListener() {
            @Override
            public void positiveButtonClick(View v) {
                preparePad(samplePath, selectedSampleID, launchPadprefs.getInt(String.valueOf(selectedSampleID) + COLOR, 0));
                playerFragment.dismiss();
            }

            @Override
            public void negativeButtonClick(View v) {
                File newSample = new File(samplePath);
                newSample.delete();
                playerFragment.getDialog().cancel();
            }
        };
        playerFragment.setOnClickListener(listener);
        Bundle args = new Bundle();
        args.putString(SamplePlayerFragment.WAV_PATH, samplePath);
        playerFragment.setArguments(args);
        playerFragment.show(getFragmentManager(), "PlayFragment");
    }

    // Playback
    // Listener to turn off touch pads when sound is finished
    private AudioTrack.OnPlaybackPositionUpdateListener samplePlayListener = new AudioTrack.OnPlaybackPositionUpdateListener() {
        @Override
        public void onMarkerReached(AudioTrack track) {
            int id = track.getAudioSessionId();
            View v = findViewById(id);
            if (v != null) {
                v.setPressed(false);
                Sample s = (Sample) samples.get(id);
                s.stop();
                if (isRecording)
                    launchEvents.add(new LaunchEvent(counter, LaunchEvent.PLAY_STOP, v.getId()));
            }
        }

        @Override
        public void onPeriodicNotification(AudioTrack track) {

        }
    };
    private class CounterThread implements Runnable {
        @Override
        public void run(){
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            long startMillis = SystemClock.elapsedRealtime() - counter;
            do {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {e.printStackTrace();}
                counter = SystemClock.elapsedRealtime() - startMillis;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateCounterMessage();
                        ArrayList<LaunchEvent> tempQueue = (ArrayList<LaunchEvent>)launchQueue.clone();
                        for (LaunchEvent event : tempQueue){
                            if (counter >= event.getTimeStamp()) {
                                switch (event.getEventType()) {
                                    case LaunchEvent.PLAY_START:
                                    samples.get(event.sampleId).play();
                                    rootLayout.findViewById(event.sampleId).setPressed(true);
                                    rootLayout.findViewById(event.sampleId).setActivated(false);
                                    launchQueue.remove(event);
                                        break;
                                    case LaunchEvent.PLAY_STOP:
                                        samples.get(event.sampleId).stop();
                                        rootLayout.findViewById(event.sampleId).setPressed(false);
                                        rootLayout.findViewById(event.sampleId).setActivated(false);
                                        launchQueue.remove(event);
                                        break;
                                }
                            }

                        }
                    }
                });
                if (isRecording)
                    recordingEndTime = counter;
            } while ((isRecording || isPlaying) && !stopCounterThread);
        }
    }
    private class playBackRecording implements Runnable {
        @Override
        public void run() {
            isRecording = false;
            isPlaying = true;
            new Thread(new CounterThread()).start();
            /*
            for (int i = 0; i < launchEvents.size() && launchEvents.get(i).getTimeStamp() < counter; i++){
                playEventIndex = i;
            } */
            playEventIndex = 0;
            counter = 0;
            for (int i = playEventIndex; i < launchEvents.size() && isPlaying && !stopPlaybackThread; i++) {
                ArrayList<LaunchEvent> tempArray = new ArrayList<LaunchEvent>(loopingSamplesPlaying.size());
                tempArray.addAll(loopingSamplesPlaying);
                for (LaunchEvent e : tempArray){
                    if (e.timeStamp <= counter) {
                        samples.get(e.getSampleId()).play();
                        final int id = e.getSampleId();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                findViewById(id).setPressed(true);
                            }
                        });
                        loopingSamplesPlaying.remove(e);
                        //Log.d(LOG_TAG, "Restarting looped sample");
                    }
                }
                LaunchEvent event = launchEvents.get(i);
                playEventIndex = i;
                while (event.timeStamp > counter)
                {
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (event.eventType.equals(LaunchEvent.PLAY_START)) {
                    final int id = event.getSampleId();
                    samples.get(id).play();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            findViewById(id).setPressed(true);
                        }
                    });
                }
                else {
                    final int id = event.getSampleId();
                    samples.get(id).stop();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            findViewById(id).setPressed(false);
                        }
                    });
                }
            }
            if (!stopPlaybackThread) {
                isPlaying = false;
                reconnectTouchListeners();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ActionBar actionBar = getSupportActionBar();
                        if (actionBar != null) {
                            MenuItem item = actionBarMenu.findItem(R.id.action_play);
                            item.setIcon(R.drawable.ic_action_av_play_arrow);
                        }
                    }
                });
            }
        }
    }
    private void playMix(){
        if (launchEvents.size() > 0) {
            disconnectTouchListeners();
            isRecording = false;
            new Thread(new playBackRecording()).start();
        }
    }
    // Disables pads during playback
    private void disconnectTouchListeners(){
        for (int i : touchPadIds){
            TouchPad pad = (TouchPad)findViewById(i);
            pad.setOnTouchListener(null);
            pad.setClickable(false);
        }
    }
    // Enables pads after playback is finished
    private void reconnectTouchListeners(){
        for (int i : touchPadIds) {
            TouchPad pad = (TouchPad) findViewById(i);
            pad.setOnTouchListener(TouchPadTouchListener);
            pad.setClickable(true);
        }
    }
    private void resetRecording(){
        isRecording = false;
        counter = 0;
        recordingEndTime = 0;
        launchEvents = new ArrayList<>(50);
        launchQueue = new ArrayList<>(10);
        loopingSamplesPlaying = new ArrayList<>(5);
        updateCounterMessage();
    }
    private void updateCounterMessage(){
        beatsPerSec = (double)bpm / 60;
        sec = (double)counter / 1000;
        beats = sec * beatsPerSec;
        int bars = (int)Math.floor(beats / timeSignature);
        // Subtract one from beats so that counter displays zero when zero
        if (beats == 0)
            beats = -1;
        counterTextView.setText(String.format(Locale.US, "%2d : %.2f", bars, beats % timeSignature + 1));
    }
    private void stopPlayBack(){
        isPlaying = false;
        timeOutTimer.cancel();
        timeOutTimer = null;
        MenuItem playButton = actionBarMenu.findItem(R.id.action_play);
        playButton.setIcon(R.drawable.ic_action_av_play_arrow);
        loopingSamplesPlaying = new ArrayList<>(5);
        for (Integer i : activePads) {
            Sample s = samples.get(i);
            View v = findViewById(i);
            v.setPressed(false);
            v.setActivated(false);
            if (s.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                if (isRecording)
                    launchEvents.add(new LaunchEvent(counter, LaunchEvent.PLAY_STOP, i));
                else if (s.getLoopMode()){
                    //Log.d(LOG_TAG, "Counter: " + String.valueOf(counter));
                    double newTime = counter - s.audioTrack.getPlaybackHeadPosition() * 4 / (8 * 44100) * 1000 + s.getSampleLengthMillis();
                    loopingSamplesPlaying.add(new LaunchEvent(newTime, LaunchEvent.PLAY_START, i));
                    //Log.d(LOG_TAG, "New loop sample time: " + String.valueOf(newTime));
                }
                s.stop();
            }
        }

        reconnectTouchListeners();
        isRecording = false;
    }
    public void PlayButtonClick(View v){
        if (isPlaying || isRecording) {
            stopPlayBack();
        }
        else if (launchEvents.size() > 0 && playEventIndex < launchEvents.size()){
            playMix();
        }
    }
    public void RewindButtonClick(View v){
        stopPlayBack();
        counter = 0;
        playEventIndex = 0;
        loopingSamplesPlaying = new ArrayList<LaunchEvent>(5);
        updateCounterMessage();
    }
    public void FastForwardButtonClick(View v){
        stopPlayBack();
        counter = recordingEndTime;
        playEventIndex = Math.max(launchEvents.size() - 1, 0);
        loopingSamplesPlaying = new ArrayList<LaunchEvent>(5);
        updateCounterMessage();
    }
    public void Stop(View v){
        stopPlayBack();
    }

    // File save/export methods
    private void promptForFilename(){
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Enter a name");
        LayoutInflater inflater = getLayoutInflater();
        final View view = inflater.inflate(R.layout.enter_text_dialog, null);
        builder.setView(view);
        builder.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                EditText text = (EditText) view.findViewById(R.id.dialogText);
                String fileName = text.getText().toString();
                if (fileName.toLowerCase().endsWith(".wav"))
                    fileName = fileName.substring(0, fileName.length() - 4);
                SaveToFile(fileName);
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    private void SaveToFile(final String fileName){
        progressDialog = new ProgressDialog(context);
        progressDialog.setMessage(getString(R.string.file_export_msg) + fileName + ".wav");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setIndeterminate(false);
        progressDialog.setCancelable(true);
        progressDialog.setCanceledOnTouchOutside(false);
        dialogCanceled = false;
        progressDialog.setMax(launchEvents.size());
        progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                progressDialog.dismiss();
                dialogCanceled = true;
            }
        });
        progressDialog.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                WriteWavFile(fileName);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                        if (!dialogCanceled)
                            Toast.makeText(context, "Saved to " + homeDirectory + "/" + fileName + ".wav", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }
    private void selectEncoder(){
        if (launchEvents.size() > 1) {
            ArrayList<String> codecs = new ArrayList<String>();
            codecs.add("wav");
            final String[] fileTypes;
            for (int i = 0; i < MediaCodecList.getCodecCount(); i++) {
                MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
                if (codecInfo.isEncoder()) {
                    String[] types = codecInfo.getSupportedTypes();
                    for (String s : types) {
                        if (s.startsWith("audio"))
                            if (codecs.indexOf(s.substring(6)) < 0)
                                codecs.add(s.substring(6));
                        Log.d(LOG_TAG, codecInfo.getName() + " supported type: " + s);
                    }
                }
            }
            fileTypes = new String[codecs.size()];
            for (int i = 0; i < codecs.size(); i++)
                fileTypes[i] = codecs.get(i);
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Select audio file format");
            builder.setItems(fileTypes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    SaveToFile(fileTypes[which]);
                }
            });
            AlertDialog dialog = builder.create();
            dialog.show();
        }
        else
        {
            Toast.makeText(context, "Nothing to export", Toast.LENGTH_SHORT).show();
        }
    }
    private void EncodeAudio(String fileType){
        if (!fileType.equals("wav")) {
            File encodedFile = new File(homeDirectory + "/saved.aac");
            if (encodedFile.isFile())
                encodedFile.delete();
            FileOutputStream fileWriter;
            long TIMEOUT_US = 10000;
            ByteBuffer[] codecInputBuffers;
            ByteBuffer[] codecOutputBuffers;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            InputStream wavStream;
            MediaFormat format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, "audio/" + fileType);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 128);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 2);
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
            try {
                fileWriter = new FileOutputStream(encodedFile);
                wavStream = new BufferedInputStream(new FileInputStream(new File(sampleDirectory, "saved.wav")));
                wavStream.skip(44);
                MediaCodec codec = MediaCodec.createEncoderByType("audio/" + fileType);
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                codec.start();
                codecInputBuffers = codec.getInputBuffers();
                codecOutputBuffers = codec.getOutputBuffers();
                boolean sawInputEOS = false;
                boolean sawOutputEOS = false;
                do {
                    // Load input buffer
                    int inputBufIndex = codec.dequeueInputBuffer(TIMEOUT_US);
                    Log.d(LOG_TAG, "inputBufIndex = " + String.valueOf(inputBufIndex));
                    if (inputBufIndex >= 0) {
                        ByteBuffer inputBuffer = codecInputBuffers[inputBufIndex];
                        inputBuffer.clear();
                        byte[] inputBytes = new byte[inputBuffer.capacity()];
                        int len = wavStream.read(inputBytes);
                        inputBuffer.put(inputBytes);
                        codec.queueInputBuffer(inputBufIndex, 0, len, 0, 0);
                        Log.d(LOG_TAG, "codec read bytes " + String.valueOf(inputBytes.length));
                    }
                    else
                        sawInputEOS = true;
                    // Process output buffers
                    int outputBufIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US);
                    if (outputBufIndex >=0){
                        ByteBuffer outputBuffer = codecOutputBuffers[outputBufIndex];
                        byte[] outputBytes = new byte[info.size];
                        outputBuffer.position(0);
                        outputBuffer.get(outputBytes);
                        fileWriter.write(outputBytes);
                        codec.releaseOutputBuffer(outputBufIndex, false);
                        Log.d(LOG_TAG, "codec wrote bytes " + String.valueOf(outputBytes.length));
                    }
                    if ((info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) || (info.flags == MediaCodec.BUFFER_FLAG_SYNC_FRAME + MediaCodec.BUFFER_FLAG_END_OF_STREAM))
                        sawOutputEOS = true;
                } while (!sawInputEOS && !sawOutputEOS && !dialogCanceled);
                codec.stop();
                fileWriter.close();

            } catch (IOException e) {e.printStackTrace();}
        }
    }
    private void WriteWavFileOld(String fileName){
        // Wave file to write
        File waveFileTemp = new File(homeDirectory, fileName + ".wav");
        if (waveFileTemp.isFile())
            waveFileTemp.delete();
        WaveFile waveFile = new WaveFile();
        waveFile.OpenForWrite(waveFileTemp.getAbsolutePath(), 44100, (short)16, (short)2);
        // Array holds all the samples that are being played at a given time
        ArrayList<String> playingSamples = new ArrayList<String>(24);
        // Array to contain offsets for samples that play longer than the next event.  Stored as strings
        SparseArray<String> playingSampleOffsets = new SparseArray<String>(24);
        int bytesWritten = 0; // Total bytes written, also used to track time
        int i = 0;
        int length = 0;
        do {
            LaunchEvent event = launchEvents.get(i);
            if (event.eventType.equals(LaunchEvent.PLAY_START))
                playingSamples.add(String.valueOf(event.getSampleId()));
            else {
                playingSamples.remove(String.valueOf(event.getSampleId()));
                playingSampleOffsets.put(event.getSampleId(), String.valueOf(0));
            }
            // Figure out how much can be written before the next start/stop event
            if (i < launchEvents.size() - 1) {
                length = (int)(launchEvents.get(i + 1).timeStamp / 1000 * 44100) * 16 / 4 - bytesWritten;
            }
            else
                length = 0;
            // short array to hold that data to be written before the next event
            short[] shortData = new short[length / 2];
            // For each sample that is playing load its data and add it to the array to be written
            for (String idString : playingSamples){
                // byte array to hold that data to be written before the next event for this sample
                int id = Integer.parseInt(idString);
                byte[] byteData = new byte[length];
                byte [] sampleBytes = samples.get(id).getAudioBytes();
                int bytesCopied = 0;
                int offset = Integer.parseInt(playingSampleOffsets.get(id, "0"));
                if (offset > sampleBytes.length)
                    offset -= sampleBytes.length;
                if (sampleBytes.length - offset <= byteData.length) {
                    // Fill the byte array with copies of the sample until it is full
                    do {
                        ByteBuffer.wrap(sampleBytes, offset, sampleBytes.length - offset).get(byteData, bytesCopied, sampleBytes.length - offset);
                        bytesCopied += sampleBytes.length - offset;
                        offset = 0;
                    } while (bytesCopied + sampleBytes.length <= byteData.length);
                    ByteBuffer.wrap(sampleBytes, 0, byteData.length - bytesCopied).get(byteData, bytesCopied, byteData.length - bytesCopied);
                    playingSampleOffsets.put(id, String.valueOf((byteData.length - bytesCopied)));
                }
                else{
                    ByteBuffer.wrap(sampleBytes, offset, byteData.length).get(byteData);
                    playingSampleOffsets.put(Integer.parseInt(idString), String.valueOf(sampleBytes.length + offset + byteData.length));
                }
                // Convert byte data to shorts
                short[] shorts = new short[byteData.length / 2];
                ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
                // Add the sample short data to the total data to be written to file
                float [] mixedBuffer = new float[shorts.length];
                float max = 0;
                float volume = samples.get(id).getVolume();
                for (int j = 0; j < shorts.length; j++){
                    mixedBuffer[j] = shortData[j] + shorts[j] * volume;
                    max = Math.max(mixedBuffer[j], max);
                }
                Log.d(LOG_TAG, "Wav max value: " + String.valueOf(max));
                for (int j = 0; j < mixedBuffer.length; j++) {
                    shortData[j] = (short) (mixedBuffer[j] * 32767 / max);
                }
            }
            waveFile.WriteData(shortData, shortData.length);
            final int progress = i;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressDialog.setProgress(progress);
                }
            });
            i++;
            bytesWritten += length;
        } while (i < launchEvents.size() && !dialogCanceled);
        waveFile.Close();
    }
    private void WriteWavFile(String fileName){
        // Wave file to write
        File tempFile = new File(homeDirectory, fileName + ".mix");
        if (tempFile.isFile())
            tempFile.delete();
        OutputStream outputStream = null;
        float max = 0;
        try {
            tempFile.createNewFile();
            outputStream = new BufferedOutputStream(new FileOutputStream(tempFile));
            // Array holds all the samples that are being played at a given time
            ArrayList<String> playingSamples = new ArrayList<String>(24);
            // Array to contain offsets for samples that play longer than the next event.  Stored as strings
            SparseArray<String> playingSampleOffsets = new SparseArray<String>(24);
            int bytesWritten = 0; // Total bytes written, also used to track time
            int i = 0;
            int length = 0;
            do {
                LaunchEvent event = launchEvents.get(i);
                if (event.eventType.equals(LaunchEvent.PLAY_START))
                    playingSamples.add(String.valueOf(event.getSampleId()));
                else {
                    playingSamples.remove(String.valueOf(event.getSampleId()));
                    playingSampleOffsets.put(event.getSampleId(), String.valueOf(0));
                }
                // Figure out how much can be written before the next start/stop event
                if (i < launchEvents.size() - 1) {
                    length = (int) (launchEvents.get(i + 1).timeStamp / 1000 * 44100) * 16 / 4 - bytesWritten;
                } else
                    length = 0;
                // short array to hold that data to be written before the next event
                float[] mixedBuffer = new float[length / 2];
                // For each sample that is playing load its data and add it to the array to be written
                for (String idString : playingSamples) {
                    // byte array to hold that data to be written before the next event for this sample
                    int id = Integer.parseInt(idString);
                    byte[] byteData = new byte[length];
                    byte[] sampleBytes = samples.get(id).getAudioBytes();
                    int bytesCopied = 0;
                    int offset = Integer.parseInt(playingSampleOffsets.get(id, "0"));
                    if (offset > sampleBytes.length)
                        offset -= sampleBytes.length;
                    if (sampleBytes.length - offset <= byteData.length) {
                        // Fill the byte array with copies of the sample until it is full
                        do {
                            ByteBuffer.wrap(sampleBytes, offset, sampleBytes.length - offset).get(byteData, bytesCopied, sampleBytes.length - offset);
                            bytesCopied += sampleBytes.length - offset;
                            offset = 0;
                        } while (bytesCopied + sampleBytes.length <= byteData.length);
                        ByteBuffer.wrap(sampleBytes, 0, byteData.length - bytesCopied).get(byteData, bytesCopied, byteData.length - bytesCopied);
                        playingSampleOffsets.put(id, String.valueOf((byteData.length - bytesCopied)));
                    } else {
                        ByteBuffer.wrap(sampleBytes, offset, byteData.length).get(byteData);
                        playingSampleOffsets.put(Integer.parseInt(idString), String.valueOf(sampleBytes.length + offset + byteData.length));
                    }
                    // Convert byte data to shorts
                    short[] shorts = new short[byteData.length / 2];
                    ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
                    // Add the sample short data to the total data to be written to file
                    float volume = samples.get(id).getVolume();
                    for (int j = 0; j < shorts.length; j++) {
                        mixedBuffer[j] = mixedBuffer[j] + shorts[j] * volume;
                        max = Math.max(mixedBuffer[j], max);
                    }
                    ByteBuffer buffer = ByteBuffer.allocate(4 * mixedBuffer.length);
                    for (float value : mixedBuffer){
                        buffer.putFloat(value);
                    }
                    byte[] bytes = new byte[4 * mixedBuffer.length];
                    buffer.rewind();
                    buffer.get(bytes);
                    outputStream.write(bytes);
                }
                final int progress = i;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.setProgress(progress);
                    }
                });
                i++;
                bytesWritten += length;
            } while (i < launchEvents.size() && !dialogCanceled);
            outputStream.close();
        } catch (FileNotFoundException e){e.printStackTrace();}
        catch (IOException e) {e.printStackTrace();}
        finally {
            if (outputStream != null)
                try {
                    outputStream.close();
                } catch(IOException e) {}
        }
        // Write wav file from temp mix file
        InputStream inputStream = null;
        try {
            inputStream = new BufferedInputStream(new FileInputStream(tempFile));
            File waveFileFinal = new File(homeDirectory, fileName + ".wav");
            if (waveFileFinal.isFile())
                waveFileFinal.delete();
            WaveFile waveFile = new WaveFile();
            waveFile.OpenForWrite(waveFileFinal.getAbsolutePath(), 44100, (short)16, (short)2);
            int numBytes = inputStream.available();
            while (numBytes > 0){
                byte[] bytes = new byte[numBytes];
                float[] floats = new float[bytes.length / 4];
                short[] shorts = new short[floats.length];
                inputStream.read(bytes);
                ByteBuffer.wrap(bytes).asFloatBuffer().get(floats);
                for (int j = 0; j < floats.length; j++) {
                    shorts[j] = (short) (floats[j] * 32767 / max);
                }
                waveFile.WriteData(shorts, shorts.length);
                numBytes = inputStream.available();
            }
            waveFile.Close();
        }catch (IOException e) {e.printStackTrace();}
    }
    private void WriteWavFile2(String fileName){
        // Wave file to write
        File tempFile = new File(homeDirectory, fileName + ".mix");
        if (tempFile.isFile())
            tempFile.delete();
        OutputStream outputStream = null;
        float max = 0;
        try {
            tempFile.createNewFile();
            outputStream = new BufferedOutputStream(new FileOutputStream(tempFile));
            // Array holds all the samples that are being played at a given time
            ArrayList<String> playingSamples = new ArrayList<String>(24);
            // Array to contain offsets for samples that play longer than the next event.  Stored as strings
            SparseArray<String> playingSampleOffsets = new SparseArray<String>(24);
            int bytesWritten = 0; // Total bytes written, also used to track time
            int i = 0;
            int length = 0;
            do {
                LaunchEvent event = launchEvents.get(i);
                if (event.eventType.equals(LaunchEvent.PLAY_START))
                    playingSamples.add(String.valueOf(event.getSampleId()));
                else {
                    playingSamples.remove(String.valueOf(event.getSampleId()));
                    playingSampleOffsets.put(event.getSampleId(), String.valueOf(0));
                }
                // Figure out how much can be written before the next start/stop event
                if (i < launchEvents.size() - 1) {
                    length = (int) (launchEvents.get(i + 1).timeStamp / 1000 * 44100) * 16 / 4 - bytesWritten;
                } else
                    length = 0;
                // short array to hold that data to be written before the next event
                float[] mixedBuffer = new float[length / 2];
                // For each sample that is playing load its data and add it to the array to be written
                for (String idString : playingSamples) {
                    // byte array to hold that data to be written before the next event for this sample
                    int id = Integer.parseInt(idString);
                    byte[] byteData = new byte[length];
                    byte[] sampleBytes = samples.get(id).getAudioBytes();
                    int bytesCopied = 0;
                    int offset = Integer.parseInt(playingSampleOffsets.get(id, "0"));
                    if (offset > sampleBytes.length)
                        offset -= sampleBytes.length;
                    if (sampleBytes.length - offset <= byteData.length) {
                        // Fill the byte array with copies of the sample until it is full
                        do {
                            ByteBuffer.wrap(sampleBytes, offset, sampleBytes.length - offset).get(byteData, bytesCopied, sampleBytes.length - offset);
                            bytesCopied += sampleBytes.length - offset;
                            offset = 0;
                        } while (bytesCopied + sampleBytes.length <= byteData.length);
                        ByteBuffer.wrap(sampleBytes, 0, byteData.length - bytesCopied).get(byteData, bytesCopied, byteData.length - bytesCopied);
                        playingSampleOffsets.put(id, String.valueOf((byteData.length - bytesCopied)));
                    } else {
                        ByteBuffer.wrap(sampleBytes, offset, byteData.length).get(byteData);
                        playingSampleOffsets.put(Integer.parseInt(idString), String.valueOf(sampleBytes.length + offset + byteData.length));
                    }
                    // Convert byte data to shorts
                    short[] shorts = new short[byteData.length / 2];
                    ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
                    // Add the sample short data to the total data to be written to file
                    float volume = samples.get(id).getVolume();
                    for (int j = 0; j < shorts.length; j++) {
                        mixedBuffer[j] = mixedBuffer[j] + shorts[j] * volume;
                        max = Math.max(mixedBuffer[j], max);
                    }
                    ByteBuffer buffer = ByteBuffer.allocate(4 * mixedBuffer.length);
                    for (float value : mixedBuffer){
                        buffer.putFloat(value);
                    }
                    byte[] bytes = new byte[4 * mixedBuffer.length];
                    buffer.rewind();
                    buffer.get(bytes);
                    outputStream.write(bytes);
                }
                final int progress = i;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.setProgress(progress);
                    }
                });
                i++;
                bytesWritten += length;
            } while (i < launchEvents.size() && !dialogCanceled);
            outputStream.close();
        } catch (FileNotFoundException e){e.printStackTrace();}
        catch (IOException e) {e.printStackTrace();}
        finally {
            if (outputStream != null)
                try {
                    outputStream.close();
                } catch(IOException e) {}
        }
        InputStream inputStream = null;
        try {
            inputStream = new BufferedInputStream(new FileInputStream(tempFile));
            File waveFileFinal = new File(homeDirectory, fileName + ".wav");
            if (waveFileFinal.isFile())
                waveFileFinal.delete();
            WaveFile waveFile = new WaveFile();
            waveFile.OpenForWrite(waveFileFinal.getAbsolutePath(), 44100, (short)16, (short)2);
            int numBytes = inputStream.available();
            while (numBytes > 0){
                byte[] bytes = new byte[numBytes];
                float[] floats = new float[bytes.length / 4];
                short[] shorts = new short[floats.length];
                inputStream.read(bytes);
                ByteBuffer.wrap(bytes).asFloatBuffer().get(floats);
                for (int j = 0; j < floats.length; j++) {
                    shorts[j] = (short) (floats[j] * 32767 / max);
                }
                waveFile.WriteData(shorts, shorts.length);
                numBytes = inputStream.available();
            }
            waveFile.Close();
        }catch (IOException e) {e.printStackTrace();}
    }

    private void CopyFile(File src, File dst) throws IOException {
        FileChannel inChannel = new FileInputStream(src).getChannel();
        FileChannel outChannel = new FileOutputStream(dst).getChannel();
        try {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        } finally {
            if (inChannel != null)
                inChannel.close();
            if (outChannel != null)
                outChannel.close();
        }
    }

    public class Sample{
        // Public fields
        public static final int LAUNCHMODE_GATE = 0;
        public static final int LAUNCHMODE_TRIGGER = 1;
        public static final int ERROR_NONE = 9000;
        public static final int ERROR_OUT_OF_MEMORY = 9001;
        public static final int ERROR_FILE_IO = 9002;
        public static final int Q_NONE = 0;
        public static final int Q_BEAT = 1;
        public static final int Q_HALF_BEAT = 2;
        public static final int Q_QUART_BEAT = 4;
        public static final int Q_BAR = 5;

        // Private fields
        private int id;
        private String path;
        private boolean loop = false;
        private int loopMode = 0;
        private int launchMode = LAUNCHMODE_TRIGGER;
        private int quantizationMode = Q_NONE;
        private File sampleFile;
        private int sampleByteLength;
        private int sampleRate = 44100;
        private float volume = 0.5f * AudioTrack.getMaxVolume();
        private boolean played = false;
        private int sampleState = ERROR_NONE;
        private AudioTrack audioTrack;
        private AudioTrack.OnPlaybackPositionUpdateListener listener;

        // Constructors
        public Sample(String path, int id){
            this.path = path;
            this.id = id;
            sampleFile = new File(path);
            Log.d(LOG_TAG, "Sample path: " + sampleFile.getAbsolutePath());
            if (sampleFile.exists()){
                sampleByteLength = (int)sampleFile.length() - 44;
                sampleRate = Utils.getWavSampleRate(sampleFile);
                reloadAudioTrack();
            } else Log.d(LOG_TAG, "File doesn't exist");
        }
        public Sample(String path, int launchMode, boolean loopMode){
            this.path = path;
            loop = loopMode;
            if (!setLaunchMode(launchMode))
                this.launchMode = LAUNCHMODE_TRIGGER;
            reloadAudioTrack();
        }

        // Public methods
        public void setViewId(int id){this.id = id;}
        public int getViewId(){return id;}
        public String getPath(){return path;}
        public void setLoopMode(boolean loop){
            this.loop = loop;
            if (loop) {
                loopMode = -1;
                if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED)
                    reloadAudioTrack();

                audioTrack.setLoopPoints(0, sampleByteLength / 4 + 1, -1);
                audioTrack.setNotificationMarkerPosition(0);
                audioTrack.setPlaybackPositionUpdateListener(null);
            }
            else {
                loopMode = 0;
                try {
                    audioTrack.setLoopPoints(0, 0, 0);
                } catch (Exception e) {}
                setOnPlayFinishedListener(listener);
            }
        }
        public boolean getLoopMode(){
            return loop;
        }
        public int getLoopModeInt() {
            return loopMode;
        }
        public boolean setLaunchMode(int launchMode){
            if (launchMode == LAUNCHMODE_GATE){
                this.launchMode = LAUNCHMODE_GATE;
                return true;
            }
            else if (launchMode == LAUNCHMODE_TRIGGER){
                this.launchMode = LAUNCHMODE_TRIGGER;
                return true;
            }
            else
                return false;
        }
        public int getLaunchMode(){
            return launchMode;
        }
        public void setQuantizationMode(int mode){
            quantizationMode = mode;
        }
        public int getQuantizationMode(){
            return quantizationMode;
        }
        public boolean isQuantized(){
            return quantizationMode > 0;
        }
        public double getSampleLengthMillis(){
            return  1000 * (double)sampleByteLength / (sampleRate * 16 / 4);
        }
        public byte[] getAudioBytes(){
            InputStream stream = null;
            byte[] bytes = null;
            try {
                stream = new BufferedInputStream(new FileInputStream(sampleFile));
                stream.skip(44);
                bytes = new byte[sampleByteLength];
                stream.read(bytes);
            } catch (IOException e) {e.printStackTrace();}
            return bytes;
        }
        public boolean isReady(){
            if (audioTrack != null)
                return audioTrack.getState() == AudioTrack.STATE_INITIALIZED;
            else
                return false;
        }
        public int getState(){
            return sampleState;
        }
        public void play(){
            try {
                played = true;
                if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED && audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                    resetMarker();
                    audioTrack.play();
                } else if (audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                    //Log.d("AudioTrack", String.valueOf(id) + " uninitialized");
                    reloadAudioTrack();
                    if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED && audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING)
                        audioTrack.play();
                }
            }catch (IllegalStateException e) {e.printStackTrace();}
        }
        public void stop(){
            if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                try {
                    audioTrack.pause();
                    audioTrack.stop();
                    audioTrack.flush();
                    audioTrack.release();
                    //audioTrack.reloadStaticData();
                } catch (IllegalStateException e) {
                    reloadAudioTrack();
                }
                reloadAudioTrack();
            }
        }
        public void pause(){
            audioTrack.pause();
        }
        public void reset(){
            if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING)
                audioTrack.stop();
            audioTrack.flush();
            audioTrack.release();
            reloadAudioTrack();
        }
        public void release(){
            audioTrack.release();
        }
        public void setVolume(float volume){
            this.volume = volume;
            reloadAudioTrack();
        }
        public float getVolume() {return volume;}
        public boolean hasPlayed(){
            return played;
        }
        public void setOnPlayFinishedListener(AudioTrack.OnPlaybackPositionUpdateListener listener){
            this.listener = listener;
            audioTrack.setPlaybackPositionUpdateListener(listener);
            resetMarker();
        }
        public void resetMarker(){
            audioTrack.setNotificationMarkerPosition(sampleByteLength / 4 - 2000);
        }
        public String matchTempo(double tempo){
            AudioProcessor processor = new AudioProcessor(path);
            int L, M;
            M = (int)(tempo * 100);
            L = 100;
            processor.resample(L, M, homeDirectory.getAbsolutePath() + "/tempo_stretch_test.wav");
            return homeDirectory.getAbsolutePath() + "/tempo_stretch_test.wav";
        }
        public void reloadAudioTrack() {
            //Log.d(LOG_TAG, "sampleByteLength = " + sampleByteLength);
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    2*sampleByteLength,
                    AudioTrack.MODE_STATIC, id);
            if (runtime.totalMemory() + (sampleByteLength - runtime.freeMemory()) < runtime.maxMemory() * 0.9) {
                InputStream stream = null;
                try {
                    stream = new BufferedInputStream(new FileInputStream(sampleFile));
                    stream.skip(44);
                    byte[] bytes = new byte[sampleByteLength];
                    stream.read(bytes);
                    short[] shorts = new short[bytes.length / 2];
                    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
                    audioTrack.write(shorts, 0, shorts.length);
                    stream.close();
                    played = false;

                    audioTrack.setStereoVolume(volume, volume);
                    if (listener != null) {
                        audioTrack.setPlaybackPositionUpdateListener(listener);
                        resetMarker();
                    }

                    if (loop) {
                        setLoopMode(true);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    sampleState = ERROR_FILE_IO;
                }
            } else {
                sampleState = ERROR_OUT_OF_MEMORY;
            }
        }
    }

    public class LaunchEvent{
        public static final String PLAY_START = "com.nakedape.mixmaticlooppad.playstart";
        public static final String PLAY_STOP = "com.nakedape.mixmaticlooppad.playstop";
        private double timeStamp;
        private String eventType;
        private int sampleId;

        public LaunchEvent(double timeStamp, String eventType, int sampleId){
            this.timeStamp = timeStamp;
            this.eventType = eventType;
            this.sampleId = sampleId;
        }

        public double getTimeStamp() {return timeStamp;}
        public String getEventType() {return eventType;}
        public int getSampleId() {return sampleId;}
    }
}
