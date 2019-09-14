/**
 * Copyright Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.udacity.friendlychat2;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
//import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {


    //test update to fork via android studio6 - starting tutorial day2


    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;

    public static final int RC_SIGN_IN = 1;
    private static final int RC_PHOTO_PICKER =  2;


    //.. Firebase objects
    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mMessagesDatabaseReference;
    private ChildEventListener mChildEventListener;

    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;

    private FirebaseStorage mFirebaseStorage;
    private StorageReference mChatPhotoStorageReference;

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;

    private String mUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //..  Initially what was used as username before authentication added
        //..  Also what mUsername set to when user logs out
        mUsername = ANONYMOUS;

        mFirebaseDatabase = FirebaseDatabase.getInstance();
        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseStorage = FirebaseStorage.getInstance();

        mMessagesDatabaseReference = mFirebaseDatabase.getReference().child("messages");
        mChatPhotoStorageReference = mFirebaseStorage.getReference().child("chat_photos");


        // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);

        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);
            }
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                FriendlyMessage friendlyMessage = new FriendlyMessage(mMessageEditText.getText().toString(), mUsername, null);

                // Clear input box
                mMessageEditText.setText("");
                //added from Firebase tutorial day1, part19 - "push" means to push it to the Firebase database
                mMessagesDatabaseReference.push().setValue(friendlyMessage);
            }
        });

        //..  This moved down to private void attachedDatabaseReadListener
        //..  (called from onSignedInInitialize(String username) because
        //..  we only want to show the user the messages IF user is logged in...
        //..  Also, because authentication is kicked off first now, mChildEventListener doesn't
        //..  even work if it's here because the login flow gets displayed instead...
        //..  This listener doesn't get triggered here because the user isn't logged in yet, because
        //..  this is in oncreate... "read listener" mChildEventListener must be attached and only works when
        //..  user is signed in. -> That's why it was "moved" to attachedDatabaseReadListener (called from
        // ..   onSignedInInitialize method
        //..
//        mChildEventListener = new ChildEventListener() {
//            @Override
//            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
//
//                //.. get any messages that were added to the database (on initial startup or after
//                //..  while app is "active"
//                FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);
//
//                //.. mMessageAdapter is the list adapter to display the list of messages in the app
//                mMessageAdapter.add(friendlyMessage);
//
//            }
//
//            @Override
//            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
//
//            }
//
//            @Override
//            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
//
//            }
//
//            @Override
//            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
//
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError databaseError) {
//
//            }
//        };
//
//        //.. attaches the "messages" node to the listener
//        mMessagesDatabaseReference.addChildEventListener(mChildEventListener);

        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {

                FirebaseUser user = firebaseAuth.getCurrentUser();

                if (user != null) {
                    //user signed in
                    //Toast.makeText(MainActivity.this,"You are now signed in. Welcome to Friendly Chat",Toast.LENGTH_SHORT).show();
                    onSignedInInitialize(user.getDisplayName());

                } else {
                    onSignedOutCleanup();
                    //user not signed in
                    //.. so kick off login screen

                    // Choose authentication providers
                    List<AuthUI.IdpConfig> providers = Arrays.asList(
                            new AuthUI.IdpConfig.EmailBuilder().build(),
                            //new AuthUI.IdpConfig.PhoneBuilder().build(),
                            new AuthUI.IdpConfig.GoogleBuilder().build());
                    //new AuthUI.IdpConfig.FacebookBuilder().build(),
                    //new AuthUI.IdpConfig.TwitterBuilder().build());

                    // Create and launch sign-in inten0;
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)
                                    .setAvailableProviders(providers)
                                    .build(),
                            RC_SIGN_IN);

                }

            }
        };


    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            //sign out
            case R.id.sign_out_menu:
                AuthUI.getInstance().signOut(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    //..  This code is called BEFORE onResume and thus allows the user to hit the
    //..  back button to "exit" the app without signing in (if they're on the signin
    //..  screen)... Without this, it would be an endless loop attempting to hit the back button

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        //.. User of if with RC_SIGN_IN here means... if the activity that we're being returned from is our login flow,
        //.. execute the if statement and then check for RESULT_OK or RESULT_CANCELED
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Signed In!", Toast.LENGTH_SHORT).show();
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Signed In Canceled", Toast.LENGTH_SHORT).show();
                finish();
            } else if (requestCode == RC_PHOTO_PICKER && resultCode == RESULT_OK) {  //to get photos
                //Uri selectedImageUri = Uri.parse("R.drawable.thor_poster");
                Uri selectedImageUri = data.getData();

                //.. get a reference to store file at chat_photos/<FILENAME>
                final StorageReference photoRef = mChatPhotoStorageReference.child(selectedImageUri.getLastPathSegment());
                //.. example:  if photo at ->  content://local_images/foo/4 , then filename that stored image is
                //..  save at, would be "4" (lastPathSegment)

//..              ***** OLD CODE FROM UDACITY TUTORIAL DAY 2, #8... getDownloadUrl() NOT recognized as a real method
//
//                //.. upload file to Firebase storage
//                photoRef.putFile(selectedImageUri).addOnSuccessListener(this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
//                    @Override
//                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
//
//                        Uri downLoadUrl = taskSnapshot.getDownloadUrl();
//                    }
//                });

                //.. Upload file to Firebase Storage - from https://github.com/udacity/and-nd-firebase from
                //.. Store photo in Firebase Storage. #42 - swdevdave PULL request
                //..
                    photoRef.putFile(selectedImageUri);
                    mChatPhotoStorageReference.putFile(selectedImageUri).continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
                        @Override
                        public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                            if (!task.isSuccessful()) {
                                throw task.getException();
                            }
                            return mChatPhotoStorageReference.getDownloadUrl();
                        }
                    }).addOnCompleteListener(new OnCompleteListener<Uri>() {
                        @Override
                        public void onComplete(@NonNull Task<Uri> task) {
                            if (task.isSuccessful()) {
                                Uri downloadUri = task.getResult();
                                FriendlyMessage friendlyMessage = new FriendlyMessage(null, mUsername, downloadUri.toString());
                                mMessagesDatabaseReference.push().setValue(friendlyMessage);
                            }
                        }
                    });
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }
        mMessageAdapter.clear();
        detachDatabaseReadListener();
    }

    private void onSignedInInitialize(String username) {
        mUsername = username;
        attachDatabaseReadListener();

    }

    private void onSignedOutCleanup() {

        mUsername = ANONYMOUS;
        mMessageAdapter.clear();
        detachDatabaseReadListener();

    }

    private void attachDatabaseReadListener() {

        //.. checks to see if a child read listener was already attached (you don't want to
        //..   attempt to attach it again! So, if the mChildEventListener is "detached", create it

        if (mChildEventListener == null) {

            //.. Used to be above in on create before we added authentication
            //.. Create the child listener
            mChildEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                    //.. get any messages that were added to the database (on initial startup or after
                    //..  while app is "active"
                    FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);

                    //.. mMessageAdapter is the list adapter to display the list of messages in the app
                    mMessageAdapter.add(friendlyMessage);

                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                }

                @Override
                public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {

                }

                @Override
                public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            };

            //.. Attach listener that was just created to the "messages" node of the database
            mMessagesDatabaseReference.addChildEventListener(mChildEventListener);
        }

    }

    private void detachDatabaseReadListener() {

        //.. When user logs out, remove the listener
        //.. If statement enforces that you only attach/detach a listener at one time
        if(mChildEventListener != null){
            mMessagesDatabaseReference.removeEventListener(mChildEventListener);
            mChildEventListener = null;
        }
    }


}
