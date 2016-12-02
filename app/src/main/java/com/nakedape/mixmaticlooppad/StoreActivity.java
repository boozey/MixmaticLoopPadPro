package com.nakedape.mixmaticlooppad;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.ui.ResultCodes;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import IABUtils.IabHelper;
import IABUtils.IabResult;
import IABUtils.Inventory;
import IABUtils.Purchase;

public class StoreActivity extends Activity {

    public final static String ACCOUNT_PREFS = "ACCOUNT_PREFS";
    public final static String AUTO_SIGNIN = "AUTO_SIGNIN";
    private final static String SHOW_CREATE_ACCOUNT_PROMPT = "SHOW_CREATE_ACCOUNT_PROMPT";
    private final String LOG_TAG = "StoreActivity";

    // Firebase
    private FirebaseStorage storage = FirebaseStorage.getInstance();
    private StorageReference packFolderRef;
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener authStateListener;

    private Context context;
    private RelativeLayout rootLayout;
    private File cacheFolder, samplePackFolder;
    private SamplePackListAdapter samplePackListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_store);
        context = this;
        rootLayout = (RelativeLayout)findViewById(R.id.rootLayout);

        // Initialize price list with default values
        packPriceList = new HashMap<>();
        packPriceList.put(SKU_TWO_DOLLAR_PACK, "$2.00");

        // In-app billing init
        String base64EncodedPublicKey = RSA_STRING_1 + RSA_STRING_2 + RSA_STRING_3;
        mBillingHelper = new IabHelper(this, base64EncodedPublicKey);
        mBillingHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    // Oh no, there was a problem.
                    Log.d(LOG_TAG, "Problem setting up In-app Billing: " + result);
                }
                // Hooray, IAB is fully set up!
                querySkuDetails();
            }
        });

        // Setup Firebase authentication
        mAuth = FirebaseAuth.getInstance();
        authStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    // User is signed in
                    Log.d(LOG_TAG, "onAuthStateChanged:signed_in:" + user.getUid());

                    // Setup storage references and download sample pack index
                    packFolderRef = storage.getReferenceFromUrl("gs://mixmatic-loop-pad.appspot.com/SamplePacks");
                    StorageReference indexRef = packFolderRef.child("index.txt");

                    final long ONE_MEGABYTE = 1024 * 1024;
                    indexRef.getBytes(ONE_MEGABYTE).addOnSuccessListener(new OnSuccessListener<byte[]>() {
                        @Override
                        public void onSuccess(byte[] bytes) {
                            Log.d(LOG_TAG, "Downloaded index, size = " + bytes.length);
                            LoadStoreData(bytes);
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception exception) {
                            // Handle any errors
                            Log.e(LOG_TAG, "Index download error");
                        }
                    });

                    // Download purchase list
                    if (!user.isAnonymous()) {
                        StorageReference purchasesRef = storage.getReferenceFromUrl("gs://mixmatic-loop-pad.appspot.com/user/" + user.getUid() + "/purchases.txt");
                        purchasesRef.getBytes(ONE_MEGABYTE).addOnSuccessListener(new OnSuccessListener<byte[]>() {
                            @Override
                            public void onSuccess(byte[] bytes) {
                                LoadPurchaseRec(bytes);
                            }
                        });
                    } else {
                        LoadLocalPurchaseRec();
                    }

                } else {
                    // User is signed out
                    Log.d(LOG_TAG, "onAuthStateChanged:signed_out");
                }
            }
        };

        // If user is not signed in and has not signed in before, sign-in anon
        if (mAuth.getCurrentUser() == null) {
            SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS, MODE_PRIVATE);
            if (prefs.getBoolean(AUTO_SIGNIN, false)){
                startSignInFlow();
            } else {
                mAuth.signInAnonymously()
                        .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                Log.d(LOG_TAG, "signInAnonymously:onComplete:" + task.isSuccessful());

                                // If sign in fails, display a message to the user. If sign in succeeds
                                // the auth state listener will be notified and logic to handle the
                                // signed in user can be handled in the listener.
                                if (!task.isSuccessful()) {
                                    Log.w(LOG_TAG, "signInAnonymously", task.getException());
                                    Toast.makeText(context, "Authentication failed.",
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
            }
        }

    }

    @Override
    public void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(authStateListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (authStateListener != null) {
            mAuth.removeAuthStateListener(authStateListener);
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
                Toast.makeText(context, "Sign-in succeeded", Toast.LENGTH_SHORT).show();
                SharedPreferences.Editor editor = getSharedPreferences(ACCOUNT_PREFS, MODE_PRIVATE).edit();
                editor.putBoolean(AUTO_SIGNIN, true);
                editor.apply();

                if (restartPurchaseFlow) {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null)
                        makePurchase(user.getUid(), savedSku, savedPackName, savedStatusBar);
                }
                return;
            }

            // Sign in canceled
            if (resultCode == RESULT_CANCELED) {
                return;
            }

            // No network
            if (resultCode == ResultCodes.RESULT_NO_NETWORK) {
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

    // Store
    private ArrayList<String> availPacks;
    private ArrayList<String> ownedPacks;
    private int storeWindowSize = 5;
    private int storeIndex = 0;
    private void LoadStoreData(byte[] indexBytes){
        storeIndex = 0;
        storeWindowSize = 5;
        String indexText;
        try {
            indexText = new String(indexBytes, "UTF-8");
            String[] strings = indexText.split("\n");
            availPacks = new ArrayList<>(strings.length);
            for (String s : strings){
                availPacks.add(s.trim());
                Log.d(LOG_TAG, s);
            }
            setupFolders();
            samplePackListAdapter = new SamplePackListAdapter(this, R.layout.store_sample_pack_item);
            ListView packListView = (ListView)findViewById(R.id.store_item_list);
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
                        Log.d(LOG_TAG, "Cached pack: " + filename);
                        return true;
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
            }
            else {
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
        }
        storeIndex += storeWindowSize;
        storeIndex = Math.min(storeIndex, availPacks.size());
    }
    private void LoadPurchaseRec(byte[] purchListBytes){
        // Processs remote purchase record
        try{
            String purchasesTextDL;
            purchasesTextDL = new String(purchListBytes, "UTF-8");
            String[] packNameArray = purchasesTextDL.split("\n");
            ownedPacks = new ArrayList<>(packNameArray.length);
            for (String s : packNameArray)
                ownedPacks.add(s.trim());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            Log.e(LOG_TAG, "Error reading remote purchase list");
        }

        // Save remote purchase record locally
        File recordFile = new File(getFilesDir(), "purchases.txt");
        try {
            if (recordFile.exists()) recordFile.delete();
            else recordFile.createNewFile();
            FileWriter writer = new FileWriter(recordFile);
            for (String packName : ownedPacks) {
                writer.append(packName + "\n");
            }
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(LOG_TAG, "Error writing local purchase record");
        }

    }
    private void LoadLocalPurchaseRec(){
        ownedPacks = new ArrayList<>();
        File recordFile = new File(getFilesDir(), "purchases.txt");
        if (recordFile.exists()){
            try {
                BufferedReader reader = new BufferedReader(new FileReader(recordFile));
                String line;
                while ((line = reader.readLine()) != null){
                    ownedPacks.add(line.trim());
                }

            } catch (IOException e) {
                e.printStackTrace();
                Log.e(LOG_TAG, "Error reading local purchase record");
            }
        }
    }
    private class SamplePackListAdapter extends BaseAdapter {
        private ArrayList<String> names;
        private ArrayList<String> titles;
        private ArrayList<String> genres;
        private ArrayList<String> skus;
        private ArrayList<String> bpms;
        private ArrayList<String> details;
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
        }

        private void addPack(String name){
            names.add(name);
            titles.add(null);
            genres.add(null);
            skus.add(null);
            bpms.add(null);
            details.add(null);
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

                final View statusBar = convertView.findViewById(R.id.status_bar_linear_layout);
                buyButton.setVisibility(View.VISIBLE);
                if ((ownedPacks != null && ownedPacks.contains(names.get(position))) || BuildConfig.DEBUG){
                    buyButton.setText(R.string.download);
                    buyButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            downloadPack(statusBar, names.get(position));
                        }
                    });
                } else {
                    buyButton.setText(R.string.buy);
                    buyButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            startPurchaseFlow(skus.get(position), statusBar, names.get(position));
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
            } else {
                loadingBar.setVisibility(View.VISIBLE);
                titleView.setVisibility(View.GONE);
                bpmView.setVisibility(View.GONE);
                genreView.setVisibility(View.GONE);
                detailsView.setVisibility(View.GONE);
                priceView.setVisibility(View.GONE);
                buyButton.setVisibility(View.GONE);
            }
            return  convertView;
        }
    }

    // Tab tray
    private boolean isTrayShowing;
    public void TrayTabClick(View v){
        if (isTrayShowing) {
            hideTabTray();
        }
        else showTabTray();
    }
    private void showTabTray(){
        if (!isTrayShowing) {
            final LinearLayout tabTray = (LinearLayout)findViewById(R.id.tab_tray);

            tabTray.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hideTabTray();
                }
            });

            // Start the animation
            AnimatorSet set = new AnimatorSet();
            ObjectAnimator slideRight = ObjectAnimator.ofFloat(tabTray, "TranslationX", -tabTray.findViewById(R.id.tray).getWidth(), 0);
            set.setInterpolator(new AccelerateDecelerateInterpolator());
            set.play(slideRight);
            set.setTarget(tabTray);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    isTrayShowing = true;
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
    private void hideTabTray(){
        if (isTrayShowing) {
            LinearLayout tabTray = (LinearLayout) findViewById(R.id.tab_tray);

            // Start the animation
            AnimatorSet set = new AnimatorSet();
            ObjectAnimator slideRight = ObjectAnimator.ofFloat(tabTray, "TranslationX", 0, -tabTray.findViewById(R.id.tray).getWidth());
            set.setInterpolator(new AccelerateDecelerateInterpolator());
            set.play(slideRight);
            set.setTarget(tabTray);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    isTrayShowing = false;
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

    // Firebase user account
    private static int RC_SIGN_IN = 1001;
    private void startSignInFlow(){
        startActivityForResult(
                AuthUI.getInstance()
                        .createSignInIntentBuilder()
                        .setProviders(Arrays.asList(new AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build(),
                                new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build()))
                        .setIsSmartLockEnabled(!BuildConfig.DEBUG)
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
                    if (user != null)
                        makePurchase(user.getUid(), savedSku, savedPackName, savedStatusBar);
                }
            }
        });

        rootLayout.addView(layout);
        Animations.slideUp(layout, 200, 0, rootLayout.getHeight() / 3).start();
    }

    // Purchase and download
    private IabHelper mBillingHelper;
    private static String RSA_STRING_1 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAjcQ7YSSmv5GSS3FrQ801508P/r5laGtv7GBG2Ax9ql6ZAJZI6UPrJIvN9gXjoRBnH",
            RSA_STRING_2 = "OIphIg9HycJRxBwGfgcpEQ3F47uWJ/UvmPeQ3cVffFKIb/cAUqCS4puEtcDL2yDXoKjagsJNBjbRWz6tqDvzH5BtvdYoy4QUf8NqH8wd3/2R/m3PAVIr+lRlUAc1Dj2y40uOEdluDW+i9kbkMD8vrLKr+DGnB7JrKFAPaqxBNTeogv",
            RSA_STRING_3 = "0vGNOWwJd3Tgx7VDm825Op/vyG9VQSM7W53TsyJE8NdwP8Q59B/WRlcsr+tHCyoQcjscrgVegiOyME1DfEUrQk/SPzr5AlCqa2AZ//wIDAQAB";
    private static int RC_PURCHASE = 1002;
    private static String SKU_TWO_DOLLAR_PACK = "two_dollar_pack";
    private static HashMap<String, String> packPriceList;
    private String savedSku, savedPackName;
    private View savedStatusBar;
    private boolean restartPurchaseFlow = false;

    private void checkForUnconsumedPurchases(){
        try {
            mBillingHelper.queryInventoryAsync(new IabHelper.QueryInventoryFinishedListener() {
                @Override
                public void onQueryInventoryFinished(IabResult result, Inventory inv) {
                    if (inv.hasPurchase(SKU_TWO_DOLLAR_PACK))
                        try {
                            mBillingHelper.consumeAsync(inv.getPurchase(SKU_TWO_DOLLAR_PACK), new IabHelper.OnConsumeFinishedListener() {
                                @Override
                                public void onConsumeFinished(Purchase purchase, IabResult result) {
                                    Log.i(LOG_TAG, "Consumed purchase on start");
                                }
                            });
                        } catch (IabHelper.IabAsyncInProgressException e) { e.printStackTrace(); }
                }
            });
        } catch (IabHelper.IabAsyncInProgressException e) { e.printStackTrace(); }
    }
    private void downloadPack(final View statusBar, final String name){
        //Toast.makeText(context, "Downloading sample pack ...", Toast.LENGTH_SHORT).show();
        statusBar.setVisibility(View.VISIBLE);

        TextView statusView = (TextView)statusBar.findViewById(R.id.status_textview);
        statusView.setText(R.string.downloading);

        final File downloadFile = new File(samplePackFolder, name + ".zip");
        if (downloadFile.exists()) downloadFile.delete();
        try {
            downloadFile.createNewFile();
            StorageReference downloadRef = packFolderRef.child(name + ".zip");
            downloadRef.getFile(downloadFile).addOnCompleteListener(new OnCompleteListener<FileDownloadTask.TaskSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<FileDownloadTask.TaskSnapshot> task) {
                    //Toast.makeText(context, "Decompressing zip archive ...", Toast.LENGTH_SHORT).show();
                    final ProgressBar progressBar = (ProgressBar)statusBar.findViewById(R.id.progressBar);
                    progressBar.setIndeterminate(true);
                    final TextView statusText = (TextView)statusBar.findViewById(R.id.status_textview);
                    statusText.setText(R.string.decompressing);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Utils.unzip(downloadFile, new File(samplePackFolder, name));
                                downloadFile.delete();
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        //Toast.makeText(context, "Decompression complete", Toast.LENGTH_SHORT).show();
                                        progressBar.setVisibility(View.INVISIBLE);
                                        statusText.setText(R.string.downloaded);
                                    }
                                });
                            } catch (IOException e){
                                Toast.makeText(context, "Error decompressing zip file", Toast.LENGTH_SHORT).show();
                                e.printStackTrace();
                            }

                        }
                    }).start();
                }
            }).addOnProgressListener(new OnProgressListener<FileDownloadTask.TaskSnapshot>() {
                @Override
                public void onProgress(FileDownloadTask.TaskSnapshot taskSnapshot) {
                    ProgressBar progressBar = (ProgressBar)statusBar.findViewById(R.id.progressBar);
                    if (progressBar.isIndeterminate()) progressBar.setIndeterminate(false);

                    int percent = (int)(taskSnapshot.getBytesTransferred() * 100 / taskSnapshot.getTotalByteCount());
                    progressBar.setProgress(percent);
                }
            });
        } catch (IOException e){
            e.printStackTrace();
            Toast.makeText(context, "Error downloading file", Toast.LENGTH_SHORT).show();
        }
    }
    private void querySkuDetails(){
        // Access IAB SKU details
        ArrayList<String> additionalSkuList = new ArrayList<>();
        additionalSkuList.add(SKU_TWO_DOLLAR_PACK);
        try {
            mBillingHelper.queryInventoryAsync(true, additionalSkuList, null, new IabHelper.QueryInventoryFinishedListener() {
                @Override
                public void onQueryInventoryFinished(IabResult result, Inventory inv) {
                    if (result.isFailure()) {
                        // handle error
                        return;
                    }

                    packPriceList.put(SKU_TWO_DOLLAR_PACK, inv.getSkuDetails(SKU_TWO_DOLLAR_PACK).getPrice());
                    samplePackListAdapter.notifyDataSetChanged();

                    checkForUnconsumedPurchases();

                }
            });
        } catch (IabHelper.IabAsyncInProgressException e) {
            e.printStackTrace();
        }
    }
    private void startPurchaseFlow(String sku, final View statusBar, final String packName){
        FirebaseUser user = mAuth.getCurrentUser();
        SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS, MODE_PRIVATE);
        if (user == null || (user.isAnonymous()) && prefs.getBoolean(SHOW_CREATE_ACCOUNT_PROMPT, true)) {
            // Prompt to create account
            savedSku = sku;
            savedStatusBar = statusBar;
            savedPackName = packName;
            restartPurchaseFlow = true;
            showCreateAccountPrompt();

        } else {
            makePurchase(user.getUid(), sku, packName, statusBar);
        }
    }
    private void makePurchase(String uid, String sku, final String packName, final View statusBar){
        restartPurchaseFlow = false;
        try {
            mBillingHelper.launchPurchaseFlow(this, sku, RC_PURCHASE,
                    new IabHelper.OnIabPurchaseFinishedListener() {
                        @Override
                        public void onIabPurchaseFinished(IabResult result, Purchase info) {
                            if (result.isFailure()) {
                                Log.e(LOG_TAG, "Error purchasing: " + result);
                                return;
                            }

                            Log.i(LOG_TAG, "Purchase complete");
                            statusBar.setVisibility(View.VISIBLE);
                            TextView statusView = (TextView)statusBar.findViewById(R.id.status_textview);
                            statusView.setText(R.string.purchasing);
                            ProgressBar progressBar = (ProgressBar)statusBar.findViewById(R.id.progressBar);
                            progressBar.setIndeterminate(true);
                            // Save record of purchase
                            updatePurchaseRecord(packName);

                            // Consume purchase
                            try {
                                mBillingHelper.consumeAsync(info, new IabHelper.OnConsumeFinishedListener() {
                                    @Override
                                    public void onConsumeFinished(Purchase purchase, IabResult result) {
                                        if (! result.isSuccess()) return;
                                        // Download pack
                                        Log.i(LOG_TAG, "Purchase consumed");
                                        downloadPack(statusBar, packName);
                                    }
                                });
                            } catch (IabHelper.IabAsyncInProgressException e){
                                e.printStackTrace();
                            }

                        }
                    }, uid);
        } catch (IabHelper.IabAsyncInProgressException e){
            e.printStackTrace();
        }
    }
    private void updatePurchaseRecord(String packName){
        ownedPacks.add(packName);
        File recordFile = new File(getFilesDir(), "purchases.txt");
        try {
            // Write local record
            if (!recordFile.exists()) recordFile.createNewFile();
            FileWriter writer = new FileWriter(recordFile);
            writer.append(packName + "\n");
            writer.flush();
            writer.close();

            // Upload record to Firebase
            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null && !user.isAnonymous()){
                StorageReference purchasesRef = storage.getReferenceFromUrl("gs://mixmatic-loop-pad.appspot.com/user/" + user.getUid() + "/purchases.txt");
                purchasesRef.putFile(Uri.fromFile(recordFile)).addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                        Log.d(LOG_TAG, "Purchase record uploaded");
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
