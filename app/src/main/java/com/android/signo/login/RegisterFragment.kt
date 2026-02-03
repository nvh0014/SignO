package com.android.signo.login

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.android.signo.R
import com.android.signo.databinding.FragmentRegisterBinding
import com.android.signo.main.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnIrAInicio.setOnClickListener {
            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
        }

        binding.registerButton.setOnClickListener {
            handleRegistration()
        }
    }

    private fun handleRegistration() {
        val name = binding.nameEditText.text.toString().trim()
        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString().trim()

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(context, "Por favor, completa todos los campos", Toast.LENGTH_SHORT).show()
            return
        }
        if (password.length < 6) {
            Toast.makeText(context, "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
            return
        }

        binding.registerButton.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser!!
                val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(name).build()

                user.updateProfile(profileUpdates).addOnCompleteListener { profileTask ->
                    if (profileTask.isSuccessful) {
                        val uid = user.uid
                        // NO GUARDAMOS EL UID DENTRO DEL DOCUMENTO
                        val userData = hashMapOf(
                            "name" to name,
                            "email" to email,
                            "id_grupo" to "",
                            "rol" to "usuario"
                        )

                        db.collection("users").document(uid).set(userData)
                            .addOnSuccessListener {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(context, "Registro exitoso.", Toast.LENGTH_SHORT).show()
                                val intent = Intent(activity, MainActivity::class.java)
                                startActivity(intent)
                                activity?.finish()
                            }
                            .addOnFailureListener { e ->
                                binding.registerButton.isEnabled = true
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(context, "Error al guardar datos: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    } else {
                        binding.registerButton.isEnabled = true
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(context, "Error al guardar el perfil: ${profileTask.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                binding.registerButton.isEnabled = true
                binding.progressBar.visibility = View.GONE
                Toast.makeText(context, "Fallo en el registro: ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
