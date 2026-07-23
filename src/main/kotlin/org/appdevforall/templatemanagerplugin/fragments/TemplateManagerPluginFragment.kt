package org.appdevforall.templatemanagerplugin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.appdevforall.templatemanagerplugin.R
import org.appdevforall.templatemanagerplugin.adapters.CgtFileAdapter
import org.appdevforall.templatemanagerplugin.models.CgtFileItem
import org.appdevforall.templatemanagerplugin.models.TemplateMetadata
import org.appdevforall.templatemanagerplugin.models.displayName
import org.appdevforall.templatemanagerplugin.models.primaryTemplate
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

    /**
     * Reads every "<path>/template/template.json" entry in a .cgt (zip) file — there can be
     * more than one when the file bundles multiple templates behind a single install.
     */
    private fun parseCgtFile(file: File, installed: Boolean, unregisterName: String): CgtFileItem? {
        val templates = mutableListOf<TemplateMetadata>()
        file.inputStream().use { stream ->
            ZipInputStream(stream).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name.endsWith(TEMPLATE_JSON_SUFFIX)) {
                        val json = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                        templates.add(
                            TemplateMetadata(
                                name = json.optString("name"),
                                description = json.optString("description"),
                                version = json.optString("version"),
                                optionalTags = parseOptionalTags(json)
                            )
                        )
                    }
                    zip.closeEntry()
                }
            }
        }
        if (templates.isEmpty()) return null
        return CgtFileItem(
            file = file,
            name = file.name,
            templates = templates,
            installed = installed,
            unregisterName = unregisterName
        )
    }

    /**
     * Collects the tags declared under parameters.optional. Each is rendered as
     * "<tag> (<identifier>)" when the entry carries an identifier, else just "<tag>".
     */
    private fun parseOptionalTags(json: JSONObject): List<String> {
        val optional = json.optJSONObject("parameters")?.optJSONObject("optional")
            ?: return emptyList()
        val tags = mutableListOf<String>()
        val keys = optional.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val identifier = optional.optJSONObject(key)?.optString("identifier").orEmpty()
            tags.add(if (identifier.isNotBlank()) "$key ($identifier)" else key)
        }
        return tags
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
            if (success) "Installed ${item.displayName}" else "Failed to install ${item.displayName}",
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
            if (success) "Uninstalled ${item.displayName}" else "Failed to uninstall ${item.displayName}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun confirmDeleteDownloadFile(item: CgtFileItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete ${item.displayName}?")
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
            if (success) "Deleted ${item.displayName}" else "Failed to delete ${item.displayName}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showDetails(item: CgtFileItem) {
        val primary = item.primaryTemplate
        val message = buildString {
            append("File: ${item.displayName}\n")
            append("Status: ${if (item.installed) "Installed" else "Not installed"}\n")
            append("Location: ${item.file.absolutePath}\n\n")
            if (item.templates.size > 1) {
                append("Contains ${item.templates.size} templates:\n")
                item.templates.forEachIndexed { index, template ->
                    append("\n${index + 1}. ${template.name.ifBlank { "(unnamed)" }} (v${template.version})")
                    if (template.description.isNotBlank()) {
                        append("\n   ${template.description}")
                    }
                    if (template.optionalTags.isNotEmpty()) {
                        append("\n   Optional: ${template.optionalTags.joinToString(", ")}")
                    }
                }
            } else {
                append("Version: ${primary.version}\n\n")
                append(primary.description)
                if (primary.optionalTags.isNotEmpty()) {
                    append("\n\nOptional parameters:")
                    primary.optionalTags.forEach { append("\n  • $it") }
                }
            }
        }
        val density = resources.displayMetrics.density
        val padding = (24 * density).toInt()
        val textView = TextView(requireContext()).apply {
            text = message
            setTextIsSelectable(true)
            setPadding(padding, padding, padding, 0)
        }
        val scrollView = ScrollView(requireContext()).apply {
            addView(textView)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(primary.name.ifBlank { item.displayName })
            .setView(scrollView)
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
