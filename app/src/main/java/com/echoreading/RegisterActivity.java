package com.echoreading;


import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Objects;


public class RegisterActivity extends AppCompatActivity {
    TextInputLayout name;
    TextInputLayout email;
    TextInputLayout username;
    TextInputLayout password;
    TextInputLayout password_confirm;

    private String nameInsert, emailInsert,
        usernameInsert, passwordInsert, passwordConfirmInsert;
    private static final String TAG = RegisterActivity.class.getSimpleName();

    FirebaseAuth auth;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        name = findViewById(R.id.name_register_field);
        email = findViewById(R.id.email_register_field);
        username = findViewById(R.id.username_register_field);
        password = findViewById(R.id.password_register_field);
        password_confirm = findViewById(R.id.confirm_password_register_field);

        if (savedInstanceState != null) {
            nameInsert = savedInstanceState.getString("nameInsert");
            emailInsert = savedInstanceState.getString("emailInsert");
            usernameInsert = savedInstanceState.getString("usernameInsert");
            passwordInsert = savedInstanceState.getString("passwordInsert");
            passwordConfirmInsert = savedInstanceState.getString("passwordConfirmInsert");

            Objects.requireNonNull(name.getEditText()).setText(nameInsert);
            Objects.requireNonNull(email.getEditText()).setText(emailInsert);
            Objects.requireNonNull(username.getEditText()).setText(usernameInsert);
            Objects.requireNonNull(password.getEditText()).setText(passwordInsert);
            Objects.requireNonNull(password_confirm.getEditText()).setText(passwordConfirmInsert);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        nameInsert = Objects.requireNonNull(name.getEditText()).getText().toString();
        emailInsert = Objects.requireNonNull(email.getEditText()).getText().toString();
        usernameInsert = Objects.requireNonNull(username.getEditText()).getText().toString();
        passwordInsert = Objects.requireNonNull(password.getEditText()).getText().toString();
        passwordConfirmInsert = Objects.requireNonNull(password_confirm.getEditText()).getText().toString();

        savedInstanceState.putString("nameInsert", nameInsert);
        savedInstanceState.putString("emailInsert", emailInsert);
        savedInstanceState.putString("usernameInsert", usernameInsert);
        savedInstanceState.putString("passwordInsert", passwordInsert);
        savedInstanceState.putString("passwordConfirmInsert", passwordConfirmInsert);
    }

    public void registerUser(View view) {

        if (!validateName() | !validateEmail() | !validateUsername() | (!validatePassword1() | !validatePassword2())) {
            return;
        } else if (!checkIfPasswordsMatch()) {
            return;
        }

        String name_commit = Objects.requireNonNull(name.getEditText()).getText().toString();
        String email_commit = Objects.requireNonNull(email.getEditText()).getText().toString();
        String username_commit = Objects.requireNonNull(username.getEditText()).getText().toString();
        String password_commit = Objects.requireNonNull(password.getEditText()).getText().toString();

        // Show the progress bar
        MaterialCardView progress_circular = findViewById(R.id.progress_circular);
        progress_circular.setVisibility(View.VISIBLE);

        // Create a new account
        auth = FirebaseAuth.getInstance();
        auth.createUserWithEmailAndPassword(email_commit, password_commit).addOnCompleteListener(this,
                task -> {
                    if (task.isSuccessful()) {
                        progress_circular.setVisibility(View.GONE);
                        // Sign in success, update UI with the signed-in user's information
                        Log.d(TAG, "createUserWithEmail:success");

                        // Update the user name
                        FirebaseUser user = auth.getCurrentUser();
                        UserProfileChangeRequest profileChangeRequest = new UserProfileChangeRequest
                                .Builder().setDisplayName(username_commit).build();
                        assert user != null;
                        user.updateProfile(profileChangeRequest);

                        // Save the user data to the database
                        ReadWriteUserDetails writeUserDetails = new ReadWriteUserDetails(name_commit,
                                username_commit);
                        DatabaseReference reference = FirebaseDatabase.getInstance().
                                getReference("users");
                        reference.child(user.getUid()).setValue(writeUserDetails).
                                addOnCompleteListener(task1 -> {
                                    if (task1.isSuccessful()) {
                                        // Send a verification email to the user
                                        user.sendEmailVerification();

                                        // Go to home screen
                                        String msg = getString(R.string.register_welcome);
                                        Toast.makeText(RegisterActivity.this,
                                                msg + username_commit, Toast.LENGTH_LONG).show();
                                        Intent intent = new Intent(RegisterActivity.this, HomeActivity.class);
                                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                                Intent.FLAG_ACTIVITY_CLEAR_TASK |
                                                Intent.FLAG_ACTIVITY_NEW_TASK);
                                        startActivity(intent);
                                        finish();
                                    } else {
                                        progress_circular.setVisibility(View.GONE);
                                        // String database_error = getString(R.string.database_error);
                                        String database_error = Objects.requireNonNull(task1.getException()).getMessage();
                                        Toast.makeText(RegisterActivity.this,
                                                database_error, Toast.LENGTH_LONG).show();
                                        user.delete();
                                    }
                                });
                    } else {
                        progress_circular.setVisibility(View.GONE);
                        try {
                            throw Objects.requireNonNull(task.getException());
                        } catch (FirebaseAuthUserCollisionException e) {
                            email.setError(getString(R.string.email_in_use));
                            email.requestFocus();
                            Toast.makeText(RegisterActivity.this,
                                    getString(R.string.email_already_in_use),
                                    Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Log.e(TAG, Objects.requireNonNull(e.getMessage()));
                            Toast.makeText(RegisterActivity.this,
                                    e.getMessage(), Toast.LENGTH_SHORT).show();
                            progress_circular.setVisibility(View.GONE);
                        }
                    }
                });
    }

    // Methods to validate each field of the registration screen
    private Boolean validateName() {
        String name_value = Objects.requireNonNull(name.getEditText()).getText().toString();
        String value_error = getString(R.string.empty_field);

        if (name_value.isEmpty()) {
            name.setError(value_error);
            return false;
        } else {
            name.setError(null);
            name.setErrorEnabled(false);
            return true;
        }
    }

    private Boolean validateEmail() {
        String email_value = Objects.requireNonNull(email.getEditText()).getText().toString();
        String value_error = getString(R.string.empty_field);
        String email_error = getString(R.string.invalid_email);
        String email_pattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+";

        if (email_value.isEmpty()){
            email.setError(value_error);
            return false;
        } else if (!email_value.matches(email_pattern)) {
            email.setError(email_error);
            return false;
        } else {
            email.setError(null);
            email.setErrorEnabled(false);
            return true;
        }
    }

    private Boolean validateUsername() {
        String username_value = Objects.requireNonNull(username.getEditText()).getText().toString();
        String value_error = getString(R.string.empty_field);
        String too_long_error = getString(R.string.too_long_username);
        String white_space_error = getString(R.string.no_white_space);
        String no_white_space = "\\A_\\w{6,20}\\z";

        if (username_value.isEmpty()){
            username.setError(value_error);
            return false;
        } else if (username_value.length() >= 15) {
            username.setError(too_long_error);
            return false;
        } else if (username_value.matches(no_white_space)) {
            username.setError(white_space_error);
            return false;
        } else {
            username.setError(null);
            username.setErrorEnabled(false);
            return true;
        }
    }

    private Boolean validatePassword1() {
        String password_value = Objects.requireNonNull(password.getEditText()).getText().toString();
        String value_error = getString(R.string.empty_field);
        String weak_password = getString(R.string.weak_password);
        String short_password = getString(R.string.short_password);

        String password_pattern = "^" +
                // "(?=.*[a-z])"   +      // At least one lowercase
                // "(?=\\S+$)"     +      // No whitespace
                // "(?=.*[@#$%^&+=])" +   // At least one special character
                "(?=.*[0-9])"      +      // At least one digit
                "(?=.*[a-zA-Z])"   +      // Any letter
                "(?=.*[A-Z])"      +      // At least one uppercase
                ".{6,}"            +      // At least six characters
                "$";

        if (password_value.isEmpty()) {
            password.setError(value_error);
            return false;
        } else if (password_value.matches("^[^0-9]*$")) {
            // Checks if the password contains only letters
            password.setError(weak_password);
            Toast.makeText(RegisterActivity.this, getText(R.string.no_number).toString(),
                    Toast.LENGTH_SHORT).show();
            return false;
        } else if (password_value.length() < 6) {
            password.setError(short_password);
            return false;
        } else if (!password_value.matches(password_pattern)) {
            password.setError(weak_password);
            return false;
        } else {
            password.setError(null);
            password.setErrorEnabled(false);
            return true;
        }
    }

    private Boolean validatePassword2() {
        String password_value = Objects.requireNonNull(password_confirm.getEditText()).getText().toString();
        String value_error = getString(R.string.empty_field);

        if (password_value.isEmpty()) {
            password_confirm.setError(value_error);
            return false;
        } else {
            password_confirm.setError(null);
            password_confirm.setErrorEnabled(false);
            return true;
        }
    }

    private Boolean checkIfPasswordsMatch() {
        String not_match = getString(R.string.not_match_password);
        String password1 = Objects.requireNonNull(password.getEditText()).getText().toString();
        String password2 = Objects.requireNonNull(password_confirm.getEditText()).getText().toString();

        if (!password1.equals(password2)) {
            password_confirm.setError(not_match);
            return  false;
        } else {
            password_confirm.setError(null);
            password_confirm.setErrorEnabled(false);
            return  true;
        }
    }


    // Go back to the login activity
    public void goToLoginActivity(View view) {
        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(intent);
    }
}