#!/usr/bin/env kscript

import java.io.File

val allFiles = pwd().allFiles()

var sourceFiles = allFiles.withKtAndJava().toSourceFiles()
var packages = sourceFiles.toPackages()

// Resolve star imports
sourceFiles = sourceFiles.resolveStarImports(packages)
packages = sourceFiles.toPackages()

// Resolve dependencies
val sourceFilesWithDependencies = sourceFiles.resolveDependencies(packages)

println(sourceFilesWithDependencies)

TODO https://www.graphviz.org/doc/info/lang.html


fun pwd(): File = File(System.getProperty("user.dir"))

fun File.allFiles(): List<File> = this.walkTopDown().toList()

fun List<File>.withKtAndJava(): List<File> = this.filter { it.extension == "kt" || it.extension == "java" }

fun List<File>.toSourceFiles(): List<SourceFile> = this.map {
    SourceFile(
            it,
            it.nameWithoutExtension,
            it.pkg(),
            it.imports()
    )
}

fun File.pkg(): String = this.readLines().first { it.startsWith("package ") }.replace("package ", "").replace(";", "")

fun File.imports(): List<String> = this.readLines().filter { it.startsWith("import ") }.map { it.replace("import ", "").replace(";", "") }

fun List<SourceFile>.toPackages(): List<Package> = this.groupBy { it.pkg }.map { Package(it.key, it.value) }

fun List<SourceFile>.resolveStarImports(packages: List<Package>): List<SourceFile> = this.map { sourceFile ->
    val resolvedImports: List<String> = sourceFile.imports.flatMap { import ->
        if(import.endsWith(".*")) {
            val possiblePackageName = import.replace(".*", "")
            val isPackageImport = packages.map {it.name}.contains(possiblePackageName)

            if(isPackageImport) {
                val pkg = packages.first { it.name == possiblePackageName }
                pkg.files.map { it.toImport() }
            } else {
                listOf(possiblePackageName)
            }
        } else {
            listOf(import)
        }
    }.toSet().toList()

    sourceFile.copy(
            imports = resolvedImports
    )
}

fun List<String>.toDependencies(packages: List<Package>): List<Dependency> = this.map { import ->
    val split = import.split(".")
    val pkgName = split.slice(0..split.size-2).joinToString(".")
    val nameWithoutExtension = split.last()

    val pkg = packages.firstOrNull { it.name == pkgName }
    val dependency = pkg?.files?.firstOrNull { it.nameWithoutExtension == nameWithoutExtension } ?: ExternalDependency(import)

    dependency
}

fun List<SourceFile>.resolveDependencies(packages: List<Package>): List<SourceFileWithDependencies> = this.map { sourceFile ->
    val dependencies = sourceFile.imports.toDependencies(packages)
    SourceFileWithDependencies(sourceFile, dependencies)
}

fun SourceFile.toImport(): String = "${this.pkg}.${this.nameWithoutExtension}"

data class Package(
        val name: String,
        val files: List<SourceFile>
)

data class SourceFile(
        val file: File,
        val nameWithoutExtension: String,
        val pkg: String,
        val imports: List<String>
): Dependency

interface Dependency

data class ExternalDependency(
        val import: String
): Dependency

data class SourceFileWithDependencies(
        val sourceFile: SourceFile,
        val dependencies: List<Dependency>
)
