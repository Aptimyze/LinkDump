package com.linkdump.tchur.ld.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.linkdump.tchur.ld.R;
import com.linkdump.tchur.ld.adapters.GroupChatAdapter;
import com.linkdump.tchur.ld.adapters.NewGroupChatAdapter;
import com.linkdump.tchur.ld.objects.Message;
import com.linkdump.tchur.ld.utils.RichLinkUtil;
import com.linkedin.urls.Url;
import com.linkedin.urls.detection.UrlDetector;
import com.linkedin.urls.detection.UrlDetectorOptions;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatActivity extends AppCompatActivity implements GroupChatAdapter.ItemClickListener, NewGroupChatAdapter.ItemClickListener {
    private static String TAG = "ChatActivity";

    private RecyclerView mRecyclerView;
    private NewGroupChatAdapter adapter;
    private ArrayList<String> events;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String currentGroup;
    private List<Message> messages;
    private DocumentReference userRef;
    private List<String> userGroups;
    private SharedPreferences prefs;
    private LinearLayoutManager mLayoutManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        clearNotifications();


        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        userRef = db.collection("users").document(mAuth.getUid());
        messages = new ArrayList<>();
        Intent intent = getIntent();
        currentGroup = intent.getStringExtra("groupID");
        prefs = this.getSharedPreferences(
                getPackageName(), MODE_PRIVATE);

        prefs.edit().putString("currentGroup", currentGroup).apply();

        events = new ArrayList<>();
        userGroups = new ArrayList<>();
        mRecyclerView = findViewById(R.id.chat_recyclerview);
        mLayoutManager = new LinearLayoutManager(this);
        mLayoutManager.setStackFromEnd(true);
        mRecyclerView.setLayoutManager(mLayoutManager);
        adapter = new NewGroupChatAdapter(this, messages);
        adapter.setClickListener(this);
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                mLayoutManager.smoothScrollToPosition(mRecyclerView, null, adapter.getItemCount());
            }
        });
        mRecyclerView.setAdapter(adapter);
        groupChatListener(currentGroup);

        ImageButton imageButton = findViewById(R.id.imageButton);
        final EditText chatEditText = findViewById(R.id.chat_message_edit_text);

        imageButton.setOnClickListener(view -> {
//            RichLinkUtil.test(this, "https://www.imgur.com");
            if (!chatEditText.getText().toString().equals("")) {
                Boolean hasLink = false;
                String url = "";
                UrlDetector detector = new UrlDetector(chatEditText.getText().toString(), UrlDetectorOptions.Default);
                List<Url> urls = detector.detect();
                if (!urls.isEmpty()) {
                    hasLink = true;
                    url = urls.get(0).getFullUrl();
                }
                Map<String, Object> sendMessage = new HashMap<>();
                sendMessage.put("message", chatEditText.getText() + "");
                sendMessage.put("user", mAuth.getUid());
                sendMessage.put("userName", mAuth.getCurrentUser().getDisplayName());
                sendMessage.put("sentTime", Calendar.getInstance().getTimeInMillis());
                Boolean finalHasLink = hasLink;
                String finalUrl = url;
                Log.d(TAG, "url before message push: " + finalUrl);
                Log.d(TAG, "before database push");
                db.collection("groups").document(currentGroup)
                        .collection("messages").add(sendMessage).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Successfully pushed");
                        DocumentReference messageRef = task.getResult();
                        if (finalHasLink) {
                            Log.d(TAG, "found Link in text");
                            RichLinkUtil.getRichLinkProperties(getApplicationContext(), finalUrl, data -> {
                                Log.d(TAG, "right before setting message with OG: tags");
                                sendMessage.put("link", data);
                                messageRef.set(sendMessage, SetOptions.merge());
                            });
                        }
                    }
                });
                chatEditText.setText("");
            }
        });
        chatEditText.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER:
                        imageButton.callOnClick();
                        return true;
                    default:
                        break;
                }
            }
            return false;
        });

//        CharSequence message = getMessageText(getIntent());
//        if (message != null){
//            chatEditText.setText(message);
//            imageButton.callOnClick();
//            Notification repliedNotification = new Notification.Builder(getApplicationContext(), "Group Chat")
//                    .setSmallIcon(R.drawable.ic_link_dump)
//                    .setContentText(message)
//                    .build();
//
//            NotificationManagerCompat notificationManager1 = NotificationManagerCompat.from(this);
//            notificationManager1.notify(NOTIFICATION_ID, repliedNotification);
//        }
    }

    public void groupChatListener(String currentGroup) {
        db.collection("groups").document(currentGroup).collection("messages").orderBy("sentTime", Query.Direction.DESCENDING).limit(25).addSnapshotListener((queryDocumentSnapshots, e) -> {
            if (e != null) {
                Log.w("demo", "Listener Failed", e);
                return;
            }

            for (DocumentChange doc : queryDocumentSnapshots.getDocumentChanges()) {
                QueryDocumentSnapshot mDoc = doc.getDocument();
                Log.d("demo", mDoc.get("message") + "");
                Message tempMessage = mDoc.toObject(Message.class);
                if (!tempMessage.getUser().equals(mAuth.getUid())) {
                    tempMessage.setIsUser(false);
                } else {
                    tempMessage.setIsUser(true);
                }
                messages.add(tempMessage);
                events.add(mDoc.getString("message"));
            }
            Collections.sort(messages);
            adapter.notifyDataSetChanged();
//            adapter.notifyItemInserted(adapter.getItemCount() - 1);
            mLayoutManager.scrollToPosition(messages.size() - 1);
        });
    }

//    private CharSequence getMessageText(Intent intent) {
//        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
//        if (remoteInput != null) {
//            return remoteInput.getCharSequence(KEY_TEXT_REPLY);
//        }
//        return null;
//    }

    @Override
    public void onItemClick(View view, int position) {

    }

    @Override
    protected void onResume() {
        super.onResume();
        prefs.edit().putString("currentGroup", currentGroup).apply();
    }

    @Override
    protected void onPause() {
        super.onPause();
        prefs.edit().putString("currentGroup", "NONE").apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        prefs.edit().putString("currentGroup", "NONE").apply();
    }

    public void clearNotifications() {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
        notificationManager.cancel(0);
    }
}
