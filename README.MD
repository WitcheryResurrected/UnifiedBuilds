# UnifiedBuilds

Helper Gradle plugin with the goal of wrapping Forge Gradle and Loom into easy to use, modular APIs.

## Current features:
* Minifying Jars by using proguard
* Having a similar setup for tasks between Forge and Fabric projects(or any other platform that is to be supported)
* Allow common code easily between projects of different modloaders
* Not requiring any sort of runtime
* Providing a common mod information setup between different loaders
* Handling modular projects and allowing them to be exported to a single Jar file(similarly to the Fabric API).
* Handling problems with Forge Gradle in legacy projects, as well as adding new easier to use handling for hacks(such as coremods and accesstransformers)

### What UnifiedBuilds is _not_ for:
* Doing stuff that are impossible to do with normal Forge Gradle and Loom
* Replacing Forge Gradle and Loom entirely(even dependencies beyond the very basics would require using their APIs)
* Shade projects into each other(even if this is done internally for a few reasons)
