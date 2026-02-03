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
import com.android.signo.databinding.FragmentGroupAdminBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch

class GroupAdminFragment : Fragment() {

    private var _binding: FragmentGroupAdminBinding? = null
    private val binding get() = _binding!!

    private val args: GroupAdminFragmentArgs by navArgs()
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupAdminBinding.inflate(inflater, container, false)
        db = FirebaseFirestore.getInstance()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnManageMembers.setOnClickListener {
            val action = GroupAdminFragmentDirections.actionGroupAdminFragmentToManageUsersFragment(args.groupId)
            findNavController().navigate(action)
        }

        binding.btnEditGroupName.setOnClickListener {
            val action = GroupAdminFragmentDirections.actionGroupAdminFragmentToCrearGrupoFragment(args.groupId)
            findNavController().navigate(action)
        }

        binding.btnDeleteGroup.setOnClickListener {
            showDeleteGroupConfirmationDialog()
        }
    }

    private fun showDeleteGroupConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Estás seguro de que deseas eliminar este grupo? Esta acción es permanente y desvinculará a todos los miembros. No se puede deshacer.")
            .setPositiveButton("Eliminar Grupo") { _, _ ->
                deleteGroup()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteGroup() {
        val groupId = args.groupId
        if (groupId.isEmpty()) {
            Toast.makeText(context, "Error: ID de grupo inválido", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        // 1. Encontrar y desvincular a todos los miembros del grupo
        db.collection("users").whereEqualTo("id_grupo", groupId).get()
            .addOnSuccessListener { userDocuments ->
                val batch: WriteBatch = db.batch()

                for (document in userDocuments) {
                    val userRef = db.collection("users").document(document.id)
                    val updates = mapOf(
                        "id_grupo" to "",
                        "rol" to "usuario"
                    )
                    batch.update(userRef, updates)
                }

                // Ejecutar la actualización por lotes de todos los usuarios
                batch.commit().addOnSuccessListener {
                    // 2. Una vez que los usuarios han sido desvinculados, eliminar el grupo
                    db.collection("groups").document(groupId).delete()
                        .addOnSuccessListener {
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(context, "Grupo eliminado con éxito", Toast.LENGTH_SHORT).show()
                            // Navegar hacia atrás, ya que el grupo ya no existe
                            findNavController().popBackStack()
                        }
                        .addOnFailureListener { e ->
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(context, "Error al eliminar el documento del grupo: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }.addOnFailureListener { e ->
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, "Error al desvincular a los miembros: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(context, "Error al buscar miembros del grupo: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}