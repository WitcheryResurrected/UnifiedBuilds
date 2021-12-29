import net.msrandom.unifiedbuilds.platforms.Fabric

fabricEntrypoints {
    add(Fabric.Entrypoint("common", listOf("net.msrandom.test.TestModule")))
}
