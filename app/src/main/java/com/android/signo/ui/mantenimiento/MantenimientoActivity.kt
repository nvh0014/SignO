package com.android.signo.ui.mantenimiento

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.signo.R
import com.android.signo.ui.crear.Catastro // Reutilizamos el modelo de Catastro
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Data class para el nuevo registro de Mantenimiento.
 * Incluye una referencia al ID del catastro al que pertenece.
 */
data class Mantenimiento(
    val catastroId: String = "", // ID del documento en la colección "catastros"
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
    private lateinit var toolbar: MaterialToolbar
    private lateinit var autoCompleteBuscarSenal: AutoCompleteTextView
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
    private var selectedCatastroId: String? = null // Almacenará el ID del catastro seleccionado
    private val catastroNameToIdMap = mutableMapOf<String, String>() // Mapa para buscar ID por nombre

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mantenimiento)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        checkUserAndInitialize()
    }

    private fun checkUserAndInitialize() {
        val user = auth.currentUser
        if (user == null) { /* ... Manejo de usuario no autenticado ... */ return }

        db.collection("users").document(user.uid).get().addOnSuccessListener {
            currentGroupId = it.getString("id_grupo")
            currentUserName = it.getString("name")
            if (!currentGroupId.isNullOrEmpty()) {
                initializeUI()
            } else {
                Toast.makeText(this, "Debes unirte a un grupo para registrar mantenimientos.", Toast.LENGTH_LONG).show()
                finish()
            }
        }.addOnFailureListener { /* ... Manejo de error ... */ }
    }

    private fun initializeUI() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Nuevo Mantenimiento"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Referencias a vistas del layout
        autoCompleteBuscarSenal = findViewById(R.id.auto_complete_buscar_senal)
        editTextNombreSenal = findViewById(R.id.edit_text_nombre_senal)
        editTextCallePrincipal = findViewById(R.id.edit_text_calle_principal)
        radioGroupEstado = findViewById(R.id.radiogroup_estado)
        checkboxPodado = findViewById(R.id.checkbox_podado)
        checkboxPintado = findViewById(R.id.checkbox_pintado)
        checkboxLimpieza = findViewById(R.id.checkbox_limpieza)
        editTextObservacion = findViewById(R.id.edit_text_observacion)
        buttonGuardar = findViewById(R.id.button_guardar_mantenimiento)

        setupCatastroSearch()

        buttonGuardar.setOnClickListener {
            if (validarCampos()) {
                saveMantenimientoData()
            }
        }
    }

    /**
     * Configura el AutoCompleteTextView para buscar señales en la colección "catastros".
     */
    private fun setupCatastroSearch() {
        if (currentGroupId == null) return

        db.collection("catastros")
            .whereEqualTo("groupId", currentGroupId)
            .get()
            .addOnSuccessListener { documents ->
                val catastroDisplayList = mutableListOf<String>()
                catastroNameToIdMap.clear()
                for (document in documents) {
                    val catastro = document.toObject(Catastro::class.java)
                    // Creamos un texto descriptivo para mostrar en el buscador
                    val displayText = "${catastro.nombreSenal} (ID: ${document.id})"
                    catastroDisplayList.add(displayText)
                    catastroNameToIdMap[displayText] = document.id
                }
                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, catastroDisplayList)
                autoCompleteBuscarSenal.setAdapter(adapter)
            }

        autoCompleteBuscarSenal.setOnItemClickListener { parent, _, position, _ ->
            val selectedDisplayText = parent.getItemAtPosition(position) as String
            selectedCatastroId = catastroNameToIdMap[selectedDisplayText]
            if (selectedCatastroId != null) {
                loadCatastroData(selectedCatastroId!!)
            }
        }
    }

    /**
     * Carga los datos del catastro seleccionado en los campos no editables.
     */
    private fun loadCatastroData(catastroId: String) {
        db.collection("catastros").document(catastroId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val catastro = document.toObject(Catastro::class.java)
                    catastro?.let {
                        editTextNombreSenal.setText(it.nombreSenal)
                        editTextCallePrincipal.setText(it.callePrincipal)
                    }
                } else {
                    Toast.makeText(this, "Error: No se encontró el catastro.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun validarCampos(): Boolean {
        if (selectedCatastroId == null) {
            Toast.makeText(this, "Debes seleccionar una señal del catastro", Toast.LENGTH_SHORT).show()
            return false
        }
        if (radioGroupEstado.checkedRadioButtonId == -1) {
            Toast.makeText(this, "Selecciona el estado de la señal", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    /**
     * Guarda el nuevo mantenimiento en la colección "mantenimientos".
     */
    private fun saveMantenimientoData() {
        val user = auth.currentUser
        if (user == null || currentGroupId == null || selectedCatastroId == null) return

        val estadoSeleccionado = findViewById<RadioButton>(radioGroupEstado.checkedRadioButtonId).text.toString()
        val trabajosSeleccionados = mutableListOf<String>().apply {
            if (checkboxPodado.isChecked) add("Podado")
            if (checkboxPintado.isChecked) add("Pintado")
            if (checkboxLimpieza.isChecked) add("Limpieza")
        }

        val mantenimiento = Mantenimiento(
            catastroId = selectedCatastroId!!,
            estado = estadoSeleccionado,
            trabajosRealizados = trabajosSeleccionados,
            observacion = editTextObservacion.text.toString().trim(),
            groupId = currentGroupId!!,
            userUid = user.uid,
            userName = currentUserName ?: "Desconocido"
        )

        db.collection("mantenimientos").add(mantenimiento)
            .addOnSuccessListener {
                Toast.makeText(this, "Mantenimiento guardado con éxito", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al guardar: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
