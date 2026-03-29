import os

def deduplicate(file_path):
    if not os.path.exists(file_path):
        return
    with open(file_path, "r") as f:
        lines = f.readlines()
    
    seen_names = set()
    unique_lines = []
    for line in lines:
        parts = line.strip().split(" ", 1)
        if len(parts) == 2:
            name = parts[1].strip()
            if name.lower() not in seen_names:
                seen_names.add(name.lower())
                unique_lines.append(line)
        else:
            unique_lines.append(line)
            
    with open(file_path, "w") as f:
        f.writelines(unique_lines)

deduplicate("app/build/generated/third_party_licenses/debug/res/raw/third_party_license_metadata")
deduplicate("app/build/generated/third_party_licenses/release/res/raw/third_party_license_metadata")
