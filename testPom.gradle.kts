tasks.register("testPom") {
    doLast {
        val config = configurations.getByName("releaseRuntimeClasspath")
        val view = config.incoming.artifactView {
            attributes {
                attribute(Attribute.of("artifactType", String::class.java), "pom")
            }
        }
        view.artifacts.forEach {
            println("Found POM: ${it.file.name}")
        }
    }
}
