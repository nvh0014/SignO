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

/**
 * Fragmento que muestra una lista paginada de "Catastros" o "Mantenimientos".
 * Implementa paginación para manejar grandes volúmenes de datos de forma eficiente.
 */
class InventarioFragment : Fragment() {

    private var _binding: FragmentInventarioBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var currentGroupId: String? = null

    private lateinit var inventarioAdapter: InventarioAdapter
    private var currentInventoryType = InventoryType.CATASTROS

    // --- Variables para la Paginación ---
    private val BATCH_SIZE = 50L // Tamaño del lote a cargar
    private var lastVisible: DocumentSnapshot? = null // Último documento visible en la consulta anterior
    private var isLoading = false // Flag para evitar cargas múltiples simultáneas
    private var isDataEnd = false // Flag para indicar que se han cargado todos los datos

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
            val query = binding.editTextBuscar.text.toString().trim()

            // --- NUEVO: VALIDAR CONEXIÓN PARA BÚSQUEDA ---
            if (query.isNotEmpty() && !isNetworkAvailable(requireContext())) {
                Toast.makeText(requireContext(), "Necesitas internet para buscar por ID específico.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // ---------------------------------------------

            if (query.isNotEmpty()) {
                searchInventoryById(query)
            } else {
                resetPagination()
                loadInventory(true)
            }
        }
    }

    /**
     * Configura el RecyclerView, su LayoutManager y el listener de scroll para la paginación.
     */
    private fun setupRecyclerView() {
        // El adaptador ahora se inicializa con una lista mutable.
        inventarioAdapter = InventarioAdapter(mutableListOf(), true) { itemId, item, action ->
            handleItemAction(itemId, item, action)
        }
        binding.recyclerViewInventario.adapter = inventarioAdapter
        binding.recyclerViewInventario.layoutManager = LinearLayoutManager(requireContext())

        // --- Listener de Scroll para Paginación ---
        binding.recyclerViewInventario.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val totalItemCount = layoutManager.itemCount
                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()

                // Si no estamos cargando, no hemos llegado al final de los datos
                // y estamos cerca del final de la lista, cargamos la siguiente página.
                if (!isLoading && !isDataEnd && lastVisibleItemPosition + 5 >= totalItemCount) {
                    loadInventory(false) // Cargar la siguiente página, no la primera.
                }
            }
        })
    }

    /**
     * Resetea el estado de la paginación a sus valores iniciales.
     * Esencial al cambiar de tipo de inventario o al refrescar la lista.
     */
    private fun resetPagination() {
        lastVisible = null
        isLoading = false
        isDataEnd = false
        inventarioAdapter.setData(emptyList()) // Limpia el adaptador.
    }

    private fun setupChipGroupListener() {
        binding.chipGroupInventoryType.setOnCheckedStateChangeListener { _, checkedIds ->
            currentInventoryType = when (checkedIds.firstOrNull()) {
                R.id.chip_mantenimientos -> InventoryType.MANTENIMIENTOS
                else -> InventoryType.CATASTROS
            }
            // Al cambiar de tipo, reseteamos la paginación y cargamos desde el principio.
            resetPagination()
            loadInventory(true)
        }
    }

    private fun checkUserGroupAndLoadInventory() {
        val user = auth.currentUser ?: return
        binding.progressBar.visibility = View.VISIBLE

        db.collection("users").document(user.uid).get().addOnSuccessListener { document ->
            currentGroupId = document.getString("id_grupo")
            if (currentGroupId.isNullOrEmpty()) {
                showError("No perteneces a ningún grupo.")
            } else {
                binding.inventoryContent.visibility = View.VISIBLE
                loadInventory(true) // Carga la primera página.
            }
        }.addOnFailureListener { showError("Error al cargar datos.") }
    }

    /**
     * Carga datos de forma paginada desde Firestore.
     * @param isInitialLoad Boolean que indica si es la carga inicial (primera página).
     */
    private fun loadInventory(isInitialLoad: Boolean) {
        if (currentGroupId == null || isLoading || isDataEnd) return // Previene cargas innecesarias.

        isLoading = true // Marcamos que una carga ha comenzado.
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatusMessage.visibility = View.GONE
        binding.recyclerViewInventario.visibility = View.VISIBLE // Asegura que la lista sea visible

        val collectionPath = if (currentInventoryType == InventoryType.CATASTROS) "catastros" else "mantenimientos"

        // --- Construcción de la Consulta Paginada ---
        var query = db.collection(collectionPath)
            .whereEqualTo("groupId", currentGroupId)
            // Ordenamos por 'timestamp' de forma descendente para mostrar los más recientes primero.
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(BATCH_SIZE)

        // Si NO es la carga inicial, empezamos la consulta después del último documento visible.
        if (!isInitialLoad && lastVisible != null) {
            query = query.startAfter(lastVisible!!)
        }

        query.get()
            .addOnSuccessListener { documents ->
                binding.progressBar.visibility = View.GONE
                isLoading = false // La carga ha terminado.

                if (documents.isEmpty) {
                    isDataEnd = true // No hay más documentos, hemos llegado al final.
                    if (isInitialLoad) { // Si es la primera carga y no hay datos, mostramos un mensaje.
                        showStatusMessage("No hay ${if (currentInventoryType == InventoryType.CATASTROS) "catastros" else "mantenimientos"} aún.")
                    }
                    return@addOnSuccessListener
                }

                // Guardamos el último documento de este lote para la siguiente consulta.
                lastVisible = documents.documents.lastOrNull()
                // Si el número de documentos es menor que el tamaño del lote, significa que hemos llegado al final.
                if (documents.size() < BATCH_SIZE) {
                    isDataEnd = true
                }

                // Mapeamos los documentos a nuestros objetos de datos.
                val itemsWithIds = documents.mapNotNull { doc ->
                    val item = try {
                        if (currentInventoryType == InventoryType.CATASTROS) doc.toObject(Catastro::class.java)
                        else doc.toObject(Mantenimiento::class.java)
                    } catch (e: Exception) { null }
                    item?.let { Pair(doc.id, it) }
                }

                // Si es la carga inicial, usamos setData. Si no, usamos addData para paginar.
                if (isInitialLoad) {
                    inventarioAdapter.setData(itemsWithIds)
                } else {
                    inventarioAdapter.addData(itemsWithIds)
                }
            }
            .addOnFailureListener { e ->
                isLoading = false
                showError("Error al cargar el inventario: ${e.message}")
            }
    }

    private fun searchInventoryById(id: String) {
        if (currentGroupId == null) return

        val collectionPath = if (currentInventoryType == InventoryType.CATASTROS) "catastros" else "mantenimientos"

        // Asegúrate de mantener el prefijo si lo usas, si no, usa solo 'id'
        val docId = if (currentInventoryType == InventoryType.CATASTROS) "CAT_$id" else id

        db.collection(collectionPath).document(docId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists() && document.getString("groupId") == currentGroupId) {
                    val item = if (currentInventoryType == InventoryType.CATASTROS) {
                        document.toObject(Catastro::class.java)
                    } else {
                        document.toObject(Mantenimiento::class.java)
                    }

                    if (item != null) {
                        inventarioAdapter.setData(listOf(Pair(document.id, item)))
                        isDataEnd = true
                        binding.tvStatusMessage.visibility = View.GONE // Ocultar mensaje si hay éxito
                    } else {
                        showStatusMessage("Error al procesar el registro.")
                    }
                } else {
                    // Si existe pero es de otro grupo, o si el ID coincide pero no hay datos
                    inventarioAdapter.setData(emptyList())
                    showStatusMessage("No se encontró un registro con ese ID.")
                }
            }
            .addOnFailureListener { e ->
                // AQUÍ ESTÁ LA SOLUCIÓN:
                // Si el error es de PERMISOS, asumimos que es porque el documento no existe
                // y por tanto la regla de seguridad falló al intentar leerlo.
                if (e.message?.contains("PERMISSION_DENIED") == true) {
                    inventarioAdapter.setData(emptyList())
                    showStatusMessage("No se encontró un registro con ese ID.")
                } else {
                    showError("Error al buscar: ${e.message}")
                }
            }
    }

    private fun handleItemAction(itemId: String, item: Any, action: InventarioAction) {
        // --- NUEVO: VERIFICACIÓN DE CONEXIÓN ---
        if (!isNetworkAvailable(requireContext())) {
            Toast.makeText(requireContext(), "Modo Offline: No puedes editar ni eliminar sin internet.", Toast.LENGTH_SHORT).show()
            return
        }
        // ---------------------------------------

        when (action) {
            InventarioAction.EDIT -> {
                when (item) {
                    is Catastro -> {
                        val intent = Intent(requireContext(), CrearActivity::class.java)
                        intent.putExtra(CrearActivity.EDIT_CATASTRO_ID, itemId)
                        startActivity(intent)
                    }
                    is Mantenimiento -> {
                        val intent = Intent(requireContext(), MantenimientoActivity::class.java)
                        intent.putExtra(MantenimientoActivity.EDIT_MANTENIMIENTO_ID, itemId)
                        startActivity(intent)
                    }
                }
            }
            InventarioAction.DELETE -> showDeleteConfirmationDialog(itemId)
        }
    }

    private fun showDeleteConfirmationDialog(itemId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Estás seguro de que deseas eliminar este elemento?")
            .setPositiveButton("Eliminar") { _, _ -> deleteItem(itemId) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteItem(itemId: String) {
        val collectionPath = if (currentInventoryType == InventoryType.CATASTROS) "catastros" else "mantenimientos"
        db.collection(collectionPath).document(itemId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Elemento eliminado", Toast.LENGTH_SHORT).show()
                // Después de eliminar, refrescamos la lista desde el principio.
                resetPagination()
                loadInventory(true)
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al eliminar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showStatusMessage(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.recyclerViewInventario.visibility = if(message.startsWith("No hay")) View.GONE else View.VISIBLE
        binding.tvStatusMessage.text = message
        binding.tvStatusMessage.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.inventoryContent.visibility = View.GONE
        binding.tvStatusMessage.text = message
        binding.tvStatusMessage.visibility = View.VISIBLE
    }

    /**
     * Al volver a la pantalla, reseteamos y recargamos para asegurar que los datos estén actualizados.
     * Esto es útil si el usuario editó un item y volvió.
     */
    override fun onResume() {
        super.onResume()

        // Verificamos conexión al entrar
        if (!isNetworkAvailable(requireContext())) {
            Toast.makeText(requireContext(), "Sin conexión: Mostrando datos guardados.", Toast.LENGTH_LONG).show()
        }

        if (currentGroupId != null) {
            // Firestore es inteligente: si no hay red, loadInventory cargará desde la caché automáticamente
            resetPagination()
            loadInventory(true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
