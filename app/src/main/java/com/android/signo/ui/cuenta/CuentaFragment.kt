package com.android.signo.ui.cuenta

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.android.signo.R
import com.android.signo.databinding.FragmentCuentaBinding
import com.android.signo.login.LoginActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CuentaFragment : Fragment() {

    private var _binding: FragmentCuentaBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var currentGroupId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCuentaBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadUserData()

        binding.btnLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(activity, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            activity?.finish()
        }

        binding.btnCreateGroup.setOnClickListener { 
            findNavController().navigate(R.id.action_navigation_cuenta_to_crearGrupoFragment)
        }
        
        binding.btnJoinGroup.setOnClickListener { 
            showJoinGroupDialog()
        }

        binding.btnAdminGroup.setOnClickListener {
            currentGroupId?.let {
                val action = CuentaFragmentDirections.actionNavigationCuentaToGroupAdminFragment(it)
                findNavController().navigate(action)
            }
        }
    }

    private fun showJoinGroupDialog() {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_join_group, null)
        builder.setView(dialogView)
            .setTitle("Unirse a un grupo")
            .setPositiveButton("Unirse") { dialog, which ->
                val etGroupName = dialogView.findViewById<TextInputEditText>(R.id.etGroupName)
                val etGroupPassword = dialogView.findViewById<TextInputEditText>(R.id.etGroupPassword)
                val groupName = etGroupName.text.toString()
                val groupPassword = etGroupPassword.text.toString()

                if (groupName.isNotEmpty() && groupPassword.isNotEmpty()) {
                    joinGroup(groupName, groupPassword)
                } else {
                    Toast.makeText(context, "Por favor, rellene todos los campos", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)

        val dialog = builder.create()
        dialog.show()
    }

    private fun joinGroup(groupName: String, groupPass: String) {
        binding.progressBar.visibility = View.VISIBLE
        db.collection("groups")
            .whereEqualTo("name", groupName)
            .whereEqualTo("password", groupPass)
            .get()
            .addOnSuccessListener { documents ->
                val currentBinding = _binding ?: return@addOnSuccessListener
                if (documents.isEmpty) {
                    Toast.makeText(context, "Grupo no encontrado o contraseña incorrecta", Toast.LENGTH_SHORT).show()
                    currentBinding.progressBar.visibility = View.GONE
                } else {
                    val groupId = documents.documents[0].id
                    val user = auth.currentUser
                    if (user != null) {
                        db.collection("users").document(user.uid)
                            .update("id_grupo", groupId, "rol", "usuario")
                            .addOnSuccessListener {
                                if (_binding == null) return@addOnSuccessListener
                                Toast.makeText(context, "Te has unido al grupo exitosamente", Toast.LENGTH_SHORT).show()
                                loadUserData() // Recargar datos
                            }
                            .addOnFailureListener { e ->
                                if (_binding == null) return@addOnFailureListener
                                Toast.makeText(context, "Error al unirse al grupo: ${e.message}", Toast.LENGTH_SHORT).show()
                                binding.progressBar.visibility = View.GONE
                            }
                    }
                }
            }
            .addOnFailureListener { exception ->
                _binding?.progressBar?.visibility = View.GONE
                if (isAdded) Toast.makeText(context, "Error al buscar grupo: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadUserData() {
        val user = auth.currentUser
        if (user == null) {
            val intent = Intent(activity, LoginActivity::class.java)
            startActivity(intent)
            activity?.finish()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                val currentBinding = _binding ?: return@addOnSuccessListener
                currentBinding.progressBar.visibility = View.GONE
                if (document != null && document.exists()) {
                    currentBinding.tvUserName.text = document.getString("name")
                    currentBinding.tvUserEmail.text = document.getString("email")
                    
                    this.currentGroupId = document.getString("id_grupo")
                    val userRole = document.getString("rol")

                    if (currentGroupId.isNullOrEmpty()) {
                        showNoGroupUI()
                    } else {
                        fetchGroupInfo(currentGroupId!!, userRole)
                    }
                } else {
                    if (isAdded) Toast.makeText(context, "No se encontraron datos del usuario.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                _binding?.progressBar?.visibility = View.GONE
                if (isAdded) Toast.makeText(context, "Error al cargar datos: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showNoGroupUI() {
        _binding?.apply {
            tvGroupStatus.visibility = View.VISIBLE
            tvGroupName.visibility = View.GONE
            tvGroupId.visibility = View.GONE
            btnAdminGroup.visibility = View.GONE
            btnCreateGroup.visibility = View.VISIBLE
            btnJoinGroup.visibility = View.VISIBLE
        }
    }

    private fun fetchGroupInfo(groupId: String, userRole: String?) {
        binding.progressBar.visibility = View.VISIBLE
        db.collection("groups").document(groupId).get()
            .addOnSuccessListener { groupDoc ->
                val currentBinding = _binding ?: return@addOnSuccessListener
                currentBinding.progressBar.visibility = View.GONE
                if (groupDoc != null && groupDoc.exists()) {
                    val groupName = groupDoc.getString("name")
                    showGroupInfoUI(groupName, groupId, userRole)
                } else {
                    showNoGroupUI()
                    if (isAdded) Toast.makeText(context, "Error: No se encontró el grupo al que perteneces.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                _binding?.progressBar?.visibility = View.GONE
                if (isAdded) Toast.makeText(context, "Error al cargar datos del grupo: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
    
    private fun showGroupInfoUI(groupName: String?, groupId: String, userRole: String?) {
        _binding?.apply {
            tvGroupStatus.visibility = View.GONE
            btnCreateGroup.visibility = View.GONE
            btnJoinGroup.visibility = View.GONE

            tvGroupName.text = groupName ?: "Grupo sin nombre"
            tvGroupId.text = "ID: $groupId"
            tvGroupName.visibility = View.VISIBLE
            tvGroupId.visibility = View.VISIBLE

            if (userRole == "admin") {
                btnAdminGroup.visibility = View.VISIBLE
            } else {
                btnAdminGroup.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
