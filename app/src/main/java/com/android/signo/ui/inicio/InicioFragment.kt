package com.android.signo.ui.inicio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.signo.adapter.InventarioAdapter
import com.android.signo.databinding.FragmentInicioBinding
import com.android.signo.ui.crear.Catastro
import com.android.signo.ui.mantenimiento.Mantenimiento
import com.android.signo.utils.normalizarParaBusqueda // Asegúrate de tener utils/StringUtils.kt creado
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot

/**
 * Fragmento para la pantalla de Inicio.
 * Muestra un resumen de la actividad reciente del grupo del usuario.
 * Incluye parche temporal para actualizar base de datos antigua.
 */
class InicioFragment : Fragment() {

    private var _binding: FragmentInicioBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
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

        // =========================================================================
        // === ZONA DE MANTENIMIENTO DE BASE DE DATOS ==============================
        // Descomenta la siguiente línea UNA SOLA VEZ para actualizar tus registros antiguos.
        // Cuando veas el mensaje de "¡Listo!", vuelve a comentarla o bórrala.

        //parcheActualizarBusqueda()

        // =========================================================================

        checkUserAndLoadRecentActivity()
    }

    private fun setupRecyclerView() {
        inventarioAdapter = InventarioAdapter(mutableListOf(), false) { _, _, _ -> }
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
                    showStatusMessage("No perteneces a ningún grupo. Ve a la pestaña 'Cuenta' para unirte a uno.")
                } else {
                    fetchRecentActivity(groupId)
                }
            }
            .addOnFailureListener { e -> showStatusMessage("Error al obtener datos del usuario: ${e.message}") }
    }

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

        Tasks.whenAllSuccess<QuerySnapshot>(recentCatastrosQuery, recentMantenimientosQuery).addOnSuccessListener { results ->
            // Verificamos si el binding aún existe antes de usarlo (evita crash si cambias de pantalla rápido)
            if (_binding == null) return@addOnSuccessListener

            binding.progressBar.visibility = View.GONE

            val catastros = results[0]!!.map { doc ->
                Pair(doc.id, doc.toObject(Catastro::class.java))
            }

            val mantenimientos = results[1]!!.map { doc ->
                Pair(doc.id, doc.toObject(Mantenimiento::class.java))
            }

            val combinedList = (catastros + mantenimientos).toMutableList()

            combinedList.sortByDescending { item ->
                when (val dataObject = item.second) {
                    is Catastro -> dataObject.timestamp
                    is Mantenimiento -> dataObject.timestamp
                    else -> null
                }
            }

            val recentItems = combinedList.take(3)

            if (recentItems.isEmpty()) {
                showStatusMessage("No hay actividad reciente en tu grupo.")
            } else {
                inventarioAdapter.setData(recentItems)
                binding.recyclerViewReports.visibility = View.VISIBLE
                binding.tvStatusMessage.visibility = View.GONE
            }
        }.addOnFailureListener { e ->
            if (_binding != null) {
                showStatusMessage("Error al cargar la actividad reciente: ${e.message}")
            }
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

    // --- FUNCIÓN TEMPORAL PARA ACTUALIZAR DB (SCRIPT) ---
    private fun parcheActualizarBusqueda() {
        val user = auth.currentUser
        if (user == null) return

        binding.progressBar.visibility = View.VISIBLE
        Toast.makeText(context, "Actualizando calles...", Toast.LENGTH_SHORT).show()

        // Recuerda poner las reglas en TRUE momentáneamente en Firebase si tienes problemas de permisos
        db.collection("catastros").get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    val calle = doc.get("callePrincipal")?.toString() ?: ""

                    // CREAMOS LA CALLE NORMALIZADA
                    val calleNorm = calle.normalizarParaBusqueda()

                    db.collection("catastros").document(doc.id)
                        .update("calleNormalizada", calleNorm)
                }
                Toast.makeText(context, "¡Calles Actualizadas!", Toast.LENGTH_LONG).show()
                binding.progressBar.visibility = View.GONE
            }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            checkUserAndLoadRecentActivity()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}