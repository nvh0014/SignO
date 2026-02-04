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
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.signo.R
import com.android.signo.adapter.InventarioAdapter
import com.android.signo.databinding.FragmentExportarBinding
import com.android.signo.ui.crear.Catastro
import com.android.signo.ui.mantenimiento.Mantenimiento
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Enum para definir el tipo de datos a exportar
enum class ExportType { CATASTROS, MANTENIMIENTOS }

class ExportarFragment : Fragment() {

    private var _binding: FragmentExportarBinding? = null
    private val binding get() = _binding!!

    // Firebase y estado
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var currentGroupId: String? = null
    private var currentExportType = ExportType.CATASTROS // Por defecto, exportamos Catastros

    // Datos y UI
    private lateinit var inventarioAdapter: InventarioAdapter
    private var itemsToExport = mutableListOf<Any>()
    private var startDate: Date? = null
    private var endDate: Date? = null

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) exportDataToExcel()
        else Toast.makeText(requireContext(), "Permiso de escritura denegado.", Toast.LENGTH_SHORT).show()
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
        // Usamos el adaptador genérico. Las acciones de item no son necesarias aquí (showActions = false).
        inventarioAdapter = InventarioAdapter(emptyList(), false) { _, _ -> }
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
            itemsToExport.clear()
            inventarioAdapter.updateData(itemsToExport)
        }

        binding.etStartDate.setOnClickListener { showDatePickerDialog(true) }
        binding.etEndDate.setOnClickListener { showDatePickerDialog(false) }
        binding.btnFilter.setOnClickListener { loadData() }
        binding.btnExport.setOnClickListener { checkStoragePermissionAndExport() }
    }

    /**
     * Carga los datos desde Firestore (Catastros o Mantenimientos) según la selección
     * y el rango de fechas para mostrarlos antes de exportar.
     */
    private fun loadData() {
        if (startDate == null || endDate == null) {
            Toast.makeText(requireContext(), "Selecciona ambas fechas", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        val collectionPath = if (currentExportType == ExportType.CATASTROS) "catastros" else "mantenimientos"
        val objectClass = if (currentExportType == ExportType.CATASTROS) Catastro::class.java else Mantenimiento::class.java

        db.collection(collectionPath)
            .whereEqualTo("groupId", currentGroupId)
            .whereGreaterThanOrEqualTo("timestamp", startDate!!)
            .whereLessThanOrEqualTo("timestamp", endDate!!)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                itemsToExport.clear()
                itemsToExport.addAll(documents.map { it.toObject(objectClass) })
                inventarioAdapter.updateData(itemsToExport)
                binding.progressBar.visibility = View.GONE
                if (itemsToExport.isEmpty()) {
                    Toast.makeText(context, "No se encontraron datos en ese rango de fechas.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Error al cargar: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Función principal para exportar a Excel. Crea un libro de trabajo y llama a la función
     * correspondiente para rellenarlo según el tipo de datos.
     */
    private fun exportDataToExcel() {
        if (itemsToExport.isEmpty()) {
            Toast.makeText(requireContext(), "No hay datos filtrados para exportar.", Toast.LENGTH_SHORT).show()
            return
        }

        val workbook: Workbook = XSSFWorkbook()
        val sheetName = if (currentExportType == ExportType.CATASTROS) "Catastros" else "Mantenimientos"
        val sheet = workbook.createSheet(sheetName)

        // Llama a la función específica para crear el contenido del Excel
        if (currentExportType == ExportType.CATASTROS) {
            createCatastrosExcel(sheet, itemsToExport.filterIsInstance<Catastro>())
        } else {
            createMantenimientosExcel(sheet, itemsToExport.filterIsInstance<Mantenimiento>())
        }

        saveWorkbook(workbook)
    }

    /**
     * Rellena una hoja de Excel con los datos de Catastros.
     */
    private fun createCatastrosExcel(sheet: org.apache.poi.ss.usermodel.Sheet, data: List<Catastro>) {
        val headerRow = sheet.createRow(0)
        val headers = listOf("Nombre Señal", "Leyenda", "Calle Principal", "Intersección", "Numeración", "Cant. Postes", "Tipo Poste", "Medida", "Existencia", "Registrado por", "Fecha")
        headers.forEachIndexed { index, text -> headerRow.createCell(index).setCellValue(text) }

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        data.forEachIndexed { index, catastro ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(catastro.nombreSenal)
            row.createCell(1).setCellValue(catastro.leyenda)
            row.createCell(2).setCellValue(catastro.callePrincipal)
            row.createCell(3).setCellValue(catastro.interseccion)
            row.createCell(4).setCellValue(catastro.numeracion)
            row.createCell(5).setCellValue(catastro.cantidadPostes)
            row.createCell(6).setCellValue(catastro.tipoPoste)
            row.createCell(7).setCellValue(catastro.medida)
            row.createCell(8).setCellValue(catastro.existencia)
            row.createCell(9).setCellValue(catastro.userName)
            row.createCell(10).setCellValue(catastro.timestamp?.let { sdf.format(it) } ?: "N/A")
        }
    }

    /**
     * Rellena una hoja de Excel con los datos de Mantenimientos.
     */
    private fun createMantenimientosExcel(sheet: org.apache.poi.ss.usermodel.Sheet, data: List<Mantenimiento>) {
        val headerRow = sheet.createRow(0)
        val headers = listOf("ID Catastro", "Estado", "Trabajos Realizados", "Observación", "Realizado por", "Fecha")
        headers.forEachIndexed { index, text -> headerRow.createCell(index).setCellValue(text) }

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        data.forEachIndexed { index, mantenimiento ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(mantenimiento.catastroId)
            row.createCell(1).setCellValue(mantenimiento.estado)
            row.createCell(2).setCellValue(mantenimiento.trabajosRealizados.joinToString(", "))
            row.createCell(3).setCellValue(mantenimiento.observacion)
            row.createCell(4).setCellValue(mantenimiento.userName)
            row.createCell(5).setCellValue(mantenimiento.timestamp?.let { sdf.format(it) } ?: "N/A")
        }
    }

    // --- Boilerplate para permisos, guardado de archivos y selección de fechas (sin cambios mayores) ---

    private fun checkStoragePermissionAndExport() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportDataToExcel()
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                exportDataToExcel()
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun saveWorkbook(workbook: Workbook) {
        val sheetName = if (currentExportType == ExportType.CATASTROS) "Catastros" else "Mantenimientos"
        val fileName = "${sheetName}_SignO_${System.currentTimeMillis()}.xlsx"
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/SignO")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val resolver = requireContext().contentResolver
            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { out -> workbook.write(out) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(it, contentValues, null, null)
                }
                Toast.makeText(requireContext(), "Archivo guardado en Documentos/SignO", Toast.LENGTH_LONG).show()
            } ?: throw IOException("No se pudo crear el archivo en MediaStore.")
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Error al guardar el archivo: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun checkUserRoleAndInitialize() {
        val user = auth.currentUser
        if (user == null) { showError("Usuario no autenticado."); return }
        binding.progressBar.visibility = View.VISIBLE

        db.collection("users").document(user.uid).get().addOnSuccessListener {
            currentGroupId = it.getString("id_grupo") // Guardamos el id del grupo
            if (it.getString("rol") == "admin") {
                binding.progressBar.visibility = View.GONE
                binding.tvStatusMessage.visibility = View.GONE
                binding.exportContent.visibility = View.VISIBLE
            } else {
                showError("No tienes permisos de administrador para acceder aquí.")
            }
        }.addOnFailureListener { showError("Error al verificar permisos.") }
    }

    private fun showDatePickerDialog(isStartDate: Boolean) {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, y, m, d ->
            val selected = Calendar.getInstance().apply{ set(y, m, d) }.time
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            if (isStartDate) {
                startDate = selected
                binding.etStartDate.setText(sdf.format(selected))
            } else {
                endDate = selected
                binding.etEndDate.setText(sdf.format(selected))
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showError(message: String) {
        _binding?.apply {
            progressBar.visibility = View.GONE
            tvStatusMessage.text = message
            tvStatusMessage.visibility = View.VISIBLE
            exportContent.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
