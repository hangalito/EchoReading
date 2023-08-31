package com.echoreading

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class MainActivity : AppCompatActivity() {
    private var auth: FirebaseAuth? = null
    private var user: FirebaseUser? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        auth = FirebaseAuth.getInstance()
        user = auth!!.currentUser
        Handler().postDelayed({
            // Check if the user is already logged in
            if (user != null) {
                // Toast a welcome message for the user
                val userName = user!!.displayName
                val welcomeMessage = getString(R.string.welcome_user)
                Toast.makeText(this, welcomeMessage + userName, Toast.LENGTH_SHORT).show()

                // If the user is already logged in go to home activity
                val intent = Intent(this@MainActivity, HomeActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                // If the user is not logged in, then go to the login activity
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
                finish()
            }
        }, 3000)
    }
}