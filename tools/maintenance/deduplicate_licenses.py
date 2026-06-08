# Copyright 2026 Muchen Jiang (lava-crafter)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

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
