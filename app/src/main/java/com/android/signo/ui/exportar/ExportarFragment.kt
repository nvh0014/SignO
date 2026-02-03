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
import com.android.signo.adapter.ReportsAdapter
import com.android.signo.databinding.FragmentExportarBinding
import com.android.signo.model.Report
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

class ExportarFragment : Fragment() {

    private var _binding: FragmentExportarBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var reportsAdapter: ReportsAdapter
    private var reportList = mutableListOf<Report>()
    private var startDate: Date? = null
    private var endDate: Date? = null

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            exportReportsToExcel()
        } else {
            Toast.makeText(requireContext(), "Permiso de escritura denegado.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExportarBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        checkUserRoleAndInitialize()

        binding.etStartDate.setOnClickListener { showDatePickerDialog(true) }
        binding.etEndDate.setOnClickListener { showDatePickerDialog(false) }
        binding.btnFilter.setOnClickListener { loadReports() }
        binding.btnExport.setOnClickListener { checkStoragePermissionAndExport() }
    }

    private fun setupRecyclerView() {
        reportsAdapter = ReportsAdapter(reportList, false) { _, _ -> }
        binding.rvReports.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = reportsAdapter
        }
    }

    private fun checkUserRoleAndInitialize() {
        val user = auth.currentUser
        if (user == null) {
            showError("Usuario no autenticado.")
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatusMessage.text = "Verificando permisos..."

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val role = document.getString("rol")
                    if (role == "admin") {
                        binding.progressBar.visibility = View.GONE
                        binding.tvStatusMessage.visibility = View.GONE
                        binding.exportContent.visibility = View.VISIBLE
                    } else {
                        showError("No tienes permisos de administrador para acceder aquí.")
                    }
                } else {
                    showError("No se encontraron datos de tu usuario.")
                }
            }
            .addOnFailureListener { exception ->
                showError("Error al verificar permisos: ${exception.message}")
            }
    }

    private fun showDatePickerDialog(isStartDate: Boolean) {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedDate = Calendar.getInstance().apply { set(year, month, dayOfMonth) }.time
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                if (isStartDate) {
                    startDate = selectedDate
                    binding.etStartDate.setText(sdf.format(selectedDate))
                } else {
                    endDate = selectedDate
                    binding.etEndDate.setText(sdf.format(selectedDate))
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun loadReports() {
        if (startDate == null || endDate == null) {
            Toast.makeText(requireContext(), "Selecciona ambas fechas", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        db.collection("reports")
            .whereGreaterThanOrEqualTo("timestamp", startDate!!)
            .whereLessThanOrEqualTo("timestamp", endDate!!)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                reportList.clear()
                for (document in documents) {
                    val report = document.toObject(Report::class.java)
                    reportList.add(report)
                }
                reportsAdapter.updateData(reportList)
                binding.progressBar.visibility = View.GONE
            }
            .addOnFailureListener { exception ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Error al cargar reportes: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun checkStoragePermissionAndExport() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportReportsToExcel()
        } else {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    exportReportsToExcel()
                }
                else -> {
                    requestPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
    }

    private fun exportReportsToExcel() {
        if (reportList.isEmpty()) {
            Toast.makeText(requireContext(), "No hay reportes para exportar.", Toast.LENGTH_SHORT).show()
            return
        }

        val workbook: Workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Reportes")

        val headerRow = sheet.createRow(0)
        headerRow.createCell(0).setCellValue("ID Reporte")
        headerRow.createCell(1).setCellValue("Nombre de la Señal")
        headerRow.createCell(2).setCellValue("Leyenda")
        headerRow.createCell(3).setCellValue("Calle Principal")
        headerRow.createCell(4).setCellValue("Intersección")
        headerRow.createCell(5).setCellValue("Numeración")
        headerRow.createCell(6).setCellValue("Cantidad de Postes")
        headerRow.createCell(7).setCellValue("Tipo de Poste")
        headerRow.createCell(8).setCellValue("Medida")
        headerRow.createCell(9).setCellValue("Existencia")
        headerRow.createCell(10).setCellValue("Estado")
        headerRow.createCell(11).setCellValue("Mantenciones")
        headerRow.createCell(12).setCellValue("Observación")
        headerRow.createCell(13).setCellValue("Reportado por")
        headerRow.createCell(14).setCellValue("Fecha")
        //headerRow.createCell(15).setCellValue("ID de Grupo")
        //headerRow.createCell(16).setCellValue("ID de Usuario")
        //headerRow.createCell(17).setCellValue("ID de Documento")

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        for ((index, report) in reportList.withIndex()) {
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue("Reporte_${(index + 1).toString().padStart(3, '0')}")
            row.createCell(1).setCellValue(report.nombreSenal)
            row.createCell(2).setCellValue(report.leyenda)
            row.createCell(3).setCellValue(report.callePrincipal)
            row.createCell(4).setCellValue(report.interseccion)
            row.createCell(5).setCellValue(report.numeracion)
            row.createCell(6).setCellValue(report.cantidadPostes.toDouble())
            row.createCell(7).setCellValue(report.tipoPoste)
            row.createCell(8).setCellValue(report.medida)
            row.createCell(9).setCellValue(report.existencia)
            row.createCell(10).setCellValue(report.estado)
            row.createCell(11).setCellValue(report.mantencion.joinToString())
            row.createCell(12).setCellValue(report.observacion)
            row.createCell(13).setCellValue(report.userName)
            row.createCell(14).setCellValue(report.timestamp?.let { sdf.format(it) } ?: "")
            //row.createCell(15).setCellValue(report.groupId)
            //row.createCell(16).setCellValue(report.userUid)
            //row.createCell(17).setCellValue(report.id)
        }

        val fileName = "Reportes_SignO_${System.currentTimeMillis()}.xlsx"
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
                resolver.openOutputStream(it)?.use { outputStream ->
                    workbook.write(outputStream)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(it, contentValues, null, null)
                }
                Toast.makeText(requireContext(), "Archivo guardado en Documentos/SignO", Toast.LENGTH_LONG).show()
            } ?: throw IOException("Failed to create new MediaStore record.")

        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Error al guardar el archivo: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.tvStatusMessage.text = message
        binding.tvStatusMessage.visibility = View.VISIBLE
        binding.exportContent.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
