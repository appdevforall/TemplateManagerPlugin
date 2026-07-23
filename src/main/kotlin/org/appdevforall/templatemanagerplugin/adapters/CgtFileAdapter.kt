package org.appdevforall.templatemanagerplugin.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.appdevforall.templatemanagerplugin.R
import org.appdevforall.templatemanagerplugin.databinding.ItemCgtFileBinding
import org.appdevforall.templatemanagerplugin.models.CgtFileItem

class CgtFileAdapter(
    private val items: List<CgtFileItem>,
    private val onInstall: (CgtFileItem) -> Unit,
    private val onUninstall: (CgtFileItem) -> Unit,
    private val onDetails: (CgtFileItem) -> Unit,
    private val onDelete: (CgtFileItem) -> Unit
) : RecyclerView.Adapter<CgtFileAdapter.FileViewHolder>() {

    private companion object {
        const val MENU_INSTALL = 1
        const val MENU_UNINSTALL = 2
        const val MENU_DETAILS = 3
        const val MENU_DELETE = 4
    }

    inner class FileViewHolder(private val binding: ItemCgtFileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CgtFileItem) {
            binding.tvTemplateName.text = item.templateName.ifBlank { item.name }
            binding.tvTemplateVersion.text = item.templateVersion
            binding.tvTemplateDesc.text = item.templateDesc
            binding.tvFileName.text = item.name

            if (item.installed) {
                binding.tvStatus.text = "Installed"
                binding.tvStatus.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.status_success_text)
                )
            } else {
                binding.tvStatus.text = "Not installed"
                binding.tvStatus.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.status_error_text)
                )
            }

            binding.btnMenu.setOnClickListener { anchor ->
                val popup = PopupMenu(anchor.context, anchor)
                if (item.installed) {
                    popup.menu.add(0, MENU_UNINSTALL, 0, "Uninstall")
                    popup.menu.add(0, MENU_DETAILS, 1, "Details")
                } else {
                    popup.menu.add(0, MENU_INSTALL, 0, "Install")
                    popup.menu.add(0, MENU_DETAILS, 1, "Details")
                    popup.menu.add(0, MENU_DELETE, 2, "Delete")
                }
                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        MENU_INSTALL -> onInstall(item)
                        MENU_UNINSTALL -> onUninstall(item)
                        MENU_DETAILS -> onDetails(item)
                        MENU_DELETE -> onDelete(item)
                    }
                    true
                }
                popup.show()
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
}
