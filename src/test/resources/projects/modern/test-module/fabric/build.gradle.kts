import net.msrandom.unifiedbuilds.platforms.fabric.Fabric

fabricEntrypoints {
    add(Fabric.Entrypoint("common", listOf("net.msrandom.test.TestModule")))
}
