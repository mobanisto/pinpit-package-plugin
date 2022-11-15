/*
 * Copyright 2020-2022 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package de.mobanisto.compose.desktop.application.internal.files

import de.mobanisto.compose.desktop.application.dsl.TargetFormat
import de.mobanisto.compose.desktop.application.internal.OS
import de.mobanisto.compose.desktop.application.internal.currentOS
import org.gradle.api.tasks.Internal
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.Writer
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal fun File.mangledName(): String =
    buildString {
        append(nameWithoutExtension)
        append("-")
        append(contentHash())
        val ext = extension
        if (ext.isNotBlank()) {
            append(".$ext")
        }
    }

internal fun File.contentHash(): String {
    val md5 = MessageDigest.getInstance("MD5")
    if (isDirectory) {
        walk()
            .filter { it.isFile }
            .sortedBy { it.relativeTo(this).path }
            .forEach { md5.digestContent(it) }
    } else {
        md5.digestContent(this)
    }
    val digest = md5.digest()
    return buildString(digest.size * 2) {
        for (byte in digest) {
            append(Integer.toHexString(0xFF and byte.toInt()))
        }
    }
}

private fun MessageDigest.digestContent(file: File) {
    file.inputStream().buffered().use { fis ->
        DigestInputStream(fis, this).use { ds ->
            while (ds.read() != -1) {}
        }
    }
}

internal inline fun transformJar(
    sourceJar: File,
    targetJar: File,
    fn: (entry: ZipEntry, zin: ZipInputStream, zout: ZipOutputStream) -> Unit
) {
    ZipInputStream(FileInputStream(sourceJar).buffered()).use { zin ->
        ZipOutputStream(FileOutputStream(targetJar).buffered()).use { zout ->
            for (sourceEntry in generateSequence { zin.nextEntry }) {
                fn(sourceEntry, zin, zout)
            }
        }
    }
}

internal fun copyZipEntry(
    entry: ZipEntry,
    from: InputStream,
    to: ZipOutputStream,
) {
    val newEntry = ZipEntry(entry.name).apply {
        comment = entry.comment
        extra = entry.extra
        lastModifiedTime = entry.lastModifiedTime
    }
    to.withNewEntry(newEntry) {
        from.copyTo(to)
    }
}

internal inline fun ZipOutputStream.withNewEntry(zipEntry: ZipEntry, fn: () -> Unit) {
    putNextEntry(zipEntry)
    fn()
    closeEntry()
}

internal fun InputStream.copyTo(file: File) {
    file.outputStream().buffered().use { os ->
        copyTo(os)
    }
}

@Internal
internal fun findOutputFileOrDir(dir: File, targetFormat: TargetFormat): File =
    when (targetFormat) {
        TargetFormat.AppImage -> dir
        else -> dir.walk().first { it.isFile && it.name.endsWith(targetFormat.fileExt) }
    }

internal fun File.checkExistingFile(): File =
    apply {
        check(isFile) { "'$absolutePath' does not exist" }
    }

internal val File.isJarFile: Boolean
    get() = name.endsWith(".jar", ignoreCase = true) && isFile

internal fun File.normalizedPath() =
    if (currentOS == OS.Windows) absolutePath.replace("\\", "\\\\") else absolutePath

internal fun Writer.writeLn(s: String) {
    write(s)
    write("\n")
}

internal fun ByteArray.isProbablyNotBinary() : Boolean {
    var printable = 0
    var nonPrintable = 0
    for (byte in this) {
        if (byte in 32..126) {
            printable++
        } else {
            nonPrintable++
        }
        if (printable + nonPrintable >= 100) break
    }
    return printable / (printable + nonPrintable).toDouble() > 0.8
}
