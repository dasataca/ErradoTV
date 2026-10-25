package com.gareca.tv

import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gareca.tv.auth.AuthManager
import com.gareca.tv.auth.AuthResult
import com.gareca.tv.updater.AppUpdater
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnGoogleLogin: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var tvErrorStatus: TextView

    private val googleAccountPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!accountName.isNullOrEmpty()) {
                performGoogleLogin(accountName)
            } else {
                showError("No se pudo obtener el correo de la cuenta seleccionada.")
            }
        } else {
            setLoading(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Si ya hay una sesión activa, saltamos directo a MainActivity
        if (AuthManager.isLoggedIn(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin)
        progressBar = findViewById(R.id.progressBar)
        tvErrorStatus = findViewById(R.id.tvErrorStatus)

        // Verificar versión en la web al iniciar la pantalla
        lifecycleScope.launch {
            val update = AppUpdater.checkForUpdates(this@LoginActivity)
            if (update != null) {
                AppUpdater.showUpdateDialog(this@LoginActivity, update)
            }
        }

        // Eventos
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString().trim()
            if (email.isEmpty()) {
                showError("Ingresa tu correo o usuario")
                etEmail.requestFocus()
                return@setOnClickListener
            }
            if (pass.isEmpty()) {
                showError("Ingresa tu contraseña")
                etPassword.requestFocus()
                return@setOnClickListener
            }
            performCredentialsLogin(email, pass)
        }

        btnGoogleLogin.setOnClickListener {
            launchGoogleAccountPicker()
        }

        // Foco inicial en el botón de Google si está vacío, o en email
        btnGoogleLogin.requestFocus()
    }

    private fun launchGoogleAccountPicker() {
        setLoading(true)
        tvErrorStatus.visibility = View.GONE

        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AccountManager.newChooseAccountIntent(
                    null, null, arrayOf("com.google"), null, null, null, null
                )
            } else {
                @Suppress("DEPRECATION")
                AccountManager.newChooseAccountIntent(
                    null, null, arrayOf("com.google"), false, null, null, null, null
                )
            }
            googleAccountPickerLauncher.launch(intent)
        } catch (e: Exception) {
            setLoading(false)
            showError("No se pudo abrir el selector de Google: ${e.message}. Puedes ingresar con credenciales arriba.")
        }
    }

    private fun performGoogleLogin(googleEmail: String) {
        setLoading(true)
        tvErrorStatus.visibility = View.GONE

        lifecycleScope.launch {
            when (val res = AuthManager.loginWithGoogle(this@LoginActivity, googleEmail)) {
                is AuthResult.Success -> {
                    Toast.makeText(this@LoginActivity, "¡Bienvenido, ${res.email}!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                }
                is AuthResult.AccountDisabled -> {
                    setLoading(false)
                    showError("⛔ TU CUENTA HA SIDO DESHABILITADA POR EL ADMINISTRADOR EN LA BASE DE DATOS.")
                }
                is AuthResult.DeviceLimitReached -> {
                    setLoading(false)
                    showError("⚠️ Límite de dispositivos simultáneos alcanzado para esta cuenta.")
                }
                is AuthResult.Error -> {
                    setLoading(false)
                    showError(res.message)
                }
            }
        }
    }

    private fun performCredentialsLogin(email: String, pass: String) {
        setLoading(true)
        tvErrorStatus.visibility = View.GONE

        lifecycleScope.launch {
            when (val res = AuthManager.loginWithEmailPassword(this@LoginActivity, email, pass)) {
                is AuthResult.Success -> {
                    Toast.makeText(this@LoginActivity, "¡Bienvenido a GarecaTV!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                }
                is AuthResult.AccountDisabled -> {
                    setLoading(false)
                    showError("⛔ TU CUENTA HA SIDO DESHABILITADA POR EL ADMINISTRADOR EN LA BASE DE DATOS.")
                }
                is AuthResult.DeviceLimitReached -> {
                    setLoading(false)
                    showError("⚠️ Límite de dispositivos simultáneos alcanzado para esta cuenta.")
                }
                is AuthResult.Error -> {
                    setLoading(false)
                    showError(res.message)
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !loading
        btnGoogleLogin.isEnabled = !loading
        etEmail.isEnabled = !loading
        etPassword.isEnabled = !loading
    }

    private fun showError(msg: String) {
        tvErrorStatus.text = msg
        tvErrorStatus.visibility = View.VISIBLE
    }
}
