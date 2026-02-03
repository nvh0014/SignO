package com.android.signo.ui.cuenta

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.android.signo.databinding.FragmentCrearGrupoBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CrearGrupoFragment : Fragment() {

    private var _binding: FragmentCrearGrupoBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val args: CrearGrupoFragmentArgs by navArgs()
    private var editingGroupId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCrearGrupoBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        editingGroupId = args.groupId
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        if (editingGroupId != null) {
            setupEditMode()
        } else {
            setupCreateMode()
        }
    }

    private fun setupEditMode() {
        binding.toolbar.title = "Editar Nombre del Grupo"
        binding.btnCreateGroup.text = "Guardar Cambios"
        binding.groupPasswordLayout.visibility = View.GONE // Ocultamos la contraseña

        // Cargar nombre actual del grupo
        db.collection("groups").document(editingGroupId!!).get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    binding.groupNameEditText.setText(doc.getString("name"))
                }
            }

        binding.btnCreateGroup.setOnClickListener {
            updateGroupName()
        }
    }

    private fun setupCreateMode() {
        binding.toolbar.title = "Crear Nuevo Grupo"
        binding.btnCreateGroup.text = "Crear Grupo"
        binding.btnCreateGroup.setOnClickListener {
            createGroup()
        }
    }

    private fun updateGroupName(){
        val newGroupName = binding.groupNameEditText.text.toString().trim()
        if(newGroupName.isEmpty()){
            binding.groupNameLayout.error = "El nombre no puede estar vacío"
            return
        }
        binding.progressBar.visibility = View.VISIBLE
        binding.btnCreateGroup.isEnabled = false

        db.collection("groups").document(editingGroupId!!).update("name", newGroupName)
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(context, "Nombre del grupo actualizado.", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp() // Vuelve al panel de admin
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.btnCreateGroup.isEnabled = true
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun createGroup() {
        val groupName = binding.groupNameEditText.text.toString().trim()
        val groupPassword = binding.groupPasswordEditText.text.toString().trim()
        val user = auth.currentUser

        if (groupName.isEmpty() || groupPassword.isEmpty()) {
            Toast.makeText(context, "Nombre y clave no pueden estar vacíos", Toast.LENGTH_SHORT).show()
            return
        }

        if (groupPassword.length < 6) {
            binding.groupPasswordLayout.error = "La clave debe tener al menos 6 caracteres"
            return
        } else {
            binding.groupPasswordLayout.error = null
        }

        if (user == null) return

        binding.progressBar.visibility = View.VISIBLE
        binding.btnCreateGroup.isEnabled = false

        val newGroupRef = db.collection("groups").document()
        val groupId = newGroupRef.id

        val groupData = hashMapOf(
            "id" to groupId,
            "name" to groupName,
            "password" to groupPassword,
            "admin_uid" to user.uid
        )

        newGroupRef.set(groupData)
            .addOnSuccessListener {
                db.collection("users").document(user.uid).update(mapOf("id_grupo" to groupId, "rol" to "admin"))
                    .addOnSuccessListener {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(context, "¡Grupo '$groupName' creado con éxito!", Toast.LENGTH_LONG).show()
                        findNavController().navigateUp()
                    }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.btnCreateGroup.isEnabled = true
                Toast.makeText(context, "Error al crear el grupo: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}