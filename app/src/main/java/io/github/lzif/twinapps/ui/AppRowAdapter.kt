package io.github.lzif.twinapps.ui

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView
import io.github.lzif.twinapps.databinding.ItemAppRowBinding

/** One action button on an app row. */
data class RowAction(val id: String, val label: String)

/** One row in an app list. */
data class AppRow(
    val packageName: String,
    val label: CharSequence,
    val icon: Drawable?,
    val actions: List<RowAction>,
)

/**
 * Simple app list adapter. Rows carry their own action buttons, rendered
 * into [ItemAppRowBinding.actionContainer].
 */
class AppRowAdapter(
    private val onAction: (AppRow, String) -> Unit,
) : RecyclerView.Adapter<AppRowAdapter.ViewHolder>() {

    var rows: List<AppRow> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(rows[position])
    }

    override fun getItemCount(): Int = rows.size

    inner class ViewHolder(
        private val binding: ItemAppRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: AppRow) {
            binding.appLabel.text = row.label
            binding.appPackage.text = row.packageName
            if (row.icon != null) {
                binding.appIcon.setImageDrawable(row.icon)
            } else {
                binding.appIcon.setImageDrawable(null)
            }
            binding.actionContainer.removeAllViews()
            for (action in row.actions) {
                val button = Button(binding.root.context).apply {
                    text = action.label
                    isAllCaps = false
                    setOnClickListener { onAction(row, action.id) }
                }
                binding.actionContainer.addView(button)
            }
        }
    }
}
