package com.nakedape.mixmaticlooppad;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.os.Bundle;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.content.res.AppCompatResources;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.ui.ResultCodes;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import IABUtils.IabHelper;
import IABUtils.IabResult;
import IABUtils.Inventory;
import IABUtils.Purchase;

public class StoreActivity extends AppCompatActivity {

    public final static String ACCOUNT_PREFS = "ACCOUNT_PREFS";
    public final static String AUTO_SIGN_IN = "AUTO_SIGN_IN";
    public final static String SHOW_APP_EXTRAS = "SHOW_APP_EXTRAS";
    private final static String SHOW_CREATE_ACCOUNT_PROMPT = "SHOW_CREATE_ACCOUNT_PROMPT";
    private final String LOG_TAG = "StoreActivity";

    // Firebase
    private FirebaseStorage storage = FirebaseStorage.getInstance();
    private StorageReference packFolderRef;
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener authStateListener;
    private FirebaseAnalytics firebaseAnalytics;
    private static final String SAMPLE_PACK_PURCHASE = "SAMPLE_PACK_PURCHASE";

    private Context context;
    private RelativeLayout rootLayout;
    private File cacheFolder, samplePackFolder;
    private SamplePackListAdapter samplePackListAdapter;
    private SharedPreferences appPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_store);
        context = this;
        rootLayout = (RelativeLayout)findViewById(R.id.rootLayout);
        appPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Initialize navigation bar
        BottomNavigationView.OnNavigationItemSelectedListener navListener = new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                return onNavigation(item);
            }
        };
        BottomNavigationView navView = (BottomNavigationView)findViewById(R.id.bottom_navigation);
        navView.setOnNavigationItemSelectedListener(navListener);

        // Setup toolbar
        Toolbar toolbar = (Toolbar) findViewById(R.id.my_toolbar);
        //toolbar.setNavigationIcon(AppCompatResources.getDrawable(this, R.drawable.ic_navigation_arrow_back));
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            //actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setCustomView(R.layout.store_action_bar_custom_view);
            actionBar.setDisplayShowCustomEnabled(true);

            Toolbar bar = (Toolbar)actionBar.getCustomView().getParent();
            bar.setContentInsetsAbsolute(0, 0);
            bar.getContentInsetEnd();
            bar.setPadding(0, 0, 0, 0);
        }

        // Initialize price list with default values
        packPriceList = new HashMap<>();
        packPriceList.put(SKU_TWO_DOLLAR_PACK, "");

        // Initialize Firebase Analytics
        firebaseAnalytics = FirebaseAnalytics.getInstance(context);

        // Initialize AdMob
        if (appPrefs.getBoolean(LaunchPadActivity.SHOW_ADS, true)) {
            MobileAds.initialize(getApplicationContext(), getString(R.string.admob_id));
            AdView mAdView = (AdView) findViewById(R.id.adView);
            mAdView.setVisibility(View.VISIBLE);
            AdRequest adRequest = new AdRequest.Builder()
                    .addTestDevice("19BA58A88672F3F9197685FEEB600EA7")
                    .addTestDevice("84217760FD1D092D92F5FE072A2F1861")
                    .addTestDevice("B351AB87B7184CD82FD0563D59D1E95B")
                    .build();
            mAdView.loadAd(adRequest);
        }

        // Setup Firebase authentication
        mAuth = FirebaseAuth.getInstance();
        authStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                handleAuthStateChange(firebaseAuth.getCurrentUser());
            }
        };

        // If user is not signed in and has not signed in before, sign-in anon
        if (mAuth.getCurrentUser() == null) {
            SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS, MODE_PRIVATE);
            if (prefs.getBoolean(AUTO_SIGN_IN, false)){
                startSignInFlow();
            } else {
                mAuth.signInAnonymously()
                        .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                Log("signInAnonymously:onComplete:" + task.isSuccessful());

                                // If sign in fails, display a message to the user. If sign in succeeds
                                // the auth state listener will be notified and logic to handle the
                                // signed in user can be handled in the listener.
                                if (!task.isSuccessful()) {
                                    Log.e(LOG_TAG, "signInAnonymously", task.getException());
                                    Toast.makeText(context, R.string.auth_error,
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
            }
        } else if (!mAuth.getCurrentUser().isAnonymous()){
            // user is signed in!
            Toast.makeText(context, R.string.sign_in_success, Toast.LENGTH_SHORT).show();
            updateSigninButton();
        }

    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume(){
        super.onResume();
        checkInternetConnection();
        mAuth.addAuthStateListener(authStateListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        unregisterNetworkListener();
        if (authStateListener != null) {
            mAuth.removeAuthStateListener(authStateListener);
        }
        if (mediaPlayer != null){
            if (mediaPlayer.isPlaying())
                mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mBillingHelper != null)  try {
            mBillingHelper.dispose();
        } catch (IabHelper.IabAsyncInProgressException e){
            e.printStackTrace();
        }
        mBillingHelper = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                // user is signed in!
                Toast.makeText(context, R.string.sign_in_success, Toast.LENGTH_SHORT).show();
                SharedPreferences.Editor editor = getSharedPreferences(ACCOUNT_PREFS, MODE_PRIVATE).edit();
                editor.putBoolean(AUTO_SIGN_IN, true);
                editor.apply();

                updateSigninButton();

                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null) {
                    // Record Firebase sign-in event
                    Bundle bundle = new Bundle();
                    bundle.putString(FirebaseAnalytics.Param.SIGN_UP_METHOD, user.getProviderId());
                    firebaseAnalytics.logEvent(FirebaseAnalytics.Event.LOGIN, bundle);

                    if (restartPurchaseFlow) {
                        //makeSamplePackPurchase(user.getUid(), savedSku, savedPackName);
                        acceptLicense(user.getUid(), savedSku, savedPackName);
                    }
                }
                return;
            }

            // Sign in canceled
            if (resultCode == RESULT_CANCELED) {
                return;
            }

            // No network
            if (resultCode == ResultCodes.RESULT_NO_NETWORK) {
                Toast.makeText(context, R.string.network_error, Toast.LENGTH_SHORT).show();
                return;
            }

            // User is not signed in. Maybe just wait for the user to press
            // "sign in" again, or show a message.
        }
        else if (requestCode == RC_PURCHASE) {
            if (mBillingHelper != null)
                mBillingHelper.handleActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public boolean onKeyDown(int keycode, KeyEvent e) {
        switch (keycode) {
            case KeyEvent.KEYCODE_BACK:
                if (rootLayout.findViewById(R.id.privacy_policy_popup) != null){
                    hidePrivacyPolicyPopup();
                    return true;
                }
                if (rootLayout.findViewById(R.id.create_account_popup) != null){
                    hideCreateAccountPrompt();
                    return true;
                }
                if (downloadRef != null && downloadRef.getActiveDownloadTasks().size() > 0){
                    FileDownloadTask task = downloadRef.getActiveDownloadTasks().get(0);
                    if (task != null) {
                        task.removeOnSuccessListener(onSuccessListener);
                        Intent data = new Intent();
                        data.putExtra(FB_STORAGE_REF, downloadRef.toString());
                        data.putExtra(DOWNLOAD_FILENAME, downloadRef.getName());
                        setResult(Activity.RESULT_OK, data);
                    }
                }
                return super.onKeyDown(keycode, e);
            default:
                return super.onKeyDown(keycode, e);
        }
    }

    // Iniitialization
    private BroadcastReceiver br;
    private void setupFolders(){
        // Prepare stoarage directories
        if (Utils.isExternalStorageWritable()){
            samplePackFolder = new File(getExternalFilesDir(null), "Sample Packs");
            if (!samplePackFolder.exists())
                if (!samplePackFolder.mkdir()) Log.e(LOG_TAG, "error creating external files directory");
        } else {
            samplePackFolder = new File(getFilesDir(), "Sample Packs");
            if (!samplePackFolder.exists())
                if (!samplePackFolder.mkdir()) Log.e(LOG_TAG, "error creating internal files directory");
        }

        // Folder to hold cached sample pack descriptions and images
        cacheFolder = getCacheDir();
    }
    private void checkInternetConnection() {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            //should check null because in airplane mode it will be null
            if (netInfo == null || !netInfo.isConnected()) disableApp();

            if (br == null) {
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

                            } else  {
                                disableApp();
                                disposeIAB();
                                //Toast.makeText(getApplicationContext(), "Internet connection is Off", Toast.LENGTH_LONG).show();
                            }
                        }

                    }
                };
            }

            final IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            registerReceiver((BroadcastReceiver) br, intentFilter);
    }
    private void initializeIAB(){
        String base64EncodedPublicKey = RSA_STRING_1 + RSA_STRING_2 + RSA_STRING_3;
        mBillingHelper = new IabHelper(this, base64EncodedPublicKey);
        mBillingHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    // Oh no, there was a problem.
                    Log.e(LOG_TAG, "Problem setting up In-app Billing: " + result);
                }
                // Hooray, IAB is fully set up!
                if (getIntent().hasExtra(SHOW_APP_EXTRAS)) {
                    hideSamplePacks();
                    showAppExtras();
                }
                querySkuDetails();
            }
        });
    }
    private void disposeIAB(){
        // Disposed when finished and when network is disconnected
        if (mBillingHelper != null){
            try {
                mBillingHelper.dispose();
            } catch (IabHelper.IabAsyncInProgressException e){
                e.printStackTrace();
            }
            mBillingHelper = null;
            Log("mBillingHelper disposed");
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
            ((TextView)overlay.findViewById(R.id.no_network_msg_view)).setText(R.string.store_no_network_msg);
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            overlay.setLayoutParams(params);
            rootLayout.addView(overlay);
            findViewById(R.id.bottom_navigation).setVisibility(View.GONE);
            findViewById(R.id.adView).setVisibility(View.GONE);
            if (getSupportActionBar() != null)getSupportActionBar().hide();
        }
    }
    private void enableApp(){
        View overlay = rootLayout.findViewById(R.id.no_network_overlay);
        if (overlay != null)
            rootLayout.removeView(overlay);

        findViewById(R.id.bottom_navigation).setVisibility(View.VISIBLE);
        findViewById(R.id.adView).setVisibility(View.VISIBLE);
        if (getSupportActionBar() != null)getSupportActionBar().show();
    }

    // Store
    private MediaPlayer mediaPlayer;
    private Snackbar snackbar;
    private ArrayList<String> availPacks;
    private ArrayList<String> ownedPacks;
    private HashMap<String, String> parentPacks;
    private PurchasesListAdapter purchasesListAdapter;
    private int storeWindowSize = 5;
    private int storeIndex = 0;
    private boolean purchasesShowing = false;
    private class SamplePackListAdapter extends BaseAdapter {
        private ArrayList<String> names;
        private ArrayList<String> titles;
        private ArrayList<String> genres;
        private ArrayList<String> skus;
        private ArrayList<String> bpms;
        private ArrayList<String> details;
        private ArrayList<String> previewFilenames;
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
            skus = new ArrayList<>();
            bpms = new ArrayList<>();
            details = new ArrayList<>();
            previewFilenames = new ArrayList<>();
        }

        private void addPack(String name){
            names.add(name);
            titles.add(null);
            genres.add(null);
            skus.add(null);
            bpms.add(null);
            details.add(null);
            previewFilenames.add(null);
            notifyDataSetChanged();
        }

        private void addPackDetails(String name){
            File desc = new File(cacheFolder, name + ".txt");
            int index = names.indexOf(name);
            if (desc.exists() && index >= 0) {
                try {
                    BufferedReader br = new BufferedReader(new FileReader(desc));
                    titles.set(index, br.readLine());
                    bpms.set(index, br.readLine());
                    genres.set(index, br.readLine());
                    skus.set(index, br.readLine());
                    previewFilenames.set(index, br.readLine());
                    String line;
                    StringBuilder text = new StringBuilder();
                    while ((line = br.readLine()) != null) {
                        text.append(line);
                        text.append('\n');
                    }
                    details.set(index, text.toString());
                    br.close();
                    notifyDataSetChanged();
                }
                catch (IOException e) {
                    //You'll need to add proper error handling here
                    e.printStackTrace();
                }
            }
        }

        private String getPackDisplayTitle(String packName){
            int index = names.indexOf(packName);
            if (index >= 0){
                return titles.get(index);
            } else {
                return getString(R.string.untitled);
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

            ProgressBar loadingBar = (ProgressBar)convertView.findViewById(R.id.loading_circle_spinner);
            TextView titleView = (TextView)convertView.findViewById(R.id.title_view);
            TextView bpmView = (TextView)convertView.findViewById(R.id.bpm_view);
            TextView genreView = (TextView)convertView.findViewById(R.id.genre_view);
            TextView detailsView = (TextView)convertView.findViewById(R.id.details_view);
            TextView priceView = (TextView)convertView.findViewById(R.id.price_view);
            Button buyButton = (Button)convertView.findViewById(R.id.purchase_button);
            final ImageButton prevButton = (ImageButton)convertView.findViewById(R.id.preview_button);

            if (titles.get(position) != null) {
                loadingBar.setVisibility(View.GONE);

                titleView.setVisibility(View.VISIBLE);
                titleView.setText(titles.get(position));

                bpmView.setVisibility(View.VISIBLE);
                bpmView.setText(bpms.get(position));

                genreView.setVisibility(View.VISIBLE);
                genreView.setText(genres.get(position));

                detailsView.setVisibility(View.VISIBLE);
                detailsView.setText(details.get(position));

                priceView.setVisibility(View.VISIBLE);
                priceView.setText(packPriceList.get(skus.get(position)));

                buyButton.setVisibility(View.VISIBLE);
                if ((ownedPacks != null && ownedPacks.contains(names.get(position))) || BuildConfig.DEBUG){
                    buyButton.setText(R.string.download);
                    buyButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (!isPurchasing)
                                downloadPack(names.get(position));
                            else
                                Toast.makeText(context, "Please wait for transaction to finish before starting a new one", Toast.LENGTH_LONG).show();

                        }
                    });
                } else {
                    buyButton.setText(R.string.buy);
                    buyButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (!isPurchasing)
                                startPurchaseFlow(skus.get(position), names.get(position));
                            else
                                Toast.makeText(context, "Please wait for transaction to finish before starting a new one", Toast.LENGTH_LONG).show();
                        }
                    });
                }

                File image = new File(cacheFolder, names.get(position) + ".png");
                if (image.exists()) {
                    ImageView imageView = (ImageView)convertView.findViewById(R.id.image_view);
                    BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                    Bitmap bitmap = BitmapFactory.decodeFile(image.getAbsolutePath(), bmOptions);
                    //bitmap = Bitmap.createScaledBitmap(bitmap,parent.getWidth(),parent.getHeight(),true);
                    imageView.setImageBitmap(bitmap);
                }

                prevButton.setVisibility(View.VISIBLE);
                prevButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                            previewClick(previewFilenames.get(position), titles.get(position));
                    }
                });
            } else {
                loadingBar.setVisibility(View.VISIBLE);
                titleView.setVisibility(View.GONE);
                bpmView.setVisibility(View.GONE);
                genreView.setVisibility(View.GONE);
                detailsView.setVisibility(View.GONE);
                priceView.setVisibility(View.GONE);
                buyButton.setVisibility(View.GONE);
                prevButton.setVisibility(View.GONE);
            }
            return  convertView;
        }
    }
    private class PurchasesListAdapter extends BaseAdapter {
        private ArrayList<String> names;
        private ArrayList<String> titles;
        private ArrayList<String> orderIds;
        private ArrayList<Long> timeStamps;
        private Context mContext;
        private int resource_id;
        private LayoutInflater mInflater;

        private PurchasesListAdapter(Context context, int resource_id) {
            this.mContext = context;
            this.resource_id = resource_id;
            mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            names = new ArrayList<>();
            titles = new ArrayList<>();
            orderIds = new ArrayList<>();
            timeStamps = new ArrayList<>();
        }

        private void addPack(String packName, String displayName, String orderId, long timeStamp){
            names.add(packName);
            titles.add(displayName);
            orderIds.add(orderId);
            timeStamps.add(timeStamp);
            notifyDataSetChanged();
            Log.d(LOG_TAG, "Owned pack added");
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

            ProgressBar loadingBar = (ProgressBar)convertView.findViewById(R.id.loading_circle_spinner);
            TextView titleView = (TextView)convertView.findViewById(R.id.title_view);
            TextView idView = (TextView)convertView.findViewById(R.id.order_id_view);
            TextView timeView = (TextView)convertView.findViewById(R.id.time_view);
            Button buyButton = (Button)convertView.findViewById(R.id.download_button);

            if (titles.get(position) != null) {
                loadingBar.setVisibility(View.GONE);

                titleView.setVisibility(View.VISIBLE);
                titleView.setText(titles.get(position));

                idView.setVisibility(View.VISIBLE);
                idView.setText(orderIds.get(position));

                timeView.setVisibility(View.VISIBLE);
                timeView.setText(Utils.getTimeAgo(getResources(), timeStamps.get(position)));

                buyButton.setVisibility(View.VISIBLE);
                buyButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        downloadPack(names.get(position));
                    }
                });

                File image = new File(cacheFolder, names.get(position) + ".png");
                if (image.exists()) {
                    ImageView imageView = (ImageView)convertView.findViewById(R.id.image_view);
                    BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                    Bitmap bitmap = BitmapFactory.decodeFile(image.getAbsolutePath(), bmOptions);
                    //bitmap = Bitmap.createScaledBitmap(bitmap,parent.getWidth(),parent.getHeight(),true);
                    imageView.setImageBitmap(bitmap);
                }
            } else {
                loadingBar.setVisibility(View.VISIBLE);
                titleView.setVisibility(View.GONE);
                buyButton.setVisibility(View.GONE);
                idView.setVisibility(View.GONE);
                timeView.setVisibility(View.GONE);
            }
            return  convertView;
        }
    }

    private void LoadStoreData(byte[] indexBytes){
        storeIndex = 0;
        storeWindowSize = 5;
        String indexText;
        try {
            indexText = new String(indexBytes, "UTF-8");
            String[] entries = indexText.split("\n");
            availPacks = new ArrayList<>(entries.length);
            parentPacks = new HashMap<>(entries.length);
            String[] entry;
            for (String x : entries){
                entry = x.split(";");
                availPacks.add(entry[0].trim());
                parentPacks.put(entry[0].trim(), entry[1].trim());

            }
            setupFolders();
            samplePackListAdapter = new SamplePackListAdapter(this, R.layout.store_sample_pack_item);
            ListView packListView = (ListView)findViewById(R.id.sample_pack_listview);
            packListView.setAdapter(samplePackListAdapter);
            packListView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {
                    if (view.getLastVisiblePosition() >= samplePackListAdapter.getCount() - 1)
                        downloadNextInfoSet();
                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

                }
            });

            rootLayout.findViewById(R.id.center_progress_bar).setVisibility(View.GONE);
            downloadNextInfoSet();

        } catch (UnsupportedEncodingException e){
            e.printStackTrace();
            Log.e(LOG_TAG, "Error loading index");
        }
    }
    private void downloadNextInfoSet(){
        // Check if data is already cached.  If not, download
        ArrayList<String> cachedPacks = new ArrayList<>(Arrays.asList(cacheFolder.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                if (filename.contains(".txt")){
                    if (availPacks.contains(filename.replace(".txt", ""))) {
                        Log("Cached pack: " + filename);
                        //return true;
                        return false;
                    } else
                        return false;
                } else
                    return false;
            }
        })));
        for (int i = storeIndex; i < storeIndex + storeWindowSize && i < availPacks.size(); i++){
            final String packName = availPacks.get(i);
            samplePackListAdapter.addPack(packName);
            if (cachedPacks.contains(packName + ".txt")){
                samplePackListAdapter.addPackDetails(packName);
                Log("Added pack details for " + packName);
            }
            else {
                downloadInfo(packName);
            }
        }
        storeIndex += storeWindowSize;
        storeIndex = Math.min(storeIndex, availPacks.size());
    }
    private void downloadInfo(final String packName){
        File desc = new File(cacheFolder, packName + ".txt");
        try {
            desc.createNewFile();
            StorageReference descRef = storage.getReferenceFromUrl("gs://mixmatic-loop-pad.appspot.com/SamplePacks/" + packName + ".txt"); //packFolderRef.child(pack + ".txt");
            descRef.getFile(desc).addOnCompleteListener(new OnCompleteListener<FileDownloadTask.TaskSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<FileDownloadTask.TaskSnapshot> task) {
                    samplePackListAdapter.addPackDetails(packName);
                    File image = new File(cacheFolder, packName + ".png");
                    try {
                        image.createNewFile();
                        StorageReference imageRef = storage.getReferenceFromUrl("gs://mixmatic-loop-pad.appspot.com/SamplePacks/" + packName + ".png"); //packFolderRef.child(pack + ".png");
                        imageRef.getFile(image).addOnCompleteListener(new OnCompleteListener<FileDownloadTask.TaskSnapshot>() {
                            @Override
                            public void onComplete(@NonNull Task<FileDownloadTask.TaskSnapshot> task) {
                                samplePackListAdapter.notifyDataSetChanged();
                                if (purchasesListAdapter != null)
                                    purchasesListAdapter.notifyDataSetChanged();
                            }
                        });
                    } catch (IOException e){
                        e.printStackTrace();
                    }
                }
            });
        } catch (IOException e){
            e.printStackTrace();
        }
    }
    private void LoadPurchaseRec(byte[] purchListBytes){
        // Processs remote purchase record
        String[] recordArray = null;
        try{
            String purchasesTextDL;
            purchasesTextDL = new String(purchListBytes, "UTF-8");
            recordArray = purchasesTextDL.split("\n");
            ownedPacks = new ArrayList<>(recordArray.length);
            purchasesListAdapter = new PurchasesListAdapter(context, R.layout.store_purchase_item);
            ListView purchasesList = (ListView)rootLayout.findViewById(R.id.purchases_listview);
            purchasesList.setAdapter(purchasesListAdapter);
            for (String record : recordArray) {
                String[] details = record.trim().split(";");
                Log(record);
                if (details.length == 4) {
                    ownedPacks.add(details[0].trim());
                    purchasesListAdapter.addPack(details[0], details[1], details[2], Long.valueOf(details[3]));
                }
            }

            Log("ownedPacks = " + ownedPacks.size());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            Log.e(LOG_TAG, "Error reading remote purchase list");
        }

        // Save remote purchase record locally
        if (recordArray != null) {
            File recordFile = new File(getFilesDir(), "purchases.txt");
            try {
                if (recordFile.exists()) recordFile.delete();
                else recordFile.createNewFile();
                FileWriter writer = new FileWriter(recordFile);
                for (String record : recordArray) {
                    writer.append(record);
                    writer.append("\n");
                }
                writer.flush();
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(LOG_TAG, "Error writing local purchase record");
            }
        }
    }
    private void LoadLocalPurchaseRec(){
        Log("Loading Local Purchase Record");
        ownedPacks = new ArrayList<>();
        purchasesListAdapter = new PurchasesListAdapter(context, R.layout.store_purchase_item);
        ListView purchasesList = (ListView)rootLayout.findViewById(R.id.purchases_listview);
        purchasesList.setAdapter(purchasesListAdapter);
        File recordFile = new File(getFilesDir(), "purchases.txt");
        if (recordFile.exists()){
            try {
                BufferedReader reader = new BufferedReader(new FileReader(recordFile));
                String line;
                while ((line = reader.readLine()) != null){
                    String[] details = line.trim().split(";");
                    if (details.length == 4) {
                        ownedPacks.add(details[0].trim());
                        purchasesListAdapter.addPack(details[0], details[1], details[2], Long.valueOf(details[3]));
                    }
                }

                //Log("ownedPacks = " + ownedPacks.size());

            } catch (IOException e) {
                e.printStackTrace();
                Log.e(LOG_TAG, "Error reading local purchase record");
            }
        } else
            Log("No local purchase record found");
    }
    private boolean onNavigation(MenuItem item){
        switch(item.getItemId()){
            case R.id.nav_sample_packs:
                hidePurchases();
                hideAppExtras();
                showSamplePacks();
                return true;
            case R.id.nav_app_extras:
                hidePurchases();
                hideSamplePacks();
                showAppExtras();
                break;
            case R.id.nav_purchases:
                hideSamplePacks();
                hideAppExtras();
                showPurchases();
                return true;
        }
        return true;
    }
    private void showSamplePacks(){
        // Record Firebase event
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, "Sample Packs");
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.VIEW_ITEM_LIST, bundle);

        final ListView sampleList = (ListView)rootLayout.findViewById(R.id.sample_pack_listview);
        if (sampleList.getVisibility() != View.VISIBLE){
            AnimatorSet set = Animations.fadeIn(sampleList, 200, 0);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {
                    sampleList.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd(Animator animator) {

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
    private void hideSamplePacks(){
        final ListView sampleList = (ListView)rootLayout.findViewById(R.id.sample_pack_listview);
        if (sampleList.getVisibility() == View.VISIBLE){
            AnimatorSet set = Animations.fadeOut(sampleList, 200, 0);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {

                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    sampleList.setVisibility(View.GONE);
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
    private void showAppExtras(){
        // Record Firebase event
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, "App Extras");
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.VIEW_ITEM_LIST, bundle);

        final View appExtras = rootLayout.findViewById(R.id.app_extras_layout);
        if (appExtras.getVisibility() != View.VISIBLE){
            AnimatorSet set = Animations.fadeIn(appExtras, 200, 0);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {
                    appExtras.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd(Animator animator) {

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
    private void hideAppExtras(){
        final View view = rootLayout.findViewById(R.id.app_extras_layout);
        if (view.getVisibility() != View.GONE){
            AnimatorSet set = Animations.fadeOut(view, 200, 0);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {

                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    view.setVisibility(View.GONE);
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
    private void showPurchases(){
        final ListView purchasesList = (ListView)rootLayout.findViewById(R.id.purchases_listview);
        final TextView msgTextView = (TextView)rootLayout.findViewById(R.id.no_purchases_textview);
        if (purchasesListAdapter == null || purchasesListAdapter.getCount() == 0){
            if (msgTextView.getVisibility() != View.VISIBLE) {
                purchasesList.setVisibility(View.GONE);
                purchasesShowing = true;
                AnimatorSet set = Animations.fadeIn(msgTextView, 200, 0);
                set.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animator) {
                        msgTextView.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onAnimationEnd(Animator animator) {

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
        else if (purchasesList.getVisibility() != View.VISIBLE){
            purchasesShowing = true;
            msgTextView.setVisibility(View.GONE);
            AnimatorSet set = Animations.fadeIn(purchasesList, 200, 0);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {
                    purchasesList.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd(Animator animator) {

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
    private void hidePurchases(){
        purchasesShowing = false;
        final ListView purchasesList = (ListView)rootLayout.findViewById(R.id.purchases_listview);
        final View msgView = rootLayout.findViewById(R.id.no_purchases_textview);
        if (purchasesList.getVisibility() == View.VISIBLE){
            AnimatorSet set = Animations.fadeOut(purchasesList, 200, 0);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {

                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    purchasesList.setVisibility(View.GONE);
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
        else if (msgView.getVisibility() == View.VISIBLE){
            AnimatorSet set = Animations.fadeOut(msgView, 200, 0);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {

                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    msgView.setVisibility(View.GONE);
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
    private void previewClick(String prevFilename, final String packTitle){
        // Dismiss snackbar if necessary
        if (snackbar != null && snackbar.isShown())
            snackbar.dismiss();

        // Prepare and show snackbar
        snackbar = Snackbar.make(findViewById(R.id.coordinator_layout), "Buffering", Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(R.string.action_stop, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mediaPlayer != null ) {
                    if (mediaPlayer.isPlaying())
                        mediaPlayer.stop();
                    mediaPlayer.release();
                    mediaPlayer = null;
                }
                snackbar.dismiss();
            }
        });
        snackbar.setCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar snackbar, int event) {
                if (mediaPlayer != null ) {
                    if (mediaPlayer.isPlaying())
                        mediaPlayer.stop();
                    mediaPlayer.release();
                    mediaPlayer = null;
                }
                super.onDismissed(snackbar, event);
            }
        });
        snackbar.show();

        // Prepare and start streaming
        StorageReference prevRef = storage.getReferenceFromUrl("gs://mixmatic-loop-pad.appspot.com/SamplePacks/" + prevFilename);
        prevRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
            @Override
            public void onSuccess(Uri uri) {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mediaPlayer) {
                        snackbar.dismiss();
                    }
                });
                try {
                    mediaPlayer.setDataSource(uri.toString());
                    mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mediaPlayer) {
                            snackbar.setText(packTitle);
                            mediaPlayer.start();
                        }
                    });
                    mediaPlayer.prepareAsync();
                } catch (IOException e){
                    e.printStackTrace();
                }
            }
        });
    }

    // Firebase user account
    private static int RC_SIGN_IN = 1001;
    private void startSignInFlow(){
        startActivityForResult(
                AuthUI.getInstance()
                        .createSignInIntentBuilder()
                        .setProviders(Arrays.asList(new AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build(),
                                new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build(),
                                new AuthUI.IdpConfig.Builder(AuthUI.FACEBOOK_PROVIDER).build()))
                        .setIsSmartLockEnabled(!BuildConfig.DEBUG)
                        .setTheme(R.style.FirebaseUITheme)
                        .build(),
                RC_SIGN_IN);
    }
    private void showCreateAccountPrompt(){
        final View layout = getLayoutInflater().inflate(R.layout.prompt_create_account, null);

        // Prepare popup window
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.setLayoutParams(params);
        layout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        Button yesButton = (Button)layout.findViewById(R.id.yes_button);
        yesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AnimatorSet set = Animations.slideOutDown(layout, 200, 0, rootLayout.getHeight() / 3);
                set.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        rootLayout.removeView(layout);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {

                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {

                    }
                });
                set.start();
                startSignInFlow();
            }
        });

        Button noButton = (Button)layout.findViewById(R.id.no_button);
        noButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CheckBox checkBox = (CheckBox)layout.findViewById(R.id.checkbox);
                SharedPreferences.Editor editor = getSharedPreferences(ACCOUNT_PREFS, MODE_PRIVATE).edit();
                editor.putBoolean(SHOW_CREATE_ACCOUNT_PROMPT, !checkBox.isChecked());
                editor.apply();
                AnimatorSet set = Animations.slideOutDown(layout, 200, 0, rootLayout.getHeight() / 3);
                set.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        rootLayout.removeView(layout);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {

                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {

                    }
                });
                set.start();
                if (restartPurchaseFlow){
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        //makeSamplePackPurchase(user.getUid(), savedSku, savedPackName);
                        acceptLicense(user.getUid(), savedSku, savedPackName);
                    }
                }
            }
        });

        rootLayout.addView(layout);
        Animations.slideUp(layout, 200, 0, rootLayout.getHeight() / 3).start();
    }
    private void hideCreateAccountPrompt(){
        final View layout = rootLayout.findViewById(R.id.create_account_popup);
        if (layout != null) {
            AnimatorSet set = Animations.slideOutDown(layout, 200, 0, rootLayout.getHeight() / 3);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    rootLayout.removeView(layout);
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
    public void ShowPrivacyPolicyPopup(View v){
        LayoutInflater inflater = getLayoutInflater();
        View popup = inflater.inflate(R.layout.privacy_policy_popup, null);

        WebView webView = (WebView)popup.findViewById(R.id.webView);
        webView.loadUrl(getString(R.string.privacy_policy_url));

        Button button = (Button)popup.findViewById(R.id.yes_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hidePrivacyPolicyPopup();
            }
        });

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.addRule(RelativeLayout.BELOW, R.id.my_toolbar);
        params.addRule(RelativeLayout.ABOVE, R.id.adView);
        rootLayout.addView(popup, params);
        Animations.fadeIn(popup, 200, 0).start();
    }
    private void hidePrivacyPolicyPopup(){
        final View popup = rootLayout.findViewById(R.id.privacy_policy_popup);
        if (popup != null) {
            AnimatorSet set = Animations.fadeOut(popup, 200, 0);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {

                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    rootLayout.removeView(popup);
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
    public void SignInClick(View v){
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.isAnonymous())
            startSignInFlow();
        else
            signOut();
    }
    private void signOut(){
        SharedPreferences.Editor editor = getSharedPreferences(ACCOUNT_PREFS, MODE_PRIVATE).edit();
        editor.putBoolean(AUTO_SIGN_IN, false);
        editor.apply();
        AuthUI.getInstance()
                .signOut(this)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    public void onComplete(@NonNull Task<Void> task) {
                        // user is now signed out
                        updateSigninButton();
                        Toast.makeText(context, R.string.sign_out_success, Toast.LENGTH_SHORT).show();
                    }
                });
    }
    private void handleAuthStateChange(FirebaseUser user){
        if (user != null) {
            // User is signed in
            Log("onAuthStateChanged:signed_in:" + user.getUid());

            // Setup storage references and download sample pack index
            packFolderRef = storage.getReferenceFromUrl("gs://mixmatic-loop-pad.appspot.com/SamplePacks");
            StorageReference indexRef = packFolderRef.child("index.txt");

            final long ONE_MEGABYTE = 1024 * 1024;
            indexRef.getBytes(ONE_MEGABYTE).addOnSuccessListener(new OnSuccessListener<byte[]>() {
                @Override
                public void onSuccess(byte[] bytes) {
                    LoadStoreData(bytes);
                    // Record Firebase event
                    Bundle bundle = new Bundle();
                    bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, "Sample Packs");
                    firebaseAnalytics.logEvent(FirebaseAnalytics.Event.VIEW_ITEM_LIST, bundle);
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception exception) {
                    // Handle any errors
                    Log.e(LOG_TAG, "Index download error");
                    Snackbar.make(rootLayout.findViewById(R.id.coordinator_layout), R.string.index_download_error, Snackbar.LENGTH_LONG);
                }
            });

            // Download purchase list
            if (!user.isAnonymous()) {
                Log("Downloading purchase record");
                StorageReference purchasesRef = storage.getReferenceFromUrl("gs://mixmatic-loop-pad.appspot.com/user/" + user.getUid() + "/purchases.txt");
                purchasesRef.getBytes(ONE_MEGABYTE).addOnSuccessListener(new OnSuccessListener<byte[]>() {
                    @Override
                    public void onSuccess(byte[] bytes) {
                        Log("Purchase record downloaded");
                        LoadPurchaseRec(bytes);
                        if (purchasesShowing) showPurchases();
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log("Error downloading purchase record: " + e.getMessage());
                        if (purchasesShowing) showPurchases();
                    }
                });
            } else {
                LoadLocalPurchaseRec();
                if (purchasesShowing) showPurchases();
            }

        } else {
            // User is signed out
            Log("onAuthStateChanged:signed_out");
            mAuth.signInAnonymously();
            LoadLocalPurchaseRec();
            if (purchasesShowing) showPurchases();
        }
    }
    private void updateSigninButton(){
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null){
            TextView button = (TextView)actionBar.getCustomView().findViewById(R.id.sign_in_button);
            if (mAuth.getCurrentUser() == null || mAuth.getCurrentUser().isAnonymous()){
                button.setText(R.string.sign_in);
                button.setBackgroundResource(R.drawable.rounded_corner_2dp_accent);
                button.setTextColor(ResourcesCompat.getColor(getResources(), R.color.text_primary_light, null));
            }
            else {
                button.setText(R.string.sign_out);
                button.setBackgroundResource(R.drawable.rounded_corner_2dp_primary);
                button.setTextColor(ResourcesCompat.getColor(getResources(), R.color.primary_accent, null));
            }
        }
    }

    // Purchase and download
    public static final String FB_STORAGE_REF = "FB_STORAGE_REF";
    public static final String DOWNLOAD_FILENAME = "DOWNLOAD_FILENAME";
    private IabHelper mBillingHelper;
    private static String RSA_STRING_1 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAjcQ7YSSmv5GSS3FrQ801508P/r5laGtv7GBG2Ax9ql6ZAJZI6UPrJIvN9gXjoRBnH",
            RSA_STRING_2 = "OIphIg9HycJRxBwGfgcpEQ3F47uWJ/UvmPeQ3cVffFKIb/cAUqCS4puEtcDL2yDXoKjagsJNBjbRWz6tqDvzH5BtvdYoy4QUf8NqH8wd3/2R/m3PAVIr+lRlUAc1Dj2y40uOEdluDW+i9kbkMD8vrLKr+DGnB7JrKFAPaqxBNTeogv",
            RSA_STRING_3 = "0vGNOWwJd3Tgx7VDm825Op/vyG9VQSM7W53TsyJE8NdwP8Q59B/WRlcsr+tHCyoQcjscrgVegiOyME1DfEUrQk/SPzr5AlCqa2AZ//wIDAQAB";
    private static int RC_PURCHASE = 1002;
    private static final String SKU_ONE_DOLLAR_PACK = "one_dollar_pack";
    private static final String SKU_TWO_DOLLAR_PACK = "two_dollar_pack";
    private static final String SKU_THREE_DOLLAR_PACK = "three_dollar_pack";
    private static final String SKU_FOUR_DOLLAR_PACK = "four_dollar_pack";
    private static final String SKU_FIVE_DOLLAR_PACK = "five_dollar_pack";
    private static final String SKU_REMOVE_ADS = "remove_ads";
    private static HashMap<String, String> packPriceList;
    private String savedSku, savedPackName;
    private boolean restartPurchaseFlow = false;
    private StorageReference downloadRef;

    // Sample pack purchases
    private static final String LICENSE_ACCEPTED = "LICENSE_ACCEPTED";
    private OnSuccessListener<FileDownloadTask.TaskSnapshot> onSuccessListener;
    private boolean isPurchasing = false;
    private void checkForUnconsumedPurchases(){
        try {
            mBillingHelper.queryInventoryAsync(new IabHelper.QueryInventoryFinishedListener() {
                @Override
                public void onQueryInventoryFinished(IabResult result, Inventory inv) {
                    if (!result.isSuccess()) return;

                        if (inv.hasPurchase(SKU_TWO_DOLLAR_PACK)) {
                            try {
                                mBillingHelper.consumeAsync(inv.getPurchase(SKU_TWO_DOLLAR_PACK), new IabHelper.OnConsumeFinishedListener() {
                                    @Override
                                    public void onConsumeFinished(Purchase purchase, IabResult result) {
                                        Log.i(LOG_TAG, "Consumed purchase on start");
                                    }
                                });
                            } catch (IabHelper.IabAsyncInProgressException e) {
                                e.printStackTrace();
                            }
                        }
                    if (inv.hasPurchase(SKU_REMOVE_ADS)){
                        ((Button)rootLayout.findViewById(R.id.remove_ads_purchase_button)).setText(R.string.owned);
                        rootLayout.findViewById(R.id.remove_ads_purchase_button).setEnabled(false);
                    }

                }
            });
        } catch (IabHelper.IabAsyncInProgressException e) { e.printStackTrace(); }
    }
    private void downloadPack(final String name){
        isPurchasing = true;
        final Snackbar snackbar = Snackbar.make(findViewById(R.id.coordinator_layout), getString(R.string.downloading, 0), Snackbar.LENGTH_INDEFINITE);
        final WeakReference<Snackbar> snackbarReference = new WeakReference<>(snackbar);
        snackbar.setAction(R.string.hide, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                snackbar.dismiss();
            }
        });
        snackbar.show();
        final File downloadFile = new File(samplePackFolder, name + ".zip");
        if (downloadFile.exists()) downloadFile.delete();
        try {
            downloadFile.createNewFile();
            onSuccessListener = new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                    if (snackbarReference.get() != null) {
                        snackbar.setText(R.string.decompressing);
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Utils.unzip(downloadFile, new File(samplePackFolder, name));
                                    downloadFile.delete();
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            isPurchasing = false;
                                            snackbar.dismiss();
                                            Snackbar.make(findViewById(R.id.coordinator_layout), R.string.pack_install_complete, Snackbar.LENGTH_SHORT).show();
                                        }
                                    });
                                } catch (IOException e) {
                                    isPurchasing = false;
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            snackbar.dismiss();
                                            Snackbar.make(findViewById(R.id.coordinator_layout), R.string.unzip_error, Snackbar.LENGTH_SHORT).show();
                                        }
                                    });
                                    e.printStackTrace();
                                }

                            }
                        }).start();
                    }
                }
            };
            downloadRef = packFolderRef.child(name + ".zip");
            downloadRef.getFile(downloadFile)
                    .addOnProgressListener(new OnProgressListener<FileDownloadTask.TaskSnapshot>() {
                @Override
                public void onProgress(FileDownloadTask.TaskSnapshot taskSnapshot) {
                    snackbar.setText(getString(R.string.downloading, taskSnapshot.getBytesTransferred() * 100 / taskSnapshot.getTotalByteCount()));
                }
            }).addOnSuccessListener(onSuccessListener)
                    .addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    isPurchasing = false;
                    if (snackbarReference.get() != null) {
                        snackbar.dismiss();
                        Snackbar.make(findViewById(R.id.coordinator_layout), R.string.download_error, Snackbar.LENGTH_SHORT).show();
                    }
                }
            });
        } catch (IOException e){
            isPurchasing = false;
            snackbar.dismiss();
            e.printStackTrace();
            Toast.makeText(context, R.string.download_error, Toast.LENGTH_SHORT).show();
        }
    }
    private void querySkuDetails(){
        // Access IAB SKU details
        ArrayList<String> additionalSkuList = new ArrayList<>();
        additionalSkuList.add(SKU_ONE_DOLLAR_PACK);
        additionalSkuList.add(SKU_TWO_DOLLAR_PACK);
        additionalSkuList.add(SKU_THREE_DOLLAR_PACK);
        additionalSkuList.add(SKU_FOUR_DOLLAR_PACK);
        additionalSkuList.add(SKU_FIVE_DOLLAR_PACK);
        additionalSkuList.add(SKU_REMOVE_ADS);
        try {
            mBillingHelper.queryInventoryAsync(true, additionalSkuList, null, new IabHelper.QueryInventoryFinishedListener() {
                @Override
                public void onQueryInventoryFinished(IabResult result, Inventory inv) {
                    if (result.isFailure()) {
                        // handle error
                        Toast.makeText(context, R.string.store_error_msg, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    packPriceList.put(SKU_ONE_DOLLAR_PACK, inv.getSkuDetails(SKU_ONE_DOLLAR_PACK).getPrice());
                    packPriceList.put(SKU_TWO_DOLLAR_PACK, inv.getSkuDetails(SKU_TWO_DOLLAR_PACK).getPrice());
                    packPriceList.put(SKU_THREE_DOLLAR_PACK, inv.getSkuDetails(SKU_THREE_DOLLAR_PACK).getPrice());
                    packPriceList.put(SKU_FOUR_DOLLAR_PACK, inv.getSkuDetails(SKU_FOUR_DOLLAR_PACK).getPrice());
                    packPriceList.put(SKU_FIVE_DOLLAR_PACK, inv.getSkuDetails(SKU_FIVE_DOLLAR_PACK).getPrice());
                    if (samplePackListAdapter != null) samplePackListAdapter.notifyDataSetChanged();

                    ((TextView)rootLayout.findViewById(R.id.remove_ads_price_view)).setText(inv.getSkuDetails(SKU_REMOVE_ADS).getPrice());
                    ((TextView)rootLayout.findViewById(R.id.remove_ads_details_view)).setText(inv.getSkuDetails(SKU_REMOVE_ADS).getDescription());
                    ((TextView)rootLayout.findViewById(R.id.remove_ads_title_view)).setText(inv.getSkuDetails(SKU_REMOVE_ADS).getTitle());
                    ((ImageView)rootLayout.findViewById(R.id.remove_ads_image_view)).setImageResource(R.drawable.ic_no_ads);

                    checkForUnconsumedPurchases();

                }
            });
        } catch (IabHelper.IabAsyncInProgressException e) {
            e.printStackTrace();
        }
    }
    private void startPurchaseFlow(String sku, final String packName){
        FirebaseUser user = mAuth.getCurrentUser();
        SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS, MODE_PRIVATE);
        if (user == null || (user.isAnonymous()) && prefs.getBoolean(SHOW_CREATE_ACCOUNT_PROMPT, true)) {
            // Prompt to create account
            savedSku = sku;
            savedPackName = packName;
            restartPurchaseFlow = true;
            showCreateAccountPrompt();

        } else {
            //makeSamplePackPurchase(user.getUid(), sku, packName);
            acceptLicense(user.getUid(), sku, packName);
        }
    }
    private void acceptLicense(final String uid, final String sku, final String packName){
        if (!appPrefs.getBoolean(LICENSE_ACCEPTED, false)) {
            final View layout = getLayoutInflater().inflate(R.layout.license_agreement_popup, null);

            // Prepare popup window
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            layout.setLayoutParams(params);
            layout.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });

            Button yesButton = (Button) layout.findViewById(R.id.yes_button);
            yesButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AnimatorSet set = Animations.slideOutDown(layout, 200, 0, rootLayout.getHeight() / 3);
                    set.addListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animation) {

                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            rootLayout.removeView(layout);
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {

                        }

                        @Override
                        public void onAnimationRepeat(Animator animation) {

                        }
                    });
                    set.start();
                    SharedPreferences.Editor editor = appPrefs.edit();
                    editor.putBoolean(LICENSE_ACCEPTED, true);
                    editor.apply();
                    makeSamplePackPurchase(uid, sku, packName);
                }
            });

            Button noButton = (Button) layout.findViewById(R.id.no_button);
            noButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AnimatorSet set = Animations.slideOutDown(layout, 200, 0, rootLayout.getHeight() / 3);
                    set.addListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animation) {

                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            rootLayout.removeView(layout);
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
            });

            rootLayout.addView(layout);
            Animations.slideUp(layout, 200, 0, rootLayout.getHeight() / 3).start();
        }
        else {
            makeSamplePackPurchase(uid, sku, packName);
        }
    }
    private void makeSamplePackPurchase(String uid, String sku, final String packName){
        restartPurchaseFlow = false;
        isPurchasing = true;
        try {
            mBillingHelper.launchPurchaseFlow(this, sku, RC_PURCHASE,
                    new IabHelper.OnIabPurchaseFinishedListener() {
                        @Override
                        public void onIabPurchaseFinished(IabResult result, Purchase info) {
                            if (result.isFailure()) {
                                isPurchasing = false;
                                Log.e(LOG_TAG, "Error purchasing: " + result);
                                Toast.makeText(context, getString(R.string.purchase_error, result.getMessage()), Toast.LENGTH_LONG).show();
                                return;
                            }

                            Log.i(LOG_TAG, "Purchase complete");
                            // Save record of purchase
                            updatePurchaseRecord(packName, info);

                            // Record Firebase event for purchase reporting
                            Bundle bundle = new Bundle();
                            bundle.putString(FirebaseAnalytics.Param.ITEM_ID, packName);
                            bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, parentPacks.get(packName));
                            firebaseAnalytics.logEvent(SAMPLE_PACK_PURCHASE, bundle);

                            // Consume purchase
                            try {
                                mBillingHelper.consumeAsync(info, new IabHelper.OnConsumeFinishedListener() {
                                    @Override
                                    public void onConsumeFinished(Purchase purchase, IabResult result) {
                                        if (! result.isSuccess()) {
                                            isPurchasing = false;
                                            return;
                                        }
                                        // Download pack
                                        Log.i(LOG_TAG, "Purchase consumed");
                                        downloadPack(packName);
                                    }
                                });
                            } catch (IabHelper.IabAsyncInProgressException e){
                                isPurchasing = false;
                                e.printStackTrace();
                            }

                        }
                    }, uid);
        } catch (IabHelper.IabAsyncInProgressException e){
            isPurchasing = false;
            e.printStackTrace();
        }
    }
    private void updatePurchaseRecord(String packName, Purchase info){
        File recordFile = new File(getFilesDir(), "purchases.txt");
        try {
            // Write local record
            // packName;orderId;purchaseTime
            if (!recordFile.exists()) {
                recordFile.createNewFile();
            }
            FileWriter writer = new FileWriter(recordFile, true);
            writer.append(System.getProperty("line.separator"));
            writer.append(packName);
            writer.append(";");
            writer.append(samplePackListAdapter.getPackDisplayTitle(packName));
            writer.append(";");
            writer.append(info.getOrderId());
            writer.append(";");
            writer.append(String.valueOf(info.getPurchaseTime()));
            writer.flush();
            writer.close();

            // Upload record to Firebase
            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null && !user.isAnonymous()){
                StorageReference purchasesRef = storage.getReferenceFromUrl("gs://mixmatic-loop-pad.appspot.com/user/" + user.getUid() + "/purchases.txt");
                purchasesRef.putFile(Uri.fromFile(recordFile)).addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                        Log("Purchase record uploaded");
                    }
                });
            }
            LoadLocalPurchaseRec();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // App Extra Purchases
    private void makePurchase(String sku){
        try {
            mBillingHelper.launchPurchaseFlow(this, sku, RC_PURCHASE,
                    new IabHelper.OnIabPurchaseFinishedListener() {
                        @Override
                        public void onIabPurchaseFinished(IabResult result, Purchase info) {
                            if (result.isFailure()) {
                                Log.e(LOG_TAG, "Error purchasing: " + result);
                                Toast.makeText(context, getString(R.string.purchase_error, result.getMessage()), Toast.LENGTH_LONG).show();
                                return;
                            }

                            switch (info.getSku()) {
                                case SKU_REMOVE_ADS:
                                    SharedPreferences.Editor prefEditor = appPrefs.edit();
                                    prefEditor.putBoolean(LaunchPadActivity.SHOW_ADS, false);
                                    prefEditor.apply();
                                    Log.i(LOG_TAG, "Purchase complete");
                                    break;
                            }

                        }
                    });
        } catch (IabHelper.IabAsyncInProgressException e){
            e.printStackTrace();
        }
    }
    public void BuyRemoveAdsClick(View v){
        if (appPrefs.getBoolean(LaunchPadActivity.SHOW_ADS, true))
            makePurchase(SKU_REMOVE_ADS);
    }

    // Utils
    private void Log(String msg){
        if (BuildConfig.DEBUG)
            Log.d(LOG_TAG, msg);
    }
}
