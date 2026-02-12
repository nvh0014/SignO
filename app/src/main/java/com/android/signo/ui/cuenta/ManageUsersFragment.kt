package com.android.signo.ui.cuenta

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.android.signo.adapter.UserAction
import com.android.signo.adapter.UsersAdapter
import com.android.signo.databinding.FragmentManageUsersBinding
import com.android.signo.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ManageUsersFragment : Fragment() {

    private var _binding: FragmentManageUsersBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var usersAdapter: UsersAdapter
    private val args: ManageUsersFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManageUsersBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        setupRecyclerView()
        loadGroupMembers()
    }

    private fun setupRecyclerView() {
        usersAdapter = UsersAdapter(emptyList(), auth.currentUser?.uid ?: "") { user, action ->
            handleUserAction(user, action)
        }
        binding.recyclerViewUsers.adapter = usersAdapter
    }

    private fun handleUserAction(user: User, action: UserAction) {
        when (action) {
            UserAction.PROMOTE_ADMIN -> {
                showConfirmationDialog("Ascender a Administrador", "¿Estás seguro de que quieres ascender a ${user.name} a administrador?", user, action)
            }
            UserAction.REMOVE_USER -> {
                showConfirmationDialog("Eliminar Usuario", "¿Estás seguro de que quieres eliminar a ${user.name} del grupo?", user, action)
            }
            UserAction.DESCEND_USER -> {
                showConfirmationDialog("Descender de Administrador", "¿Estás seguro de que quieres descender de ${user.name} a usuario?", user, action)
            }
        }
    }

    private fun showConfirmationDialog(title: String, message: String, user: User, action: UserAction) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Confirmar") { s_, _ ->
                when (action) {
                    UserAction.PROMOTE_ADMIN -> updateUserRole(user, "admin")
                    UserAction.REMOVE_USER -> updateUserRole(user, "")
                    UserAction.DESCEND_USER -> updateUserRole(user, "usuario")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    private fun updateUserRole(user: User, newRole: String) {
        val updates = if (newRole.isEmpty()) {
            mapOf("rol" to "usuario", "id_grupo" to "")
        } else {
            mapOf("rol" to newRole)
        }

        db.collection("users").document(user.id).update(updates)
            .addOnSuccessListener {
                val message = when (newRole) {
                    "admin" -> "Usuario ascendido a administrador"
                    "usuario" -> "Usuario descendido a usuario"
                    else -> "Usuario eliminado del grupo"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                loadGroupMembers() // Recargar la lista
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error al actualizar usuario: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
    private fun loadGroupMembers() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatusMessage.visibility = View.GONE

        db.collection("users").whereEqualTo("id_grupo", args.groupId).get()
            .addOnSuccessListener { documents ->
                binding.progressBar.visibility = View.GONE
                if (documents.isEmpty) {
                    binding.tvStatusMessage.visibility = View.VISIBLE
                } else {
                    val users = documents.toObjects(User::class.java)
                    usersAdapter.updateData(users.sortedByDescending { it.rol == "admin" }) // Admins primero
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.tvStatusMessage.text = "Error al cargar miembros: ${e.message}"
                binding.tvStatusMessage.visibility = View.VISIBLE
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}