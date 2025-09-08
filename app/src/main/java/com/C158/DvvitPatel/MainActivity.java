package com.C158.DvvitPatel;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int VOICE_REQUEST_CODE = 1001;

    private EditText promptEditText;
    private RecyclerView recyclerView;
    private MyAdapter myAdapter;
    private List<MyItem> chatHistory;
    private GenerativeModelFutures modelFutures;
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        promptEditText = findViewById(R.id.promptEditText);
        ImageButton submitPromptButton = findViewById(R.id.sendButton);
        ImageButton micButton = findViewById(R.id.micButton);

        recyclerView = findViewById(R.id.recyclerView);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Load chat history
        chatHistory = loadChatHistory();
        myAdapter = new MyAdapter(chatHistory);
        recyclerView.setAdapter(myAdapter);

        executorService = Executors.newSingleThreadExecutor();

        // Gemini Model
        GenerativeModel generativeModel = new GenerativeModel(
                "gemini-1.5-flash",
                BuildConfig.API_KEY
        );
        modelFutures = GenerativeModelFutures.from(generativeModel);

        // Send button
        submitPromptButton.setOnClickListener(v -> sendMessage());

        // Mic button
        micButton.setOnClickListener(v -> {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your query");
            try {
                startActivityForResult(intent, VOICE_REQUEST_CODE);
            } catch (Exception e) {
                Toast.makeText(this, "Speech recognition not available.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendMessage() {
        String prompt = promptEditText.getText().toString().trim();
        if (prompt.isEmpty()) {
            promptEditText.setError(getString(R.string.field_cannot_be_empty));
            return;
        }


        // Add user message
        MyItem userPromptItem = new MyItem("User:", prompt);
        chatHistory.add(userPromptItem);
        myAdapter.notifyItemInserted(chatHistory.size() - 1);
        recyclerView.scrollToPosition(chatHistory.size() - 1);
        promptEditText.setText("");

        // Prepare API request
        Content content = new Content.Builder()
                .addText(prompt)
                .build();

        ListenableFuture<GenerateContentResponse> responseFuture =
                modelFutures.generateContent(content);

        Futures.addCallback(responseFuture, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String responseString = (result.getText() != null)
                        ? result.getText()
                        : "No text response received.";

                runOnUiThread(() -> {

                    MyItem modelResponseItem = new MyItem("Bot:", responseString);
                    chatHistory.add(modelResponseItem);
                    myAdapter.notifyItemInserted(chatHistory.size() - 1);
                    recyclerView.scrollToPosition(chatHistory.size() - 1);
                    saveChatHistory(chatHistory);
                });
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Error: " + t.getMessage(),
                            Toast.LENGTH_LONG).show();
                    MyItem errorItem = new MyItem("Error", t.getMessage());
                    chatHistory.add(errorItem);
                    myAdapter.notifyItemInserted(chatHistory.size() - 1);
                    recyclerView.scrollToPosition(chatHistory.size() - 1);
                });
            }
        }, executorService);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VOICE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (result != null && !result.isEmpty()) {
                promptEditText.setText(result.get(0));
            }
        }
    }

    // Save chat history
    private void saveChatHistory(List<MyItem> history) {
        SharedPreferences prefs = getSharedPreferences("chat_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        StringBuilder sb = new StringBuilder();
        for (MyItem item : history) {
            String title = item.getTitle() != null ? item.getTitle().replace("|", "").replace("#", "") : "";
            String description = item.getDescription() != null ? item.getDescription().replace("|", "").replace("#", "") : "";
            sb.append(title).append("||").append(description).append("##");
        }
        editor.putString("chat_data", sb.toString());
        editor.apply();
    }

    // Load chat history
    private List<MyItem> loadChatHistory() {
        SharedPreferences prefs = getSharedPreferences("chat_prefs", MODE_PRIVATE);
        String data = prefs.getString("chat_data", "");
        List<MyItem> list = new ArrayList<>();
        if (!data.isEmpty()) {
            String[] pairs = data.split("##");
            for (String pair : pairs) {
                if (!pair.trim().isEmpty()) {
                    String[] parts = pair.split("\\|\\|");
                    if (parts.length == 2) {
                        list.add(new MyItem(parts[0], parts[1]));
                    }
                }
            }
        }
        return list;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
