package org.appdevforall.templatemanagerplugin.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.appdevforall.templatemanagerplugin.R
import org.appdevforall.templatemanagerplugin.adapters.CgtFileAdapter
import org.appdevforall.templatemanagerplugin.models.CgtFileItem
import com.itsaky.androidide.plugins.base.PluginFragmentHelper
import com.itsaky.androidide.plugins.services.IdeTemplateService
import com.itsaky.androidide.plugins.services.IdeTooltipService
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

class TemplateManagerPluginFragment : Fragment() {

    companion object {
        private const val PLUGIN_ID = "org.appdevforall.templatemanagerplugin"
        private const val TEMPLATE_JSON_SUFFIX = "/template/template.json"
    }

    private var recyclerView: RecyclerView? = null
    private var btnCancel: Button? = null
    private var btnLoad: Button? = null
    private var tooltipService: IdeTooltipService? = null
    private var templateService: IdeTemplateService? = null

    private val items = mutableListOf<CgtFileItem>()
    private val adapter = CgtFileAdapter(items)

    private val openTemplates = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult

        val parsed = uris.mapNotNull { uri -> runCatching { parseCgtFile(uri) }.getOrNull() }
        items.addAll(parsed)
        adapter.notifyDataSetChanged()

        val skipped = uris.size - parsed.size
        if (skipped > 0) {
            Toast.makeText(
                context,
                "Skipped $skipped file(s) that aren't valid templates",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

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
        btnCancel = view.findViewById(R.id.btnCancel)
        btnLoad = view.findViewById(R.id.btnLoad)

        recyclerView?.layoutManager = LinearLayoutManager(requireContext())
        recyclerView?.adapter = adapter

        btnCancel?.setOnClickListener {
            onCancelClicked()
        }

        btnLoad?.setOnClickListener {
            onLoadClicked()
        }

        btnLoad?.setOnLongClickListener { button ->
            tooltipService?.showTooltip(
                anchorView = button,
                category = "org_appdevforall_templatemanagerplugin",
                tag = "org_appdevforall_templatemanagerplugin.overview"
            ) ?: run {
                activity?.runOnUiThread {
                    Toast.makeText(context, "Long press detected! Tooltip service not available.", Toast.LENGTH_SHORT).show()
                }
            }
            true
        }

        if (savedInstanceState == null && items.isEmpty()) {
            openTemplates.launch(arrayOf("*/*"))
        }
    }

    /** Reads a .cgt (zip) file's "template/template.json" entry to extract its metadata. */
    private fun parseCgtFile(uri: Uri): CgtFileItem? {
        val displayName = DocumentFile.fromSingleUri(requireContext(), uri)?.name
            ?: uri.lastPathSegment
            ?: "template.cgt"

        val input = requireContext().contentResolver.openInputStream(uri) ?: return null
        return input.use { stream ->
            ZipInputStream(stream).use { zip ->
                var result: CgtFileItem? = null
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name.endsWith(TEMPLATE_JSON_SUFFIX)) {
                        val json = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                        result = CgtFileItem(
                            uri = uri,
                            name = displayName,
                            isChecked = true,
                            templateName = json.optString("name"),
                            templateDesc = json.optString("description"),
                            templateVersion = json.optString("version")
                        )
                        break
                    }
                    zip.closeEntry()
                }
                result
            }
        }
    }

    private fun onCancelClicked() {
        items.clear()
        adapter.notifyDataSetChanged()
    }

    private fun onLoadClicked() {
        val selected = adapter.getSelectedItems()
        if (selected.isEmpty()) {
            Toast.makeText(context, "No templates selected", Toast.LENGTH_SHORT).show()
            return
        }

        val service = templateService
        if (service == null) {
            Toast.makeText(context, "Template service is not available", Toast.LENGTH_SHORT).show()
            return
        }

        val successCount = selected.count { item ->
            val file = copyToCache(item)
            file != null && service.registerTemplate(file)
        }
        service.reloadTemplates()

        Toast.makeText(
            context,
            "Loaded $successCount of ${selected.size} template(s)",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun copyToCache(item: CgtFileItem): File? = runCatching {
        val outFile = File(requireContext().cacheDir, item.name)
        requireContext().contentResolver.openInputStream(item.uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        outFile
    }.getOrNull()

    fun setupServices() {
        runCatching {
            val serviceRegistry = PluginFragmentHelper.getServiceRegistry(PLUGIN_ID)
            tooltipService = serviceRegistry?.get(IdeTooltipService::class.java)
            templateService = serviceRegistry?.get(IdeTemplateService::class.java)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        recyclerView?.adapter = null
        recyclerView = null
        btnCancel = null
        btnLoad = null
    }
}
