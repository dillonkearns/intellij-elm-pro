package org.elm.workspace.elmreview

import com.google.gson.Gson
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange

data class ElmReviewError(
    var suppressed: Boolean? = null,
    var path: String? = null,
    var rule: String? = null,
    var ruleLink: String? = null,
    var message: String? = null,
    var region: Region? = null,
    val details: List<String>? = null,
    var html: String? = null,
    val fix: List<Fix>?,

)

data class Region(
    var start: Location? = null,
    var end: Location? = null
) {
    fun toTextRange(document: Document): TextRange? {
        val startOffset = toOffset(document, start?.line!!, start?.column!!)
        val endOffset = toOffset(document, end?.line!!, end?.column!!)
        return if (startOffset != null && endOffset != null && startOffset < endOffset) {
            TextRange(startOffset, endOffset)
        } else {
            null
        }
    }

    companion object {
        @Suppress("NAME_SHADOWING")
        fun toOffset(document: Document, line: Int, column: Int): Int? {
            val line = line - 1
            val column = column - 1
            if (line < 0 || line >= document.lineCount) return null
            return (document.getLineStartOffset(line) + column)
                .takeIf { it <= document.textLength }
        }
    }
}

data class Location(
    var line: Int = 0,
    var column: Int = 0
)

enum class ReviewOutputType(val label: String) {
    ERROR("error"), COMPILE_ERRORS("compile-errors"), REVIEW_ERRORS("review-errors");
}

data class Root(
    val type: String,
    val errors: List<ErrorEntry>,
)
data class ErrorEntry(
    val path: String,
    val errors: List<ErrorDetail>
)
data class ErrorDetail(
    var path: String?,
    val rule: String,
    val message: String,
    val ruleLink: String,
    val details: List<String>?,
    val region: Region,
    val fix: List<Fix>,
    val formatted: List<org.elm.workspace.compiler.Chunk>,
    val suppressed: Boolean,
    val originallySuppressed: Boolean
)
data class Fix(
    val range: Region,
    val string: String
)

fun readErrorReportLine(text: String): ElmReviewError? {
    return Gson().fromJson(text, ElmReviewError::class.java)
}

fun readErrorReport(text: String, disposable: Disposable): List<ElmReviewError> {
    val result = mutableListOf<ElmReviewError>()
    val reader = Gson().newJsonReader(text.reader())
    reader.beginObject()


    while (reader.hasNext()) {
        when (reader.nextName()) {
            "errors" -> {
                reader.beginArray()

                while (reader.hasNext()) {
                    if (Disposer.isDisposed(disposable)) {
                        return emptyList()
                    }

                    Gson().fromJson<ErrorEntry>(reader, ErrorEntry::class.java).let {
                        result.addAll(it.errors.map { errorDetail ->
                            if (Disposer.isDisposed(disposable)) {
                                return emptyList()
                            }
                            ElmReviewError(
                                suppressed = errorDetail.suppressed,
                                path = it.path,
                                rule = errorDetail.rule,
                                message = errorDetail.message,
                                region = errorDetail.region,
                                ruleLink = errorDetail.ruleLink,
                                details = errorDetail.details,
                                html = org.elm.workspace.compiler.chunksToHtml(errorDetail.formatted),
                                fix = errorDetail.fix
                            )
                        })
                    }
                }
                reader.endArray()
            }
            else -> {
                reader.skipValue()
            }
        }
    }
    return result.toList()
}
