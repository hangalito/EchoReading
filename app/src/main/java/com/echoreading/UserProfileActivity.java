package com.echoreading;


import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Objects;

public class UserProfileActivity extends AppCompatActivity {
    private MaterialCardView progress_circular;
    private MaterialCardView profilePicture;
    private TextInputLayout edit_username;
    private TextInputLayout edit_name;
    private TextInputLayout edit_email;
    private TextView full_name_view;
    private TextView username_view;
    private FirebaseAuth firebaseAuth;
    private FirebaseUser user;
    private ScrollView editViewLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        firebaseAuth = FirebaseAuth.getInstance();
        user = firebaseAuth.getCurrentUser();

        if (user != null) {
            setContentView(R.layout.activity_user_profile);
            Toolbar toolbar = findViewById(R.id.user_toolbar);
            toolbar.setTitle(getString(R.string.profile));
            setSupportActionBar(toolbar);
            ActionBar actionBar = getSupportActionBar();
            assert actionBar != null;
            actionBar.setDisplayHomeAsUpEnabled(true);

            edit_username = findViewById(R.id.username_edit_layout);
            edit_name = findViewById(R.id.full_name_edit_layout);
            edit_email = findViewById(R.id.email_edit_layout);
            full_name_view = findViewById(R.id.full_name_view);
            username_view = findViewById(R.id.username_view);
            progress_circular = findViewById(R.id.progress_circular);
            progress_circular.setVisibility(View.VISIBLE);
            profilePicture = findViewById(R.id.pp_card_view);
            editViewLayout = findViewById(R.id.edit_info_form);
            getUserData();
        } else {
            setContentView(R.layout.activity_user_profile_null);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.profile_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    public void logOut(MenuItem view) {
        firebaseAuth.signOut();
        Intent intent = new Intent(UserProfileActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    public void deleteAccount(View view) {
        // Deletes the current account
        user.delete();
    }

    public void updateUserData(View view) {
        progress_circular.setVisibility(View.VISIBLE);
        if (validateUsername() | validateEmail() | validateName()) {
            String edit_username_text = Objects.requireNonNull(edit_username.getEditText())
                    .getText().toString();

            UserProfileChangeRequest userProfileChangeRequest = new UserProfileChangeRequest
                    .Builder().setDisplayName(edit_username_text).build();
            user.updateProfile(userProfileChangeRequest).addOnCompleteListener(
                    task -> {
                        if (task.isSuccessful()) {
                            getUserData();
                            String msg = getString(R.string.changes_applied_successfully);
                            Toast.makeText(UserProfileActivity.this,
                                    msg, Toast.LENGTH_SHORT).show();
                        } else {
                            try {
                                throw Objects.requireNonNull(task.getException());
                            } catch (FirebaseException e) {
                                Toast.makeText(UserProfileActivity.this,
                                        e.getMessage(), Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
            );
        }
    }

    private Boolean validateUsername() {
        TextInputLayout edit_username = findViewById(R.id.username_edit_layout);
        String username_value = Objects.requireNonNull(edit_username.getEditText()).getText().toString();
        String value_error = getString(R.string.empty_field);
        String too_long_error = getString(R.string.too_long_username);
        String white_space_error = getString(R.string.no_white_space);
        String no_white_space = "\\A_\\w{6,20}\\z";

        if (username_value.isEmpty()) {
            edit_username.setError(value_error);
            return false;
        } else if (username_value.length() >= 15) {
            edit_username.setError(too_long_error);
            return false;
        } else if (username_value.matches(no_white_space)) {
            edit_username.setError(white_space_error);
            return false;
        } else {
            edit_username.setError(null);
            edit_username.setErrorEnabled(false);
            return true;
        }
    }

    private Boolean validateEmail() {
        TextInputLayout edit_email = findViewById(R.id.email_edit_layout);
        String email_value = Objects.requireNonNull(edit_email.getEditText()).getText().toString();
        String value_error = getString(R.string.empty_field);
        String email_error = getString(R.string.invalid_email);
        String email_pattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+";

        if (email_value.isEmpty()) {
            edit_email.setError(value_error);
            return false;
        } else if (!email_value.matches(email_pattern)) {
            edit_email.setError(email_error);
            return false;
        } else {
            edit_email.setError(null);
            edit_email.setErrorEnabled(false);
            return true;
        }
    }

    private Boolean validateName() {
        TextInputLayout edit_name = findViewById(R.id.full_name_edit_layout);
        String name_value = Objects.requireNonNull(edit_name.getEditText()).getText().toString();
        String value_error = getString(R.string.empty_field);

        if (name_value.isEmpty()) {
            edit_name.setError(value_error);
            return false;
        } else {
            edit_name.setError(null);
            edit_name.setErrorEnabled(false);
            return true;
        }
    }

    private void getUserData() {
        DatabaseReference reference = FirebaseDatabase.getInstance()
                .getReference("Registered users");
        reference.child(user.getUid()).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                profilePicture.setVisibility(View.VISIBLE);
                editViewLayout.setVisibility(View.VISIBLE);

                if (task.getResult().exists()) {
                    DataSnapshot snapshot = task.getResult();
                    String db_full_name = String.valueOf(snapshot.child("full_name").getValue());
                    String db_username = String.valueOf(snapshot.child("username").getValue());

                    // Display the full name and the username
                    full_name_view.setText(db_full_name);
                    username_view.setText(db_username);

                    Objects.requireNonNull(edit_name.getEditText()).setText(full_name_view.getText().toString());
                    Objects.requireNonNull(edit_username.getEditText()).setText(user.getDisplayName());
                    Objects.requireNonNull(edit_email.getEditText()).setText(user.getEmail());
                    progress_circular.setVisibility(View.GONE);
                }
            } else {
                progress_circular.setVisibility(View.GONE);
                String msg = getString(R.string.get_data_error);
                Toast.makeText(UserProfileActivity.this, msg, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    public void goToLoginActivity(View view) {
        Intent intent = new Intent(UserProfileActivity.this, LoginActivity.class);
        startActivity(intent);
    }

    public void goToRegisterActivity(View view) {
        Intent intent = new Intent(UserProfileActivity.this, RegisterActivity.class);
        startActivity(intent);
    }
}