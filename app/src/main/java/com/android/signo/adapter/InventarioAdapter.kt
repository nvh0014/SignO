package com.android.signo.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.signo.R
import com.android.signo.ui.crear.Catastro
import com.android.signo.ui.mantenimiento.Mantenimiento
import java.text.SimpleDateFormat
import java.util.Locale

// Enum para definir las acciones posibles en un item
enum class InventarioAction { EDIT, DELETE }

/**
 * Adaptador versátil para mostrar tanto Catastros como Mantenimientos.
 */
class InventarioAdapter(
    private var items: List<Any>,
    private val showActions: Boolean,
    private val onAction: (item: Any, action: InventarioAction) -> Unit
) : RecyclerView.Adapter<InventarioAdapter.InventarioViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InventarioViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_inventario, parent, false)
        return InventarioViewHolder(view)
    }

    override fun onBindViewHolder(holder: InventarioViewHolder, position: Int) {
        val item = items[position]
        // Determina el tipo de item y llama al método de enlace correspondiente
        when (item) {
            is Catastro -> holder.bindCatastro(item)
            is Mantenimiento -> holder.bindMantenimiento(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<Any>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    inner class InventarioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tv_item_title)
        private val subtitle: TextView = itemView.findViewById(R.id.tv_item_subtitle)
        private val date: TextView = itemView.findViewById(R.id.tv_item_date)
        private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        // Rellena las vistas con datos de un Catastro
        fun bindCatastro(catastro: Catastro) {
            title.text = catastro.nombreSenal
            subtitle.text = catastro.callePrincipal
            date.text = catastro.timestamp?.let { sdf.format(it) } ?: "N/A"
            // Aquí podrías añadir lógica para los botones de acción si es necesario
        }

        // Rellena las vistas con datos de un Mantenimiento
        fun bindMantenimiento(mantenimiento: Mantenimiento) {
            title.text = "Mantenimiento (${mantenimiento.estado})"
            subtitle.text = "ID Catastro: ${mantenimiento.catastroId}"
            date.text = mantenimiento.timestamp?.let { sdf.format(it) } ?: "N/A"
            // Aquí podrías añadir lógica para los botones de acción si es necesario
        }
    }
}
