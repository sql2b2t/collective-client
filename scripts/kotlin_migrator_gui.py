#!/usr/bin/env python3
"""
Material Design 3 (MD3) GUI & Server for Collective Client Kotlin Migrator.
Provides automated module discovery, interactive migration, real-time logging, and verification.
"""

import os
import sys
import json
import time
import queue
import threading
import subprocess
import webbrowser
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

from module_scanner import scan_modules
from kotlin_migrator import convert_java_file_to_kotlin, run_fixer_on_paths, fix_kotlin_content

LOG_QUEUE = queue.Queue(maxsize=1000)
STATUS_LOCK = threading.Lock()
CURRENT_TASK = {
    "running": False,
    "action": "idle",
    "progress": 0,
    "current_file": "",
    "total": 0,
    "current": 0
}

def log(msg: str):
    timestamp = time.strftime("%H:%M:%S")
    entry = f"[{timestamp}] {msg}"
    print(entry)
    try:
        LOG_QUEUE.put_nowait(entry)
    except queue.Full:
        try:
            LOG_QUEUE.get_nowait()
            LOG_QUEUE.put_nowait(entry)
        except Exception:
            pass

HTML_PAGE = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Collective Migrator • Material Design 3</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Google+Sans:wght@400;500;700&family=Roboto+Mono:wght@400;500&family=Roboto:wght@400;500;700&display=swap" rel="stylesheet">
    <link href="https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@20..48,100..700,0..1,-50..200" rel="stylesheet" />
    <style>
        :root {
            --md-sys-color-primary: #a8c7fa;
            --md-sys-color-on-primary: #062e6f;
            --md-sys-color-primary-container: #0842a0;
            --md-sys-color-on-primary-container: #d3e3fd;
            --md-sys-color-surface: #111318;
            --md-sys-color-on-surface: #e2e2e9;
            --md-sys-color-surface-variant: #44474f;
            --md-sys-color-on-surface-variant: #c4c6d0;
            --md-sys-color-surface-container: #1d2026;
            --md-sys-color-surface-container-high: #282a30;
            --md-sys-color-surface-container-highest: #33353b;
            --md-sys-color-outline: #8e9099;
            --md-sys-color-outline-variant: #44474f;
            --md-sys-color-secondary-container: #004a77;
            --md-sys-color-on-secondary-container: #c2e7ff;
            --md-sys-color-success: #6dd58c;
            --md-sys-color-success-container: #0f5223;
            --md-sys-color-on-success-container: #c4eed0;
            --md-sys-color-error: #f2b8b5;
            --md-sys-color-error-container: #8c1d18;
            --md-shape-corner-small: 8px;
            --md-shape-corner-medium: 12px;
            --md-shape-corner-large: 16px;
            --md-shape-corner-extra-large: 28px;
            --md-shape-corner-full: 9999px;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
            font-family: 'Roboto', -apple-system, BlinkMacSystemFont, sans-serif;
            -webkit-font-smoothing: antialiased;
        }

        body {
            background-color: var(--md-sys-color-surface);
            color: var(--md-sys-color-on-surface);
            min-height: 100vh;
            display: flex;
            flex-direction: column;
        }

        /* Top App Bar */
        .top-app-bar {
            height: 64px;
            background-color: var(--md-sys-color-surface-container);
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 0 24px;
            border-bottom: 1px solid var(--md-sys-color-outline-variant);
            position: sticky;
            top: 0;
            z-index: 100;
        }

        .brand {
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .brand-icon {
            color: var(--md-sys-color-primary);
            font-size: 28px;
        }

        .brand-title {
            font-family: 'Google Sans', sans-serif;
            font-size: 20px;
            font-weight: 500;
            letter-spacing: -0.2px;
        }

        .brand-badge {
            background-color: var(--md-sys-color-secondary-container);
            color: var(--md-sys-color-on-secondary-container);
            font-size: 11px;
            font-weight: 500;
            padding: 2px 8px;
            border-radius: var(--md-shape-corner-full);
            text-transform: uppercase;
        }

        /* Layout */
        .app-container {
            display: flex;
            flex: 1;
            overflow: hidden;
        }

        /* Navigation Rail / Sidebar */
        .nav-rail {
            width: 260px;
            background-color: var(--md-sys-color-surface-container);
            border-right: 1px solid var(--md-sys-color-outline-variant);
            padding: 16px 12px;
            display: flex;
            flex-direction: column;
            gap: 4px;
        }

        .nav-item {
            display: flex;
            align-items: center;
            gap: 16px;
            padding: 12px 16px;
            border-radius: var(--md-shape-corner-full);
            color: var(--md-sys-color-on-surface-variant);
            cursor: pointer;
            transition: all 0.2s cubic-bezier(0.2, 0, 0, 1);
            font-size: 14px;
            font-weight: 500;
            border: none;
            background: transparent;
            width: 100%;
            text-align: left;
        }

        .nav-item:hover {
            background-color: var(--md-sys-color-surface-container-highest);
            color: var(--md-sys-color-on-surface);
        }

        .nav-item.active {
            background-color: var(--md-sys-color-secondary-container);
            color: var(--md-sys-color-on-secondary-container);
        }

        .nav-item .material-symbols-rounded {
            font-size: 22px;
        }

        /* Main Content */
        .main-content {
            flex: 1;
            padding: 28px 36px;
            overflow-y: auto;
            max-width: 1400px;
            margin: 0 auto;
            width: 100%;
        }

        /* Cards */
        .card {
            background-color: var(--md-sys-color-surface-container);
            border-radius: var(--md-shape-corner-large);
            padding: 24px;
            margin-bottom: 24px;
            border: 1px solid var(--md-sys-color-outline-variant);
        }

        .card-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 16px;
        }

        .card-title {
            font-family: 'Google Sans', sans-serif;
            font-size: 18px;
            font-weight: 500;
        }

        /* Stat Grid */
        .stats-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
            gap: 16px;
            margin-bottom: 24px;
        }

        .stat-card {
            background-color: var(--md-sys-color-surface-container-high);
            border-radius: var(--md-shape-corner-medium);
            padding: 20px;
            display: flex;
            flex-direction: column;
            gap: 8px;
            border: 1px solid transparent;
            transition: border-color 0.2s;
        }

        .stat-card:hover {
            border-color: var(--md-sys-color-outline);
        }

        .stat-label {
            font-size: 13px;
            color: var(--md-sys-color-on-surface-variant);
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .stat-value {
            font-family: 'Google Sans', sans-serif;
            font-size: 32px;
            font-weight: 700;
            color: var(--md-sys-color-primary);
        }

        .stat-sub {
            font-size: 12px;
            color: var(--md-sys-color-outline);
        }

        /* Linear Progress Bar (MD3) */
        .progress-container {
            width: 100%;
            height: 8px;
            background-color: var(--md-sys-color-surface-container-highest);
            border-radius: var(--md-shape-corner-full);
            overflow: hidden;
            margin: 12px 0 20px 0;
            position: relative;
        }

        .progress-bar {
            height: 100%;
            background: linear-gradient(90deg, var(--md-sys-color-primary), #6dd58c);
            width: 0%;
            transition: width 0.4s cubic-bezier(0.4, 0, 0.2, 1);
            border-radius: var(--md-shape-corner-full);
        }

        /* Buttons */
        .btn {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            padding: 10px 24px;
            border-radius: var(--md-shape-corner-full);
            font-size: 14px;
            font-weight: 500;
            cursor: pointer;
            transition: all 0.2s ease;
            border: none;
            outline: none;
        }

        .btn-primary {
            background-color: var(--md-sys-color-primary);
            color: var(--md-sys-color-on-primary);
        }

        .btn-primary:hover {
            opacity: 0.92;
            box-shadow: 0 1px 3px rgba(0,0,0,0.3);
        }

        .btn-tonal {
            background-color: var(--md-sys-color-secondary-container);
            color: var(--md-sys-color-on-secondary-container);
        }

        .btn-tonal:hover {
            filter: brightness(1.1);
        }

        .btn-outlined {
            background-color: transparent;
            color: var(--md-sys-color-primary);
            border: 1px solid var(--md-sys-color-outline);
        }

        .btn-outlined:hover {
            background-color: rgba(168, 199, 250, 0.08);
        }

        .btn-sm {
            padding: 6px 14px;
            font-size: 12px;
        }

        /* Chips */
        .chip {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 4px 10px;
            border-radius: var(--md-shape-corner-small);
            font-size: 12px;
            font-weight: 500;
        }

        .chip-success {
            background-color: var(--md-sys-color-success-container);
            color: var(--md-sys-color-on-success-container);
        }

        .chip-pending {
            background-color: rgba(242, 184, 181, 0.15);
            color: var(--md-sys-color-error);
            border: 1px solid rgba(242, 184, 181, 0.3);
        }

        .chip-pkg {
            background-color: var(--md-sys-color-surface-container-highest);
            color: var(--md-sys-color-on-surface);
        }

        /* Table */
        .table-responsive {
            overflow-x: auto;
        }

        table {
            width: 100%;
            border-collapse: collapse;
            font-size: 13px;
        }

        th {
            text-align: left;
            padding: 12px 16px;
            color: var(--md-sys-color-on-surface-variant);
            border-bottom: 1px solid var(--md-sys-color-outline-variant);
            font-weight: 500;
        }

        td {
            padding: 14px 16px;
            border-bottom: 1px solid rgba(255, 255, 255, 0.05);
            color: var(--md-sys-color-on-surface);
        }

        tr:hover td {
            background-color: var(--md-sys-color-surface-container-high);
        }

        /* Terminal Console */
        .console-container {
            background-color: #0d0f14;
            border-radius: var(--md-shape-corner-medium);
            border: 1px solid var(--md-sys-color-outline-variant);
            padding: 16px;
            font-family: 'Roboto Mono', monospace;
            font-size: 12.5px;
            color: #d1d5db;
            height: 360px;
            overflow-y: auto;
            line-height: 1.6;
        }

        .console-line {
            margin-bottom: 2px;
            word-break: break-all;
        }

        .console-line.success { color: var(--md-sys-color-success); }
        .console-line.error { color: var(--md-sys-color-error); }
        .console-line.info { color: var(--md-sys-color-primary); }

        /* Actions Bar */
        .actions-bar {
            display: flex;
            gap: 12px;
            flex-wrap: wrap;
            align-items: center;
            margin-bottom: 24px;
        }

        .search-input {
            background-color: var(--md-sys-color-surface-container-high);
            border: 1px solid var(--md-sys-color-outline-variant);
            color: var(--md-sys-color-on-surface);
            padding: 10px 16px;
            border-radius: var(--md-shape-corner-full);
            font-size: 14px;
            outline: none;
            flex: 1;
            min-width: 240px;
            transition: border-color 0.2s;
        }

        .search-input:focus {
            border-color: var(--md-sys-color-primary);
        }
    </style>
</head>
<body>

    <!-- Top App Bar -->
    <header class="top-app-bar">
        <div class="brand">
            <span class="material-symbols-rounded brand-icon">auto_awesome</span>
            <span class="brand-title">Collective Client Kotlin Migrator</span>
            <span class="brand-badge">Material You v3</span>
        </div>
        <div style="display: flex; gap: 12px; align-items: center;">
            <div id="status-pill" class="chip chip-success">
                <span class="material-symbols-rounded" style="font-size: 14px;">check_circle</span>
                <span id="status-text">Ready</span>
            </div>
            <button class="btn btn-outlined btn-sm" onclick="triggerScan()">
                <span class="material-symbols-rounded" style="font-size: 16px;">refresh</span>
                Scan
            </button>
        </div>
    </header>

    <div class="app-container">
        <!-- Sidebar Navigation Rail -->
        <nav class="nav-rail">
            <button class="nav-item active" onclick="switchTab('dashboard')">
                <span class="material-symbols-rounded">dashboard</span>
                Dashboard
            </button>
            <button class="nav-item" onclick="switchTab('explorer')">
                <span class="material-symbols-rounded">folder_open</span>
                Module Explorer
            </button>
            <button class="nav-item" onclick="switchTab('automigrate')">
                <span class="material-symbols-rounded">rocket_launch</span>
                Auto-Migrator
            </button>
            <button class="nav-item" onclick="switchTab('console')">
                <span class="material-symbols-rounded">terminal</span>
                Live Console
            </button>
        </nav>

        <!-- Main Content -->
        <main class="main-content">

            <!-- TAB: Dashboard -->
            <div id="tab-dashboard" class="tab-pane">
                <div class="stats-grid">
                    <div class="stat-card">
                        <div class="stat-label">
                            <span class="material-symbols-rounded" style="font-size: 16px;">data_object</span>
                            Kotlin Progress
                        </div>
                        <div class="stat-value" id="stat-progress">0%</div>
                        <div class="stat-sub" id="stat-loc">0 / 0 LOC</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-label">
                            <span class="material-symbols-rounded" style="font-size: 16px;">check</span>
                            Converted Files
                        </div>
                        <div class="stat-value" id="stat-converted" style="color: var(--md-sys-color-success);">0</div>
                        <div class="stat-sub">Kotlin classes compiled</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-label">
                            <span class="material-symbols-rounded" style="font-size: 16px;">pending</span>
                            Unconverted Files
                        </div>
                        <div class="stat-value" id="stat-unconverted" style="color: var(--md-sys-color-error);">0</div>
                        <div class="stat-sub">Java classes pending</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-label">
                            <span class="material-symbols-rounded" style="font-size: 16px;">verified</span>
                            Gradle Compiler
                        </div>
                        <div class="stat-value" style="color: var(--md-sys-color-primary);" id="stat-compiler">PASS</div>
                        <div class="stat-sub">0 compile errors</div>
                    </div>
                </div>

                <div class="card">
                    <div class="card-header">
                        <span class="card-title">Overall Migration Progress</span>
                        <span id="progress-percent-label" style="font-weight: 700; color: var(--md-sys-color-primary);">0%</span>
                    </div>
                    <div class="progress-container">
                        <div class="progress-bar" id="overall-progress-bar"></div>
                    </div>
                    <p style="font-size: 13px; color: var(--md-sys-color-on-surface-variant);">
                        Automated batch transpiler translates Java structures, resolves Minecraft accessors, injects null-safety assertions, and verifies Gradle build targets.
                    </p>
                </div>

                <div class="card">
                    <div class="card-header">
                        <span class="card-title">Unconverted Packages Breakdown</span>
                    </div>
                    <div id="packages-container" style="display: flex; gap: 12px; flex-wrap: wrap;"></div>
                </div>
            </div>

            <!-- TAB: Explorer -->
            <div id="tab-explorer" class="tab-pane" style="display: none;">
                <div class="actions-bar">
                    <input type="text" id="module-search" class="search-input" placeholder="Search modules by name or package..." oninput="filterModules()">
                    <button class="btn btn-primary" onclick="autoMigrateAll()">
                        <span class="material-symbols-rounded">play_arrow</span>
                        Auto-Migrate All
                    </button>
                    <button class="btn btn-tonal" onclick="runFixer()">
                        <span class="material-symbols-rounded">build</span>
                        Run Fixer
                    </button>
                    <button class="btn btn-outlined" onclick="verifyGradle()">
                        <span class="material-symbols-rounded">check</span>
                        Verify Build
                    </button>
                </div>

                <div class="card">
                    <div class="table-responsive">
                        <table>
                            <thead>
                                <tr>
                                    <th>Module / Class</th>
                                    <th>Package</th>
                                    <th>Lines of Code</th>
                                    <th>Status</th>
                                    <th style="text-align: right;">Action</th>
                                </tr>
                            </thead>
                            <tbody id="modules-table-body"></tbody>
                        </table>
                    </div>
                </div>
            </div>

            <!-- TAB: Auto-Migrate -->
            <div id="tab-automigrate" class="tab-pane" style="display: none;">
                <div class="card">
                    <div class="card-header">
                        <span class="card-title">One-Click Automated Batch Migration</span>
                    </div>
                    <p style="font-size: 14px; color: var(--md-sys-color-on-surface-variant); margin-bottom: 20px;">
                        Select a package category to automatically transpile, fix AST null-safety, and verify with Gradle:
                    </p>
                    <div class="actions-bar">
                        <button class="btn btn-primary" onclick="migrateBatch('combat')">
                            <span class="material-symbols-rounded">swords</span>
                            Migrate combat/ (~90 files)
                        </button>
                        <button class="btn btn-tonal" onclick="migrateBatch('exploit')">
                            <span class="material-symbols-rounded">bug_report</span>
                            Migrate exploit/ (~35 files)
                        </button>
                        <button class="btn btn-outlined" onclick="migrateBatch('commands')">
                            <span class="material-symbols-rounded">terminal</span>
                            Migrate commands/
                        </button>
                    </div>
                </div>
            </div>

            <!-- TAB: Console -->
            <div id="tab-console" class="tab-pane" style="display: none;">
                <div class="card">
                    <div class="card-header">
                        <span class="card-title">Live Build & Migrator Console</span>
                        <button class="btn btn-outlined btn-sm" onclick="clearConsole()">Clear</button>
                    </div>
                    <div class="console-container" id="terminal-console"></div>
                </div>
            </div>

        </main>
    </div>

    <script>
        let appData = null;

        function switchTab(tabId) {
            document.querySelectorAll('.tab-pane').forEach(el => el.style.display = 'none');
            document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
            
            const target = document.getElementById('tab-' + tabId);
            if (target) target.style.display = 'block';

            const activeBtn = Array.from(document.querySelectorAll('.nav-item')).find(b => b.textContent.toLowerCase().includes(tabId));
            if (activeBtn) activeBtn.classList.add('active');
        }

        async function fetchStatus() {
            try {
                const res = await fetch('/api/status');
                appData = await res.json();
                renderUI();
            } catch (e) {
                console.error("Failed to fetch status:", e);
            }
        }

        function renderUI() {
            if (!appData || !appData.stats) return;
            const s = appData.stats;
            document.getElementById('stat-progress').textContent = s.progress_percent + '%';
            document.getElementById('progress-percent-label').textContent = s.progress_percent + '%';
            document.getElementById('overall-progress-bar').style.width = s.progress_percent + '%';
            document.getElementById('stat-loc').textContent = s.converted_loc.toLocaleString() + ' / ' + s.total_loc.toLocaleString() + ' LOC';
            document.getElementById('stat-converted').textContent = s.converted_files;
            document.getElementById('stat-unconverted').textContent = s.unconverted_files;

            // Render packages chips
            const pkgContainer = document.getElementById('packages-container');
            pkgContainer.innerHTML = '';
            for (const [pkg, info] of Object.entries(appData.packages)) {
                const chip = document.createElement('div');
                chip.className = 'chip chip-pkg';
                chip.innerHTML = `<strong>${pkg}</strong> &bull; ${info.count} files &bull; ${info.loc} LOC`;
                pkgContainer.appendChild(chip);
            }

            renderModulesTable(appData.unconverted);
        }

        function renderModulesTable(items) {
            const tbody = document.getElementById('modules-table-body');
            tbody.innerHTML = '';
            items.slice(0, 150).forEach(item => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td><strong>${item.name}</strong><br><span style="font-size: 11px; color: var(--md-sys-color-outline);">${item.path}</span></td>
                    <td><span class="chip chip-pkg">${item.package}</span></td>
                    <td>${item.loc}</td>
                    <td><span class="chip chip-pending">Pending</span></td>
                    <td style="text-align: right;">
                        <button class="btn btn-outlined btn-sm" onclick="convertSingle('${item.path}')">
                            Convert
                        </button>
                    </td>
                `;
                tbody.appendChild(tr);
            });
        }

        function filterModules() {
            const query = document.getElementById('module-search').value.toLowerCase();
            if (!appData || !appData.unconverted) return;
            const filtered = appData.unconverted.filter(m => 
                m.name.toLowerCase().includes(query) || m.path.toLowerCase().includes(query) || m.package.toLowerCase().includes(query)
            );
            renderModulesTable(filtered);
        }

        async function triggerScan() {
            logConsole("Scanning repository for unconverted modules...");
            await fetch('/api/scan', { method: 'POST' });
            await fetchStatus();
            logConsole("Scan complete.");
        }

        async function convertSingle(path) {
            logConsole(`Converting single file: ${path}...`);
            await fetch('/api/convert_module', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ path: path })
            });
            await fetchStatus();
        }

        async function migrateBatch(pkg) {
            logConsole(`Starting automated batch migration for ${pkg}...`);
            switchTab('console');
            await fetch('/api/convert_batch', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ package: pkg })
            });
            await fetchStatus();
        }

        async function autoMigrateAll() {
            logConsole("Triggering auto-migration of all unconverted modules...");
            switchTab('console');
            await fetch('/api/auto_migrate_all', { method: 'POST' });
            await fetchStatus();
        }

        async function runFixer() {
            logConsole("Running AST / Regex Fixer across all Kotlin files...");
            switchTab('console');
            await fetch('/api/run_fixer', { method: 'POST' });
            await fetchStatus();
        }

        async function verifyGradle() {
            logConsole("Running ./gradlew compileKotlin compileJava verification...");
            switchTab('console');
            await fetch('/api/verify', { method: 'POST' });
        }

        function logConsole(msg) {
            const el = document.getElementById('terminal-console');
            const line = document.createElement('div');
            line.className = 'console-line';
            line.textContent = msg;
            el.appendChild(line);
            el.scrollTop = el.scrollHeight;
        }

        function clearConsole() {
            document.getElementById('terminal-console').innerHTML = '';
        }

        // Live logs polling
        setInterval(async () => {
            try {
                const res = await fetch('/api/logs');
                const logs = await res.json();
                if (logs && logs.length > 0) {
                    logs.forEach(msg => logConsole(msg));
                }
            } catch (e) {}
        }, 1200);

        // Initial load
        fetchStatus();
    </script>
</body>
</html>
"""

class MigratorHandler(BaseHTTPRequestHandler):
    def _send_json(self, data, code=200):
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        self.wfile.write(json.dumps(data).encode('utf-8'))

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == '/' or parsed.path == '/index.html':
            self.send_response(200)
            self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.end_headers()
            self.wfile.write(HTML_PAGE.encode('utf-8'))
        elif parsed.path == '/api/status':
            data = scan_modules()
            self._send_json(data)
        elif parsed.path == '/api/logs':
            logs = []
            while not LOG_QUEUE.empty():
                try:
                    logs.append(LOG_QUEUE.get_nowait())
                except Exception:
                    break
            self._send_json(logs)
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        parsed = urlparse(self.path)
        content_length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_length) if content_length > 0 else b'{}'
        req_data = json.loads(body.decode('utf-8')) if body else {}

        if parsed.path == '/api/scan':
            data = scan_modules()
            log(f"Scanned repository: {data['stats']['unconverted_files']} unconverted files remaining.")
            self._send_json({"ok": True, "stats": data["stats"]})

        elif parsed.path == '/api/convert_module':
            target_path = PROJECT_ROOT / req_data.get('path', '')
            if target_path.exists() and target_path.suffix == '.java':
                log(f"Transpiling module: {target_path.name}")
                convert_java_file_to_kotlin(target_path, delete_java=True)
                run_fixer_on_paths(["src/main/kotlin"])
                log(f"Successfully converted and fixed {target_path.name}!")
                self._send_json({"ok": True, "converted": str(target_path)})
            else:
                self._send_json({"error": "File not found"}, code=400)

        elif parsed.path == '/api/convert_batch':
            pkg = req_data.get('package', '')
            log(f"Starting batch conversion for package '{pkg}'...")
            threading.Thread(target=self._run_batch, args=(pkg,), daemon=True).start()
            self._send_json({"ok": True, "status": "started"})

        elif parsed.path == '/api/auto_migrate_all':
            log("Starting full auto-migration across all unconverted modules...")
            threading.Thread(target=self._run_all, daemon=True).start()
            self._send_json({"ok": True, "status": "started"})

        elif parsed.path == '/api/run_fixer':
            log("Running AST & Regex Fixer across src/main/kotlin...")
            n = run_fixer_on_paths(["src/main/kotlin"])
            log(f"Fixer complete: updated {n} files.")
            self._send_json({"ok": True, "modified": n})

        elif parsed.path == '/api/verify':
            log("Executing Gradle compileKotlin and compileJava...")
            threading.Thread(target=self._run_verify, daemon=True).start()
            self._send_json({"ok": True, "status": "verifying"})

        else:
            self.send_response(404)
            self.end_headers()

    def _run_batch(self, pkg: str):
        data = scan_modules()
        targets = [m for m in data['unconverted'] if m['package'] == pkg]
        log(f"Found {len(targets)} files in package '{pkg}'. Converting...")
        for item in targets:
            p = Path(item['abs_path'])
            if p.exists():
                try:
                    convert_java_file_to_kotlin(p, delete_java=True)
                except Exception as e:
                    log(f"Error converting {p.name}: {e}")
        run_fixer_on_paths(["src/main/kotlin"])
        log(f"Batch conversion for '{pkg}' finished. Running verification...")
        self._run_verify()

    def _run_all(self):
        data = scan_modules()
        targets = data['unconverted']
        log(f"Auto-migrating {len(targets)} unconverted files...")
        for item in targets:
            p = Path(item['abs_path'])
            if p.exists():
                try:
                    convert_java_file_to_kotlin(p, delete_java=True)
                except Exception as e:
                    log(f"Error converting {p.name}: {e}")
        run_fixer_on_paths(["src/main/kotlin"])
        log("Auto-migration complete. Verifying build...")
        self._run_verify()

    def _run_verify(self):
        res = subprocess.run(['./gradlew', 'compileKotlin', 'compileJava'], text=True, capture_output=True)
        if res.returncode == 0:
            log(">>> BUILD SUCCESSFUL: 0 errors! <<<")
        else:
            log(">>> BUILD FAILED. Errors detected:")
            for line in res.stdout.splitlines()[-20:]:
                log(f"  {line}")

def start_server(port=8088, open_browser=True):
    server = HTTPServer(('127.0.0.1', port), MigratorHandler)
    url = f"http://localhost:{port}"
    print(f"\n=======================================================")
    print(f" Material Design 3 Migrator GUI started at:")
    print(f" {url}")
    print(f"=======================================================\n")
    if open_browser:
        webbrowser.open(url)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down GUI server.")
        server.server_close()

if __name__ == '__main__':
    port = 8088
    if len(sys.argv) > 1 and sys.argv[1].isdigit():
        port = int(sys.argv[1])
    start_server(port=port, open_browser=True)
