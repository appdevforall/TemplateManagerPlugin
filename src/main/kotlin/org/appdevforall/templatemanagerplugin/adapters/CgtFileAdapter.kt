package org.appdevforall.templatemanagerplugin.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.appdevforall.templatemanager.databinding.ItemCgtFileBinding
import org.appdevforall.templatemanager.models.CgtFileItem

class CgtFileAdapter(
    private val items: List<CgtFileItem>
) : RecyclerView.Adapter<CgtFileAdapter.FileViewHolder>() {

    inner class FileViewHolder(private val binding: ItemCgtFileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CgtFileItem) {
            binding.tvFileName.text = item.name
            binding.tvTemplateName.text = item.templateName
            binding.tvTemplateDesc.text = item.templateDesc
            binding.tvTemplateVersion.text = item.templateVersion

            // Unset listener before altering state to prevent recycling bugs
            binding.checkBox.setOnCheckedChangeListener(null)
            binding.checkBox.isChecked = item.isChecked

            binding.checkBox.setOnCheckedChangeListener { _, isChecked ->
                item.isChecked = isChecked
            }

            // Allow clicking the entire row to toggle checkbox
            binding.root.setOnClickListener {
                binding.checkBox.isChecked = !binding.checkBox.isChecked
                // Always check for a valid position before using it
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    binding.llHeadrer.tag = position
                } else {
                    binding.llHeadrer.tag = null
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemCgtFileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    // Helper method to retrieve selected files
    fun getSelectedItems(): List<CgtFileItem> {
        return items.filter { it.isChecked }.map { it}
    }
}
