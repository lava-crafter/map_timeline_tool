android {
    sourceSets {
        getByName("release").res.srcDir(layout.buildDirectory.dir("generated/res/releaseOssLicensesTask"))
        getByName("debug").res.srcDir(layout.buildDirectory.dir("generated/res/debugOssLicensesTask"))
    }
}
