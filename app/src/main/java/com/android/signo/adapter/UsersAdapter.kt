package com.android.signo.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.android.signo.R
import com.android.signo.databinding.ItemUserBinding
import com.android.signo.model.User

// Enum para las acciones del menú de usuario
enum class UserAction {
    PROMOTE_ADMIN,
    REMOVE_USER
}

class UsersAdapter(
    private var userList: List<User>,
    private val currentUserId: String,
    private val onOptionClicked: (User, UserAction) -> Unit
) : RecyclerView.Adapter<UsersAdapter.UserViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding, onOptionClicked)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = userList[position]
        holder.bind(user, currentUserId)
    }

    override fun getItemCount(): Int = userList.size

    fun updateData(newUserList: List<User>){
        this.userList = newUserList
        notifyDataSetChanged()
    }

    class UserViewHolder(
        private val binding: ItemUserBinding,
        private val onOptionClicked: (User, UserAction) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User, currentUserId: String) {
            binding.tvUserName.text = user.name
            binding.tvUserEmail.text = user.email

            if (user.rol == "admin") {
                binding.tvUserRole.text = "Admin"

                val adminColor = ContextCompat.getColor(binding.root.context, R.color.yellow_dark)

                binding.tvUserRole.background.setTint(adminColor)
                binding.tvUserRole.visibility = View.VISIBLE
            } else {
                binding.tvUserRole.visibility = View.GONE
            }

            // No se puede modificar al usuario actual
            if (user.id == currentUserId) {
                binding.ivOptionsMenu.visibility = View.INVISIBLE
            } else {
                binding.ivOptionsMenu.visibility = View.VISIBLE
                binding.ivOptionsMenu.setOnClickListener { showPopupMenu(it, user) }
            }
        }

        private fun showPopupMenu(view: View, user: User) {
            val popup = PopupMenu(view.context, view)
            popup.inflate(R.menu.user_options_menu)

            // Ocultar la opción de "Ascender" si el usuario ya es admin
            if (user.rol == "admin") {
                popup.menu.findItem(R.id.action_promote_admin).isVisible = false
            }

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_promote_admin -> {
                        onOptionClicked(user, UserAction.PROMOTE_ADMIN)
                        true
                    }
                    R.id.action_remove_user -> {
                        onOptionClicked(user, UserAction.REMOVE_USER)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }
}
