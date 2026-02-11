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
import com.google.firebase.firestore.QuerySnapshot

/**
 * Fragmento para la pantalla de Inicio.
 * Muestra un resumen de la actividad reciente del grupo del usuario,
 * combinando los últimos Catastros y Mantenimientos.
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
        checkUserAndLoadRecentActivity()
    }

    /**
     * Configura el RecyclerView con su adaptador.
     * El adaptador se inicializa vacío y sin acciones de clic (edición/eliminación).
     */
    private fun setupRecyclerView() {
        // Inicializamos el InventarioAdapter con una lista mutable vacía.
        // 'showActions' es 'false' porque en la pantalla de inicio no se permiten estas operaciones.
        inventarioAdapter = InventarioAdapter(mutableListOf(), false) { _, _, _ -> }
        binding.recyclerViewReports.adapter = inventarioAdapter
        binding.recyclerViewReports.layoutManager = LinearLayoutManager(context)
    }

    /**
     * Verifica si el usuario ha iniciado sesión y pertenece a un grupo.
     * Si es así, inicia la carga de la actividad reciente.
     */
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

    /**
     * Obtiene los 3 Catastros y 3 Mantenimientos más recientes del grupo,
     * los combina, los ordena por fecha y muestra los 3 resultados más nuevos en total.
     * Este enfoque es eficiente para obtener un resumen rápido sin cargar todos los datos.
     */
    private fun fetchRecentActivity(groupId: String) {
        // Consulta para obtener los 3 catastros más recientes.
        val recentCatastrosQuery = db.collection("catastros")
            .whereEqualTo("groupId", groupId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(3)
            .get()

        // Consulta para obtener los 3 mantenimientos más recientes.
        val recentMantenimientosQuery = db.collection("mantenimientos")
            .whereEqualTo("groupId", groupId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(3)
            .get()

        // Usamos Tasks.whenAllSuccess para ejecutar ambas consultas en paralelo y esperar a que ambas terminen.
        Tasks.whenAllSuccess<QuerySnapshot>(recentCatastrosQuery, recentMantenimientosQuery).addOnSuccessListener { results ->
            binding.progressBar.visibility = View.GONE

            // Mapeamos los resultados de Catastros a una lista de Pares (ID, Objeto).
            val catastros = results[0]!!.map { doc ->
                Pair(doc.id, doc.toObject(Catastro::class.java))
            }

            // Mapeamos los resultados de Mantenimientos.
            val mantenimientos = results[1]!!.map { doc ->
                Pair(doc.id, doc.toObject(Mantenimiento::class.java))
            }

            // Combinamos ambas listas.
            val combinedList = (catastros + mantenimientos).toMutableList()

            // Ordenamos la lista combinada por fecha (timestamp) de forma descendente.
            combinedList.sortByDescending { item ->
                when (val dataObject = item.second) {
                    is Catastro -> dataObject.timestamp
                    is Mantenimiento -> dataObject.timestamp
                    else -> null
                }
            }

            // Tomamos solo los 3 items más recientes de la lista combinada.
            val recentItems = combinedList.take(3)

            if (recentItems.isEmpty()) {
                showStatusMessage("No hay actividad reciente en tu grupo.")
            } else {
                // Usamos setData para actualizar el adaptador con los items finales.
                inventarioAdapter.setData(recentItems)
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

    /**
     * Al reanudar el fragmento, volvemos a cargar la actividad reciente
     * para asegurar que la información esté siempre actualizada.
     */
    override fun onResume() {
        super.onResume()
        // Solo recargamos si el binding sigue siendo válido, para evitar crashes.
        if (_binding != null) {
            checkUserAndLoadRecentActivity()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
