package com.android.signo.ui.inventario

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import com.android.signo.adapter.ReportAction
import com.android.signo.adapter.ReportsAdapter
import com.android.signo.databinding.FragmentInventarioBinding
import com.android.signo.model.Report
import com.android.signo.ui.crear.CrearActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class InventarioFragment : Fragment() {

    private var _binding: FragmentInventarioBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var reportsAdapter: ReportsAdapter
    private var allReports: List<Report> = emptyList()
    private var firestoreListener: ListenerRegistration? = null

    // Variables para la paginación
    private val reportsPerPage = 20
    private var currentPage = 1
    private var totalPages = 1
    private var lastVisible: DocumentSnapshot? = null
    private var firstVisible: DocumentSnapshot? = null
    private var isFetching = false


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInventarioBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearch()
        setupPagination()
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterReports(newText)
                return true
            }
        })
    }

    private fun filterReports(query: String?) {
        if (_binding == null) return
        val filteredList = if (query.isNullOrEmpty()) {
            allReports
        } else {
            val lowerCaseQuery = query.lowercase().trim()
            allReports.filter {
                it.nombreSenal.lowercase().contains(lowerCaseQuery) ||
                it.callePrincipal.lowercase().contains(lowerCaseQuery)
            }
        }
        reportsAdapter.updateData(filteredList)

        if (filteredList.isEmpty() && allReports.isNotEmpty()) {
            binding.tvStatusMessage.text = "No se encontraron resultados para \"$query\""
            binding.tvStatusMessage.visibility = View.VISIBLE
        } else {
            binding.tvStatusMessage.visibility = View.GONE
        }
    }

    private fun checkUserGroupAndLoadInventory() {
        val user = auth.currentUser
        if (user == null) {
            if (_binding == null) return
            showError("Usuario no autenticado.")
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatusMessage.text = "Cargando inventario..."
        binding.tvStatusMessage.visibility = View.VISIBLE
        binding.inventoryContent.visibility = View.GONE

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (_binding == null) return@addOnSuccessListener
                if (document != null && document.exists()) {
                    val groupId = document.getString("id_grupo")
                    if (groupId.isNullOrEmpty()) {
                        showError("No perteneces a ningún grupo para ver el inventario.")
                    } else {
                        binding.inventoryContent.visibility = View.VISIBLE
                        binding.tvStatusMessage.visibility = View.GONE
                        // Inicia la carga de datos desde la primera página
                        loadReports(groupId, true)
                    }
                } else {
                    showError("No se encontraron datos de tu usuario.")
                }
            }
            .addOnFailureListener { exception ->
                if (_binding == null) return@addOnFailureListener
                showError("Error al cargar datos: ${exception.message}")
            }
    }

    private fun setupRecyclerView() {
        reportsAdapter = ReportsAdapter(emptyList(), true) { report, action ->
            handleReportAction(report, action)
        }
        binding.recyclerViewReports.adapter = reportsAdapter
    }

    // Controladores de los botones de paginación
    private fun setupPagination() {
        binding.ibNextPage.setOnClickListener {
            if (currentPage < totalPages && !isFetching) {
                currentPage++
                checkUserGroupAndLoadInventory()
            }
        }
        binding.ibPrevPage.setOnClickListener {
            if (currentPage > 1 && !isFetching) {
                currentPage--
                checkUserGroupAndLoadInventory()
            }
        }
    }

    // Carga los reportes paginados
    private fun loadReports(groupId: String, isInitialLoad: Boolean) {
        if (isFetching) return
        isFetching = true
        binding.progressBar.visibility = View.VISIBLE

        var query = db.collection("reports")
            .whereEqualTo("groupId", groupId)
            .orderBy("timestamp", Query.Direction.DESCENDING)

        if (!isInitialLoad && lastVisible != null) {
            query = query.startAfter(lastVisible!!)
        }

        query.limit(reportsPerPage.toLong())
            .get()
            .addOnSuccessListener { documents ->
                if (_binding == null) return@addOnSuccessListener
                binding.progressBar.visibility = View.GONE
                isFetching = false

                if (documents.isEmpty) {
                    if(isInitialLoad) showStatusMessage("El inventario de tu grupo está vacío.")
                    updatePaginationUI()
                    return@addOnSuccessListener
                }

                lastVisible = documents.documents.lastOrNull()
                firstVisible = documents.documents.firstOrNull()
                allReports = documents.toObjects(Report::class.java)
                filterReports(binding.searchView.query.toString())

                // Actualiza el contador de páginas
                db.collection("reports").whereEqualTo("groupId", groupId).get().addOnSuccessListener { allDocs ->
                    totalPages = (allDocs.size() + reportsPerPage - 1) / reportsPerPage
                    updatePaginationUI()
                }
            }
            .addOnFailureListener { e ->
                if (_binding == null) return@addOnFailureListener
                binding.progressBar.visibility = View.GONE
                isFetching = false
                showError("Error al cargar el inventario: ${e.message}")
            }
    }

    // Actualiza los controles de la paginación
    private fun updatePaginationUI() {
        if (_binding == null) return
        binding.tvPageInfo.text = "Página $currentPage de $totalPages"
        binding.ibPrevPage.isEnabled = currentPage > 1
        binding.ibNextPage.isEnabled = currentPage < totalPages
    }


    private fun handleReportAction(report: Report, action: ReportAction) {
        when (action) {
            ReportAction.EDIT -> {
                val intent = Intent(activity, CrearActivity::class.java)
                intent.putExtra("EDIT_REPORT_DOC_ID", report.id_reporte)
                startActivity(intent)
            }
            ReportAction.DELETE -> {
                showDeleteConfirmationDialog(report)
            }
        }
    }

    private fun showDeleteConfirmationDialog(report: Report) {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Estás seguro de que deseas eliminar el reporte de '${report.nombreSenal}'? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteReport(report)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteReport(report: Report) {
        if (report.id_reporte.isEmpty()) {
            Toast.makeText(context, "Error: ID de reporte inválido.", Toast.LENGTH_SHORT).show()
            return
        }
        db.collection("reports").document(report.id_reporte)
            .delete()
            .addOnSuccessListener {
                if (_binding == null) return@addOnSuccessListener
                Toast.makeText(context, "Reporte eliminado con éxito", Toast.LENGTH_SHORT).show()
                // Recargar los reportes de la página actual
                checkUserGroupAndLoadInventory()
            }
            .addOnFailureListener { e ->
                if (_binding == null) return@addOnFailureListener
                Toast.makeText(context, "Error al eliminar el reporte: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showStatusMessage(message: String) {
        if (_binding == null) return
        binding.progressBar.visibility = View.GONE
        binding.recyclerViewReports.visibility = View.GONE
        binding.tvStatusMessage.text = message
        binding.tvStatusMessage.visibility = View.VISIBLE
    }
    
    private fun showError(message: String) {
        if (_binding == null) return
        binding.progressBar.visibility = View.GONE
        binding.inventoryContent.visibility = View.GONE
        binding.tvStatusMessage.text = message
        binding.tvStatusMessage.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        // Carga los reportes para la página actual al volver a la pantalla
        checkUserGroupAndLoadInventory()
    }

    override fun onPause() {
        super.onPause()
        firestoreListener?.remove()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        firestoreListener?.remove()
        _binding = null
    }
}
