with open("app/build.gradle.kts", "r") as f:
    text = f.read()

# remove sourceSets
import re
text = re.sub(r'    sourceSets \{\n        getByName\("main"\).res.srcDir\([^)]+\)\n    \}', '', text)

# update output dir
text = text.replace('layout.buildDirectory.dir("generated/oss_menu_resources/res").get().asFile', 'projectDir.resolve("src/main/res")')
text = text.replace('val outputDir = File(outputResDir.get().asFile, "raw")', 'val outputDir = projectDir.resolve("src/main/res/raw")')

with open("app/build.gradle.kts", "w") as f:
    f.write(text)
