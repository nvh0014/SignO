package com.android.signo.ui.crear

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.signo.utils.isNetworkAvailable
import com.android.signo.utils.normalizarParaBusqueda // <--- IMPORTANTE: Asegúrate de tener esto
import com.android.signo.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Data class para Catastro.
 * Se agrega calleNormalizada para permitir búsquedas precisas por calle.
 */
data class Catastro(
    @DocumentId val id: String = "",
    val nombreSenal: Any? = null,
    val leyenda: Any? = null,
    val callePrincipal: Any? = null,
    val interseccion: Any? = null,
    val numeracion: Any? = null,
    val cantidadPostes: Any? = null,
    val tipoPoste: Any? = null,
    val medida: Any? = null,
    val existencia: Any? = null,
    val estado: Any? = null,
    val calleNormalizada: String? = null, // <--- CAMPO NUEVO PARA BÚSQUEDA
    val groupId: String? = "",
    val userUid: String? = "",
    val userName: String? = "",
    @ServerTimestamp val timestamp: Date? = null
)

class CrearActivity : AppCompatActivity() {

    // --- VISTAS ---
    private lateinit var buttonGuardar: Button
    private lateinit var editTextNombreSenal: TextInputEditText
    private lateinit var editTextLeyenda: EditText
    private lateinit var editTextCallePrincipal: TextInputEditText
    private lateinit var editTextInterseccion: EditText
    private lateinit var editTextNumeracion: EditText
    private lateinit var editTextCantidadPostes: EditText
    private lateinit var autoCompleteTextViewTipoPoste: AutoCompleteTextView
    private lateinit var editTextMedida: EditText
    private lateinit var radioGroupExistencia: RadioGroup
    private lateinit var radioGroupEstado: RadioGroup
    private lateinit var toolbar: MaterialToolbar

    // --- LAYOUTS DE VALIDACIÓN ---
    private lateinit var textInputLayoutNombreSeñal: TextInputLayout
    private lateinit var textInputLayoutCallePrincipal: TextInputLayout
    private lateinit var textInputLayoutCantidadPostes: TextInputLayout
    private lateinit var textInputLayoutTipoPoste: TextInputLayout
    private lateinit var textInputLayoutMedida: TextInputLayout

    // --- FIREBASE Y DATOS ---
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var currentGroupId: String? = null
    private var currentUserName: String? = null
    private var editCatastroId: String? = null // ID del catastro a editar

    companion object {
        const val EDIT_CATASTRO_ID = "EDIT_CATASTRO_ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crear)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Se recibe el ID del catastro a editar (si existe)
        editCatastroId = intent.getStringExtra(EDIT_CATASTRO_ID)

        checkUserAndInitialize()
    }

    /**
     * Verifica si el usuario está autenticado y tiene un grupo asignado.
     */
    private fun checkUserAndInitialize() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Usuario no autenticado.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    currentGroupId = document.getString("id_grupo")
                    currentUserName = document.getString("name")
                    if (currentGroupId.isNullOrEmpty()) {
                        Toast.makeText(
                            this,
                            "Debes unirte a un grupo para crear/editar un catastro.",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    } else {
                        initializeUI()
                    }
                } else {
                    Toast.makeText(this, "No se encontraron datos de tu usuario.", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error al verificar tu estado: ${exception.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    /**
     * Inicializa todas las vistas de la UI y establece los listeners.
     */
    private fun initializeUI() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Referencias de vistas
        buttonGuardar = findViewById(R.id.button_guardar)
        editTextNombreSenal = findViewById(R.id.edit_text_nombre_senal)
        editTextLeyenda = findViewById(R.id.edit_text_leyenda)
        editTextCallePrincipal = findViewById(R.id.edit_text_calle_principal)
        editTextInterseccion = findViewById(R.id.edit_text_interseccion)
        editTextNumeracion = findViewById(R.id.edit_text_numeracion)
        editTextCantidadPostes = findViewById(R.id.edit_text_cantidad_postes)
        autoCompleteTextViewTipoPoste = findViewById(R.id.auto_complete_tipo_poste)
        editTextMedida = findViewById(R.id.edit_text_medida)
        radioGroupExistencia = findViewById(R.id.radiogroup_existencia)
        radioGroupEstado = findViewById(R.id.radiogroup_estado)

        textInputLayoutNombreSeñal = findViewById(R.id.text_input_layout_nombre_senal)
        textInputLayoutCallePrincipal = findViewById(R.id.text_input_layout_calle_principal)
        textInputLayoutCantidadPostes = findViewById(R.id.text_input_layout_cantidad_postes)
        textInputLayoutTipoPoste = findViewById(R.id.text_input_layout_tipo_poste)
        textInputLayoutMedida = findViewById(R.id.text_input_layout_medida)

        val tiposPoste = resources.getStringArray(R.array.tipos_poste)
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, tiposPoste)
        autoCompleteTextViewTipoPoste.setAdapter(adapter)

        if (editCatastroId != null) {
            toolbar.title = "Editar Catastro"
            buttonGuardar.text = "Actualizar Catastro"
            loadCatastroForEditing()
        } else {
            toolbar.title = "Nuevo Catastro"
            buttonGuardar.text = "Guardar Catastro"
        }

        buttonGuardar.setOnClickListener {
            if (validarCampos()) {
                saveCatastroData()
            }
        }
    }

    private fun loadCatastroForEditing() {
        editCatastroId?.let { id ->
            db.collection("catastros").document(id).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val catastro = document.toObject(Catastro::class.java)
                        catastro?.let { populateUIWithCatastro(it) }
                    } else {
                        Toast.makeText(this, "Error: No se encontró el catastro.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error al cargar los datos del catastro.", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun populateUIWithCatastro(catastro: Catastro) {
        editTextNombreSenal.setText(catastro.nombreSenal?.toString() ?: "")
        editTextLeyenda.setText(catastro.leyenda?.toString() ?: "")
        editTextCallePrincipal.setText(catastro.callePrincipal?.toString() ?: "")
        editTextInterseccion.setText(catastro.interseccion?.toString() ?: "")
        editTextNumeracion.setText(catastro.numeracion?.toString() ?: "")
        editTextCantidadPostes.setText(catastro.cantidadPostes?.toString() ?: "")
        autoCompleteTextViewTipoPoste.setText(catastro.tipoPoste?.toString() ?: "", false)
        editTextMedida.setText(catastro.medida?.toString() ?: "")

        if (catastro.existencia?.toString() == "Si") {
            radioGroupExistencia.check(R.id.radio_si_existe)
        } else if (catastro.existencia?.toString() == "No") {
            radioGroupExistencia.check(R.id.radio_no_existe)
        }

        if (catastro.estado?.toString() == "Buena") {
            radioGroupEstado.check(R.id.radio_buena)
        } else if (catastro.estado?.toString() == "Regular") {
            radioGroupEstado.check(R.id.radio_regular)
        } else if (catastro.estado?.toString() == "Mala") {
            radioGroupEstado.check(R.id.radio_mala)
        }
    }

    private fun validarCampos(): Boolean {
        var esValido = true

        textInputLayoutNombreSeñal.error = null
        textInputLayoutCallePrincipal.error = null
        textInputLayoutCantidadPostes.error = null
        textInputLayoutTipoPoste.error = null
        textInputLayoutMedida.error = null

        if (editTextNombreSenal.text.toString().trim().isEmpty()) {
            textInputLayoutNombreSeñal.error = "Este campo es obligatorio"
            esValido = false
        }
        if (editTextCallePrincipal.text.toString().trim().isEmpty()) {
            textInputLayoutCallePrincipal.error = "Este campo es obligatorio"
            esValido = false
        }
        if (editTextCantidadPostes.text.toString().trim().isEmpty()) {
            textInputLayoutCantidadPostes.error = "Este campo es obligatorio"
            esValido = false
        }
        if (autoCompleteTextViewTipoPoste.text.toString().trim().isEmpty()) {
            textInputLayoutTipoPoste.error = "Este campo es obligatorio"
            esValido = false
        }
        if (editTextMedida.text.toString().trim().isEmpty()) {
            textInputLayoutMedida.error = "Este campo es obligatorio"
            esValido = false
        }

        if (radioGroupExistencia.checkedRadioButtonId == -1) {
            Toast.makeText(this, "Selecciona la existencia de la señal", Toast.LENGTH_SHORT).show()
            esValido = false
        }

        if (radioGroupEstado.checkedRadioButtonId == -1) {
            Toast.makeText(this, "Selecciona el estado de la señal", Toast.LENGTH_SHORT).show()
            esValido = false
        }

        return esValido
    }

    /**
     * Guarda o actualiza un catastro en Firestore.
     * MODIFICADO: Ahora guarda 'calleNormalizada' para búsquedas.
     */
    private fun saveCatastroData() {
        val user = auth.currentUser
        if (user == null || currentGroupId == null) return

        buttonGuardar.isEnabled = false
        buttonGuardar.text = "Guardando..."

        val idExistencia = radioGroupExistencia.checkedRadioButtonId
        val idEstado = radioGroupEstado.checkedRadioButtonId
        val existenciaSeleccionada = if (idExistencia != -1) findViewById<RadioButton>(idExistencia).text.toString() else ""
        val estadoSeleccionado = if (idEstado != -1) findViewById<RadioButton>(idEstado).text.toString() else ""

        // 1. Obtenemos textos
        val nombre = editTextNombreSenal.text.toString().trim()
        val calle = editTextCallePrincipal.text.toString().trim()

        // 2. NORMALIZAMOS LA CALLE (Minúsculas, sin tildes)
        val calleLimpia = calle.normalizarParaBusqueda()

        // 3. Creamos el mapa de datos (incluyendo el campo nuevo)
        val catastroData = mutableMapOf(
            "nombreSenal" to nombre,
            "leyenda" to editTextLeyenda.text.toString().trim(),
            "callePrincipal" to calle,
            "calleNormalizada" to calleLimpia, // <--- AQUÍ ESTÁ EL CAMBIO CLAVE
            "interseccion" to editTextInterseccion.text.toString().trim(),
            "numeracion" to editTextNumeracion.text.toString().trim(),
            "cantidadPostes" to editTextCantidadPostes.text.toString().trim(),
            "tipoPoste" to autoCompleteTextViewTipoPoste.text.toString().trim(),
            "medida" to editTextMedida.text.toString().trim(),
            "existencia" to existenciaSeleccionada,
            "estado" to estadoSeleccionado,
            "groupId" to currentGroupId!!,
            "userUid" to user.uid,
            "userName" to (currentUserName ?: ""),
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        // --- LÓGICA DE GUARDADO (MANTENIDA IGUAL) ---

        if (editCatastroId != null && !editCatastroId!!.startsWith("TEMP_")) {
            // MODO EDICIÓN NORMAL
            db.collection("catastros").document(editCatastroId!!)
                .set(catastroData, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    Toast.makeText(this, "Catastro actualizado", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    buttonGuardar.isEnabled = true
                    buttonGuardar.text = "Guardar Catastro"
                    Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // MODO CREACIÓN (O edición de temporal)
            if (isNetworkAvailable(this)) {
                // --- CON INTERNET: Transacción y Contador ---
                val docRefContador = db.collection("counters").document("catastro_counter")
                val oldTempId = if (editCatastroId?.startsWith("TEMP_") == true) editCatastroId else null

                db.runTransaction { transaction ->
                    val snapshot = transaction.get(docRefContador)
                    val currentCount = snapshot.getLong("count") ?: 0
                    val nextCount = currentCount + 1
                    val nuevoId = "CAT_$nextCount"

                    transaction.update(docRefContador, "count", nextCount)

                    catastroData["isTemp"] = false
                    val nuevoCatastroRef = db.collection("catastros").document(nuevoId)
                    transaction.set(nuevoCatastroRef, catastroData)

                    nuevoId
                }.addOnSuccessListener { nuevoId ->
                    if (oldTempId != null) db.collection("catastros").document(oldTempId).delete()
                    Toast.makeText(this, "Guardado exitoso: $nuevoId", Toast.LENGTH_SHORT).show()
                    finish()
                }.addOnFailureListener {
                    buttonGuardar.isEnabled = true
                    buttonGuardar.text = "Guardar Catastro"
                    Toast.makeText(this, "Error online: ${it.message}", Toast.LENGTH_SHORT).show()
                }

            } else {
                // --- SIN INTERNET: Guardado local ---
                val tempId = if (editCatastroId?.startsWith("TEMP_") == true) editCatastroId!! else "TEMP_${System.currentTimeMillis()}"
                catastroData["isTemp"] = true

                db.collection("catastros").document(tempId)
                    .set(catastroData)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Sin conexión. Se subirá automáticamente cuando tengas internet.", Toast.LENGTH_LONG).show()
                        finish()
                    }
                    .addOnFailureListener {
                        buttonGuardar.isEnabled = true
                        buttonGuardar.text = "Guardar Catastro"
                    }
            }
        }
    }
}