package com.echoreading;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.MenuItem;
import android.view.View;

import com.google.android.material.textfield.TextInputEditText;

import java.util.Locale;
import java.util.Objects;

public class HomeActivity extends AppCompatActivity {
    private String userInput;
    TextToSpeech speech_engine;
    TextInputEditText speech_input;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        int color = getColor(R.color.white);
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitleTextColor(color);
        setSupportActionBar(toolbar);

        speech_input = findViewById(R.id.speech_input);
        speech_engine = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.ERROR) {
                speech_engine.setLanguage(Locale.getDefault());
            }
        });

        if (savedInstanceState != null) {
            userInput = savedInstanceState.getString("userInput", userInput);
            speech_input.setText(userInput);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putString("userInput", userInput);
    }

    public void onSpeakClick(View view) {
        String text = Objects.requireNonNull(speech_input.getText()).toString();
        if (speech_engine.isSpeaking()) {
            speech_engine.stop();
        } else {
            speech_engine.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    public void goToSettingsActivity(MenuItem menu) {
        Intent intent = new Intent(HomeActivity.this, SettingsActivity.class);
        startActivity(intent);
    }

    public void goToUserProfileActivity(MenuItem menuItem) {
        Intent intent = new Intent(HomeActivity.this, UserProfileActivity.class);
        startActivity(intent);
    }
}