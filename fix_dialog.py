import re

with open('app/src/main/java/com/lavacrafter/maptimelinetool/ui/EditPointDialog.kt', 'r') as f:
    text = f.read()

# Make sure title is trimmed, and if blank fallback to point.title
text = text.replace(
    'onSave(title, note, currentPhotoPath)',
    'onSave(title.trim().ifBlank { point.title }, note.trim(), currentPhotoPath)'
)

with open('app/src/main/java/com/lavacrafter/maptimelinetool/ui/EditPointDialog.kt', 'w') as f:
    f.write(text)

with open('app/src/main/java/com/lavacrafter/maptimelinetool/MainActivity.kt', 'r') as f:
    text = f.read()

text = text.replace(
    'viewModel.addPointWithTags(title, note, loc, createdAt, selectedTags, persistedPhotoPath)',
    'viewModel.addPointWithTags(title.trim(), note.trim(), loc, createdAt, selectedTags, persistedPhotoPath)'
)

with open('app/src/main/java/com/lavacrafter/maptimelinetool/MainActivity.kt', 'w') as f:
    f.write(text)
