package com.echoreading;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;

import java.util.Objects;

public class LoginActivity extends AppCompatActivity {
    private TextInputLayout email_field;
    private TextInputLayout password_field;
    private FirebaseAuth auth;
    private String emailInsert;
    private String passwordInsert;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        email_field = findViewById(R.id.email_field_login);
        password_field = findViewById(R.id.password_field_login);

        if (savedInstanceState != null) {
            emailInsert = savedInstanceState.getString("emailInsert");
            passwordInsert = savedInstanceState.getString("passwordInsert");

            Objects.requireNonNull(email_field.getEditText()).setText(emailInsert);
            Objects.requireNonNull(password_field.getEditText()).setText(passwordInsert);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        emailInsert = Objects.requireNonNull(email_field.getEditText()).getText().toString();
        passwordInsert = Objects.requireNonNull(password_field.getEditText()).getText().toString();
        savedInstanceState.putString("emailInsert", emailInsert);
        savedInstanceState.putString("passwordInsert", passwordInsert);
    }

    public void signIn(View view) {
        // Check if the fields are valid
        if (!validateEmail() | !validatePassword()) {
            return;
        }

        MaterialCardView progress_circular = findViewById(R.id.progress_circular);
        progress_circular.setVisibility(View.VISIBLE);

        new Handler().postDelayed(() -> {
            String email = Objects.requireNonNull(email_field.getEditText()).getText().toString();
            String password = Objects.requireNonNull(password_field.getEditText()).getText().toString();

            auth.signInWithEmailAndPassword(email, password).addOnCompleteListener(this,
                    task -> {
                        if (task.isSuccessful()){
                            progress_circular.setVisibility(View.GONE);
                            // Toast a welcome message for the user
                            String user_name = Objects.requireNonNull(auth.getCurrentUser()).getDisplayName();
                            String welcome_message = getString(R.string.welcome_user);
                            Toast.makeText(LoginActivity.this,
                                    welcome_message + user_name,
                                    Toast.LENGTH_SHORT).show();

                            // Got to home activity
                            Intent intent = new Intent(LoginActivity.this,
                                    HomeActivity.class);
                            startActivity(intent);
                            finish();
                        } else {
                            progress_circular.setVisibility(View.GONE);
                            String msg = "";
                            try {
                                throw Objects.requireNonNull(task.getException());
                            } catch (FirebaseNetworkException e) {
                                msg = getString(R.string.network_error);
                            } catch (FirebaseAuthInvalidUserException e) {
                                msg = getString(R.string.no_user_found);
                            } catch (Exception e) {
                                msg = e.getMessage();
                                throw new RuntimeException(e);
                            } finally {
                                Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_LONG).show();
                            }
                        }
                    });
        }, 1500);

    }

    // Methods to validate the email and password
    private Boolean validateEmail() {
        String email = Objects.requireNonNull(email_field.getEditText()).getText().toString();
        String value_error = getString(R.string.empty_field);
        String email_error = getString(R.string.invalid_email);
        String email_pattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+";

        if (email.isEmpty()){
            email_field.setError(value_error);
            return false;
        } else if (!email.matches(email_pattern)) {
            email_field.setError(email_error);
            return false;
        } else {
            email_field.setError(null);
            email_field.setErrorEnabled(false);
            return true;
        }
    }

    private Boolean validatePassword() {
        String password = Objects.requireNonNull(password_field.getEditText()).getText().toString();
        String value_error = getString(R.string.empty_field);

        if (password.isEmpty()){
            password_field.setError(value_error);
            return  false;
        } else {
            password_field.setError(null);
            password_field.setErrorEnabled(false);
            return true;
        }
    }


    public void goToRegisterActivity(View view) {

        Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
        startActivity(intent);
    }

    public void loginAsGuest(View view) {
        Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                    Intent.FLAG_ACTIVITY_CLEAR_TASK |
                Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}