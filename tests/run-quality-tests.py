#!/usr/bin/env python3
"""Compile actual production classes with only the config dirty notification stubbed.
Uses local Java 21, no downloads, and never edits the user's game/config files.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'src/main/java/me/collective'
with tempfile.TemporaryDirectory(prefix='collective-regression-') as directory:
    out = Path(directory)
    stub = out / 'me/collective/config/ConfigManager.java'
    stub.parent.mkdir(parents=True)
    stub.write_text('package me.collective.config; public class ConfigManager { '
                    'private static final ConfigManager INSTANCE = new ConfigManager(); '
                    'public static ConfigManager getInstance() { return INSTANCE; } '
                    'public void markDirty() {} }')
    sources = [SRC / 'event/EventBus.java', SRC / 'event/EventHandler.java']
    sources += [SRC / ('setting/' + name + '.java') for name in
                ['Setting', 'NumberSetting', 'IntegerSetting', 'EnumSetting', 'BooleanSetting']]
    sources += [stub, ROOT / 'tests/QualityRegression.java']
    subprocess.run(['javac', '--release', '21', '-encoding', 'UTF-8', '-d', str(out),
                    *map(str, sources)], check=True)
    subprocess.run(['java', '-cp', str(out), 'QualityRegression'], check=True)
