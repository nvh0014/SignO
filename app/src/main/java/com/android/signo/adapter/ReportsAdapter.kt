package com.android.signo.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.android.signo.R
import com.android.signo.databinding.ItemInventarioBinding
import com.android.signo.model.Report
import java.text.SimpleDateFormat
import java.util.Locale

// Enum para las acciones del menú
enum class ReportAction {
    EDIT,
    DELETE
}

class ReportsAdapter(
    private var reportList: List<Report>,
    private val showOptionsMenu: Boolean,
    private val onOptionClicked: (Report, ReportAction) -> Unit
) : RecyclerView.Adapter<ReportsAdapter.ReportViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val binding = ItemInventarioBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReportViewHolder(binding, onOptionClicked)
    }

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        holder.bind(reportList[position], showOptionsMenu)
    }

    override fun getItemCount(): Int = reportList.size

    fun updateData(newReportList: List<Report>){
        this.reportList = newReportList
        notifyDataSetChanged()
    }

    class ReportViewHolder(
        private val binding: ItemInventarioBinding,
        private val onOptionClicked: (Report, ReportAction) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(report: Report, showOptionsMenu: Boolean) {
            binding.tvItemTitle.text = report.nombreSenal
            binding.tvItemSubtitle.text = report.callePrincipal

            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val dateString = report.timestamp?.let { sdf.format(it) } ?: "Fecha desconocida"
            val userAndDate = "por ${report.userName} el $dateString"
            binding.tvItemTertiary.text = userAndDate

            binding.tvItemDate.visibility = View.GONE // Ocultamos el campo de fecha que no se usa

            if (showOptionsMenu) {
                binding.ivOptionsMenu.visibility = View.VISIBLE
                binding.ivOptionsMenu.setOnClickListener { showPopupMenu(it, report) }
            } else {
                binding.ivOptionsMenu.visibility = View.GONE
            }
        }

        private fun showPopupMenu(view: View, report: Report) {
            val popup = PopupMenu(view.context, view)
            popup.inflate(R.menu.report_options_menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_edit_report -> {
                        onOptionClicked(report, ReportAction.EDIT)
                        true
                    }
                    R.id.action_delete_report -> {
                        onOptionClicked(report, ReportAction.DELETE)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }
}
