/*
 * Copyright 2020-2021 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package de.mobanisto.compose.desktop.ui.tooling.preview.rpc.utils

internal fun systemProperty(name: String): String =
    System.getProperty(name) ?: error("System property is not found: '$name'")

internal val isWindows =
    systemProperty("os.name").startsWith("windows", ignoreCase = true)

internal val previewTestClaspath: String
    get() = systemProperty("org.jetbrains.compose.test.rpc.classpath")

internal val Int.secondsAsMillis: Int
    get() = this * 1000

internal val Long.secondsAsMillis: Long
    get() = this * 1000