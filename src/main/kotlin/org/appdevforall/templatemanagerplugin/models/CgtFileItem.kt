package org.appdevforall.templatemanagerplugin.models

import java.io.File

data class CgtFileItem(
    val file: File,
    val name: String,
    val templateName: String,
    val templateDesc: String,
    val templateVersion: String,
    val installed: Boolean,
    val unregisterName: String
)
