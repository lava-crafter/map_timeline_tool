    sourceSets {
        getByName("release").res.srcDir(layout.buildDirectory.dir("generated/res/releaseOssLicensesTask").get().asFile)
        getByName("debug").res.srcDir(layout.buildDirectory.dir("generated/res/debugOssLicensesTask").get().asFile)
    }
