# Java Kotlin Class Dependencies
Visualize Java and Kotlin class dependencies

## Install

You gotta have `kscript` installed: https://github.com/holgerbrandl/kscript#installation

## Running

Move the script to the project you'd like to analyize

Run the script

## TODO

- resolve dependencies that are not imported

e.g., the relationship between InMemoryRepositoryCache and RepositoryCache

They're in the same package so there's no import statement to declare the dependency

Reminder: A file can use a Class that's in a sub-package of the package it's in
