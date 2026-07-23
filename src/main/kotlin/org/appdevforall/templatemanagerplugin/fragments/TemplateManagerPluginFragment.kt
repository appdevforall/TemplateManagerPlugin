package org.appdevforall.templatemanagerplugin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.appdevforall.templatemanagerplugin.R
import org.appdevforall.templatemanagerplugin.adapters.CgtFileAdapter
import org.appdevforall.templatemanagerplugin.models.CgtFileItem
import com.itsaky.androidide.plugins.base.PluginFragmentHelper
import com.itsaky.androidide.plugins.services.IdeEnvironmentService
import com.itsaky.androidide.plugins.services.IdeTemplateService
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

class TemplateManagerPluginFragment : Fragment() {

    companion object {
        private const val PLUGIN_ID = "org.appdevforall.templatemanagerplugin"
        private const val TEMPLATE_JSON_SUFFIX = "/template/template.json"
        private const val TEMPLATES_SUBDIR = "templates"
        private val DOWNLOAD_DIR = File("/sdcard/Download")
    }

    private var recyclerView: RecyclerView? = null
    private var emptyView: TextView? = null
    private var templateService: IdeTemplateService? = null
    private var environmentService: IdeEnvironmentService? = null

    private val items = mutableListOf<CgtFileItem>()
    private val adapter = CgtFileAdapter(
        items,
        onInstall = ::installTemplate,
        onUninstall = ::uninstallTemplate,
        onDetails = ::showDetails,
        onDelete = ::confirmDeleteDownloadFile
    )

    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
        val inflater = super.onGetLayoutInflater(savedInstanceState)
        return PluginFragmentHelper.getPluginInflater(PLUGIN_ID, inflater)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setupServices()
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.recyclerView)
        emptyView = view.findViewById(R.id.tvEmpty)

        recyclerView?.layoutManager = LinearLayoutManager(requireContext())
        recyclerView?.adapter = adapter

        refreshTemplates()
    }

    override fun onResume() {
        super.onResume()
        refreshTemplates()
    }

    /** Re-scans the IDE's templates directory and /sdcard/Download, rebuilding the card list. */
    private fun refreshTemplates() {
        val prefix = "plugin_${PLUGIN_ID}_"

        val installedItems = templatesDirectory()
            ?.listFiles { f -> f.isFile && f.name.endsWith(".cgt", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?.mapNotNull { file ->
                val unregisterName = file.name.removePrefix(prefix)
                runCatching { parseCgtFile(file, installed = true, unregisterName = unregisterName) }.getOrNull()
            }
            ?: emptyList()

        val downloadItems = DOWNLOAD_DIR
            .listFiles { f -> f.isFile && f.name.endsWith(".cgt", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?.mapNotNull { file ->
                runCatching { parseCgtFile(file, installed = false, unregisterName = file.name) }.getOrNull()
            }
            ?: emptyList()

        items.clear()
        items.addAll(installedItems)
        items.addAll(downloadItems)
        adapter.notifyDataSetChanged()

        emptyView?.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun templatesDirectory(): File? {
        val ideHome = environmentService?.getIdeHomeDirectory() ?: return null
        return File(ideHome, TEMPLATES_SUBDIR)
    }

    /** Reads a .cgt (zip) file's "template/template.json" entry to extract its metadata. */
    private fun parseCgtFile(file: File, installed: Boolean, unregisterName: String): CgtFileItem? {
        return file.inputStream().use { stream ->
            ZipInputStream(stream).use { zip ->
                var result: CgtFileItem? = null
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name.endsWith(TEMPLATE_JSON_SUFFIX)) {
                        val json = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                        result = CgtFileItem(
                            file = file,
                            name = file.name,
                            templateName = json.optString("name"),
                            templateDesc = json.optString("description"),
                            templateVersion = json.optString("version"),
                            installed = installed,
                            unregisterName = unregisterName
                        )
                        break
                    }
                    zip.closeEntry()
                }
                result
            }
        }
    }

    private fun installTemplate(item: CgtFileItem) {
        val service = templateService
        if (service == null) {
            Toast.makeText(context, "Template service is not available", Toast.LENGTH_SHORT).show()
            return
        }
        val success = service.registerTemplate(item.file)
        if (success) {
            item.file.delete()
        }
        service.reloadTemplates()
        refreshTemplates()
        Toast.makeText(
            context,
            if (success) "Installed ${item.name}" else "Failed to install ${item.name}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun uninstallTemplate(item: CgtFileItem) {
        val service = templateService
        if (service == null) {
            Toast.makeText(context, "Template service is not available", Toast.LENGTH_SHORT).show()
            return
        }

        val restoredFile = File(DOWNLOAD_DIR, item.unregisterName)
        val restored = runCatching { item.file.copyTo(restoredFile, overwrite = true) }.isSuccess

        val success = service.unregisterTemplate(item.unregisterName)
        if (!success && restored) {
            restoredFile.delete()
        }

        service.reloadTemplates()
        refreshTemplates()
        Toast.makeText(
            context,
            if (success) "Uninstalled ${item.name}" else "Failed to uninstall ${item.name}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun confirmDeleteDownloadFile(item: CgtFileItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete ${item.name}?")
            .setMessage("This permanently deletes the file from Downloads.")
            .setPositiveButton("Delete") { _, _ -> deleteDownloadFile(item) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteDownloadFile(item: CgtFileItem) {
        val success = item.file.delete()
        refreshTemplates()
        Toast.makeText(
            context,
            if (success) "Deleted ${item.name}" else "Failed to delete ${item.name}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showDetails(item: CgtFileItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.templateName.ifBlank { item.name })
            .setMessage(
                "File: ${item.name}\n" +
                    "Version: ${item.templateVersion}\n" +
                    "Status: ${if (item.installed) "Installed" else "Not installed"}\n" +
                    "Location: ${item.file.absolutePath}\n\n" +
                    item.templateDesc
            )
            .setPositiveButton("Close", null)
            .show()
    }

    fun setupServices() {
        runCatching {
            val serviceRegistry = PluginFragmentHelper.getServiceRegistry(PLUGIN_ID)
            templateService = serviceRegistry?.get(IdeTemplateService::class.java)
            environmentService = serviceRegistry?.get(IdeEnvironmentService::class.java)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        recyclerView?.adapter = null
        recyclerView = null
        emptyView = null
    }
}
