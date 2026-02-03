package com.android.signo.ui.inicio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.android.signo.adapter.ReportsAdapter
import com.android.signo.databinding.FragmentInicioBinding
import com.android.signo.model.Report
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class InicioFragment : Fragment() {

    private var _binding: FragmentInicioBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var reportsAdapter: ReportsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInicioBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        checkUserAndLoadReports()
    }

    private fun setupRecyclerView() {
        reportsAdapter = ReportsAdapter(emptyList(), false) { _, _ -> }
        binding.recyclerViewReports.adapter = reportsAdapter
    }

    private fun checkUserAndLoadReports() {
        val user = auth.currentUser
        if (user == null) {
            if (_binding == null) return
            showStatusMessage("No has iniciado sesión.")
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatusMessage.visibility = View.GONE
        binding.recyclerViewReports.visibility = View.GONE

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { userDoc ->
                if (_binding == null) return@addOnSuccessListener
                if (userDoc != null && userDoc.exists()) {
                    val groupId = userDoc.getString("id_grupo")
                    if (groupId.isNullOrEmpty()) {
                        showStatusMessage("No perteneces a ningún grupo. \nVe a la pestaña 'Cuenta' para unirte a uno.")
                    } else {
                        fetchReportsForGroup(groupId)
                    }
                } else {
                    showStatusMessage("No se encontraron datos de usuario.")
                }
            }
            .addOnFailureListener { e ->
                if (_binding == null) return@addOnFailureListener
                showStatusMessage("Error al obtener datos del usuario: ${e.message}")
            }
    }

    private fun fetchReportsForGroup(groupId: String) {
        db.collection("reports")
            .whereEqualTo("groupId", groupId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(3)
            .get()
            .addOnSuccessListener { documents ->
                if (_binding == null) return@addOnSuccessListener
                binding.progressBar.visibility = View.GONE
                if (documents.isEmpty) {
                    showStatusMessage("No hay reportes recientes en tu grupo.")
                } else {
                    val reports = documents.toObjects(Report::class.java)
                    reportsAdapter.updateData(reports)
                    binding.recyclerViewReports.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { e ->
                if (_binding == null) return@addOnFailureListener
                showStatusMessage("Error al cargar reportes: ${e.message}")
            }
    }

    private fun showStatusMessage(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.recyclerViewReports.visibility = View.GONE
        binding.tvStatusMessage.text = message
        binding.tvStatusMessage.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
             checkUserAndLoadReports()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
