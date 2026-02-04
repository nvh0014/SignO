package com.android.signo.ui.inicio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.signo.adapter.InventarioAdapter
import com.android.signo.databinding.FragmentInicioBinding
import com.android.signo.ui.crear.Catastro
import com.android.signo.ui.mantenimiento.Mantenimiento
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Date

class InicioFragment : Fragment() {

    private var _binding: FragmentInicioBinding? = null
    private val binding get() = _binding!!

    // Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // Usamos el nuevo adaptador genérico
    private lateinit var inventarioAdapter: InventarioAdapter

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
        checkUserAndLoadRecentActivity()
    }

    private fun setupRecyclerView() {
        // Inicializamos el InventarioAdapter. No necesita acciones de clic aquí (showActions = false)
        inventarioAdapter = InventarioAdapter(emptyList(), false) { _, _ -> }
        binding.recyclerViewReports.adapter = inventarioAdapter
        binding.recyclerViewReports.layoutManager = LinearLayoutManager(context)
    }

    private fun checkUserAndLoadRecentActivity() {
        val user = auth.currentUser
        if (user == null) {
            showStatusMessage("No has iniciado sesión.")
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { userDoc ->
                val groupId = userDoc.getString("id_grupo")
                if (groupId.isNullOrEmpty()) {
                    showStatusMessage("No perteneces a ningún grupo. \nVe a la pestaña 'Cuenta' para unirte a uno.")
                } else {
                    fetchRecentActivity(groupId)
                }
            }
            .addOnFailureListener { e -> showStatusMessage("Error al obtener datos del usuario: ${e.message}") }
    }

    /**
     * Obtiene los últimos items de Catastros y Mantenimientos, los combina,
     * los ordena y muestra los 3 más recientes en total.
     */
    private fun fetchRecentActivity(groupId: String) {
        val recentCatastrosQuery = db.collection("catastros")
            .whereEqualTo("groupId", groupId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(3)
            .get()

        val recentMantenimientosQuery = db.collection("mantenimientos")
            .whereEqualTo("groupId", groupId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(3)
            .get()

        // Combinamos ambas tareas
        Tasks.whenAllSuccess<Any>(recentCatastrosQuery, recentMantenimientosQuery).addOnSuccessListener { results ->
            binding.progressBar.visibility = View.GONE

            val catastros = (results[0] as com.google.firebase.firestore.QuerySnapshot).toObjects(Catastro::class.java)
            val mantenimientos = (results[1] as com.google.firebase.firestore.QuerySnapshot).toObjects(Mantenimiento::class.java)

            val combinedList = (catastros + mantenimientos).toMutableList()

            // Ordenamos la lista combinada por fecha de forma descendente (los más nuevos primero)
            combinedList.sortByDescending { item ->
                when(item) {
                    is Catastro -> item.timestamp
                    is Mantenimiento -> item.timestamp
                    else -> null
                }
            }

            // Tomamos los 3 más recientes y los pasamos al adaptador
            val recentItems = combinedList.take(3)

            if (recentItems.isEmpty()) {
                showStatusMessage("No hay actividad reciente en tu grupo.")
            } else {
                inventarioAdapter.updateData(recentItems)
                binding.recyclerViewReports.visibility = View.VISIBLE
                binding.tvStatusMessage.visibility = View.GONE
            }
        }.addOnFailureListener { e ->
            showStatusMessage("Error al cargar la actividad reciente: ${e.message}")
        }
    }

    private fun showStatusMessage(message: String) {
        _binding?.apply {
            progressBar.visibility = View.GONE
            recyclerViewReports.visibility = View.GONE
            tvStatusMessage.text = message
            tvStatusMessage.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        checkUserAndLoadRecentActivity()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
