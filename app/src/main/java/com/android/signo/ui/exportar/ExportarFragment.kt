package com.android.signo.ui.exportar

import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.signo.R
import com.android.signo.adapter.InventarioAdapter
import com.android.signo.databinding.FragmentExportarBinding
import com.android.signo.ui.crear.Catastro
import com.android.signo.ui.mantenimiento.Mantenimiento
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ExportType { CATASTROS, MANTENIMIENTOS }

class ExportarFragment : Fragment() {

    private var _binding: FragmentExportarBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var currentGroupId: String? = null
    private var currentExportType = ExportType.CATASTROS

    private lateinit var inventarioAdapter: InventarioAdapter
    private var previewItems = mutableListOf<Pair<String, Any>>()
    private var startDate: Date? = null
    private var endDate: Date? = null

    private val PREVIEW_LIMIT = 50L
    private val PREVIEW_THRESHOLD = 70L
    private val EXPORT_BATCH_SIZE = 500L

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            startCsvExport()
        } else {
            Toast.makeText(requireContext(), "Permiso de escritura denegado.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExportarBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        checkUserRoleAndInitialize()
        setupListeners()
    }

    private fun setupRecyclerView() {
        inventarioAdapter = InventarioAdapter(mutableListOf(), false) { _, _, _ -> }
        binding.rvItemsToExport.adapter = inventarioAdapter
        binding.rvItemsToExport.layoutManager = LinearLayoutManager(context)
    }

    private fun setupListeners() {
        binding.chipGroupExportType.setOnCheckedChangeListener { _, checkedId ->
            currentExportType = when (checkedId) {
                R.id.chip_catastros -> ExportType.CATASTROS
                R.id.chip_mantenimientos -> ExportType.MANTENIMIENTOS
                else -> ExportType.CATASTROS
            }
            clearPreview()
        }

        binding.etStartDate.setOnClickListener { showDatePickerDialog(isStart = true) }
        binding.etEndDate.setOnClickListener { showDatePickerDialog(isStart = false) }

        binding.btnFilter.setOnClickListener { loadPreviewData() }
        binding.btnExport.setOnClickListener { checkStoragePermissionAndExport() }
    }

    private fun showDatePickerDialog(isStart: Boolean) {
        val calendar = Calendar.getInstance()
        if (isStart && startDate != null) calendar.time = startDate!!
        else if (!isStart && endDate != null) calendar.time = endDate!!

        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            calendar.set(year, month, dayOfMonth)
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            if (isStart) {
                calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0)
                startDate = calendar.time
                binding.etStartDate.setText(sdf.format(startDate!!))
            } else {
                calendar.set(Calendar.HOUR_OF_DAY, 23); calendar.set(Calendar.MINUTE, 59); calendar.set(Calendar.SECOND, 59)
                endDate = calendar.time
                binding.etEndDate.setText(sdf.format(endDate!!))
            }
            binding.btnExport.isEnabled = false
        }

        DatePickerDialog(requireContext(), dateSetListener, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun clearPreview() {
        previewItems.clear()
        inventarioAdapter.setData(emptyList())
        binding.btnExport.isEnabled = false
    }

    private fun loadPreviewData() {
        if (!validateDates()) return
        clearPreview()
        setLoading(true, "Contando registros...")

        val query = buildQuery()
        if (query == null) {
            setLoading(false)
            binding.btnExport.isEnabled = false
            return
        }

        query.count().get(AggregateSource.SERVER).addOnSuccessListener { aggregateQuerySnapshot ->
            val count = aggregateQuerySnapshot.count
            if (count == 0L) {
                setLoading(false)
                Toast.makeText(requireContext(), "No se encontraron datos para exportar.", Toast.LENGTH_SHORT).show()
                binding.btnExport.isEnabled = false
                return@addOnSuccessListener
            }

            if (count > PREVIEW_THRESHOLD) {
                setLoading(false)
                Toast.makeText(requireContext(), "Se encontraron $count registros. Vista previa omitida para mejorar el rendimiento.", Toast.LENGTH_LONG).show()
                binding.btnExport.isEnabled = true
                return@addOnSuccessListener
            }

            setLoading(true, "Cargando vista previa...")
            query.limit(PREVIEW_LIMIT).get().addOnSuccessListener { documents ->
                setLoading(false)
                if (context == null) {
                    binding.btnExport.isEnabled = false
                    return@addOnSuccessListener
                }
                try {
                    val objectClass = if (currentExportType == ExportType.CATASTROS) Catastro::class.java else Mantenimiento::class.java
                    previewItems = documents.map { doc -> Pair(doc.id, doc.toObject(objectClass)) }.toMutableList()
                    inventarioAdapter.setData(previewItems)
                    binding.rvItemsToExport.scheduleLayoutAnimation()
                    binding.btnExport.isEnabled = true
                } catch (e: Exception) {
                    if (context != null) handleFirestoreError(e)
                    binding.btnExport.isEnabled = false
                }

            }.addOnFailureListener { exception ->
                setLoading(false)
                if (context != null) handleFirestoreError(exception)
                binding.btnExport.isEnabled = false
            }

        }.addOnFailureListener { exception ->
            setLoading(false)
            if (context != null) handleFirestoreError(exception)
            binding.btnExport.isEnabled = false
        }
    }

    private fun checkStoragePermissionAndExport() {
        if (!validateDates()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startCsvExport()
        } else {
            when (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                PackageManager.PERMISSION_GRANTED -> startCsvExport()
                else -> requestPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun startCsvExport() {
        setLoading(true, "Exportando... Por favor, espere.")

        lifecycleScope.launch(Dispatchers.IO) {
            val sheetName = if (currentExportType == ExportType.CATASTROS) "Catastros" else "Mantenimientos"
            val fileName = "${sheetName}_SignO_${System.currentTimeMillis()}.csv"

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/SignO")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val resolver = requireContext().contentResolver
            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)

            if (uri == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "No se pudo crear el archivo de exportación.", Toast.LENGTH_LONG).show()
                    setLoading(false)
                }
                return@launch
            }

            var documentsProcessed = 0
            try {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                        val headers = if (currentExportType == ExportType.CATASTROS) {
                            listOf("Nombre Señal", "Leyenda", "Calle Principal", "Intersección", "Numeración", "Cant. Postes", "Tipo Poste", "Medida", "Existencia", "Registrado por", "Fecha")
                        } else {
                            listOf("ID Catastro", "Estado", "Trabajos Realizados", "Observación", "Realizado por", "Fecha")
                        }
                        writer.write(headers.joinToString(","))
                        writer.newLine()

                        var lastLoadedDoc: DocumentSnapshot? = null
                        while (true) {
                            val query = buildQuery()?.limit(EXPORT_BATCH_SIZE)?.let {
                                if (lastLoadedDoc != null) it.startAfter(lastLoadedDoc!!) else it
                            } ?: break

                            val documents: QuerySnapshot = query.get().await()
                            if (documents.isEmpty) break

                            val objectClass = if (currentExportType == ExportType.CATASTROS) Catastro::class.java else Mantenimiento::class.java
                            for (doc in documents) {
                                val item = doc.toObject(objectClass)
                                val row = if (currentExportType == ExportType.CATASTROS) {
                                    createCatastroCsvRow(item as Catastro)
                                } else {
                                    createMantenimientoCsvRow(item as Mantenimiento)
                                }
                                writer.write(row)
                                writer.newLine()
                                documentsProcessed++
                            }

                            withContext(Dispatchers.Main) {
                                binding.tvStatusMessage.text = "Exportando... $documentsProcessed registros"
                            }

                            lastLoadedDoc = documents.documents.lastOrNull()
                            if (documents.size() < EXPORT_BATCH_SIZE) break
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }

                withContext(Dispatchers.Main) {
                    if (documentsProcessed > 0) {
                        Toast.makeText(requireContext(), "Archivo CSV guardado en Documentos/SignO", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireContext(), "No se encontraron datos para exportar.", Toast.LENGTH_SHORT).show()
                        resolver.delete(uri, null, null)
                    }
                }

            } catch (t: Throwable) {
                // If it fails, try to delete the partial file
                try {
                    resolver.delete(uri, null, null)
                } catch (e: Exception) { e.printStackTrace() }

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error en la exportación: ${t.message}", Toast.LENGTH_LONG).show()
                }
                t.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                }
            }
        }
    }

    private fun buildQuery(): Query? {
        if (currentGroupId == null || startDate == null || endDate == null) return null
        val collectionPath = if (currentExportType == ExportType.CATASTROS) "catastros" else "mantenimientos"
        return db.collection(collectionPath)
            .whereEqualTo("groupId", currentGroupId)
            .whereGreaterThanOrEqualTo("timestamp", startDate!!)
            .whereLessThanOrEqualTo("timestamp", endDate!!)
            .orderBy("timestamp", Query.Direction.ASCENDING)
    }

    // FIXED: Corrected escaping syntax for CSV
    private fun escapeCsvField(data: String?): String {
        if (data == null) return ""
        if (data.contains(",") || data.contains("\"") || data.contains("\n")) {
            return "\"${data.replace("\"", "\"\"")}\""
        }
        return data
    }

    private fun createCatastroCsvRow(catastro: Catastro): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val data = listOf(
            catastro.nombreSenal?.toString() ?: "",
            catastro.leyenda?.toString() ?: "",
            catastro.callePrincipal?.toString() ?: "",
            catastro.interseccion?.toString() ?: "",
            catastro.numeracion?.toString() ?: "",
            catastro.cantidadPostes?.toString() ?: "",
            catastro.tipoPoste?.toString() ?: "",
            catastro.medida?.toString() ?: "",
            catastro.existencia?.toString() ?: "",
            catastro.userName?.toString() ?: "",
            catastro.timestamp?.let { sdf.format(it) } ?: ""
        )
        return data.joinToString(",") { escapeCsvField(it) }
    }

    private fun createMantenimientoCsvRow(mantenimiento: Mantenimiento): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val data = listOf(
            mantenimiento.catastroId,
            mantenimiento.estado,
            mantenimiento.trabajosRealizados.joinToString("; "),
            mantenimiento.observacion,
            mantenimiento.userName,
            mantenimiento.timestamp?.let { sdf.format(it) } ?: ""
        )
        return data.joinToString(",") { escapeCsvField(it) }
    }

    private fun checkUserRoleAndInitialize() {
        val user = auth.currentUser
        if (user == null) {
            showError("Usuario no autenticado."); return
        }
        setLoading(true, "Verificando permisos...")
        db.collection("users").document(user.uid).get().addOnSuccessListener { documentSnapshot ->
            if (context == null) return@addOnSuccessListener

            val userRole = documentSnapshot.getString("rol")
            currentGroupId = documentSnapshot.getString("id_grupo")

            if (userRole == "admin") {
                if (currentGroupId == null) {
                    showError("No perteneces a ningún grupo.")
                } else {
                    setLoading(false)
                    binding.exportContent.visibility = View.VISIBLE
                    binding.tvStatusMessage.visibility = View.GONE
                }
            } else {
                showError("Solo los administradores pueden exportar datos. Contacte con su administrador.")
            }

        }.addOnFailureListener {
            if (context == null) return@addOnFailureListener
            showError("Error al verificar tu rol.")
        }
    }

    private fun validateDates(): Boolean {
        if (startDate == null || endDate == null) {
            Toast.makeText(requireContext(), "Selecciona ambas fechas", Toast.LENGTH_SHORT).show()
            return false
        }
        if (startDate!!.after(endDate!!)) {
            Toast.makeText(requireContext(), "La fecha de inicio no puede ser posterior a la fecha de fin.", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun handleFirestoreError(exception: Exception) {
        val message = if (exception.message?.contains("FAILED PRECONDITION") == true) {
            "Error: La consulta requiere un índice de Firestore que no existe. Revisa el Logcat para encontrar el enlace directo para crearlo."
        } else {
            "Error al cargar o deserializar: ${exception.message}"
        }
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.exportContent.visibility = View.GONE
        binding.tvStatusMessage.visibility = View.VISIBLE
        binding.tvStatusMessage.text = message
    }

    private fun setLoading(isLoading: Boolean, message: String = "") {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.tvStatusMessage.text = message
        binding.tvStatusMessage.visibility = if (isLoading && message.isNotEmpty()) View.VISIBLE else View.GONE
        binding.exportContent.alpha = if (isLoading) 0.5f else 1.0f
        binding.btnFilter.isEnabled = !isLoading

        if (isLoading) {
            binding.btnExport.isEnabled = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}