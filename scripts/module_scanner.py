#!/usr/bin/env python3
"""
Module Scanner for Collective Client.
Discovers unconverted Java modules, calculates LOC, categorizes them, and tracks migration progress.
"""

import os
from pathlib import Path
from typing import Dict, List, Any

PROJECT_ROOT = Path(__file__).resolve().parent.parent
JAVA_SRC = PROJECT_ROOT / "src" / "main" / "java" / "me" / "collective" / "client"
KOTLIN_SRC = PROJECT_ROOT / "src" / "main" / "kotlin" / "me" / "collective" / "client"

# Packages and root files explicitly excluded from conversion per MIGRATION.md
EXCLUDED_PATTERNS = [
    "mixin/",
    "bootstrap/",
    "managers/",
    "render/",
    "ClientContext.java",
    "ClientAccess.java",
    "CollectiveClient.java",
    "NamedFeature.java"
]

def is_excluded(rel_path: str) -> bool:
    if rel_path in ["ClientContext.java", "ClientAccess.java", "CollectiveClient.java", "NamedFeature.java"]:
        return True
    if rel_path.startswith("render/"):
        return True
    for pat in ["mixin/", "bootstrap/", "managers/"]:
        if pat in rel_path:
            return True
    return False

def count_lines(file_path: Path) -> int:
    try:
        with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
            return sum(1 for _ in f)
    except Exception:
        return 0

def scan_modules() -> Dict[str, Any]:
    """
    Scans the repository for converted and unconverted modules and components.
    """
    unconverted: List[Dict[str, Any]] = []
    converted: List[Dict[str, Any]] = []
    excluded: List[Dict[str, Any]] = []

    # 1. Scan Java files
    if JAVA_SRC.exists():
        for root, _, files in os.walk(JAVA_SRC):
            for file in files:
                if file.endswith('.java'):
                    full_path = Path(root) / file
                    rel_to_client = str(full_path.relative_to(JAVA_SRC))
                    
                    loc = count_lines(full_path)
                    entry = {
                        "name": file[:-5],
                        "filename": file,
                        "path": str(full_path.relative_to(PROJECT_ROOT)),
                        "abs_path": str(full_path),
                        "package": full_path.parent.name,
                        "category": full_path.parent.parent.name if full_path.parent.name in ['combat', 'exploit', 'player', 'render', 'movement', 'misc', 'client', 'hud'] else full_path.parent.name,
                        "loc": loc,
                        "type": "java"
                    }
                    
                    if is_excluded(rel_to_client):
                        entry["status"] = "excluded"
                        excluded.append(entry)
                    else:
                        entry["status"] = "unconverted"
                        unconverted.append(entry)

    # 2. Scan Kotlin files
    if KOTLIN_SRC.exists():
        for root, _, files in os.walk(KOTLIN_SRC):
            for file in files:
                if file.endswith('.kt'):
                    full_path = Path(root) / file
                    loc = count_lines(full_path)
                    converted.append({
                        "name": file[:-3],
                        "filename": file,
                        "path": str(full_path.relative_to(PROJECT_ROOT)),
                        "abs_path": str(full_path),
                        "package": full_path.parent.name,
                        "category": full_path.parent.parent.name if full_path.parent.name in ['combat', 'exploit', 'player', 'render', 'movement', 'misc', 'client', 'hud'] else full_path.parent.name,
                        "loc": loc,
                        "type": "kotlin",
                        "status": "converted"
                    })

    total_files = len(unconverted) + len(converted)
    total_loc = sum(e["loc"] for e in unconverted) + sum(e["loc"] for e in converted)
    converted_loc = sum(e["loc"] for e in converted)
    progress_pct = (converted_loc / total_loc * 100.0) if total_loc > 0 else 100.0

    # Group unconverted by package
    packages: Dict[str, List[Dict[str, Any]]] = {}
    for item in unconverted:
        pkg = item["package"]
        if pkg not in packages:
            packages[pkg] = []
        packages[pkg].append(item)

    return {
        "stats": {
            "total_files": total_files,
            "converted_files": len(converted),
            "unconverted_files": len(unconverted),
            "excluded_files": len(excluded),
            "total_loc": total_loc,
            "converted_loc": converted_loc,
            "progress_percent": round(progress_pct, 1)
        },
        "packages": {pkg: {"count": len(files), "loc": sum(f["loc"] for f in files)} for pkg, files in packages.items()},
        "unconverted": sorted(unconverted, key=lambda x: x["path"]),
        "converted": sorted(converted, key=lambda x: x["path"])
    }

if __name__ == "__main__":
    import json
    data = scan_modules()
    print(json.dumps(data["stats"], indent=2))
    print(f"\nUnconverted packages: {data['packages']}")
