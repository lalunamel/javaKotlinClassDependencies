#!/usr/bin/env kscript

import java.io.File
import kotlin.system.exitProcess

val argumentsAreValid = validateArguments(args.toList())
if(!argumentsAreValid) {
    println("Required arguments:")
    println("   --source path/to/source/directory")
    println("   --destination directory/to/save/class-diagram.gv")
    // TODO better help message
    exitProcess(1)
}

val argumentPairs = getArgumentPairs(args.toList())
val sourceArg = argumentPairs["--source"]
val destinationArg = argumentPairs["--destination"]

val source = File(sourceArg).canonicalFile
val destination = File("$destinationArg${File.separator}${source.nameWithoutExtension}-class-diagram.gv").canonicalFile


// ===========
// == Begin ==
// ===========

val allFiles = source.allFiles()

var sourceFiles = allFiles.withKtAndJava().toSourceFiles()
var packages = sourceFiles.toPackages()

// Resolve star imports
sourceFiles = sourceFiles.resolveStarImports(packages)
packages = sourceFiles.toPackages()

// Resolve dependencies declared via import statements
var sourceFilesWithDependencies = sourceFiles.resolveImportedDependencies(packages)

// Resolve dependencies that are undeclared (i.e., dependencies within the same package)
sourceFilesWithDependencies = sourceFilesWithDependencies.resolveImplicitDependencies(packages)

// Format for Graphviz
val graphvizString = sourceFilesWithDependencies.toGraphviz()

destination.writeText(graphvizString)
println("GraphViz .gv file written to $destination")

// ===========
// === End ===
// ===========

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

fun List<SourceFile>.resolveImportedDependencies(packages: List<Package>): List<SourceFileWithDependencies> = this.map { sourceFile ->
    val dependencies = sourceFile.imports.toDependencies(packages)
    SourceFileWithDependencies(sourceFile, dependencies)
}

fun SourceFile.otherClassesInPackage(packages: List<Package>): List<SourceFile> {
    val thisPackage: Package = packages.first { it.name == this.pkg }

    val otherClassesInThisPackage = thisPackage.files - this

    return otherClassesInThisPackage
}

fun SourceFile.getImplicitDependencies(packages: List<Package>): List<Dependency> {
    val listOfOtherClassesInSamePackage: List<SourceFile> = this.otherClassesInPackage(packages)
    val contentsOfFile = this.file.readText()
    val classesUsedInFile = listOfOtherClassesInSamePackage.filter {
        val regex = "[^\\w]${it.nameWithoutExtension}[^\\w]".toRegex()
        regex.find(contentsOfFile) != null
    }
    return classesUsedInFile
}

fun List<SourceFileWithDependencies>.resolveImplicitDependencies(packages: List<Package>): List<SourceFileWithDependencies> = this.map { sourceFile ->
    val implicitDependencies = sourceFile.sourceFile.getImplicitDependencies(packages)
    sourceFile.copy(dependencies = sourceFile.dependencies + implicitDependencies)
}

fun SourceFile.toImport(): String = "${this.pkg}.${this.nameWithoutExtension}"

fun List<SourceFileWithDependencies>.toGraphviz(): String {
    val graphVizLines = this.map { it.toGraphviz().toString() }
            .map { "     $it" }
            .joinToString("\n")

    return "digraph {\n" +
            "${graphVizLines}\n" +
            "}"
}

fun SourceFileWithDependencies.toGraphviz(): OneToManyGraphVizRelation {
    val node1 = this.sourceFile.toImport()
    val connections = this.dependencies.map {
        when(it) {
            is ExternalDependency -> it.import
            is SourceFile -> it.toImport()
            else -> ""
        }
    }
    return OneToManyGraphVizRelation(node1, connections)
}

data class OneToOneGraphVizRelation(
        val node1: String,
        val node2: String
) {
    override fun toString(): String = "\"$node1\" -> \"$node2\""
}

data class OneToManyGraphVizRelation(
        val node1: String,
        val connections: List<String>
) {
    override fun toString(): String = "\"$node1\" -> { ${connections.map { "\"$it\"" }.joinToString(" ")}}"
}

data class Package(
        val name: String,
        val files: List<SourceFile>
)

interface Dependency

data class SourceFile(
        val file: File,
        val nameWithoutExtension: String,
        val pkg: String,
        val imports: List<String>
): Dependency

data class ExternalDependency(
        val import: String
): Dependency

data class SourceFileWithDependencies(
        val sourceFile: SourceFile,
        val dependencies: List<Dependency>
)

fun validateArguments(arguments: List<String>): Boolean {
    try {
        val argPairs = getArgumentPairs(arguments)

        val sourceArg = argPairs["--source"]
        val destinationArg = argPairs["--destination"]
        val helpArgumentExists = argPairs.containsKey("--help")

        return if(helpArgumentExists) {
            false
        } else {
            val validArguments = !sourceArg.isNullOrBlank() && !destinationArg.isNullOrBlank()
            if(!validArguments) {
                println("Invalid arguments!")
            }
            validArguments
        }
    } catch(e: Exception) {
        return false
    }
}

fun getArgumentPairs(arguments: List<String>): Map<String, String> =
        arguments.chunked(2).map { it[0] to (it.getOrNull(1) ?: "") }.toMap()
