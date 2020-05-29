package com.example.musicmashup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.example.jean.jcplayer.model.JcAudio;
import com.example.jean.jcplayer.view.JcPlayerView;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.core.operation.ListenComplete;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import java.util.ArrayList;

import static com.karumi.dexter.Dexter.*;

public class MainActivity extends AppCompatActivity {
    private boolean checkpermission = false;
    String songName,songUrl;
    Uri uri;
    ListView listView;
    ArrayList<String> arraylistSongsName = new ArrayList<>();
    ArrayList<String> arraylistSongsUrl = new ArrayList<>();
    ArrayAdapter<String> arrayAdapter;

    JcPlayerView jcPlayerView;
    ArrayList<JcAudio> jcAudios = new ArrayList<>();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = findViewById(R.id.myListView);
        jcPlayerView = findViewById(R.id.jcplayer);
        retriveSongs();

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                jcPlayerView.playAudio(jcAudios.get(position));
                jcPlayerView.setVisibility(View.VISIBLE);
                jcPlayerView.createNotification();
            }
        });


    }

    private void retriveSongs() {
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("Songs");
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    for(DataSnapshot ds :dataSnapshot.getChildren()){
                        Song songObj = ds.getValue(Song.class);
                        arraylistSongsName.add(songObj.getSongName());
                        arraylistSongsUrl.add(songObj.getSongUrl());
                        jcAudios.add(JcAudio.createFromURL(songObj.getSongName(),songObj.getSongUrl()));
                    }
                    arrayAdapter = new ArrayAdapter<String>(MainActivity.this,android.R.layout.simple_list_item_1,arraylistSongsName);
                    jcPlayerView.initPlaylist(jcAudios,null);
                    listView.setAdapter(arrayAdapter);

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    @Override
        public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.custom_menu,menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId()==R.id.nav_upload) {
            if (validatePermission()){
                pickSong();
            }
        }
        return super.onOptionsItemSelected(item);
    }

                private void pickSong() {
        Intent intent_upload = new Intent();
        intent_upload.setType("audio/*");
        intent_upload.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(intent_upload,1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode==1){
                uri = data.getData();
                Cursor mcursor = getApplicationContext().getContentResolver()
                        .query(uri,null,null,null,null);
                int indexedname = mcursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                mcursor.moveToFirst();
                songName = mcursor.getString(indexedname);
                mcursor.close();

                uploadingSongToFirebaseStorage();{
                }

        }
        super.onActivityResult(requestCode, resultCode, data);
    }

                private void uploadingSongToFirebaseStorage() {  ///Uploading Song To Firebase Storage
                    StorageReference storageReference = FirebaseStorage.getInstance().getReference()
                            .child("Songs").child(uri.getLastPathSegment());
                    final ProgressDialog progressDialog = new ProgressDialog(this);
                    progressDialog.show();

                    storageReference.putFile(uri).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                            Task<Uri> uriTask = taskSnapshot.getStorage().getDownloadUrl();
                            while(!uriTask.isComplete());
                            Uri urlSong = uriTask.getResult();
                            songUrl = urlSong.toString();
                            uploadDetailsToDatabase();
                            progressDialog.dismiss();
                           // Toast.makeText(MainActivity.this,"Song Uploaded",Toast.LENGTH_SHORT).show();
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Toast.makeText(MainActivity.this,e.getMessage(),Toast.LENGTH_SHORT).show();
                            progressDialog.dismiss();
                        }
                    }).addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                            double progress = (100.0*taskSnapshot.getBytesTransferred())/taskSnapshot.getTotalByteCount();
                            int currentprogress = (int)progress;
                            progressDialog.setMessage("Song Uploaded : "+currentprogress+"%");
                        }
                    });


                }

    private void uploadDetailsToDatabase() {    ///Upload Song Detail to database
        Song songobj = new Song(songName, songUrl);

        FirebaseDatabase.getInstance().getReference("Songs")
                .push().setValue(songobj).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if(task.isSuccessful()){
                    Toast.makeText(MainActivity.this,"Song Uploaded",Toast.LENGTH_SHORT).show();
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MainActivity.this,e.getMessage().toString(),Toast.LENGTH_SHORT).show();
            }
        });
    }

                private boolean validatePermission(){
                        //Dexter.withContext(MainActivity.this)
                        Dexter.withActivity(MainActivity.this) // Not in Current dexter versoio only on 6.0.1
                                .withPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                                .withListener(new PermissionListener() {
                                    @Override
                                    public void onPermissionGranted(PermissionGrantedResponse permissionGrantedResponse) {
                                        checkpermission = true;
                                    }

                                    @Override
                                    public void onPermissionDenied(PermissionDeniedResponse permissionDeniedResponse) {
                                        checkpermission = false;
                                    }

                                    @Override
                                    public void onPermissionRationaleShouldBeShown(PermissionRequest permissionRequest, PermissionToken permissionToken) {
                                        permissionToken.continuePermissionRequest();
                                    }
                                }).check();
                        return checkpermission;

                    }
}