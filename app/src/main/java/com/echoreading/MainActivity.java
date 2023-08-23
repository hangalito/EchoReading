package com.echoreading;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {
    FirebaseAuth auth;
    FirebaseUser user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        auth = FirebaseAuth.getInstance();
        user = auth.getCurrentUser();

        new Handler().postDelayed(() -> {
            // Check if the user is already logged in
            if (user != null) {
                // Toast a welcome message for the user
                String user_name = user.getDisplayName();
                String welcome_message = getString(R.string.welcome_user);
                Toast.makeText(this,  welcome_message + user_name, Toast.LENGTH_SHORT).show();

                // If the user is already logged in go to home activity
                Intent intent = new Intent(MainActivity.this, HomeActivity.class);
                startActivity(intent);
                finish();
            } else {
                // If the user is not logged in, then go to the login activity
                Intent intent = new Intent(this, LoginActivity.class);
                startActivity(intent);
                finish();
            }
        }, 3000);
    }
}