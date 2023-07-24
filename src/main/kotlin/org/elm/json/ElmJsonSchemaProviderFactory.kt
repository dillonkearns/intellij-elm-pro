package org.elm.json

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType

class ElmJsonSchemaProviderFactory : JsonSchemaProviderFactory, DumbAware {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
        return listOf(ElmJsonJsonSchemaFileProvider())
    }
}

class ElmJsonJsonSchemaFileProvider : JsonSchemaFileProvider {
    override fun isAvailable(file: VirtualFile): Boolean = file.name == "elm.json"
    override fun getName(): String {
        return "ElmJsonSchema"
    }

    override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema
    override fun isUserVisible(): Boolean = false
    override fun getSchemaFile(): VirtualFile? {
        return JsonSchemaProviderFactory.getResourceFile(ElmJsonJsonSchemaFileProvider::class.java, SCHEMA_PATH)
    }

    companion object {
        const val SCHEMA_PATH: String = "/jsonSchema/elm.json-schema.json"
    }
}
