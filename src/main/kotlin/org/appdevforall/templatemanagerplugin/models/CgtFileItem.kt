package org.appdevforall.templatemanagerplugin.models

import java.io.File

data class TemplateMetadata(
    val name: String,
    val description: String,
    val version: String
)

data class CgtFileItem(
    val file: File,
    val name: String,
    val templates: List<TemplateMetadata>,
    val installed: Boolean,
    val unregisterName: String
)

/** The first template's metadata, used to populate the card's title/description/version. */
val CgtFileItem.primaryTemplate: TemplateMetadata
    get() = templates.firstOrNull() ?: TemplateMetadata(name = "", description = "", version = "")

/** True when this .cgt file bundles more than one template. */
val CgtFileItem.hasMultipleTemplates: Boolean
    get() = templates.size > 1

/** [CgtFileItem.name] without the redundant ".cgt" extension, for display only. */
val CgtFileItem.displayName: String
    get() = if (name.endsWith(".cgt", ignoreCase = true)) name.dropLast(4) else name
