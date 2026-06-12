package org.appdevforall.templatemanagerplugin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import org.appdevforall.templatemanagerplugin.R
import com.itsaky.androidide.plugins.base.PluginFragmentHelper
import com.itsaky.androidide.plugins.services.IdeTooltipService

class TemplateManagerPluginFragment : Fragment() {

    companion object {
        private const val PLUGIN_ID = "org.appdevforall.templatemanagerplugin"
    }

    private var statusText: TextView? = null
    private var actionButton: MaterialButton? = null
    private var tooltipService: IdeTooltipService? = null

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
        statusText = view.findViewById(R.id.statusText)
        actionButton = view.findViewById(R.id.actionButton)

        actionButton?.setOnClickListener {
            onActionButtonClicked()
        }

        actionButton?.setOnLongClickListener { button ->
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

        updateStatus("TemplateManagerPlugin is ready!")
    }

    private fun onActionButtonClicked() {
        updateStatus("Action performed!")
    }

    private fun updateStatus(message: String) {
        statusText?.text = message
    }

    fun setupServices() {
        runCatching {
            val serviceRegistry = PluginFragmentHelper.getServiceRegistry(PLUGIN_ID)
            tooltipService = serviceRegistry?.get(IdeTooltipService::class.java)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        statusText = null
        actionButton = null
    }
}
