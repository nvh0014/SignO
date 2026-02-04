package com.android.signo.ui.inventario

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.android.signo.R
import com.android.signo.adapter.InventarioAction
import com.android.signo.adapter.InventarioAdapter
import com.android.signo.databinding.FragmentInventarioBinding
import com.android.signo.ui.crear.Catastro
import com.android.signo.ui.mantenimiento.Mantenimiento
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.toObject

// Enum para saber qué tipo de inventario estamos mostrando
enum class InventoryType { CATASTROS, MANTENIMIENTOS }

class InventarioFragment : Fragment() {

    private var _binding: FragmentInventarioBinding? = null
    private val binding get() = _binding!!

    // Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var currentGroupId: String? = null

    // Adaptador y estado de la UI
    private lateinit var inventarioAdapter: InventarioAdapter
    private var currentInventoryType = InventoryType.CATASTROS // Por defecto, mostramos Catastros

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
        setupChipGroupListener()
        checkUserGroupAndLoadInventory()
    }

    /**
     * Configura el RecyclerView con el nuevo InventarioAdapter.
     */
    private fun setupRecyclerView() {
        inventarioAdapter = InventarioAdapter(emptyList(), true) { item, action ->
            handleItemAction(item, action)
        }
        binding.recyclerViewInventario.adapter = inventarioAdapter // Usamos el nuevo ID
    }

    /**
     * Añade un listener al ChipGroup para cambiar el tipo de inventario a mostrar.
     */
    private fun setupChipGroupListener() {
        binding.chipGroupInventoryType.setOnCheckedChangeListener { group, checkedId ->
            currentInventoryType = when (checkedId) {
                R.id.chip_catastros -> InventoryType.CATASTROS
                R.id.chip_mantenimientos -> InventoryType.MANTENIMIENTOS
                else -> InventoryType.CATASTROS
            }
            // Recargamos el inventario con el nuevo tipo seleccionado
            loadInventory()
        }
    }

    /**
     * Verifica el grupo del usuario y, si es válido, inicia la carga del inventario.
     */
    private fun checkUserGroupAndLoadInventory() {
        val user = auth.currentUser
        if (user == null) { /* ... Manejo de error ... */ return }

        binding.progressBar.visibility = View.VISIBLE

        db.collection("users").document(user.uid).get().addOnSuccessListener {
            currentGroupId = it.getString("id_grupo")
            if (currentGroupId.isNullOrEmpty()) {
                showError("No perteneces a ningún grupo.")
            } else {
                binding.inventoryContent.visibility = View.VISIBLE
                loadInventory()
            }
        }.addOnFailureListener { showError("Error al cargar datos.") }
    }

    /**
     * Carga los datos desde Firestore basándose en el 'currentInventoryType'.
     */
    private fun loadInventory() {
        if (currentGroupId == null) return

        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatusMessage.visibility = View.GONE

        val collectionPath = if (currentInventoryType == InventoryType.CATASTROS) "catastros" else "mantenimientos"
        val objectClass = if (currentInventoryType == InventoryType.CATASTROS) Catastro::class.java else Mantenimiento::class.java

        db.collection(collectionPath)
            .whereEqualTo("groupId", currentGroupId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                binding.progressBar.visibility = View.GONE
                if (documents.isEmpty) {
                    showStatusMessage("No hay ${if (currentInventoryType == InventoryType.CATASTROS) "catastros" else "mantenimientos"} aún.")
                } else {
                    val items = documents.map { it.toObject(objectClass) }
                    inventarioAdapter.updateData(items)
                }
            }
            .addOnFailureListener { e ->
                showError("Error al cargar el inventario: ${e.message}")
            }
    }

    /**
     * Maneja las acciones de editar o eliminar sobre un item del inventario.
     * La lógica de edición está pendiente de implementación.
     */
    private fun handleItemAction(item: Any, action: InventarioAction) {
        when (action) {
            InventarioAction.EDIT -> {
                Toast.makeText(context, "La edición para este tipo de item aún no está implementada.", Toast.LENGTH_SHORT).show()
                // TODO: Implementar la lógica para abrir la actividad de edición correcta
            }
            InventarioAction.DELETE -> {
                // La lógica de eliminación puede ser más compleja y requerir confirmación
                Toast.makeText(context, "La eliminación aún no está implementada.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showStatusMessage(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.recyclerViewInventario.visibility = View.GONE
        binding.tvStatusMessage.text = message
        binding.tvStatusMessage.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.inventoryContent.visibility = View.GONE
        binding.tvStatusMessage.text = message
        binding.tvStatusMessage.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        // Recargamos los datos cada vez que el fragmento es visible
        if (currentGroupId != null) {
            loadInventory()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
