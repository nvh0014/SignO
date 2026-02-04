package com.android.signo.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.android.signo.R
import com.android.signo.databinding.ActivityMainBinding
import com.android.signo.login.LoginActivity
import com.android.signo.ui.crear.CrearActivity
import com.android.signo.ui.mantenimiento.MantenimientoActivity // Importamos la nueva actividad
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        if (auth.currentUser == null) {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        } else {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            setupNavigation()
        }
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNavView.setupWithNavController(navController)

        // --- MODIFICACIÓN DEL BOTÓN FLOTANTE ---
        // Ahora el botón llamará a la función que muestra el diálogo de selección.
        binding.fabAdd.setOnClickListener {
            showCreationDialog()
        }
    }

    /**
     * Muestra un diálogo de alerta para que el usuario elija qué desea crear:
     * un nuevo catastro o un nuevo mantenimiento.
     */
    private fun showCreationDialog() {
        val options = arrayOf("Nuevo Catastro", "Nuevo Mantenimiento")

        val builder = AlertDialog.Builder(this)
        builder.setTitle("¿Qué deseas crear?")
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> {
                    // Opción "Nuevo Catastro"
                    val intent = Intent(this, CrearActivity::class.java)
                    startActivity(intent)
                }
                1 -> {
                    // Opción "Nuevo Mantenimiento"
                    val intent = Intent(this, MantenimientoActivity::class.java)
                    startActivity(intent)
                }
            }
        }
        builder.show()
    }
}
