package com.android.signo.ui.inventario

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.signo.R
import com.android.signo.adapter.InventarioAction
import com.android.signo.adapter.InventarioAdapter
import com.android.signo.databinding.FragmentInventarioBinding
import com.android.signo.ui.crear.Catastro
import com.android.signo.ui.crear.CrearActivity
import com.android.signo.ui.mantenimiento.Mantenimiento
import com.android.signo.ui.mantenimiento.MantenimientoActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.android.signo.utils.isNetworkAvailable

enum class InventoryType { CATASTROS, MANTENIMIENTOS }

class InventarioFragment : Fragment() {

    private var _binding: FragmentInventarioBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var currentGroupId: String? = null

    private lateinit var inventarioAdapter: InventarioAdapter
    private var currentInventoryType = InventoryType.CATASTROS

    // Paginación
    private val BATCH_SIZE = 50L
    private var lastVisible: DocumentSnapshot? = null
    private var isLoading = false
    private var isDataEnd = false

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
        setupSearchButton()
        checkUserGroupAndLoadInventory()
    }

    private fun setupSearchButton() {
        binding.buttonBuscar.setOnClickListener {
            val rawQuery = binding.editTextBuscar.text.toString().trim()

            if (rawQuery.isEmpty()) {
                resetPagination()
                loadInventory(true)
                return@setOnClickListener
            }

            if (!isNetworkAvailable(requireContext())) {
                Toast.makeText(requireContext(), "Requiere internet para buscar.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Lógica Sencilla:
            // 1. Si son solo números -> Buscamos ID exacto.
            // 2. Si hay letras -> Buscamos por Calle (o Nombre en Mantenimiento).
            if (rawQuery.all { it.isDigit() }) {
                searchInventoryById(rawQuery)
            } else {
                searchByText(rawQuery)
            }
        }
    }

    // Búsqueda simple y directa por los campos originales
    private fun searchByText(textQuery: String) {
        if (currentGroupId == null) return

        val collectionPath = if (currentInventoryType == InventoryType.CATASTROS) "catastros" else "mantenimientos"

        // VOLVEMOS A LO BÁSICO: Buscar en los campos que SÍ existen.
        val fieldToSearch = if (currentInventoryType == InventoryType.CATASTROS) "callePrincipal" else "nombreSenal"

        // Solo convertimos a mayúsculas para coincidir con tus datos (que suelen estar en mayúsculas)
        val queryUpperCase = textQuery.uppercase()

        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatusMessage.visibility = View.GONE

        db.collection(collectionPath)
            .whereEqualTo("groupId", currentGroupId)
            .whereGreaterThanOrEqualTo(fieldToSearch, queryUpperCase)
            .whereLessThanOrEqualTo(fieldToSearch, queryUpperCase + "\uf8ff")
            .limit(30)
            .get()
            .addOnSuccessListener { documents ->
                val currentBinding = _binding ?: return@addOnSuccessListener
                currentBinding.progressBar.visibility = View.GONE

                val items = documents.mapNotNull { doc ->
                    val item = if (currentInventoryType == InventoryType.CATASTROS)
                        doc.toObject(Catastro::class.java) else doc.toObject(Mantenimiento::class.java)
                    item?.let { Pair(doc.id, it) }
                }

                inventarioAdapter.setData(items)
                isDataEnd = true

                if (items.isEmpty()) {
                    showStatusMessage("No se encontraron resultados para '$textQuery'.")
                }
            }
            .addOnFailureListener { e ->
                if (_binding == null) return@addOnFailureListener
                showError("Error: ${e.message}")
            }
    }

    private fun searchInventoryById(id: String) {
        if (currentGroupId == null) return
        val collectionPath = if (currentInventoryType == InventoryType.CATASTROS) "catastros" else "mantenimientos"
        val docId = if (currentInventoryType == InventoryType.CATASTROS) "CAT_$id" else id

        binding.progressBar.visibility = View.VISIBLE
        db.collection(collectionPath).document(docId).get()
            .addOnSuccessListener { document ->
                val currentBinding = _binding ?: return@addOnSuccessListener
                currentBinding.progressBar.visibility = View.GONE
                if (document.exists() && document.getString("groupId") == currentGroupId) {
                    val item = if (currentInventoryType == InventoryType.CATASTROS)
                        document.toObject(Catastro::class.java) else document.toObject(Mantenimiento::class.java)

                    item?.let {
                        inventarioAdapter.setData(listOf(Pair(document.id, it)))
                        isDataEnd = true
                        currentBinding.tvStatusMessage.visibility = View.GONE
                    }
                } else {
                    inventarioAdapter.setData(emptyList())
                    showStatusMessage("ID '$id' no encontrado.")
                }
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                showError("Error al buscar ID.")
            }
    }

    // --- CONFIGURACIÓN ESTÁNDAR (PAGINACIÓN Y LISTA) ---

    private fun setupRecyclerView() {
        inventarioAdapter = InventarioAdapter(mutableListOf(), true) { itemId, item, action ->
            handleItemAction(itemId, item, action)
        }
        binding.recyclerViewInventario.adapter = inventarioAdapter
        binding.recyclerViewInventario.layoutManager = LinearLayoutManager(requireContext())

        binding.recyclerViewInventario.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val totalItemCount = layoutManager.itemCount
                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()

                if (!isLoading && !isDataEnd && lastVisibleItemPosition + 5 >= totalItemCount) {
                    loadInventory(false)
                }
            }
        })
    }

    private fun loadInventory(isInitialLoad: Boolean) {
        if (currentGroupId == null || isLoading || isDataEnd) return

        isLoading = true
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatusMessage.visibility = View.GONE
        val collectionPath = if (currentInventoryType == InventoryType.CATASTROS) "catastros" else "mantenimientos"

        var query = db.collection(collectionPath)
            .whereEqualTo("groupId", currentGroupId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(BATCH_SIZE)

        if (!isInitialLoad && lastVisible != null) {
            query = query.startAfter(lastVisible!!)
        }

        query.get().addOnSuccessListener { documents ->
            val currentBinding = _binding ?: return@addOnSuccessListener
            currentBinding.progressBar.visibility = View.GONE
            isLoading = false

            if (documents.isEmpty) {
                isDataEnd = true
                if (isInitialLoad) showStatusMessage("No hay registros aún.")
                return@addOnSuccessListener
            }

            lastVisible = documents.documents.lastOrNull()
            if (documents.size() < BATCH_SIZE) isDataEnd = true

            val itemsWithIds = documents.mapNotNull { doc ->
                val item = if (currentInventoryType == InventoryType.CATASTROS)
                    doc.toObject(Catastro::class.java) else doc.toObject(Mantenimiento::class.java)
                item?.let { Pair(doc.id, it) }
            }

            if (isInitialLoad) inventarioAdapter.setData(itemsWithIds)
            else inventarioAdapter.addData(itemsWithIds)
        }.addOnFailureListener { e ->
            isLoading = false
            if (_binding == null) return@addOnFailureListener
            showError("Error de carga: ${e.message}")
        }
    }

    private fun resetPagination() {
        lastVisible = null
        isLoading = false
        isDataEnd = false
        inventarioAdapter.setData(emptyList())
    }

    private fun setupChipGroupListener() {
        binding.chipGroupInventoryType.setOnCheckedStateChangeListener { _, checkedIds ->
            currentInventoryType = when (checkedIds.firstOrNull()) {
                R.id.chip_mantenimientos -> InventoryType.MANTENIMIENTOS
                else -> InventoryType.CATASTROS
            }
            resetPagination()
            loadInventory(true)
        }
    }

    private fun checkUserGroupAndLoadInventory() {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid).get().addOnSuccessListener { document ->
            if (_binding == null) return@addOnSuccessListener
            currentGroupId = document.getString("id_grupo")
            if (currentGroupId.isNullOrEmpty()) showError("No perteneces a ningún grupo.")
            else {
                binding.inventoryContent.visibility = View.VISIBLE
                loadInventory(true)
            }
        }.addOnFailureListener {
            if (_binding == null) return@addOnFailureListener
            showError("Error al verificar usuario.")
        }
    }

    private fun handleItemAction(itemId: String, item: Any, action: InventarioAction) {
        if (!isAdded || context == null) return
        if (!isNetworkAvailable(requireContext())) {
            Toast.makeText(requireContext(), "Modo Offline: Funciones limitadas.", Toast.LENGTH_SHORT).show()
            return
        }

        when (action) {
            InventarioAction.EDIT -> {
                val intent = if (item is Catastro) Intent(requireContext(), CrearActivity::class.java)
                else Intent(requireContext(), MantenimientoActivity::class.java)

                val extraKey = if (item is Catastro) CrearActivity.EDIT_CATASTRO_ID else MantenimientoActivity.EDIT_MANTENIMIENTO_ID
                intent.putExtra(extraKey, itemId)
                startActivity(intent)
            }
            InventarioAction.DELETE -> showDeleteConfirmationDialog(itemId)
        }
    }

    private fun showDeleteConfirmationDialog(itemId: String) {
        if (!isAdded || context == null) return
        AlertDialog.Builder(requireContext())
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Estás seguro de eliminar este registro?")
            .setPositiveButton("Eliminar") { _, _ -> deleteItem(itemId) }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun deleteItem(itemId: String) {
        val path = if (currentInventoryType == InventoryType.CATASTROS) "catastros" else "mantenimientos"
        db.collection(path).document(itemId).delete().addOnSuccessListener {
            if (_binding == null) return@addOnSuccessListener
            if (isAdded) Toast.makeText(requireContext(), "Eliminado correctamente", Toast.LENGTH_SHORT).show()
            resetPagination()
            loadInventory(true)
        }
    }

    private fun showStatusMessage(message: String) {
        _binding?.let {
            it.progressBar.visibility = View.GONE
            it.tvStatusMessage.text = message
            it.tvStatusMessage.visibility = View.VISIBLE
        }
    }

    private fun showError(message: String) {
        _binding?.let {
            it.progressBar.visibility = View.GONE
            it.inventoryContent.visibility = if(message.contains("grupo")) View.GONE else View.VISIBLE
            it.tvStatusMessage.text = message
            it.tvStatusMessage.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentGroupId != null && _binding != null) {
            resetPagination()
            loadInventory(true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
