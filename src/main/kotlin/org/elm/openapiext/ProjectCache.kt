/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.elm.openapiext

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.containers.ContainerUtil
import org.elm.lang.core.psi.elmStructureModificationTracker
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class ProjectCache<in T, R>(
    cacheName: String,
    private val dependencyGetter: (Project) -> Any = { it.elmStructureModificationTracker }
) {
    init {
        if (!registered.add(cacheName)) {
            error("""
                ProjectCache `$cacheName` is already registered.
                Make sure ProjectCache is static, that is, put it inside companion object.
            """.trimIndent())
        }
    }

    private val cacheKey: Key<CachedValue<ConcurrentMap<T, R>>> = Key.create(cacheName)

    fun getOrPut(project: Project, key: T, defaultValue: () -> R): R {
        val cache = CachedValuesManager.getManager(project)
            .getCachedValue(project, cacheKey, {
                CachedValueProvider.Result.create(
                    ConcurrentHashMap(),
                    dependencyGetter(project)
                )
            }, false)
        return cache.getOrPut(key) { defaultValue() }
    }

    companion object {
        private val registered = ContainerUtil.newConcurrentSet<String>()
    }
}
