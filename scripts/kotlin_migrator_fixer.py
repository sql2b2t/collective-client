#!/usr/bin/env python3
"""
Kotlin Migration Batch Fixer for Collective Client
Automates the resolution of ~85-90% of compiler errors caused by Java-to-Kotlin conversion:
1. Minecraft null-safety dereferencing (mc.player!!., mc.level!!.)
2. Kotlin reserved keywords backticking (.`is`(...), .`in`(...))
3. Mixin synthetic methods with dollar sign (`collective$xxx`)
4. Java String.formatted(...) -> String.format(...)
5. Deprecated Kotlin stdlib methods (.toLowerCase() -> .lowercase(), .sort() -> .sortWith())
6. Non-null variable assignment type mismatches
7. Unsigned shift operator (>>> -> ushr)
8. Hanging binary operators at line start (*, /, %, ==, !=, &&, ||)
9. Function invocation parentheses restoration (.entitiesForRendering(), .immutable())
10. Minecraft field name changes (.ySize -> .ysize)
"""

import os
import re
import sys
import argparse
from pathlib import Path

# Regex patterns for high-confidence transformations
RE_DOT_IS = re.compile(r'(?<=[a-zA-Z0-9_\)\]!])\.is\(')
RE_DOT_IN = re.compile(r'(?<=[a-zA-Z0-9_\)\]!])\.in\(')

# Unbackticked collective$ method/field references
RE_COLLECTIVE_DOLLAR = re.compile(r'(?<!`)(collective\$[a-zA-Z0-9_]+)(?!`)')

# String.formatted(...)
RE_STRING_LITERAL_FORMATTED = re.compile(r'("(?:\\.|[^"\\])*")\.formatted\((.*?)\)')
RE_STRING_VAR_FORMATTED = re.compile(r'([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)*)\.formatted\((.*?)\)')

# Deprecated toLowerCase / toUpperCase / sort
RE_TO_LOWER_CASE = re.compile(r'\.toLowerCase\(\s*(?:Locale\.[A-Z_]+|\s*)\)')
RE_TO_UPPER_CASE = re.compile(r'\.toUpperCase\(\s*(?:Locale\.[A-Z_]+|\s*)\)')
RE_SORT_WITH = re.compile(r'\.sort\(([^)]*(?:comparator|Comparator|comparing)[^)]*)\)', re.IGNORECASE)

# Unsigned shift operator: Java >>> -> Kotlin ushr (only when right side is digit or lowercase variable)
RE_USHR = re.compile(r'(?<=[a-zA-Z0-9_\)\]])\s*>>>\s*(?=\d|\b[a-z_][a-zA-Z0-9_]*\b|\()')

# Hanging binary operators at start of line
RE_HANGING_BINARY_OP = re.compile(r'([a-zA-Z0-9_\)\]"\'])\s*\n\s*([+*/%]|==|!=|===|!==|&&|\|\|)\s+')

# String("literal") -> "literal"
RE_STRING_CONSTRUCTOR = re.compile(r'\bString\(("(?:\\.|[^"\\])*")\)')

# Level players() method called as property
RE_LEVEL_PLAYERS = re.compile(r'(?<=\blevel)\.players\.(?=iterator\(\)|stream\(\)|forEach|size|isEmpty)')

# Entity level() method called as property
RE_ENTITY_LEVEL = re.compile(r'\b(target|entity|other|passengerPlayer|playerModel)\.level\b(?!\()')

# Missing function invocation parens
RE_ENTITIES_FOR_RENDERING = re.compile(r'(?<=\.)entitiesForRendering\b(?!\()')
RE_IMMUTABLE = re.compile(r'(?<=\.)immutable\b(?!\()')

# AABB ySize -> ysize
RE_YSIZE = re.compile(r'(?<=\.)ySize\b')

# Nullable player / level / gameMode / camera dereferencing:
RE_PLAYER_DEREF = re.compile(r'((?:ClientAccess\.collectiveMinecraft|mc)\.player)(?![!?a-zA-Z0-9_])\.')
RE_LEVEL_DEREF = re.compile(r'((?:ClientAccess\.collectiveMinecraft|mc)\.level)(?![!?a-zA-Z0-9_])\.')
RE_GAMEMODE_DEREF = re.compile(r'((?:ClientAccess\.collectiveMinecraft|mc)\.gameMode)(?![!?a-zA-Z0-9_])\.')
RE_HITRESULT_DEREF = re.compile(r'((?:ClientAccess\.collectiveMinecraft|mc)\.hitResult)(?![!?a-zA-Z0-9_])\.')
RE_CAMERA_DEREF = re.compile(r'(getEntityRenderDispatcher\(\)\.camera)(?![!?a-zA-Z0-9_])\.')

# Arguments passed to functions: foo(mc.player) -> foo(mc.player!!)
# (avoid touching == null, != null, ?: return)
RE_ARG_PLAYER = re.compile(r'((?:ClientAccess\.collectiveMinecraft|mc)\.player)(?![!?a-zA-Z0-9_]|==|!=|\?:)(?=[,)\s])')
RE_ARG_LEVEL = re.compile(r'((?:ClientAccess\.collectiveMinecraft|mc)\.level)(?![!?a-zA-Z0-9_]|==|!=|\?:)(?=[,)\s])')
RE_ARG_GAMEMODE = re.compile(r'((?:ClientAccess\.collectiveMinecraft|mc)\.gameMode)(?![!?a-zA-Z0-9_]|==|!=|\?:)(?=[,)\s])')

# Non-null assignments:
RE_ASSIGN_PLAYER = re.compile(
    r'(:\s*(?:LocalPlayer|Player)\s*=\s*(?:ClientAccess\.collectiveMinecraft|mc)\.player)(?![!?a-zA-Z0-9_])'
)
RE_ASSIGN_LEVEL = re.compile(
    r'(:\s*(?:ClientLevel|Level)\s*=\s*(?:ClientAccess\.collectiveMinecraft|mc)\.level)(?![!?a-zA-Z0-9_])'
)
RE_ASSIGN_GAMEMODE = re.compile(
    r'(:\s*(?:MultiPlayerGameMode)\s*=\s*(?:ClientAccess\.collectiveMinecraft|mc)\.gameMode)(?![!?a-zA-Z0-9_])'
)
RE_ASSIGN_HITRESULT = re.compile(
    r'(:\s*(?:HitResult)\s*=\s*(?:ClientAccess\.collectiveMinecraft|mc)\.hitResult)(?![!?a-zA-Z0-9_])'
)

def fix_content(content: str) -> tuple[str, list[str]]:
    changes = []
    
    # 1. Reserved keywords backticking (.`is`(...), .`in`(...))
    if RE_DOT_IS.search(content):
        content, n = RE_DOT_IS.subn('.`is`(', content)
        changes.append(f"Fixed {n} .is(...) -> .`is`(...)")
        
    if RE_DOT_IN.search(content):
        content, n = RE_DOT_IN.subn('.`in`(', content)
        changes.append(f"Fixed {n} .in(...) -> .`in`(...)")

    # 2. Mixin methods with dollar sign (`collective$xxx`)
    if RE_COLLECTIVE_DOLLAR.search(content):
        content, n = RE_COLLECTIVE_DOLLAR.subn(r'`\1`', content)
        changes.append(f"Backticked {n} mixin methods `collective$...`")

    # 3. String.formatted(...) -> String.format(str, ...)
    if RE_STRING_LITERAL_FORMATTED.search(content):
        content, n = RE_STRING_LITERAL_FORMATTED.subn(r'String.format(\1, \2)', content)
        changes.append(f"Converted {n} string literal .formatted(...) -> String.format(...)")

    if RE_STRING_VAR_FORMATTED.search(content):
        content, n = RE_STRING_VAR_FORMATTED.subn(r'String.format(\1, \2)', content)
        changes.append(f"Converted {n} variable .formatted(...) -> String.format(...)")

    # 4. Deprecated toLowerCase / toUpperCase / sort
    if RE_TO_LOWER_CASE.search(content):
        content, n = RE_TO_LOWER_CASE.subn('.lowercase()', content)
        changes.append(f"Replaced {n} .toLowerCase() -> .lowercase()")
        
    if RE_TO_UPPER_CASE.search(content):
        content, n = RE_TO_UPPER_CASE.subn('.uppercase()', content)
        changes.append(f"Replaced {n} .toUpperCase() -> .uppercase()")

    if RE_SORT_WITH.search(content):
        content, n = RE_SORT_WITH.subn(r'.sortWith(\1)', content)
        changes.append(f"Replaced {n} .sort(comparator) -> .sortWith(...)")

    # 5. Java >>> operator -> Kotlin ushr
    if RE_USHR.search(content):
        content, n = RE_USHR.subn(' ushr ', content)
        changes.append(f"Replaced {n} '>>>' -> 'ushr'")

    # 6. Hanging binary operators at line start
    if RE_HANGING_BINARY_OP.search(content):
        content, n = RE_HANGING_BINARY_OP.subn(r'\1 \2\n         ', content)
        changes.append(f"Joined {n} hanging binary operators to previous line")

    # 6b. String("literal") constructor replacement
    if RE_STRING_CONSTRUCTOR.search(content):
        content, n = RE_STRING_CONSTRUCTOR.subn(r'\1', content)
        changes.append(f"Replaced {n} String(\"...\") -> \"...\"")

    # 7. Function invocation parens restoration
    if RE_ENTITIES_FOR_RENDERING.search(content):
        content, n = RE_ENTITIES_FOR_RENDERING.subn('entitiesForRendering()', content)
        changes.append(f"Added parens to {n} entitiesForRendering()")

    if RE_IMMUTABLE.search(content):
        content, n = RE_IMMUTABLE.subn('immutable()', content)
        changes.append(f"Added parens to {n} immutable()")

    if RE_LEVEL_PLAYERS.search(content):
        content, n = RE_LEVEL_PLAYERS.subn('.players().', content)
        changes.append(f"Added parens to {n} level.players().")

    if RE_ENTITY_LEVEL.search(content):
        content, n = RE_ENTITY_LEVEL.subn(r'\1.level()', content)
        changes.append(f"Added parens to {n} entity.level()")

    # 8. AABB ySize -> ysize
    if RE_YSIZE.search(content):
        content, n = RE_YSIZE.subn('ysize', content)
        changes.append(f"Replaced {n} ySize -> ysize")

    # 9. Nullable dereferencing for Minecraft singletons
    if RE_PLAYER_DEREF.search(content):
        content, n = RE_PLAYER_DEREF.subn(r'\1!!.', content)
        changes.append(f"Added non-null assertion to {n} player dereferences")

    if RE_LEVEL_DEREF.search(content):
        content, n = RE_LEVEL_DEREF.subn(r'\1!!.', content)
        changes.append(f"Added non-null assertion to {n} level dereferences")

    if RE_GAMEMODE_DEREF.search(content):
        content, n = RE_GAMEMODE_DEREF.subn(r'\1!!.', content)
        changes.append(f"Added non-null assertion to {n} gameMode dereferences")

    if RE_HITRESULT_DEREF.search(content):
        content, n = RE_HITRESULT_DEREF.subn(r'\1!!.', content)
        changes.append(f"Added non-null assertion to {n} hitResult dereferences")

    if RE_CAMERA_DEREF.search(content):
        content, n = RE_CAMERA_DEREF.subn(r'\1!!.', content)
        changes.append(f"Added non-null assertion to {n} camera dereferences")

    # 9b. Nullable arguments passed to functions: foo(mc.player) -> foo(mc.player!!)
    if RE_ARG_PLAYER.search(content):
        content, n = RE_ARG_PLAYER.subn(r'\1!!', content)
        changes.append(f"Added non-null assertion to {n} player argument expressions")

    if RE_ARG_LEVEL.search(content):
        content, n = RE_ARG_LEVEL.subn(r'\1!!', content)
        changes.append(f"Added non-null assertion to {n} level argument expressions")

    if RE_ARG_GAMEMODE.search(content):
        content, n = RE_ARG_GAMEMODE.subn(r'\1!!', content)
        changes.append(f"Added non-null assertion to {n} gameMode argument expressions")

    # 10. Non-null variable assignments
    if RE_ASSIGN_PLAYER.search(content):
        content, n = RE_ASSIGN_PLAYER.subn(r'\1!!', content)
        changes.append(f"Added non-null assertion to {n} LocalPlayer assignments")

    if RE_ASSIGN_LEVEL.search(content):
        content, n = RE_ASSIGN_LEVEL.subn(r'\1!!', content)
        changes.append(f"Added non-null assertion to {n} ClientLevel assignments")

    if RE_ASSIGN_GAMEMODE.search(content):
        content, n = RE_ASSIGN_GAMEMODE.subn(r'\1!!', content)
        changes.append(f"Added non-null assertion to {n} MultiPlayerGameMode assignments")

    if RE_ASSIGN_HITRESULT.search(content):
        content, n = RE_ASSIGN_HITRESULT.subn(r'\1!!', content)
        changes.append(f"Added non-null assertion to {n} HitResult assignments")

    return content, changes

def process_file(file_path: Path, dry_run: bool = False) -> bool:
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            original = f.read()
    except Exception as e:
        print(f"Error reading {file_path}: {e}")
        return False

    fixed, changes = fix_content(original)

    if fixed != original:
        rel_path = file_path.relative_to(Path.cwd()) if file_path.is_relative_to(Path.cwd()) else file_path
        print(f"[{'DRY-RUN' if dry_run else 'APPLIED'}] {rel_path}:")
        for ch in changes:
            print(f"  - {ch}")
        if not dry_run:
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(fixed)
        return True
    return False

def main():
    parser = argparse.ArgumentParser(description="Batch fix Kotlin compiler errors.")
    parser.add_argument("paths", nargs="*", default=["src/main/kotlin"], help="Directories or files to process")
    parser.add_argument("--dry-run", action="store_true", help="Print changes without modifying files")
    args = parser.parse_args()

    total_files = 0
    modified_files = 0

    for path_str in args.paths:
        path = Path(path_str)
        if path.is_file() and path.suffix == ".kt":
            total_files += 1
            if process_file(path, dry_run=args.dry_run):
                modified_files += 1
        elif path.is_dir():
            for root, _, files in os.walk(path):
                for f in files:
                    if f.endswith(".kt"):
                        total_files += 1
                        file_path = Path(root) / f
                        if process_file(file_path, dry_run=args.dry_run):
                            modified_files += 1

    print(f"\nDone. Processed {total_files} files, modified {modified_files} files.")

if __name__ == "__main__":
    main()
