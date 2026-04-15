package com.android.signo.ui.mantenimiento

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.android.signo.R
import com.android.signo.ui.crear.Catastro
import com.android.signo.utils.isNetworkAvailable
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// --- CLASES DE DATOS (IMPORTANTE: NO BORRAR ESTAS LÍNEAS) ---

// 1. Estructura de Mantenimiento (Soluciona los errores rojos)
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

// 2. Estructura para los resultados del buscador visual
data class SearchResult(
    val id: String,
    val nombre: String,
    val calle: String,
    val interseccion: String,
    val numeracion: String
)

class MantenimientoActivity : AppCompatActivity() {

    // --- VISTAS ---
    private lateinit var editTextBuscarCalle: TextInputEditText
    private lateinit var buttonBuscarCalle: Button
    private lateinit var editTextBuscarId: TextInputEditText
    private lateinit var buttonCargarId: Button

    private lateinit var layoutNombreSenal: TextInputLayout
    private lateinit var layoutCallePrincipal: TextInputLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var editTextNombreSenal: TextInputEditText
    private lateinit var editTextCallePrincipal: TextInputEditText
    private lateinit var radioGroupEstado: RadioGroup
    private lateinit var checkboxPodado: CheckBox
    private lateinit var checkboxPintado: CheckBox
    private lateinit var checkboxLimpieza: CheckBox
    private lateinit var editTextObservacion: EditText
    private lateinit var buttonGuardar: Button

    // --- FIREBASE ---
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
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid).get().addOnSuccessListener { document ->
            currentGroupId = document.getString("id_grupo")
            currentUserName = document.getString("name")
            if (!currentGroupId.isNullOrEmpty()) initializeUI() else finish()
        }.addOnFailureListener { finish() }
    }

    private fun initializeUI() {
        // Vinculación de Vistas
        editTextBuscarCalle = findViewById(R.id.edit_text_buscar_calle)
        buttonBuscarCalle = findViewById(R.id.button_buscar_calle)
        editTextBuscarId = findViewById(R.id.edit_text_buscar_id)
        buttonCargarId = findViewById(R.id.button_cargar_id)

        layoutNombreSenal = findViewById(R.id.text_input_layout_nombre_senal)
        layoutCallePrincipal = findViewById(R.id.text_input_layout_calle_principal)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        editTextNombreSenal = findViewById(R.id.edit_text_nombre_senal)
        editTextCallePrincipal = findViewById(R.id.edit_text_calle_principal)
        radioGroupEstado = findViewById(R.id.radiogroup_estado)
        checkboxPodado = findViewById(R.id.checkbox_podado)
        checkboxPintado = findViewById(R.id.checkbox_pintado)
        checkboxLimpieza = findViewById(R.id.checkbox_limpieza)
        editTextObservacion = findViewById(R.id.edit_text_observacion)
        buttonGuardar = findViewById(R.id.button_guardar_mantenimiento)

        // Si es edición, cargamos datos previos
        if (editMantenimientoId != null) {
            supportActionBar?.title = "Editar Mantenimiento"
            loadMantenimientoData(editMantenimientoId!!)
        }

        // --- LISTENER 1: BUSCAR POR CALLE (SIMPLE) ---
        buttonBuscarCalle.setOnClickListener {
            val query = editTextBuscarCalle.text.toString().trim()
            if (query.isNotEmpty()) {
                searchByCalle(query)
            } else {
                Toast.makeText(this, "Escribe el nombre de una calle", Toast.LENGTH_SHORT).show()
            }
        }

        // --- LISTENER 2: CARGAR POR ID ---
        buttonCargarId.setOnClickListener {
            val idNum = editTextBuscarId.text.toString().trim()
            if (idNum.isNotEmpty()) {
                searchAndLoadCatastroData("CAT_$idNum")
            } else {
                Toast.makeText(this, "Escribe el número del ID", Toast.LENGTH_SHORT).show()
            }
        }

        buttonGuardar.setOnClickListener { if (validarCampos()) saveMantenimientoData() }
        enableManualInput(false)
    }

    // --- LÓGICA DE BÚSQUEDA SIMPLE (SIN TILDES NI COSAS RARAS) ---
    private fun searchByCalle(calleQuery: String) {
        if (currentGroupId == null) return

        // Simplemente convertimos a mayúsculas para coincidir con tus datos
        val queryUpper = calleQuery.uppercase()

        db.collection("catastros")
            .whereEqualTo("groupId", currentGroupId)
            // BUSCAMOS EN EL CAMPO ORIGINAL: callePrincipal
            .whereGreaterThanOrEqualTo("callePrincipal", queryUpper)
            .whereLessThanOrEqualTo("callePrincipal", queryUpper + "\uf8ff")
            .limit(15)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(this, "No se encontraron resultados para: $calleQuery", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val results = documents.map { doc ->
                    SearchResult(
                        id = doc.id,
                        nombre = doc.getString("nombreSenal") ?: "S/N",
                        calle = doc.getString("callePrincipal") ?: "Sin Calle",
                        interseccion = doc.getString("interseccion") ?: "",
                        numeracion = doc.getString("numeracion") ?: ""
                    )
                }
                showSelectionDialog(results)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error de búsqueda: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showSelectionDialog(results: List<SearchResult>) {
        val adapter = SearchAdapter(this, results)

        AlertDialog.Builder(this)
            .setTitle("Seleccione la Señal")
            .setAdapter(adapter) { _, which ->
                val selectedItem = results[which]
                // Llenamos el ID abajo para que el usuario vea qué eligió
                editTextBuscarId.setText(selectedItem.id.replace("CAT_", ""))
                searchAndLoadCatastroData(selectedItem.id)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // --- ADAPTER INTERNO PARA EL DIÁLOGO ---
    private class SearchAdapter(context: Context, private val items: List<SearchResult>)
        : ArrayAdapter<SearchResult>(context, 0, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            // Intenta usar el layout bonito si existe, sino usa uno simple por defecto
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_search_result, parent, false)
            val item = items[position]

            val tvTitle = view.findViewById<TextView>(R.id.tv_search_title)
            val tvSubtitle = view.findViewById<TextView>(R.id.tv_search_subtitle)
            val tvDetails = view.findViewById<TextView>(R.id.tv_search_details)
            val container = view.findViewById<View>(R.id.container_search_result)

            if (tvTitle != null) {
                tvTitle.text = item.nombre
                tvSubtitle.text = item.calle
                val interseccionStr = if(item.interseccion.isNotEmpty()) "Esq: ${item.interseccion}" else "S/I"
                val numStr = if(item.numeracion.isNotEmpty()) " (#${item.numeracion})" else ""
                tvDetails.text = "$interseccionStr$numStr | ID: ${item.id}"
                container.setBackgroundColor(ContextCompat.getColor(context, R.color.color_catastro_background))
            } else {
                val tv = view.findViewById<TextView>(android.R.id.text1)
                if(tv != null) tv.text = "${item.nombre} - ${item.calle}"
            }

            return view
        }
    }

    private fun searchAndLoadCatastroData(catastroId: String) {
        db.collection("catastros").document(catastroId).get().addOnSuccessListener { document ->
            if (document.exists() && document.getString("groupId") == currentGroupId) {
                val catastro = document.toObject(Catastro::class.java)
                catastro?.let {
                    selectedCatastroId = document.id
                    editTextNombreSenal.setText(it.nombreSenal?.toString() ?: "")
                    editTextCallePrincipal.setText(it.callePrincipal?.toString() ?: "")
                    enableManualInput(false)
                    Toast.makeText(this, "Señal cargada: ${it.nombreSenal}", Toast.LENGTH_SHORT).show()
                }
            } else {
                handleNotFoundOrOffline(catastroId)
            }
        }.addOnFailureListener { handleNotFoundOrOffline(catastroId) }
    }

    private fun handleNotFoundOrOffline(catastroId: String) {
        if (!isNetworkAvailable(this)) {
            selectedCatastroId = catastroId
            enableManualInput(true)
            Toast.makeText(this, "Modo Offline: Ingrese datos manualmente.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "ID no encontrado en el sistema.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun enableManualInput(enable: Boolean) {
        layoutNombreSenal.isEnabled = enable
        layoutCallePrincipal.isEnabled = enable
    }

    private fun validarCampos(): Boolean {
        if (selectedCatastroId == null) return false
        if (radioGroupEstado.checkedRadioButtonId == -1) return false
        return true
    }

    private fun saveMantenimientoData() {
        val estado = findViewById<RadioButton>(radioGroupEstado.checkedRadioButtonId).text.toString()
        val trabajos = mutableListOf<String>().apply {
            if (checkboxPodado.isChecked) add("Podado")
            if (checkboxPintado.isChecked) add("Pintado")
            if (checkboxLimpieza.isChecked) add("Limpieza")
        }

        val mant = Mantenimiento(
            catastroId = selectedCatastroId!!,
            nombreSenal = editTextNombreSenal.text.toString(),
            estado = estado,
            trabajosRealizados = trabajos,
            observacion = editTextObservacion.text.toString(),
            groupId = currentGroupId!!,
            userUid = auth.currentUser!!.uid,
            userName = currentUserName ?: "Desconocido"
        )

        db.collection("mantenimientos").add(mant).addOnSuccessListener {
            Toast.makeText(this, "Guardado exitosamente", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadMantenimientoData(id: String) {
        db.collection("mantenimientos").document(id).get().addOnSuccessListener { doc ->
            // AQUÍ ES DONDE ANTES FALLABA: Ahora 'Mantenimiento' ya existe
            val m = doc.toObject(Mantenimiento::class.java) ?: return@addOnSuccessListener

            searchAndLoadCatastroData(m.catastroId)
            when (m.estado) {
                "Bueno" -> radioGroupEstado.check(R.id.radio_bueno)
                "Regular" -> radioGroupEstado.check(R.id.radio_regular)
                "Malo" -> radioGroupEstado.check(R.id.radio_malo)
            }
            checkboxPodado.isChecked = m.trabajosRealizados.contains("Podado")
            checkboxPintado.isChecked = m.trabajosRealizados.contains("Pintado")
            checkboxLimpieza.isChecked = m.trabajosRealizados.contains("Limpieza")
            editTextObservacion.setText(m.observacion)
        }
    }
}