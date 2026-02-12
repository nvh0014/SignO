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
import com.android.signo.ui.mantenimiento.MantenimientoActivity
import com.google.firebase.auth.FirebaseAuth
// IMPORTANTE: Importa tus utilidades
import com.android.signo.utils.SyncHelper
import com.android.signo.utils.isNetworkAvailable

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

    // --- AQUÍ SE AGREGA EL ONRESUME ---
    override fun onResume() {
        super.onResume()

        // Verificamos si hay internet. Si hay, intentamos subir los catastros pendientes.
        if (isNetworkAvailable(this)) {
            // Usamos 'applicationContext' para evitar problemas si la actividad se cierra mientras sincroniza
            SyncHelper(applicationContext).syncPendingCatastros()
        }
    }
    // ----------------------------------

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.apply {
            bottomNavView.setupWithNavController(navController)

            fabAdd.setOnClickListener {
                showCreationDialog()
            }
        }
    }

    private fun showCreationDialog() {
        val options = arrayOf("Nuevo Catastro", "Nuevo Mantenimiento")

        val builder = AlertDialog.Builder(this)
        builder.setTitle("¿Qué deseas crear?")
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> {
                    val intent = Intent(this, CrearActivity::class.java)
                    startActivity(intent)
                }
                1 -> {
                    val intent = Intent(this, MantenimientoActivity::class.java)
                    startActivity(intent)
                }
            }
        }
        builder.show()
    }
}