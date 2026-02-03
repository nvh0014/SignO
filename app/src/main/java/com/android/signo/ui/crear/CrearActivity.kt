package com.android.signo.ui.crear

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
import com.android.signo.model.Report
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class CrearActivity : AppCompatActivity() {

    // Vistas del layout
    private lateinit var buttonGuardar: Button
    private lateinit var autoCompleteNombreSenal: AutoCompleteTextView
    private lateinit var editTextLeyenda: EditText
    // Se cambia el EditText por un AutoCompleteTextView para la calle principal
    private lateinit var autoCompleteCallePrincipal: AutoCompleteTextView
    private lateinit var editTextInterseccion: EditText
    private lateinit var editTextNumeracion: EditText
    private lateinit var editTextCantidadPostes: EditText
    private lateinit var autoCompleteTextViewTipoPoste: AutoCompleteTextView
    private lateinit var editTextMedida: EditText
    private lateinit var editTextObservacion: EditText
    private lateinit var radioGroupExistencia: RadioGroup
    private lateinit var radioGroupEstado: RadioGroup
    private lateinit var checkboxPodado: CheckBox
    private lateinit var checkboxPintado: CheckBox
    private lateinit var checkboxLimpieza: CheckBox
    private lateinit var toolbar: MaterialToolbar

    // Vistas de Layout para validación
    private lateinit var textInputLayoutNombreSeñal: TextInputLayout
    private lateinit var textInputLayoutCallePrincipal: TextInputLayout
    private lateinit var textInputLayoutCantidadPostes: TextInputLayout
    private lateinit var textInputLayoutTipoPoste: TextInputLayout
    private lateinit var textInputLayoutMedida: TextInputLayout

    // Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // Info
    private var currentGroupId: String? = null
    private var currentUserName: String? = null
    private var editingReportDocId: String? = null
    private val signalNameToReportIdMap = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crear)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        if (intent.hasExtra("EDIT_REPORT_DOC_ID")) {
            editingReportDocId = intent.getStringExtra("EDIT_REPORT_DOC_ID")
        }

        checkUserAndInitialize()
    }

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
                    this.currentGroupId = document.getString("id_grupo")
                    this.currentUserName = document.getString("name")
                    if (this.currentGroupId.isNullOrEmpty()) {
                        Toast.makeText(this, "Debes unirte a un grupo para crear un reporte.", Toast.LENGTH_LONG).show()
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

    private fun initializeUI() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        buttonGuardar = findViewById(R.id.button_guardar)
        autoCompleteNombreSenal = findViewById(R.id.auto_complete_nombre_senal)
        editTextLeyenda = findViewById(R.id.edit_text_leyenda)
        // Se actualiza la referencia al nuevo AutoCompleteTextView de la calle principal
        autoCompleteCallePrincipal = findViewById(R.id.auto_complete_calle_principal)
        editTextInterseccion = findViewById(R.id.edit_text_interseccion)
        editTextNumeracion = findViewById(R.id.edit_text_numeracion)
        editTextCantidadPostes = findViewById(R.id.edit_text_cantidad_postes)
        autoCompleteTextViewTipoPoste = findViewById(R.id.auto_complete_tipo_poste)
        editTextMedida = findViewById(R.id.edit_text_medida)
        editTextObservacion = findViewById(R.id.edit_text_observacion)
        radioGroupExistencia = findViewById(R.id.radiogroup_existencia)
        radioGroupEstado = findViewById(R.id.radiogroup_estado)
        checkboxPodado = findViewById(R.id.checkbox_podado)
        checkboxPintado = findViewById(R.id.checkbox_pintado)
        checkboxLimpieza = findViewById(R.id.checkbox_limpieza)

        textInputLayoutNombreSeñal = findViewById(R.id.text_input_layout_nombre_senal)
        textInputLayoutCallePrincipal = findViewById(R.id.text_input_layout_calle_principal)
        textInputLayoutCantidadPostes = findViewById(R.id.text_input_layout_cantidad_postes)
        textInputLayoutTipoPoste = findViewById(R.id.text_input_layout_tipo_poste)
        textInputLayoutMedida = findViewById(R.id.text_input_layout_medida)

        val tiposPoste = resources.getStringArray(R.array.tipos_poste)
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, tiposPoste)
        autoCompleteTextViewTipoPoste.setAdapter(adapter)

        // Configura el autocompletado para señales y calles
        setupSignalSearch()
        setupStreetSearch()

        if (editingReportDocId != null) {
            toolbar.title = "Editar Reporte"
            buttonGuardar.text = "Guardar Cambios"
            loadReportDataForEditing(editingReportDocId!!)
        } else {
            toolbar.title = "Nuevo Reporte"
            buttonGuardar.text = "Guardar Reporte"
        }

        buttonGuardar.setOnClickListener {
            if (validarCampos()) {
                saveReportData()
            }
        }
    }

    // Configura la búsqueda y autocompletado de señales desde Firestore
    private fun setupSignalSearch() {
        if (currentGroupId == null) return

        db.collection("reports")
            .whereEqualTo("groupId", currentGroupId)
            .get()
            .addOnSuccessListener { documents ->
                val signalNames = mutableListOf<String>()
                signalNameToReportIdMap.clear()
                for (document in documents) {
                    val report = document.toObject(Report::class.java)
                    signalNameToReportIdMap[report.nombreSenal] = document.id
                    signalNames.add(report.nombreSenal)
                }
                val signalAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, signalNames)
                autoCompleteNombreSenal.setAdapter(signalAdapter)
            }

        autoCompleteNombreSenal.setOnItemClickListener { parent, _, position, _ ->
            val selectedSignalName = parent.getItemAtPosition(position) as String
            val reportId = signalNameToReportIdMap[selectedSignalName]
            if (reportId != null) {
                editingReportDocId = reportId
                loadReportDataForEditing(reportId)
            }
        }
    }

    // Nueva función para configurar la búsqueda y autocompletado de calles
    private fun setupStreetSearch() {
        if (currentGroupId == null) return

        db.collection("reports")
            .whereEqualTo("groupId", currentGroupId)
            .get()
            .addOnSuccessListener { documents ->
                // Usamos un Set para obtener nombres de calles únicos automáticamente
                val streetNames = mutableSetOf<String>()
                for (document in documents) {
                    document.getString("callePrincipal")?.let {
                        if (it.isNotBlank()) {
                            streetNames.add(it)
                        }
                    }
                }
                val streetAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, streetNames.toList())
                autoCompleteCallePrincipal.setAdapter(streetAdapter)
            }
    }

    private fun loadReportDataForEditing(reportId: String) {
        db.collection("reports").document(reportId).get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    val report = documentSnapshot.toObject(Report::class.java)
                    if (report != null) {
                        autoCompleteNombreSenal.setText(report.nombreSenal, false)
                        editTextLeyenda.setText(report.leyenda)
                        // Se actualiza el AutoCompleteTextView de la calle principal
                        autoCompleteCallePrincipal.setText(report.callePrincipal)
                        editTextInterseccion.setText(report.interseccion)
                        editTextNumeracion.setText(report.numeracion)
                        editTextCantidadPostes.setText(report.cantidadPostes)
                        autoCompleteTextViewTipoPoste.setText(report.tipoPoste, false)
                        editTextMedida.setText(report.medida)
                        editTextObservacion.setText(report.observacion)

                        if (report.existencia == "Sí") radioGroupExistencia.check(R.id.radio_si_existe) else radioGroupExistencia.check(R.id.radio_no_existe)
                        if (report.estado == "Bueno") radioGroupEstado.check(R.id.radio_bueno) else if (report.estado == "Regular") radioGroupEstado.check(R.id.radio_regular) else radioGroupEstado.check(R.id.radio_malo)

                        checkboxPodado.isChecked = false
                        checkboxPintado.isChecked = false
                        checkboxLimpieza.isChecked = false
                        report.mantencion.forEach { mantencion ->
                            when (mantencion) {
                                "Podado" -> checkboxPodado.isChecked = true
                                "Pintado" -> checkboxPintado.isChecked = true
                                "Limpieza" -> checkboxLimpieza.isChecked = true
                            }
                        }
                        buttonGuardar.text = "Guardar Cambios"
                        toolbar.title = "Editar Reporte"
                    }
                } else {
                    Toast.makeText(this, "Error: No se encontró el reporte para editar.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al cargar el reporte: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun validarCampos(): Boolean {
        var esValido = true

        textInputLayoutNombreSeñal.error = null
        textInputLayoutCallePrincipal.error = null
        textInputLayoutCantidadPostes.error = null
        textInputLayoutTipoPoste.error = null
        textInputLayoutMedida.error = null

        if (autoCompleteNombreSenal.text.toString().trim().isEmpty()) {
            textInputLayoutNombreSeñal.error = "Este campo es obligatorio"
            esValido = false
        }
        // Se valida el AutoCompleteTextView de la calle principal
        if (autoCompleteCallePrincipal.text.toString().trim().isEmpty()) {
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

    private fun saveReportData() {
        val user = auth.currentUser
        if (user == null || currentGroupId == null) return

        val existenciaSeleccionada = findViewById<RadioButton>(radioGroupExistencia.checkedRadioButtonId).text.toString()
        val estadoSeleccionado = findViewById<RadioButton>(radioGroupEstado.checkedRadioButtonId).text.toString()
        val mantencionesSeleccionadas = mutableListOf<String>()
        if (checkboxPodado.isChecked) mantencionesSeleccionadas.add("Podado")
        if (checkboxPintado.isChecked) mantencionesSeleccionadas.add("Pintado")
        if (checkboxLimpieza.isChecked) mantencionesSeleccionadas.add("Limpieza")

        if (editingReportDocId != null) {
            val reportUpdates = mapOf<String, Any>(
                "nombreSenal" to autoCompleteNombreSenal.text.toString().trim(),
                "leyenda" to editTextLeyenda.text.toString().trim(),
                // Se obtiene el valor del AutoCompleteTextView de la calle principal
                "callePrincipal" to autoCompleteCallePrincipal.text.toString().trim(),
                "interseccion" to editTextInterseccion.text.toString().trim(),
                "numeracion" to editTextNumeracion.text.toString().trim(),
                "cantidadPostes" to editTextCantidadPostes.text.toString().trim(),
                "tipoPoste" to autoCompleteTextViewTipoPoste.text.toString(),
                "medida" to editTextMedida.text.toString().trim(),
                "existencia" to existenciaSeleccionada,
                "estado" to estadoSeleccionado,
                "mantencion" to mantencionesSeleccionadas,
                "observacion" to editTextObservacion.text.toString().trim(),
                "timestamp" to FieldValue.serverTimestamp()
            )

            db.collection("reports").document(editingReportDocId!!).update(reportUpdates)
                .addOnSuccessListener {
                    Toast.makeText(this, "Reporte actualizado con éxito", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error al actualizar: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } else {
            val report = Report(
                nombreSenal = autoCompleteNombreSenal.text.toString().trim(),
                leyenda = editTextLeyenda.text.toString().trim(),
                // Se obtiene el valor del AutoCompleteTextView de la calle principal
                callePrincipal = autoCompleteCallePrincipal.text.toString().trim(),
                interseccion = editTextInterseccion.text.toString().trim(),
                numeracion = editTextNumeracion.text.toString().trim(),
                cantidadPostes = editTextCantidadPostes.text.toString().trim(),
                tipoPoste = autoCompleteTextViewTipoPoste.text.toString(),
                medida = editTextMedida.text.toString().trim(),
                existencia = existenciaSeleccionada,
                estado = estadoSeleccionado,
                mantencion = mantencionesSeleccionadas,
                observacion = editTextObservacion.text.toString().trim(),
                groupId = currentGroupId!!,
                userUid = user.uid,
                userName = (currentUserName ?: "Desconocido")
            )
            db.collection("reports").add(report)
                .addOnSuccessListener {
                    Toast.makeText(this, "Reporte guardado con éxito", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error al guardar: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}
