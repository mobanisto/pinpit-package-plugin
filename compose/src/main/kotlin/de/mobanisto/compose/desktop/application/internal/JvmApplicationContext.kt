/*
 * Copyright 2020-2022 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package de.mobanisto.compose.desktop.application.internal

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import de.mobanisto.compose.desktop.application.dsl.JvmApplicationBuildType
import de.mobanisto.compose.internal.KOTLIN_JVM_PLUGIN_ID
import de.mobanisto.compose.internal.KOTLIN_MPP_PLUGIN_ID
import de.mobanisto.compose.internal.javaSourceSets
import de.mobanisto.compose.internal.joinDashLowercaseNonEmpty
import de.mobanisto.compose.internal.mppExt
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

internal data class JvmApplicationContext(
    val project: Project,
    private val appInternal: JvmApplicationInternal,
    val buildType: JvmApplicationBuildType,
    private val taskGroup: String = composeDesktopTaskGroup
) {
    val app: JvmApplicationData
        get() = appInternal.data

    val appDirName: String
        get() = joinDashLowercaseNonEmpty(appInternal.name, buildType.classifier)

    val appTmpDir: Provider<Directory>
        get() = project.layout.buildDirectory.dir(
            "mocompose/tmp/$appDirName"
        )

    fun <T : Task> T.useAppRuntimeFiles(fn: T.(JvmApplicationRuntimeFiles) -> Unit) {
        val runtimeFiles = app.jvmApplicationRuntimeFilesProvider?.jvmApplicationRuntimeFiles(project)
            ?: JvmApplicationRuntimeFiles(
                allRuntimeJars = app.fromFiles,
                mainJar = app.mainJar,
                taskDependencies = app.dependenciesTaskNames.toTypedArray()
            )
        runtimeFiles.configureUsageBy(this, fn)
    }

    val tasks = JvmTasks(project, buildType, taskGroup)

    val packageNameProvider: Provider<String>
        get() = project.provider { appInternal.nativeDistributions.packageName ?: project.name }

    inline fun <reified T> provider(noinline fn: () -> T): Provider<T> =
        project.provider(fn)

    fun configureDefaultApp() {
        if (project.plugins.hasPlugin(KOTLIN_MPP_PLUGIN_ID)) {
            var isJvmTargetConfigured = false
            project.mppExt.targets.all { target ->
                if (target.platformType == KotlinPlatformType.jvm) {
                    if (!isJvmTargetConfigured) {
                        appInternal.from(target)
                        isJvmTargetConfigured = true
                    } else {
                        project.logger.error("w: Default configuration for Compose Desktop Application is disabled: " +
                                "multiple Kotlin JVM targets definitions are detected. " +
                                "Specify, which target to use by using `compose.desktop.application.from(kotlinMppTarget)`")
                        appInternal.disableDefaultConfiguration()
                    }
                }
            }
        } else if (project.plugins.hasPlugin(KOTLIN_JVM_PLUGIN_ID)) {
            val mainSourceSet = project.javaSourceSets.getByName("main")
            appInternal.from(mainSourceSet)
        }
    }
}