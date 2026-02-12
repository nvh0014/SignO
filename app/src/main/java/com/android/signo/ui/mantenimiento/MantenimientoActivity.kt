package com.android.signo.ui.mantenimiento

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.signo.R
import com.android.signo.ui.crear.Catastro
import com.android.signo.utils.isNetworkAvailable // Importamos tu utilidad
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout // IMPORTANTE: Soluciona el error Unresolved reference
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Data class para el registro de Mantenimiento.
 */
data class Mantenimiento(
    val catastroId: String = "",
    val nombreSenal: String = "",
    val estado: String = "",
    val trabajosRealizados: List<String> = listOf(),
    val observacion: String = "",
    val groupId: String = "",
    val userUid: String = "",
    val userName: String = "",
    @ServerTimestamp val timestamp: Date? = null
)

class MantenimientoActivity : AppCompatActivity() {

    // --- VISTAS ---
    // Usamos TextInputLayout para controlar visualmente si está habilitado o no
    private lateinit var layoutNombreSenal: TextInputLayout
    private lateinit var layoutCallePrincipal: TextInputLayout

    private lateinit var toolbar: MaterialToolbar
    private lateinit var editTextBuscarSenal: TextInputEditText
    private lateinit var buttonBuscarSenal: Button
    private lateinit var editTextNombreSenal: TextInputEditText
    private lateinit var editTextCallePrincipal: TextInputEditText
    private lateinit var radioGroupEstado: RadioGroup
    private lateinit var checkboxPodado: CheckBox
    private lateinit var checkboxPintado: CheckBox
    private lateinit var checkboxLimpieza: CheckBox
    private lateinit var editTextObservacion: EditText
    private lateinit var buttonGuardar: Button

    // --- FIREBASE Y DATOS ---
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var currentGroupId: String? = null
    private var currentUserName: String? = null
    private var selectedCatastroId: String? = null
    private var editMantenimientoId: String? = null

    companion object {
        const val EDIT_MANTENIMIENTO_ID = "EDIT_MANTENIMIENTO_ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mantenimiento)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        editMantenimientoId = intent.getStringExtra(EDIT_MANTENIMIENTO_ID)

        checkUserAndInitialize()
    }

    private fun checkUserAndInitialize() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db.collection("users").document(user.uid).get().addOnSuccessListener { document ->
            currentGroupId = document.getString("id_grupo")
            currentUserName = document.getString("name")
            if (!currentGroupId.isNullOrEmpty()) {
                initializeUI()
            } else {
                Toast.makeText(this, "Debes unirte a un grupo para registrar mantenimientos.", Toast.LENGTH_LONG).show()
                finish()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error al verificar usuario.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun initializeUI() {
        // CORRECCIÓN PRINCIPAL: Especificamos el tipo <TextInputLayout>
        layoutNombreSenal = findViewById<TextInputLayout>(R.id.text_input_layout_nombre_senal)
        layoutCallePrincipal = findViewById<TextInputLayout>(R.id.text_input_layout_calle_principal)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Referencias a vistas
        editTextBuscarSenal = findViewById(R.id.edit_text_buscar_senal)
        buttonBuscarSenal = findViewById(R.id.button_buscar_senal)
        editTextNombreSenal = findViewById(R.id.edit_text_nombre_senal)
        editTextCallePrincipal = findViewById(R.id.edit_text_calle_principal)
        radioGroupEstado = findViewById(R.id.radiogroup_estado)
        checkboxPodado = findViewById(R.id.checkbox_podado)
        checkboxPintado = findViewById(R.id.checkbox_pintado)
        checkboxLimpieza = findViewById(R.id.checkbox_limpieza)
        editTextObservacion = findViewById(R.id.edit_text_observacion)
        buttonGuardar = findViewById(R.id.button_guardar_mantenimiento)

        if (editMantenimientoId != null) {
            supportActionBar?.title = "Editar Mantenimiento"
            loadMantenimientoData(editMantenimientoId!!)
        } else {
            supportActionBar?.title = "Nuevo Mantenimiento"
        }

        buttonBuscarSenal.setOnClickListener {
            val numeroId = editTextBuscarSenal.text.toString().trim()
            if (numeroId.isNotEmpty()) {
                // Buscamos asumiendo prefijo CAT_
                val catastroIdToSearch = "CAT_$numeroId"
                searchAndLoadCatastroData(catastroIdToSearch)
            } else {
                Toast.makeText(this, "Ingresa un número de ID.", Toast.LENGTH_SHORT).show()
            }
        }

        buttonGuardar.setOnClickListener {
            if (validarCampos()) {
                saveMantenimientoData()
            }
        }

        // Por defecto bloqueamos los campos manuales para obligar a usar el buscador
        enableManualInput(false)
    }

    private fun loadMantenimientoData(mantenimientoId: String) {
        db.collection("mantenimientos").document(mantenimientoId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val mantenimiento = document.toObject(Mantenimiento::class.java)
                    mantenimiento?.let {
                        // Intentamos cargar los datos del catastro asociado
                        searchAndLoadCatastroData(it.catastroId)
                        selectedCatastroId = it.catastroId

                        // Rellenamos el formulario de mantenimiento
                        when (it.estado) {
                            "Bueno" -> radioGroupEstado.check(R.id.radio_bueno)
                            "Regular" -> radioGroupEstado.check(R.id.radio_regular)
                            "Malo" -> radioGroupEstado.check(R.id.radio_malo)
                        }

                        checkboxPodado.isChecked = it.trabajosRealizados.contains("Podado")
                        checkboxPintado.isChecked = it.trabajosRealizados.contains("Pintado")
                        checkboxLimpieza.isChecked = it.trabajosRealizados.contains("Limpieza")

                        editTextObservacion.setText(it.observacion)
                    }
                } else {
                    Toast.makeText(this, "No se encontró el mantenimiento.", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al cargar datos.", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    /**
     * Busca un catastro. Si no hay red y no está en caché, permite ingreso manual.
     */
    private fun searchAndLoadCatastroData(catastroId: String) {
        if (currentGroupId == null) return

        db.collection("catastros").document(catastroId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists() && document.getString("groupId") == currentGroupId) {
                    // CASO 1: ENCONTRADO (Online o Caché)
                    val catastro = document.toObject(Catastro::class.java)
                    catastro?.let {
                        selectedCatastroId = document.id

                        editTextNombreSenal.setText(it.nombreSenal?.toString() ?: "")
                        editTextCallePrincipal.setText(it.callePrincipal?.toString() ?: "")

                        // Bloqueamos edición manual porque ya tenemos los datos oficiales
                        enableManualInput(false)

                        Toast.makeText(this, "Señal cargada.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // CASO 2: NO ENCONTRADO EN BD
                    handleNotFoundOrOffline(catastroId)
                }
            }
            .addOnFailureListener {
                // CASO 3: ERROR DE RED (Sin caché)
                handleNotFoundOrOffline(catastroId)
            }
    }

    private fun handleNotFoundOrOffline(catastroId: String) {
        if (!isNetworkAvailable(this)) {
            // MODO OFFLINE MANUAL
            selectedCatastroId = catastroId // Confiamos en el ID ingresado

            // Habilitamos campos para escritura manual
            enableManualInput(true)

            // Limpiamos para que el usuario escriba
            editTextNombreSenal.setText("")
            editTextCallePrincipal.setText("")
            editTextNombreSenal.requestFocus()

            Toast.makeText(this, "Modo Offline: Ingresa los datos manualmente.", Toast.LENGTH_LONG).show()
        } else {
            // MODO ONLINE: Si no existe, error normal.
            selectedCatastroId = null
            enableManualInput(false)
            editTextNombreSenal.setText("")
            editTextCallePrincipal.setText("")
            Toast.makeText(this, "No se encontró esa señal en el sistema.", Toast.LENGTH_LONG).show()
        }
    }

    private fun enableManualInput(enable: Boolean) {
        // Controlamos el contenedor completo para mejor feedback visual (Gris vs Blanco)
        layoutNombreSenal.isEnabled = enable
        layoutCallePrincipal.isEnabled = enable
    }

    private fun validarCampos(): Boolean {
        if (selectedCatastroId == null) {
            Toast.makeText(this, "Busca una señal o ingresa el ID en modo offline.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (radioGroupEstado.checkedRadioButtonId == -1) {
            Toast.makeText(this, "Selecciona el estado.", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun saveMantenimientoData() {
        val user = auth.currentUser
        if (user == null || currentGroupId == null || selectedCatastroId == null) return

        // Bloquear botón para evitar doble click
        buttonGuardar.isEnabled = false
        buttonGuardar.text = "Guardando..."

        val estadoSeleccionado = findViewById<RadioButton>(radioGroupEstado.checkedRadioButtonId).text.toString()
        val trabajosSeleccionados = mutableListOf<String>().apply {
            if (checkboxPodado.isChecked) add("Podado")
            if (checkboxPintado.isChecked) add("Pintado")
            if (checkboxLimpieza.isChecked) add("Limpieza")
        }

        val mantenimiento = Mantenimiento(
            catastroId = selectedCatastroId!!,
            nombreSenal = editTextNombreSenal.text.toString().trim(),
            estado = estadoSeleccionado,
            trabajosRealizados = trabajosSeleccionados,
            observacion = editTextObservacion.text.toString().trim(),
            groupId = currentGroupId!!,
            userUid = user.uid,
            userName = currentUserName ?: "Desconocido"
        )

        val hayInternet = isNetworkAvailable(this)

        if (editMantenimientoId != null) {
            // EDITAR
            db.collection("mantenimientos").document(editMantenimientoId!!).set(mantenimiento)
                .addOnSuccessListener {
                    if (!hayInternet) Toast.makeText(this, "Guardado localmente. Se subirá al conectar.", Toast.LENGTH_LONG).show()
                    else Toast.makeText(this, "Mantenimiento actualizado", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    buttonGuardar.isEnabled = true
                    buttonGuardar.text = "Guardar Mantenimiento"
                    Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // CREAR
            db.collection("mantenimientos").add(mantenimiento)
                .addOnSuccessListener {
                    if (!hayInternet) Toast.makeText(this, "Guardado localmente. Se subirá al conectar.", Toast.LENGTH_LONG).show()
                    else Toast.makeText(this, "Mantenimiento registrado", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    buttonGuardar.isEnabled = true
                    buttonGuardar.text = "Guardar Mantenimiento"
                    Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}