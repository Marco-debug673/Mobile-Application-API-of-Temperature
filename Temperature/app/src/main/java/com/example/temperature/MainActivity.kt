package com.example.temperature

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
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

    private lateinit var securePrefs: SharedPreferences

    private val apiKey = BuildConfig.MAP_API_KEY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d("API_KEY", apiKey)

        tvTemperature = findViewById(R.id.tvTemperature)
        etSymptom = findViewById(R.id.editTextText3)
        btnSave = findViewById(R.id.btnSave)
        tvSavedSymptom = findViewById(R.id.tvSavedSymptom)

        // 🔐 EncryptedSharedPreferences
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        securePrefs = EncryptedSharedPreferences.create(
            this,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        loadSavedSymptom()

        btnSave.setOnClickListener {
            saveSymptom()
        }

        getWeather()
    }

    // 🔐 Guarda cifrado
    private fun saveSymptom() {
        val symptomText = etSymptom.text.toString().trim()

        if (symptomText.isNotEmpty()) {
            securePrefs.edit()
                .putString("last_symptom", symptomText)
                .apply()

            tvSavedSymptom.text = symptomText
            etSymptom.setText("")
            etSymptom.clearFocus()

        } else {
            Toast.makeText(this, "Escribe algo antes de guardar", Toast.LENGTH_SHORT).show()
        }
    }

    // 🔐 Lee cifrado
    private fun loadSavedSymptom() {
        tvSavedSymptom.text =
            securePrefs.getString("last_symptom", "Ninguno")
    }

    // 🌤 Clima arreglado
    @SuppressLint("SetTextI18n")
    private fun getWeather() {

        if (apiKey.isBlank()) {
            tvTemperature.text = "API KEY vacía"
            Log.e("ClimaAPI", "BuildConfig.MAPS_API_KEY vacío")
            return
        }

        thread {
            try {

                val cityQuery = URLEncoder.encode("Mexico City,MX", "UTF-8")

                val url = URL(
                    "https://api.openweathermap.org/data/2.5/weather" +
                            "?q=$cityQuery" +
                            "&units=metric" +
                            "&appid=$apiKey"
                )

                Log.d("ClimaAPI_URL", url.toString())

                val connection = url.openConnection() as HttpsURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val code = connection.responseCode
                Log.d("ClimaAPI_CODE", code.toString())

                val stream = if (code in 200..299)
                    connection.inputStream
                else
                    connection.errorStream

                val response = stream.bufferedReader().use { it.readText() }
                Log.d("ClimaAPI_RESP", response)

                val json = JSONObject(response)

                if (!json.has("main")) {
                    throw Exception("Respuesta sin campo main")
                }

                val temp = json.getJSONObject("main").getDouble("temp")

                runOnUiThread {
                    tvTemperature.text = "$temp °C"
                }

            } catch (e: Exception) {
                Log.e("ClimaAPI_ERROR", e.message ?: "error", e)

                runOnUiThread {
                    tvTemperature.text = "Error red"
                }
            }
        }
    }
}