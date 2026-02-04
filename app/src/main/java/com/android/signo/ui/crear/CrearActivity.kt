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
import com.android.signo.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Data class para representar la estructura de un Catastro en Firestore.
 * Esto nos permite tener un modelo de datos limpio y separado de los mantenimientos.
 */
data class Catastro(
    val nombreSenal: String = "",
    val leyenda: String = "",
    val callePrincipal: String = "",
    val interseccion: String = "",
    val numeracion: String = "",
    val cantidadPostes: String = "",
    val tipoPoste: String = "",
    val medida: String = "",
    val existencia: String = "",
    val groupId: String = "",
    val userUid: String = "",
    val userName: String = "",
    @ServerTimestamp val timestamp: Date? = null
)

class CrearActivity : AppCompatActivity() {

    // --- VISTAS ---
    // Se han actualizado las vistas para reflejar los cambios en el layout.
    // Se eliminaron las vistas relacionadas con mantenimiento.
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
    private lateinit var toolbar: MaterialToolbar

    // --- LAYOUTS DE VALIDACIÓN ---
    private lateinit var textInputLayoutNombreSeñal: TextInputLayout
    private lateinit var textInputLayoutCallePrincipal: TextInputLayout
    private lateinit var textInputLayoutCantidadPostes: TextInputLayout
    private lateinit var textInputLayoutTipoPoste: TextInputLayout
    private lateinit var textInputLayoutMedida: TextInputLayout

    // --- FIREBASE Y DATOS DE USUARIO ---
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var currentGroupId: String? = null
    private var currentUserName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crear)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Se eliminó la lógica para recibir "EDIT_REPORT_DOC_ID"
        checkUserAndInitialize()
    }

    /**
     * Verifica si el usuario está autenticado y tiene un grupo asignado.
     * Si todo es correcto, inicializa la UI.
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
                        Toast.makeText(this, "Debes unirte a un grupo para crear un catastro.", Toast.LENGTH_LONG).show()
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
     * La lógica de edición y autocompletado ha sido eliminada.
     */
    private fun initializeUI() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Actualización de referencias de vistas
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

        textInputLayoutNombreSeñal = findViewById(R.id.text_input_layout_nombre_senal)
        textInputLayoutCallePrincipal = findViewById(R.id.text_input_layout_calle_principal)
        textInputLayoutCantidadPostes = findViewById(R.id.text_input_layout_cantidad_postes)
        textInputLayoutTipoPoste = findViewById(R.id.text_input_layout_tipo_poste)
        textInputLayoutMedida = findViewById(R.id.text_input_layout_medida)

        // Configuración del menú desplegable para tipos de poste
        val tiposPoste = resources.getStringArray(R.array.tipos_poste)
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, tiposPoste)
        autoCompleteTextViewTipoPoste.setAdapter(adapter)

        // Se establece el título y texto del botón para "Nuevo Catastro"
        toolbar.title = "Nuevo Catastro"
        buttonGuardar.text = "Guardar Catastro"

        buttonGuardar.setOnClickListener {
            if (validarCampos()) {
                saveCatastroData()
            }
        }
    }

    /**
     * Valida que los campos obligatorios del catastro no estén vacíos.
     * Se eliminó la validación para los campos de mantenimiento.
     */
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

        return esValido
    }

    /**
     * Guarda los datos del formulario como un nuevo documento en la colección "catastros".
     * Esta función reemplaza la lógica anterior que guardaba "reports".
     */
    private fun saveCatastroData() {
        val user = auth.currentUser
        if (user == null || currentGroupId == null) {
            Toast.makeText(this, "No se puede guardar: Usuario o grupo no identificado.", Toast.LENGTH_LONG).show()
            return
        }

        val existenciaSeleccionada = findViewById<RadioButton>(radioGroupExistencia.checkedRadioButtonId).text.toString()

        // Creación del objeto Catastro con los datos del formulario
        val catastro = Catastro(
            nombreSenal = editTextNombreSenal.text.toString().trim(),
            leyenda = editTextLeyenda.text.toString().trim(),
            callePrincipal = editTextCallePrincipal.text.toString().trim(),
            interseccion = editTextInterseccion.text.toString().trim(),
            numeracion = editTextNumeracion.text.toString().trim(),
            cantidadPostes = editTextCantidadPostes.text.toString().trim(),
            tipoPoste = autoCompleteTextViewTipoPoste.text.toString(),
            medida = editTextMedida.text.toString().trim(),
            existencia = existenciaSeleccionada,
            groupId = currentGroupId!!,
            userUid = user.uid,
            userName = (currentUserName ?: "Desconocido")
        )

        // Se guarda el objeto en la nueva colección "catastros"
        db.collection("catastros").add(catastro)
            .addOnSuccessListener {
                Toast.makeText(this, "Catastro guardado con éxito", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al guardar el catastro: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
