#!/usr/bin/env python3
"""
Master Kotlin Migration & Autonomous Auto-Fix Pipeline for Collective Client.
Provides:
1. Advanced Java -> Kotlin transpilation engine
2. Autonomous iterative compiler error parser & fixer
3. Safe atomic staging with automatic rollback on build failure
4. Batch and full-pipeline orchestration
"""

import os
import re
import sys
import shutil
import subprocess
import threading
from pathlib import Path
from typing import List, Tuple, Dict, Optional, Callable, Any

PROJECT_ROOT = Path(__file__).resolve().parent.parent
JAVA_SRC = PROJECT_ROOT / "src" / "main" / "java" / "me" / "collective" / "client"
KOTLIN_SRC = PROJECT_ROOT / "src" / "main" / "kotlin" / "me" / "collective" / "client"

# Root files and core packages strictly excluded per MIGRATION.md
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

# ==============================================================================
# 1. ADVANCED POST-CONVERSION ERROR FIXER RULES
# ==============================================================================

RE_DOT_IS = re.compile(r'(?<=[a-zA-Z0-9_\)\]!])\.is\(')
RE_DOT_IN = re.compile(r'(?<=[a-zA-Z0-9_\)\]!])\.in\(')
RE_COLLECTIVE_DOLLAR = re.compile(r'(?<!`)(collective\$[a-zA-Z0-9_]+)(?!`)')

RE_STRING_LITERAL_FORMATTED = re.compile(r'("(?:\\.|[^"\\])*")\.formatted\((.*?)\)')
RE_STRING_VAR_FORMATTED = re.compile(r'([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)*)\.formatted\((.*?)\)')
RE_STRING_CONSTRUCTOR = re.compile(r'\bString\(("(?:\\.|[^"\\])*")\)')

RE_TO_LOWER_CASE = re.compile(r'\.toLowerCase\(\s*(?:Locale\.[A-Z_]+|\s*)\)')
RE_TO_UPPER_CASE = re.compile(r'\.toUpperCase\(\s*(?:Locale\.[A-Z_]+|\s*)\)')
RE_SORT_WITH = re.compile(r'\.sort\(([^)]*(?:comparator|Comparator|comparing)[^)]*)\)', re.IGNORECASE)

RE_USHR = re.compile(r'(?<=[a-zA-Z0-9_\)\]])\s*>>>\s*(?=\d|\b[a-z_][a-zA-Z0-9_]*\b|\()')
RE_HANGING_BINARY_OP = re.compile(r'([a-zA-Z0-9_\)\]"\'])\s*\n\s*([+*/%]|==|!=|===|!==|&&|\|\|)\s+')

RE_ENTITIES_FOR_RENDERING = re.compile(r'(?<=\.)entitiesForRendering\b(?!\()')
RE_IMMUTABLE = re.compile(r'(?<=\.)immutable\b(?!\()')
RE_LEVEL_PLAYERS = re.compile(r'(?<=\blevel)\.players\.(?=iterator\(\)|stream\(\)|forEach|size|isEmpty)')
RE_ENTITY_LEVEL = re.compile(r'\b(target|entity|other|passengerPlayer|playerModel)\.level\b(?!\()')
RE_YSIZE = re.compile(r'(?<=\.)ySize\b')

RE_PLAYER_DEREF = re.compile(r'((?:ClientAccess\.collectiveMinecraft|mc)\.player)(?![!?a-zA-Z0-9_])\.')
RE_LEVEL_DEREF = re.compile(r'((?:ClientAccess\.collectiveMinecraft|mc)\.level)(?![!?a-zA-Z0-9_])\.')
RE_GAMEMODE_DEREF = re.compile(r'((?:ClientAccess\.collectiveMinecraft|mc)\.gameMode)(?![!?a-zA-Z0-9_])\.')
RE_HITRESULT_DEREF = re.compile(r'((?:ClientAccess\.collectiveMinecraft|mc)\.hitResult)(?![!?a-zA-Z0-9_])\.')
RE_CAMERA_DEREF = re.compile(r'(getEntityRenderDispatcher\(\)\.camera)(?![!?a-zA-Z0-9_])\.')

RE_ARG_PLAYER = re.compile(r'((?:ClientAccess\.collectiveMinecraft|mc)\.player)(?![!?a-zA-Z0-9_]|==|!=|\?:)(?=[,)\s])')
RE_ARG_LEVEL = re.compile(r'((?:ClientAccess\.collectiveMinecraft|mc)\.level)(?![!?a-zA-Z0-9_]|==|!=|\?:)(?=[,)\s])')
RE_ARG_GAMEMODE = re.compile(r'((?:ClientAccess\.collectiveMinecraft|mc)\.gameMode)(?![!?a-zA-Z0-9_]|==|!=|\?:)(?=[,)\s])')

RE_ASSIGN_PLAYER = re.compile(r'(:\s*(?:LocalPlayer|Player)\s*=\s*(?:ClientAccess\.collectiveMinecraft|mc)\.player)(?![!?a-zA-Z0-9_])')
RE_ASSIGN_LEVEL = re.compile(r'(:\s*(?:ClientLevel|Level)\s*=\s*(?:ClientAccess\.collectiveMinecraft|mc)\.level)(?![!?a-zA-Z0-9_])')
RE_ASSIGN_GAMEMODE = re.compile(r'(:\s*(?:MultiPlayerGameMode)\s*=\s*(?:ClientAccess\.collectiveMinecraft|mc)\.gameMode)(?![!?a-zA-Z0-9_])')
RE_ASSIGN_HITRESULT = re.compile(r'(:\s*(?:HitResult)\s*=\s*(?:ClientAccess\.collectiveMinecraft|mc)\.hitResult)(?![!?a-zA-Z0-9_])')

def fix_kotlin_content(content: str) -> Tuple[str, List[str]]:
    changes = []

    # 1. Reserved keywords
    if RE_DOT_IS.search(content):
        content, n = RE_DOT_IS.subn('.`is`(', content)
        changes.append(f"Fixed {n} .is(...) -> .`is`(...)")

    if RE_DOT_IN.search(content):
        content, n = RE_DOT_IN.subn('.`in`(', content)
        changes.append(f"Fixed {n} .in(...) -> .`in`(...)")

    # 2. Mixin methods
    if RE_COLLECTIVE_DOLLAR.search(content):
        content, n = RE_COLLECTIVE_DOLLAR.subn(r'`\1`', content)
        changes.append(f"Backticked {n} mixin methods `collective$...`")

    # 3. String formatting
    if RE_STRING_LITERAL_FORMATTED.search(content):
        content, n = RE_STRING_LITERAL_FORMATTED.subn(r'String.format(\1, \2)', content)
        changes.append(f"Converted {n} string literal .formatted(...) -> String.format(...)")

    if RE_STRING_VAR_FORMATTED.search(content):
        content, n = RE_STRING_VAR_FORMATTED.subn(r'String.format(\1, \2)', content)
        changes.append(f"Converted {n} variable .formatted(...) -> String.format(...)")

    if RE_STRING_CONSTRUCTOR.search(content):
        content, n = RE_STRING_CONSTRUCTOR.subn(r'\1', content)
        changes.append(f"Replaced {n} String(\"...\") -> \"...\"")

    # 4. Deprecated stdlib methods
    if RE_TO_LOWER_CASE.search(content):
        content, n = RE_TO_LOWER_CASE.subn('.lowercase()', content)
        changes.append(f"Replaced {n} .toLowerCase() -> .lowercase()")

    if RE_TO_UPPER_CASE.search(content):
        content, n = RE_TO_UPPER_CASE.subn('.uppercase()', content)
        changes.append(f"Replaced {n} .toUpperCase() -> .uppercase()")

    if RE_SORT_WITH.search(content):
        content, n = RE_SORT_WITH.subn(r'.sortWith(\1)', content)
        changes.append(f"Replaced {n} .sort(comparator) -> .sortWith(...)")

    # 5. Operators
    if RE_USHR.search(content):
        content, n = RE_USHR.subn(' ushr ', content)
        changes.append(f"Replaced {n} >>> with ushr")

    if RE_HANGING_BINARY_OP.search(content):
        content, n = RE_HANGING_BINARY_OP.subn(r'\1 \2\n', content)
        changes.append(f"Moved {n} hanging binary operators to end-of-line")

    # 6. Minecraft getters/properties
    if RE_ENTITIES_FOR_RENDERING.search(content):
        content, n = RE_ENTITIES_FOR_RENDERING.subn('entitiesForRendering()', content)
        changes.append(f"Replaced {n} .entitiesForRendering -> .entitiesForRendering()")

    if RE_IMMUTABLE.search(content):
        content, n = RE_IMMUTABLE.subn('immutable()', content)
        changes.append(f"Replaced {n} .immutable -> .immutable()")

    if RE_LEVEL_PLAYERS.search(content):
        content, n = RE_LEVEL_PLAYERS.subn('players().', content)
        changes.append(f"Replaced {n} level.players. -> level.players().")

    if RE_ENTITY_LEVEL.search(content):
        content, n = RE_ENTITY_LEVEL.subn(r'\1.level()', content)
        changes.append(f"Replaced {n} entity.level -> entity.level()")

    if RE_YSIZE.search(content):
        content, n = RE_YSIZE.subn('y', content)
        changes.append(f"Replaced {n} Vec3i.ySize -> Vec3i.y")

    # 7. Null-safety on common nullable Minecraft fields
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

    if RE_ARG_PLAYER.search(content):
        content, n = RE_ARG_PLAYER.subn(r'\1!!', content)
        changes.append(f"Added non-null assertion to {n} player argument expressions")

    if RE_ARG_LEVEL.search(content):
        content, n = RE_ARG_LEVEL.subn(r'\1!!', content)
        changes.append(f"Added non-null assertion to {n} level argument expressions")

    if RE_ARG_GAMEMODE.search(content):
        content, n = RE_ARG_GAMEMODE.subn(r'\1!!', content)
        changes.append(f"Added non-null assertion to {n} gameMode argument expressions")

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


# ==============================================================================
# 2. JAVA TO KOTLIN SYNTAX TRANSPILER
# ==============================================================================

TYPE_MAP = {
    'int': 'Int',
    'long': 'Long',
    'boolean': 'Boolean',
    'float': 'Float',
    'double': 'Double',
    'byte': 'Byte',
    'short': 'Short',
    'char': 'Char',
    'void': 'Unit',
    'Object': 'Any',
}

RE_TYPE_CAST = re.compile(r'\(([A-Z][a-zA-Z0-9_<>\?,\s]*)\)\s*([a-zA-Z0-9_\)\]"\']+)')
RE_INSTANCEOF_VAR = re.compile(r'([a-zA-Z0-9_\)\]"\']+)\s+instanceof\s+([A-Z][a-zA-Z0-9_<>\?,\s]*)\s+([a-zA-Z0-9_]+)')
RE_INSTANCEOF = re.compile(r'([a-zA-Z0-9_\)\]"\']+)\s+instanceof\s+([A-Z][a-zA-Z0-9_<>\?,\s]*)')
RE_NEW_CLASS = re.compile(r'\bnew\s+([A-Z][a-zA-Z0-9_]*(?:<[^>]*>)?)\s*\(')
RE_NEW_ARRAY_EMPTY = re.compile(r'\bnew\s+[A-Za-z0-9_]+\[0\]')
RE_NEW_ARRAY = re.compile(r'\bnew\s+([A-Za-z0-9_]+)\[(.*?)\]')
RE_CLASS_LITERAL = re.compile(r'([A-Z][a-zA-Z0-9_]*)\.class\b')
RE_LOCAL_VAR = re.compile(r'^(\s*)(?:final\s+)?([A-Z][a-zA-Z0-9_<>,?]*)\s+([a-zA-Z0-9_]+)\s*=\s*(.+)$')

def convert_param_list(params_str: str) -> str:
    if not params_str.strip():
        return ""
    parts = [p.strip() for p in params_str.split(',') if p.strip()]
    converted = []
    for p in parts:
        p = re.sub(r'\bfinal\s+', '', p).strip()
        tokens = p.split()
        if len(tokens) >= 2:
            param_type = " ".join(tokens[:-1])
            param_name = tokens[-1]
            converted.append(f"{param_name}: {param_type}")
        else:
            converted.append(p)
    return ", ".join(converted)

def transpile_java_to_kotlin(java_code: str) -> str:
    # 0. Global replacements for lambdas and common getters
    java_code = re.sub(r'\(\s*\)\s*->\s*\{', '{ ', java_code)
    java_code = re.sub(r'\(\s*([a-zA-Z0-9_,\s]+)\s*\)\s*->\s*\{', r'{ \1 -> ', java_code)
    java_code = re.sub(r'\b([a-zA-Z_][a-zA-Z0-9_]*)\s*->\s*\{', r'{ \1 -> ', java_code)
    java_code = re.sub(r'\(\s*\)\s*->\s*([^{};\n]+)', r'{ \1 }', java_code)
    java_code = re.sub(r'\(\s*([a-zA-Z0-9_,\s]+)\s*\)\s*->\s*([^{};\n]+)', r'{ \1 -> \2 }', java_code)
    java_code = re.sub(r'\.getValue\(\)', '.value', java_code)
    java_code = re.sub(r'\.getPacket\(\)', '.packet', java_code)
    java_code = re.sub(r'throw new MatchException\(.*?\);?', '', java_code)

    lines = java_code.splitlines()
    kt_lines = []
    
    in_block_comment = False
    class_name = None
    
    # Detect class name
    m_cls = re.search(r'\b(?:public\s+)?(?:final\s+|abstract\s+)?class\s+([a-zA-Z0-9_]+)', java_code)
    if m_cls:
        class_name = m_cls.group(1)

    for line in lines:
        stripped = line.strip()
        
        if '/*' in stripped and '*/' not in stripped:
            in_block_comment = True
            kt_lines.append(line)
            continue
        if '*/' in stripped:
            in_block_comment = False
            kt_lines.append(line)
            continue
        if in_block_comment or stripped.startswith('//'):
            kt_lines.append(line)
            continue
            
        l = line
        
        # Override annotation
        l = re.sub(r'@Override\s*', '', l)
        
        # Primitive type mapping
        for j_type, k_type in TYPE_MAP.items():
            l = re.sub(rf'\b{j_type}\b', k_type, l)
            
        # instanceof with pattern variable: if (expr instanceof Type var)
        m_inst = re.search(r'if\s*\(\s*(.+?)\s+instanceof\s+([a-zA-Z0-9_<>]+)\s+([a-zA-Z0-9_]+)\s*\)', l)
        if m_inst:
            expr, target_type, var_name = m_inst.groups()
            indent = re.match(r'^\s*', l).group(0)
            kt_lines.append(f'{indent}val {var_name} = {expr}')
            l = f'{indent}if ({var_name} is {target_type})'

        # instanceof -> is
        l = RE_INSTANCEOF.sub(r'\1 is \2', l)
        
        # Class.class -> Class::class.java
        l = RE_CLASS_LITERAL.sub(r'\1::class.java', l)
        
        # new Type[0] -> emptyArray()
        l = RE_NEW_ARRAY_EMPTY.sub(r'emptyArray()', l)
        
        # new Type[n] -> arrayOfNulls<Type>(n)
        l = RE_NEW_ARRAY.sub(r'arrayOfNulls<\1>(\2)', l)
        
        # Remove diamond operator on new: new Foo<>() -> Foo()
        l = re.sub(r'\bnew\s+([A-Za-z0-9_]+)<>\s*\(', r'\1(', l)

        # Generics wildcards: <?> -> <*>
        l = re.sub(r'<\?>', '<*>', l)

        # new Class(...) -> Class(...)
        l = RE_NEW_CLASS.sub(r'\1(', l)
        
        # (Type) obj -> (obj as Type)
        l = RE_TYPE_CAST.sub(r'(\2 as \1)', l)
        
        # Static fields -> @JvmField val / var
        if re.search(r'\bstatic\b', l) and not ('class ' in l or 'interface ' in l):
            l = re.sub(r'\bstatic\s+', '', l)
            if re.search(r'\bfinal\b', l):
                l = re.sub(r'\b(?:public|protected|private)?\s*final\s+([A-Za-z0-9_<>,?\s]+)\s+([a-zA-Z0-9_]+)\s*=\s*(.+);?',
                           r'@JvmField\n    val \2: \1 = \3', l)
            else:
                l = re.sub(r'\b(?:public|protected|private)?\s*([A-Za-z0-9_<>,?\s]+)\s+([a-zA-Z0-9_]+)\s*=\s*(.+);?',
                           r'@JvmField\n    var \2: \1 = \3', l)
        
        # Field declarations (uninitialized -> initialize with defaults)
        m_fld = re.search(r'\b(?:public|protected|private)\s+(?:final\s+)?([A-Za-z0-9_<>,?\*\s]+)\s+([a-zA-Z0-9_]+)\s*;', l)
        if m_fld and not any(kw in l for kw in ['return', 'class', 'fun', 'import', 'package', 'interface', 'new']):
            ftype = m_fld.group(1).strip()
            fname = m_fld.group(2).strip()
            indent = re.match(r'^\s*', l).group(0)
            is_final = 'final' in l
            
            # Primitives default values
            if ftype == 'Boolean':
                l = f'{indent}var {fname}: Boolean = false'
            elif ftype in ['Int', 'Byte', 'Short']:
                l = f'{indent}var {fname}: {ftype} = 0'
            elif ftype == 'Long':
                l = f'{indent}var {fname}: Long = 0L'
            elif ftype == 'Float':
                l = f'{indent}var {fname}: Float = 0.0f'
            elif ftype == 'Double':
                l = f'{indent}var {fname}: Double = 0.0'
            elif is_final and not any(p in ftype for p in ['Int', 'Float', 'Double', 'Boolean', 'Long', 'Byte', 'Short']):
                l = f'{indent}lateinit var {fname}: {ftype}'
            else:
                l = f'{indent}var {fname}: {ftype}? = null'
        
        # Constructors: public Foo(...) -> constructor(...)
        if class_name:
            m_ctor = re.search(rf'\b(?:public|protected|private)?\s*{class_name}\s*\((.*?)\)\s*\{{', l)
            if m_ctor:
                params = convert_param_list(m_ctor.group(1))
                l = re.sub(re.escape(m_ctor.group(0)), f"constructor({params}) {{", l)

        # super(...) call inside constructor body -> if at start, can be changed to init or delegated
        if re.search(r'^\s*super\s*\(', l):
            l = re.sub(r'super\s*\((.*?)\)\s*;?', r'// super(\1)', l)
        
        # Method declaration: public Type name(params) -> fun name(params): Type
        m_method = re.search(r'\b(?:public|protected|private|final|\s)*\b([A-Za-z0-9_<>,?\s]+)\s+([a-zA-Z0-9_]+)\s*\((.*?)\)\s*\{', l)
        if m_method and not m_method.group(2) in ['if', 'while', 'for', 'switch', 'catch', 'when', 'constructor']:
            ret_type = m_method.group(1).strip()
            name = m_method.group(2).strip()
            params = convert_param_list(m_method.group(3))
            ret_part = f": {ret_type}" if ret_type not in ['Unit', 'void'] else ""
            l = re.sub(re.escape(m_method.group(0)), f"fun {name}({params}){ret_part} {{", l)
        
        # For each loop: for (Type x : list) -> for (x in list)
        l = re.sub(r'\bfor\s*\(\s*(?:final\s+)?[A-Za-z0-9_<>,?\s]+\s+([a-zA-Z0-9_]+)\s*:\s*(.+?)\)',
                   r'for (\1 in \2)', l)

        # Catch block: catch (Exception e) -> catch (e: Exception)
        l = re.sub(r'\bcatch\s*\(\s*([A-Za-z0-9_]+)\s+([a-zA-Z0-9_]+)\s*\)',
                   r'catch (\2: \1)', l)

        # Local variable declarations
        m_var = RE_LOCAL_VAR.match(l)
        if m_var and not any(kw in m_var.group(2) for kw in ['return', 'case', 'throw', 'fun', 'val', 'var']):
            indent, v_type, v_name, v_expr = m_var.groups()
            v_expr = v_expr.rstrip(';')
            l = f"{indent}val {v_name}: {v_type} = {v_expr}"

        # Strip semicolons
        if l.rstrip().endswith(';'):
            l = l.rstrip()[:-1]
            
        kt_lines.append(l)
        
    code = '\n'.join(kt_lines)
    
    # Enum declarations
    code = re.sub(r'\b(?:public\s+)?enum\s+([a-zA-Z0-9_]+)\s+implements\s+([a-zA-Z0-9_<>,?\s]+)\s*\{',
                  r'enum class \1 : \2 {', code)
    code = re.sub(r'\b(?:public\s+)?enum\s+([a-zA-Z0-9_]+)\s*\{',
                  r'enum class \1 {', code)

    # Class declarations: inheritance & interfaces
    code = re.sub(r'\bpublic\s+(class|interface)\b', r'\1', code)
    code = re.sub(r'\bclass\s+([a-zA-Z0-9_]+)\s+extends\s+([a-zA-Z0-9_<>,?\s]+)\s+implements\s+([a-zA-Z0-9_<>,?\s]+)\s*\{',
                  r'open class \1 : \2(), \3 {', code)
    code = re.sub(r'\bclass\s+([a-zA-Z0-9_]+)\s+extends\s+([a-zA-Z0-9_<>,?\s]+)\s*\{',
                  r'open class \1 : \2() {', code)
    code = re.sub(r'\bclass\s+([a-zA-Z0-9_]+)\s+implements\s+([a-zA-Z0-9_<>,?\s]+)\s*\{',
                  r'class \1 : \2 {', code)
    code = re.sub(r'\binterface\s+([a-zA-Z0-9_]+)\s+extends\s+([a-zA-Z0-9_<>,?\s]+)\s*\{',
                  r'interface \1 : \2 {', code)

    return code


# ==============================================================================
# 3. COMPILER VERIFICATION & DIAGNOSTICS PARSER
# ==============================================================================

class CompilerError:
    def __init__(self, file_path: str, line: int, col: int, message: str):
        self.file_path = file_path
        self.line = line
        self.col = col
        self.message = message

    def __repr__(self):
        return f"CompilerError({Path(self.file_path).name}:{self.line}:{self.col}: {self.message})"

def run_gradle_compile(timeout_sec: int = 60, log_callback: Optional[Callable[[str], None]] = None) -> Tuple[bool, str, List[CompilerError]]:
    """
    Executes ./gradlew compileKotlin compileJava and captures errors and stdout.
    """
    cmd = ["./gradlew", "compileKotlin", "compileJava", "--console=plain"]
    if log_callback:
        log_callback(f"[BUILD] Executing: {' '.join(cmd)}")
        
    try:
        proc = subprocess.Popen(
            cmd,
            cwd=str(PROJECT_ROOT),
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1
        )
        
        output_lines = []
        for line in proc.stdout:
            output_lines.append(line)
            if log_callback:
                log_callback(line.rstrip('\n'))
                
        proc.wait(timeout=timeout_sec)
        full_output = "".join(output_lines)
        success = (proc.returncode == 0)
    except subprocess.TimeoutExpired:
        proc.kill()
        if log_callback:
            log_callback("[BUILD] Timed out!")
        return False, "Build process timed out", []
    except Exception as e:
        if log_callback:
            log_callback(f"[BUILD ERROR] {e}")
        return False, str(e), []

    # Parse errors
    errors: List[CompilerError] = []
    # Pattern: e: file:///path/to/File.kt:123:45 Message
    re_err = re.compile(r'e:\s+(?:file://)?([^:]+):(\d+):(\d+)\s+(.+)')
    for line in full_output.splitlines():
        m = re_err.match(line)
        if m:
            f_path, l_num, c_num, msg = m.groups()
            errors.append(CompilerError(f_path, int(l_num), int(c_num), msg))

    return success, full_output, errors


# ==============================================================================
# 4. AUTONOMOUS AUTO-FIX APPLIER
# ==============================================================================

def apply_auto_repair(error: CompilerError, log_callback: Optional[Callable[[str], None]] = None) -> bool:
    """
    Analyzes a compiler error and modifies the target file to fix it.
    """
    path = Path(error.file_path)
    if not path.exists():
        return False

    try:
        with open(path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
    except Exception:
        return False

    line_idx = error.line - 1
    if line_idx < 0 or line_idx >= len(lines):
        return False

    target_line = lines[line_idx]
    modified = False

    # 1. Property must be initialized or be abstract
    if "Property must be initialized or be abstract" in error.message:
        # e.g.: var controllingTeleport: Boolean -> var controllingTeleport: Boolean = false
        # e.g.: var ticks: Int -> var ticks: Int = 0
        # e.g.: var pos: BlockPos -> var pos: BlockPos? = null
        # e.g.: val foo: Type -> lateinit var foo: Type
        m_var = re.search(r'^\s*(?:private|protected|public)?\s*(var|val)\s+([a-zA-Z0-9_]+)\s*:\s*([A-Za-z0-9_<>,?\*\s]+)$', target_line.strip())
        if m_var:
            kind, v_name, v_type = m_var.groups()
            v_type_clean = v_type.strip()
            indent = re.match(r'^\s*', target_line).group(0)
            
            if v_type_clean == 'Boolean':
                lines[line_idx] = f"{indent}var {v_name}: Boolean = false\n"
            elif v_type_clean in ['Int', 'Byte', 'Short']:
                lines[line_idx] = f"{indent}var {v_name}: {v_type_clean} = 0\n"
            elif v_type_clean == 'Long':
                lines[line_idx] = f"{indent}var {v_name}: Long = 0L\n"
            elif v_type_clean == 'Float':
                lines[line_idx] = f"{indent}var {v_name}: Float = 0.0f\n"
            elif v_type_clean == 'Double':
                lines[line_idx] = f"{indent}var {v_name}: Double = 0.0\n"
            elif kind == 'val' and not any(p in v_type_clean for p in ['Int', 'Float', 'Double', 'Boolean', 'Long', 'Byte', 'Short']):
                lines[line_idx] = f"{indent}lateinit var {v_name}: {v_type_clean}\n"
            else:
                lines[line_idx] = f"{indent}var {v_name}: {v_type_clean}? = null\n"
            modified = True
            if log_callback:
                log_callback(f"[FIX] Initialized unassigned property `{v_name}` at {path.name}:{error.line}")

    # 2. EventStage enum name mismatch (PRE -> Pre, POST -> Post)
    elif "Unresolved reference 'PRE'" in error.message:
        lines[line_idx] = target_line.replace("EventStage.PRE", "EventStage.Pre")
        modified = True
        if log_callback:
            log_callback(f"[FIX] Replaced EventStage.PRE with EventStage.Pre at {path.name}:{error.line}")

    elif "Unresolved reference 'POST'" in error.message:
        lines[line_idx] = target_line.replace("EventStage.POST", "EventStage.Post")
        modified = True
        if log_callback:
            log_callback(f"[FIX] Replaced EventStage.POST with EventStage.Post at {path.name}:{error.line}")

    # 3. Receiver type mismatch on CharSequence.append
    elif "is inapplicable because of a receiver type mismatch" in error.message and "append" in target_line:
        m = re.search(r'([a-zA-Z0-9_]+)\.append\((.*?)\)', target_line)
        if m:
            recv, arg = m.group(1), m.group(2)
            new_call = f"Component.empty().append({recv}).append(Component.literal({arg}))"
            lines[line_idx] = target_line.replace(m.group(0), new_call)
            modified = True
            if log_callback:
                log_callback(f"[FIX] Fixed Component receiver mismatch at {path.name}:{error.line}")

    # 4. Spread operator for Varargs/Arrays
    elif "Argument type mismatch" in error.message and "Array<" in error.message:
        m = re.search(r'([A-Za-z0-9_]+)\(([a-zA-Z0-9_]+)\)', target_line)
        if m:
            callee, arg = m.group(1), m.group(2)
            lines[line_idx] = target_line.replace(f"{callee}({arg})", f"{callee}(*{arg})")
            modified = True
            if log_callback:
                log_callback(f"[FIX] Added spread operator `*` at {path.name}:{error.line}")

    # 5. Conflicting JVM declarations (getters / setters)
    elif "Platform declaration clash" in error.message or "Inherited platform declarations clash" in error.message or "CONFLICTING_JVM_DECLARATIONS" in error.message:
        if "fun get" in target_line:
            # Check if there's a getter that clashes with a val
            lines[line_idx] = f"// Redundant getter: {target_line}"
            modified = True
            if log_callback:
                log_callback(f"[FIX] Commented out conflicting getter at {path.name}:{error.line}")
        elif "var " in target_line and "@JvmField" not in target_line:
            lines[line_idx] = lines[line_idx].replace("var ", "@JvmField\n   var ")
            modified = True
            if log_callback:
                log_callback(f"[FIX] Added @JvmField to resolve declaration clash at {path.name}:{error.line}")

    # 6. Type mismatch Double -> Float
    elif "The floating-point literal does not conform to the expected type Float" in error.message or ("Type mismatch" in error.message and "Float" in error.message and "Double" in error.message):
        # e.g.: 0.0 -> 0.0f
        m_num = re.search(r'(\b\d+\.\d+)\b(?!f|F)', target_line)
        if m_num:
            lines[line_idx] = target_line.replace(m_num.group(1), f"{m_num.group(1)}f")
            modified = True
            if log_callback:
                log_callback(f"[FIX] Converted Double literal to Float at {path.name}:{error.line}")
        elif ".toDouble()" in target_line:
            lines[line_idx] = target_line.replace(".toDouble()", ".toFloat()")
            modified = True
            if log_callback:
                log_callback(f"[FIX] Replaced .toDouble() with .toFloat() at {path.name}:{error.line}")

    # 7. Nullability check: operator '?' or '!!' needed
    elif "Type mismatch: inferred type is ...? but ... was expected" in error.message:
        m = re.search(r'([a-zA-Z0-9_]+)(?=[,)\s])', target_line)
        if m:
            lines[line_idx] = target_line.replace(m.group(1), f"{m.group(1)}!!")
            modified = True
            if log_callback:
                log_callback(f"[FIX] Added non-null assertion `!!` at {path.name}:{error.line}")

    # 8. Unnecessary safe call
    elif "Unnecessary safe call on a non-null receiver" in error.message:
        lines[line_idx] = target_line.replace("?.", ".")
        modified = True
        if log_callback:
            log_callback(f"[FIX] Removed unnecessary safe call `?.` at {path.name}:{error.line}")

    # 9. Superclass constructor invocation
    elif "No value passed for parameter 'name'" in error.message and ": Module()" in target_line:
        # Check bak file for super(...) call
        bak_path = path.with_suffix('.java.bak')
        if not bak_path.exists():
            bak_path = Path(str(path).replace('src/main/kotlin', 'src/main/java')).with_suffix('.java.bak')
        if bak_path.exists():
            with open(bak_path, 'r', errors='ignore') as bf:
                bcode = bf.read()
                m_sup = re.search(r'super\s*\((.*?)\);', bcode)
                if m_sup:
                    s_args = m_sup.group(1).strip()
                    s_args = re.sub(r'new\s+String\[0\]', 'emptyArray()', s_args)
                    s_args = re.sub(r'new\s+String\[\]\s*\{(.*?)\}', r'arrayOf(\1)', s_args)
                    s_args = re.sub(r'\.class\b', '::class.java', s_args)
                    lines[line_idx] = target_line.replace(": Module()", f": Module({s_args})")
                    modified = True
                    if log_callback:
                        log_callback(f"[FIX] Restored super constructor arguments for Module at {path.name}:{error.line}")

    if modified:
        with open(path, 'w', encoding='utf-8') as f:
            f.writelines(lines)
        return True

    return False


# ==============================================================================
# 5. ATOMIC SAFE MIGRATION PIPELINE (WITH AUTO-ROLLBACK)
# ==============================================================================

def migrate_single_file_safe(java_path: Path, max_fix_iterations: int = 3, log_callback: Optional[Callable[[str], None]] = None) -> bool:
    """
    Safely converts a single Java file to Kotlin with automatic rollback if compilation fails.
    """
    java_path = java_path.resolve()
    if is_excluded(str(java_path.relative_to(JAVA_SRC))):
        if log_callback:
            log_callback(f"[SKIP] {java_path.name} is in exclusion list.")
        return False

    bak_path = java_path.with_suffix('.java.bak')
    kt_path_str = str(java_path).replace('src/main/java', 'src/main/kotlin')
    kt_path = Path(kt_path_str).with_suffix('.kt')

    # Backup original java
    shutil.copy2(java_path, bak_path)

    try:
        if log_callback:
            log_callback(f"[CONVERT] Transpiling {java_path.name} -> {kt_path.name}")

        with open(java_path, 'r', encoding='utf-8') as f:
            java_code = f.read()

        kt_code = transpile_java_to_kotlin(java_code)
        fixed_kt, changes = fix_kotlin_content(kt_code)

        kt_path.parent.mkdir(parents=True, exist_ok=True)
        with open(kt_path, 'w', encoding='utf-8') as f:
            f.write(fixed_kt)

        # Temporarily remove java so compiler doesn't complain about duplicate classes
        java_path.unlink()

        # Iterative verify & auto-fix loop
        success = False
        for iteration in range(1, max_fix_iterations + 1):
            if log_callback:
                log_callback(f"[VERIFY] Iteration {iteration}/{max_fix_iterations} for {kt_path.name}...")

            build_ok, out, errors = run_gradle_compile(timeout_sec=45, log_callback=None)
            if build_ok:
                success = True
                break

            # Filter errors relating to this file
            file_errors = [e for e in errors if Path(e.file_path).name == kt_path.name]
            if not file_errors:
                # Error elsewhere or general failure
                if log_callback:
                    log_callback(f"[WARNING] Build failed with {len(errors)} errors outside this file.")
                break

            if log_callback:
                log_callback(f"[AUTO-FIX] Found {len(file_errors)} errors in {kt_path.name}. Applying fixes...")

            fixed_any = False
            for err in file_errors:
                if apply_auto_repair(err, log_callback=log_callback):
                    fixed_any = True

            if not fixed_any:
                if log_callback:
                    log_callback("[AUTO-FIX] Could not automatically repair remaining errors.")
                break

        if success:
            if bak_path.exists():
                bak_path.unlink()
            if log_callback:
                log_callback(f"[SUCCESS] {kt_path.name} successfully migrated and verified!")
            return True
        else:
            # ROLLBACK
            if log_callback:
                log_callback(f"[ROLLBACK] Rolling back {kt_path.name} due to build errors...")
            if kt_path.exists():
                kt_path.unlink()
            if bak_path.exists():
                shutil.move(bak_path, java_path)
            return False

    except Exception as e:
        if log_callback:
            log_callback(f"[ERROR] Exception during migration: {e}. Rolling back...")
        if kt_path.exists():
            kt_path.unlink()
        if bak_path.exists():
            shutil.move(bak_path, java_path)
        return False


def migrate_batch_safe(package_name: str, max_files: int = 10, max_fix_iterations: int = 3, skip_files: Optional[set] = None, log_callback: Optional[Callable[[str], None]] = None) -> Tuple[int, int]:
    """
    Migrates files in a package one by one with safe rollback.
    """
    if skip_files is None:
        skip_files = set()

    target_dir = JAVA_SRC / package_name
    if not target_dir.exists():
        # Look for subpackage e.g. modules/exploit
        target_dir = JAVA_SRC / "modules" / package_name
    if not target_dir.exists():
        if log_callback:
            log_callback(f"[ERROR] Package directory not found for: {package_name}")
        return 0, 0

    java_files = sorted(list(target_dir.glob("*.java")))
    convertible = [f for f in java_files if not is_excluded(str(f.relative_to(JAVA_SRC))) and f.name not in skip_files]

    if log_callback:
        log_callback(f"[BATCH] Found {len(convertible)} convertible files in {package_name} (skipped: {len(skip_files)}). Processing up to {max_files}...")

    succeeded = 0
    failed = 0

    for f in convertible[:max_files]:
        ok = migrate_single_file_safe(f, max_fix_iterations=max_fix_iterations, log_callback=log_callback)
        if ok:
            succeeded += 1
        else:
            failed += 1
            skip_files.add(f.name)
            if log_callback:
                log_callback(f"[BATCH SKIP] Added {f.name} to skip list for this session so it won't block the queue.")

    return succeeded, failed


# ==============================================================================
# 6. CLI INTERFACE
# ==============================================================================

if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description="Collective Client Kotlin Migrator")
    parser.add_argument('action', choices=['verify', 'fix', 'convert', 'batch', 'app'], help="Action to perform")
    parser.add_argument('--path', type=str, help="Target file or directory")
    parser.add_argument('--pkg', type=str, help="Package name for batch migration")
    parser.add_argument('--limit', type=int, default=10, help="Max files for batch")
    args = parser.parse_args()

    if args.action == 'verify':
        ok, out, errs = run_gradle_compile(log_callback=print)
        sys.exit(0 if ok else 1)
    elif args.action == 'convert' and args.path:
        ok = migrate_single_file_safe(Path(args.path), log_callback=print)
        sys.exit(0 if ok else 1)
    elif args.action == 'batch' and args.pkg:
        s, f = migrate_batch_safe(args.pkg, max_files=args.limit, log_callback=print)
        print(f"Batch finished: {s} succeeded, {f} failed.")
    elif args.action == 'app':
        # Launch standalone desktop GUI
        import subprocess
        subprocess.run([sys.executable, str(PROJECT_ROOT / "scripts" / "kotlin_migrator_app.py")])
