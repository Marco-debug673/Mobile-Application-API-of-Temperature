package com.example.temperature

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var tvTemperature: TextView
    private lateinit var etSymptom: TextInputEditText
    private lateinit var btnSave: MaterialButton
    private lateinit var tvSavedSymptom: TextView
    private lateinit var btnLogout: ImageButton
    private lateinit var tvUserEmail: TextView

    private lateinit var securePrefs: SharedPreferences
    private lateinit var auth: FirebaseAuth

    private val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
    private val apiKey = BuildConfig.MAP_API_KEY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        
        // Verificar autenticación
        val currentUser = auth.currentUser
        if (currentUser == null) {
            goToLogin()
            return
        }

        tvTemperature = findViewById(R.id.tvTemperature)
        etSymptom = findViewById(R.id.editTextText3)
        btnSave = findViewById(R.id.btnSave)
        tvSavedSymptom = findViewById(R.id.tvSavedSymptom)
        btnLogout = findViewById(R.id.btnLogout)
        tvUserEmail = findViewById(R.id.tvUserEmail)

        tvUserEmail.text = currentUser.email ?: "Sin correo"

        // 🔐 EncryptedSharedPreferences con recuperación robusta
        try {
            // Intentamos con un nombre nuevo para forzar llaves limpias
            initSecurePrefs("secure_prefs_v2")
        } catch (e: Exception) {
            Log.e("SecurityError", "Fallo inicial, intentando limpiar Keystore y archivos...", e)
            
            // Borrar usando la API oficial (minSdk 31 es seguro)
            deleteSharedPreferences("secure_prefs_v2")
            deleteSharedPreferences("secure_prefs")
            
            try {
                // Reintentar con otro nombre para asegurar que no hay residuos
                initSecurePrefs("secure_prefs_v3")
            } catch (e2: Exception) {
                Log.e("SecurityError", "Fallo persistente, usando fallback no cifrado", e2)
                // Fallback a preferencias normales para que la app no crashee
                securePrefs = getSharedPreferences("fallback_prefs", MODE_PRIVATE)
                Toast.makeText(this, "Seguridad limitada: Error de Keystore", Toast.LENGTH_LONG).show()
            }
        }

        loadSavedSymptom()

        btnSave.setOnClickListener {
            saveSymptom()
        }

        btnLogout.setOnClickListener {
            auth.signOut()
            goToLogin()
        }

        getWeather()
    }

    private fun initSecurePrefs(fileName: String) {
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        securePrefs = EncryptedSharedPreferences.create(
            this,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    // 🔐 Guarda cifrado
    private fun saveSymptom() {
        val symptomText = etSymptom.text.toString().trim()

        if (symptomText.isNotEmpty()) {

            Log.d("Encriptacion", "------------------------------------------")
            Log.d("Encriptacion", "Texto Original: $symptomText")

            if (::securePrefs.isInitialized) {
                securePrefs.edit()
                    .putString("last_symptom", symptomText)
                    .apply()
            }

            val textoParaFirebase = android.util.Base64.encodeToString(
                symptomText.toByteArray(),
                android.util.Base64.DEFAULT
            )

            tvSavedSymptom.text = symptomText
            etSymptom.setText("")
            etSymptom.clearFocus()

            val sentimiento = hashMapOf(
                "descripcion" to textoParaFirebase,
                "usuario" to (auth.currentUser?.email ?: "anonimo"),
                "fecha" to com.google.firebase.Timestamp.now()
            )

            db.collection("sentimientos")
                .add(sentimiento)
                .addOnSuccessListener {
                    Toast.makeText(this, "Enviado a Firebase", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Log.w("Firestore", "Error al añadir documento", e)
                    Toast.makeText(this, "Error al subir a la nube", Toast.LENGTH_SHORT).show()
                }

        } else {
            Toast.makeText(this, "Escribe algo antes de guardar", Toast.LENGTH_SHORT).show()
        }
    }

    // 🔐 Lee cifrado
    private fun loadSavedSymptom() {
        if (::securePrefs.isInitialized) {
            tvSavedSymptom.text =
                securePrefs.getString("last_symptom", "Ninguno")
        } else {
            tvSavedSymptom.text = "Error de acceso"
        }
    }

    // 🌤 Clima
    @SuppressLint("SetTextI18n")
    private fun getWeather() {
        if (apiKey.isBlank()) {
            tvTemperature.text = "API KEY vacía"
            return
        }

        thread {
            try {
                val cityQuery = URLEncoder.encode("Mexico City,MX", "UTF-8")
                val url = URL("https://api.openweathermap.org/data/2.5/weather?q=$cityQuery&units=metric&appid=$apiKey")

                val connection = url.openConnection() as HttpsURLConnection
                connection.requestMethod = "GET"

                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val response = stream.bufferedReader().use { it.readText() }
                
                val json = JSONObject(response)
                val temp = json.getJSONObject("main").getDouble("temp")

                runOnUiThread {
                    tvTemperature.text = "$temp °C"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvTemperature.text = "Error red"
                }
            }
        }
    }
}
