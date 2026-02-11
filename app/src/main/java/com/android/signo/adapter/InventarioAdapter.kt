package com.android.signo.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.android.signo.R
import com.android.signo.ui.crear.Catastro
import com.android.signo.ui.mantenimiento.Mantenimiento
import java.text.SimpleDateFormat
import java.util.Locale

enum class InventarioAction { EDIT, DELETE }

class InventarioAdapter(
    private var items: MutableList<Pair<String, Any>>,
    private val showActions: Boolean,
    private val onAction: (itemId: String, item: Any, action: InventarioAction) -> Unit
) : RecyclerView.Adapter<InventarioAdapter.InventarioViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InventarioViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_inventario, parent, false)
        return InventarioViewHolder(view)
    }

    override fun onBindViewHolder(holder: InventarioViewHolder, position: Int) {
        val (itemId, item) = items[position]
        when (item) {
            is Catastro -> holder.bindCatastro(itemId, item)
            is Mantenimiento -> holder.bindMantenimiento(itemId, item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun setData(newItems: List<Pair<String, Any>>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun addData(newItems: List<Pair<String, Any>>) {
        val startPosition = items.size
        items.addAll(newItems)
        notifyItemRangeInserted(startPosition, newItems.size)
    }

    inner class InventarioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: ConstraintLayout = itemView.findViewById(R.id.item_container)
        private val title: TextView = itemView.findViewById(R.id.tv_item_title)
        private val subtitle: TextView = itemView.findViewById(R.id.tv_item_subtitle)
        private val tertiary: TextView = itemView.findViewById(R.id.tv_item_tertiary)
        private val date: TextView = itemView.findViewById(R.id.tv_item_date)
        private val optionsMenu: ImageView = itemView.findViewById(R.id.iv_options_menu)
        private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        private fun setupOptionsMenu(itemId: String, item: Any) {
            if (showActions) {
                optionsMenu.visibility = View.VISIBLE
                optionsMenu.setOnClickListener { view ->
                    val popup = PopupMenu(view.context, view)
                    popup.inflate(R.menu.menu_options)
                    popup.setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            R.id.action_edit -> {
                                onAction(itemId, item, InventarioAction.EDIT)
                                true
                            }
                            R.id.action_delete -> {
                                onAction(itemId, item, InventarioAction.DELETE)
                                true
                            }
                            else -> false
                        }
                    }
                    popup.show()
                }
            } else {
                optionsMenu.visibility = View.GONE
            }
        }

        fun bindCatastro(itemId: String, catastro: Catastro) {
            container.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.color_catastro_background))
            title.text = catastro.nombreSenal?.toString() ?: ""
            subtitle.text = catastro.callePrincipal?.toString() ?: ""
            tertiary.visibility = View.VISIBLE
            tertiary.text = "ID: $itemId"
            date.text = catastro.timestamp?.let { sdf.format(it) } ?: "N/A"
            setupOptionsMenu(itemId, catastro)
        }

        fun bindMantenimiento(itemId: String, mantenimiento: Mantenimiento) {
            container.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.color_mantenimiento_background))
            val trabajos = mantenimiento.trabajosRealizados.joinToString(", ")
            title.text = if (mantenimiento.nombreSenal.isNotEmpty()) mantenimiento.nombreSenal else "Mantenimiento"
            subtitle.text = if (trabajos.isNotEmpty()) trabajos else "Sin trabajos registrados"
            tertiary.visibility = View.VISIBLE
            tertiary.text = "ID: ${mantenimiento.catastroId}"
            date.text = mantenimiento.timestamp?.let { sdf.format(it) } ?: "N/A"
            setupOptionsMenu(itemId, mantenimiento)
        }
    }
}