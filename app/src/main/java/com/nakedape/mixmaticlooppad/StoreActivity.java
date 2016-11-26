package com.nakedape.mixmaticlooppad;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;

public class StoreActivity extends Activity {

    private final String LOG_TAG = "StoreActivity";

    // Firebase
    private FirebaseStorage storage = FirebaseStorage.getInstance();
    private StorageReference packFolderRef;
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener authStateListener;

    private Context context;
    private File cacheFolder, samplePackFolder;
    private SamplePackListAdapter samplePackListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_store);
        context = this;
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
                            LoadPackData(bytes);
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception exception) {
                            // Handle any errors
                            Log.d(LOG_TAG, "Index download error");
                        }
                    });
                } else {
                    // User is signed out
                    Log.d(LOG_TAG, "onAuthStateChanged:signed_out");
                }
            }
        };
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

    // Initialize Store
    private void LoadPackData(byte[] bytes){
        String indexText;
        try {
            indexText = new String(bytes, "UTF-8");
            String[] strings = indexText.split("\n");
            final ArrayList<String> packs = new ArrayList<>(strings.length);
            for (String s : strings){
                packs.add(s.trim());
                Log.d(LOG_TAG, s);
            }
            setupFolders();
            samplePackListAdapter = new SamplePackListAdapter(this, R.layout.store_sample_pack_item);
            ListView packListView = (ListView)findViewById(R.id.store_item_list);
            packListView.setAdapter(samplePackListAdapter);

            // Check if data is already cached.  If not, download
            ArrayList<String> cachedPacks = new ArrayList<>(Arrays.asList(cacheFolder.list(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String filename) {
                    int index = filename.lastIndexOf(".");
                    index = index >= 0 ? index : filename.length() - 1;
                    filename = filename.substring(0, index);
                    return packs.contains(filename);
                }
            })));
            for (final String packName : packs){
                if (!cachedPacks.contains(packName)){
                    File desc = new File(cacheFolder, packName + ".txt");
                    try {
                        desc.createNewFile();
                        StorageReference descRef = storage.getReferenceFromUrl("gs://mixmatic-loop-pad.appspot.com/SamplePacks/" + packName + ".txt"); //packFolderRef.child(pack + ".txt");
                        Log.d(LOG_TAG, descRef.getPath());
                        descRef.getFile(desc).addOnCompleteListener(new OnCompleteListener<FileDownloadTask.TaskSnapshot>() {
                            @Override
                            public void onComplete(@NonNull Task<FileDownloadTask.TaskSnapshot> task) {
                                File image = new File(cacheFolder, packName + ".png");
                                try {
                                    image.createNewFile();
                                    StorageReference imageRef = storage.getReferenceFromUrl("gs://mixmatic-loop-pad.appspot.com/SamplePacks/" + packName + ".png"); //packFolderRef.child(pack + ".png");
                                    imageRef.getFile(image).addOnCompleteListener(new OnCompleteListener<FileDownloadTask.TaskSnapshot>() {
                                        @Override
                                        public void onComplete(@NonNull Task<FileDownloadTask.TaskSnapshot> task) {
                                            samplePackListAdapter.addPack(packName);
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

        } catch (UnsupportedEncodingException e){
            Log.d(LOG_TAG, "Error loading index");
        }
    }
    private void setupFolders(){
        // Prepare stoarage directories
        if (Utils.isExternalStorageWritable()){
            samplePackFolder = new File(getExternalFilesDir(null), "Samples Packs");
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
    private class SamplePackListAdapter extends BaseAdapter {
        private ArrayList<String> names;
        private ArrayList<String> titles;
        private ArrayList<String> genres;
        private ArrayList<String> prices;
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
            prices = new ArrayList<>();
            details = new ArrayList<>();
        }

        private void addPack(String name){
            File desc = new File(cacheFolder, name + ".txt");
            if (desc.exists()) {
                names.add(name);
                try {
                    BufferedReader br = new BufferedReader(new FileReader(desc));
                    titles.add(br.readLine());
                    genres.add(br.readLine());
                    prices.add(br.readLine());
                    String line;
                    StringBuilder text = new StringBuilder();
                    while ((line = br.readLine()) != null) {
                        text.append(line);
                        text.append('\n');
                    }
                    details.add(text.toString());
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

            TextView titleView = (TextView)convertView.findViewById(R.id.title_view);
            titleView.setText(titles.get(position));

            TextView genreView = (TextView)convertView.findViewById(R.id.genre_view);
            genreView.setText(genres.get(position));

            TextView detailsView = (TextView)convertView.findViewById(R.id.details_view);
            detailsView.setText(details.get(position));

            File image = new File(cacheFolder, names.get(position) + ".png");
            if (image.exists()) {
                ImageView imageView = (ImageView)convertView.findViewById(R.id.image_view);
                BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                Bitmap bitmap = BitmapFactory.decodeFile(image.getAbsolutePath(), bmOptions);
                //bitmap = Bitmap.createScaledBitmap(bitmap,parent.getWidth(),parent.getHeight(),true);
                imageView.setImageBitmap(bitmap);
            }

            Button buyButton = (Button)convertView.findViewById(R.id.purchase_button);
            buyButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    buyClick(names.get(position));
                }
            });
            return  convertView;
        }
    }

    // Purchase and download
    private void buyClick(final String name){
        Toast.makeText(context, "Downloading sample pack ...", Toast.LENGTH_SHORT).show();
        final File downloadFile = new File(samplePackFolder, name + ".zip");
        if (downloadFile.exists()) downloadFile.delete();
        try {
            downloadFile.createNewFile();
            StorageReference downloadRef = packFolderRef.child(name + ".zip");
            downloadRef.getFile(downloadFile).addOnCompleteListener(new OnCompleteListener<FileDownloadTask.TaskSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<FileDownloadTask.TaskSnapshot> task) {
                    Toast.makeText(context, "Decompressing zip archive ...", Toast.LENGTH_SHORT).show();
                    try {
                        Utils.unzip(downloadFile, new File(samplePackFolder, name));
                        downloadFile.delete();
                        Toast.makeText(context, "Decompression complete", Toast.LENGTH_SHORT).show();
                    } catch (IOException e){
                        Toast.makeText(context, "Error decompressing zip file", Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                }
            });
        } catch (IOException e){
            e.printStackTrace();
            Toast.makeText(context, "Error downloading file", Toast.LENGTH_SHORT).show();
        }
    }
}
