#!/usr/bin/env python3
"""
Standalone Desktop Application: Collective Client Kotlin Migrator Studio
Built with CustomTkinter (Material Design 3 Dark Theme).
Runs natively on Linux (Wayland / X11) without any browser dependency.
"""

import os
import sys
import time
import queue
import threading
from pathlib import Path
from typing import Dict, List, Any, Optional

import customtkinter as ctk
from tkinter import font

# Add scripts directory to sys.path
SCRIPTS_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPTS_DIR.parent
sys.path.insert(0, str(SCRIPTS_DIR))

from module_scanner import scan_modules
from kotlin_migrator import (
    run_gradle_compile,
    migrate_single_file_safe,
    migrate_batch_safe,
    is_excluded
)

# CustomTkinter Global Configuration
ctk.set_appearance_mode("dark")
ctk.set_default_color_theme("blue")

# Material Design 3 Palette
MD3_BG = "#121316"
MD3_SURFACE = "#1B1D24"
MD3_SURFACE_VARIANT = "#262932"
MD3_PRIMARY = "#8AB4F8"
MD3_ON_PRIMARY = "#002D6C"
MD3_SECONDARY = "#C2C7CF"
MD3_SUCCESS = "#81C784"
MD3_WARNING = "#FFB74D"
MD3_ERROR = "#F28B82"
MD3_TEXT = "#E3E3E3"
MD3_TEXT_MUTED = "#9AA0A6"


class MigratorApp(ctk.CTk):
    def __init__(self):
        super().__init__()

        self.title("Collective Client — Kotlin Migrator Studio (MD3)")
        self.geometry("1160x780")
        self.minsize(980, 640)
        self.configure(fg_color=MD3_BG)

        # State
        self.scan_data: Dict[str, Any] = {}
        self.is_task_running = False
        self.cancel_requested = False
        self.log_queue = queue.Queue()
        self.skip_files: set = set()

        # Build UI Layout
        self._init_header()
        self._init_action_bar()
        self._init_tabs()

        # Start periodic log queue consumer
        self.after(100, self._process_log_queue)

        # Initial background scan
        self.after(300, self.action_scan)

    # ==========================================================================
    # UI INITIALIZATION
    # ==========================================================================

    def _init_header(self):
        self.header_frame = ctk.CTkFrame(self, fg_color=MD3_SURFACE, corner_radius=16)
        self.header_frame.pack(fill="x", padx=16, pady=(16, 8))

        # Title & Subtitle
        title_box = ctk.CTkFrame(self.header_frame, fg_color="transparent")
        title_box.pack(side="left", padx=20, pady=16)

        self.lbl_title = ctk.CTkLabel(
            title_box,
            text="Collective Client Kotlin Migrator",
            font=ctk.CTkFont(family="Google Sans", size=20, weight="bold"),
            text_color=MD3_TEXT
        )
        self.lbl_title.pack(anchor="w")

        self.lbl_subtitle = ctk.CTkLabel(
            title_box,
            text="Material Design 3 Autonomous Transpilation & Compiler Auto-Repair Studio",
            font=ctk.CTkFont(family="Roboto", size=12),
            text_color=MD3_TEXT_MUTED
        )
        self.lbl_subtitle.pack(anchor="w")

        # Stats Chips & Progress
        stats_box = ctk.CTkFrame(self.header_frame, fg_color="transparent")
        stats_box.pack(side="right", padx=20, pady=16)

        self.lbl_stats = ctk.CTkLabel(
            stats_box,
            text="Scanning codebase...",
            font=ctk.CTkFont(family="Roboto", size=13, weight="bold"),
            text_color=MD3_PRIMARY
        )
        self.lbl_stats.pack(anchor="e")

        self.progress_bar = ctk.CTkProgressBar(
            stats_box,
            width=260,
            height=10,
            corner_radius=5,
            progress_color=MD3_PRIMARY,
            fg_color=MD3_SURFACE_VARIANT
        )
        self.progress_bar.set(0.0)
        self.progress_bar.pack(anchor="e", pady=(6, 4))

        self.lbl_build_status = ctk.CTkLabel(
            stats_box,
            text="STATUS: READY",
            font=ctk.CTkFont(family="Roboto", size=11, weight="bold"),
            text_color=MD3_SUCCESS
        )
        self.lbl_build_status.pack(anchor="e")

    def _init_action_bar(self):
        self.action_frame = ctk.CTkFrame(self, fg_color=MD3_SURFACE, corner_radius=12)
        self.action_frame.pack(fill="x", padx=16, pady=8)

        # Buttons
        self.btn_scan = ctk.CTkButton(
            self.action_frame,
            text="🔍 Scan Repo",
            width=120,
            height=36,
            corner_radius=8,
            fg_color=MD3_SURFACE_VARIANT,
            hover_color="#323642",
            command=self.action_scan
        )
        self.btn_scan.pack(side="left", padx=(16, 6), pady=10)

        self.btn_verify = ctk.CTkButton(
            self.action_frame,
            text="⚡ Verify Build",
            width=130,
            height=36,
            corner_radius=8,
            fg_color=MD3_PRIMARY,
            text_color=MD3_ON_PRIMARY,
            hover_color="#AECBFA",
            command=self.action_verify_build
        )
        self.btn_verify.pack(side="left", padx=6, pady=10)

        self.btn_batch = ctk.CTkButton(
            self.action_frame,
            text="🚀 Migrate Next Batch",
            width=160,
            height=36,
            corner_radius=8,
            fg_color="#34A853",
            text_color="#0A3818",
            hover_color="#81C784",
            command=self.action_migrate_batch
        )
        self.btn_batch.pack(side="left", padx=6, pady=10)

        self.btn_full = ctk.CTkButton(
            self.action_frame,
            text="🔄 Full Autonomous Pipeline",
            width=190,
            height=36,
            corner_radius=8,
            fg_color="#A142F4",
            text_color="#240046",
            hover_color="#D0BCFF",
            command=self.action_full_pipeline
        )
        self.btn_full.pack(side="left", padx=6, pady=10)

        self.btn_stop = ctk.CTkButton(
            self.action_frame,
            text="⏹️ Cancel Task",
            width=110,
            height=36,
            corner_radius=8,
            fg_color="#5F6368",
            hover_color="#70757A",
            state="disabled",
            command=self.action_stop_task
        )
        self.btn_stop.pack(side="right", padx=(6, 16), pady=10)

    def _init_tabs(self):
        self.tabview = ctk.CTkTabview(
            self,
            fg_color=MD3_SURFACE,
            segmented_button_selected_color=MD3_PRIMARY,
            segmented_button_selected_hover_color="#AECBFA",
            segmented_button_unselected_color=MD3_SURFACE_VARIANT,
            corner_radius=14
        )
        self.tabview.pack(fill="both", expand=True, padx=16, pady=(8, 16))

        self.tab_modules = self.tabview.add("📦 Modules Explorer")
        self.tab_console = self.tabview.add("💻 Live Build Terminal")
        self.tab_settings = self.tabview.add("⚙️ Pipeline Configuration")

        self._init_modules_tab()
        self._init_console_tab()
        self._init_settings_tab()

    def _init_modules_tab(self):
        # Filter controls
        filter_box = ctk.CTkFrame(self.tab_modules, fg_color="transparent")
        filter_box.pack(fill="x", padx=12, pady=(10, 8))

        self.search_entry = ctk.CTkEntry(
            filter_box,
            placeholder_text="Search module name or package...",
            width=280,
            height=32,
            corner_radius=8,
            fg_color=MD3_SURFACE_VARIANT
        )
        self.search_entry.pack(side="left", padx=(0, 10))
        self.search_entry.bind("<KeyRelease>", lambda e: self._refresh_modules_view())

        self.pkg_dropdown = ctk.CTkOptionMenu(
            filter_box,
            values=["All Packages", "exploit", "combat", "components", "screens", "settings", "commands"],
            width=150,
            height=32,
            corner_radius=8,
            fg_color=MD3_SURFACE_VARIANT,
            command=lambda v: self._refresh_modules_view()
        )
        self.pkg_dropdown.pack(side="left", padx=6)

        self.status_dropdown = ctk.CTkOptionMenu(
            filter_box,
            values=["All Statuses", "Java (Unconverted)", "Kotlin (Converted)", "Excluded"],
            width=160,
            height=32,
            corner_radius=8,
            fg_color=MD3_SURFACE_VARIANT,
            command=lambda v: self._refresh_modules_view()
        )
        self.status_dropdown.pack(side="left", padx=6)

        self.lbl_module_count = ctk.CTkLabel(
            filter_box,
            text="Modules: 0",
            font=ctk.CTkFont(family="Roboto", size=12),
            text_color=MD3_TEXT_MUTED
        )
        self.lbl_module_count.pack(side="right", padx=8)

        # Scrollable modules list
        self.modules_scroll = ctk.CTkScrollableFrame(
            self.tab_modules,
            fg_color=MD3_BG,
            corner_radius=10
        )
        self.modules_scroll.pack(fill="both", expand=True, padx=10, pady=(4, 10))

    def _init_console_tab(self):
        ctrl_box = ctk.CTkFrame(self.tab_console, fg_color="transparent")
        ctrl_box.pack(fill="x", padx=10, pady=(8, 4))

        lbl = ctk.CTkLabel(
            ctrl_box,
            text="Real-time Gradle & Transpiler Console Output",
            font=ctk.CTkFont(family="Roboto", size=12, weight="bold"),
            text_color=MD3_TEXT
        )
        lbl.pack(side="left")

        btn_clear = ctk.CTkButton(
            ctrl_box,
            text="Clear Console",
            width=100,
            height=28,
            corner_radius=6,
            fg_color=MD3_SURFACE_VARIANT,
            hover_color="#363A45",
            command=self._clear_console
        )
        btn_clear.pack(side="right", padx=6)

        self.chk_autoscroll = ctk.CTkCheckBox(
            ctrl_box,
            text="Auto-scroll",
            height=24,
            corner_radius=4,
            text_color=MD3_TEXT_MUTED
        )
        self.chk_autoscroll.select()
        self.chk_autoscroll.pack(side="right", padx=12)

        # Terminal Text Box
        self.txt_console = ctk.CTkTextbox(
            self.tab_console,
            fg_color="#0E1014",
            text_color="#8AB4F8",
            font=ctk.CTkFont(family="Monospace", size=12),
            corner_radius=10
        )
        self.txt_console.pack(fill="both", expand=True, padx=10, pady=(4, 10))

    def _init_settings_tab(self):
        settings_frame = ctk.CTkFrame(self.tab_settings, fg_color="transparent")
        settings_frame.pack(fill="both", expand=True, padx=20, pady=20)

        # Safety & Rollback Card
        card_safety = ctk.CTkFrame(settings_frame, fg_color=MD3_SURFACE_VARIANT, corner_radius=12)
        card_safety.pack(fill="x", pady=10, padx=10)

        lbl_s = ctk.CTkLabel(
            card_safety,
            text="Atomic Safe Staging & Rollback",
            font=ctk.CTkFont(family="Roboto", size=15, weight="bold"),
            text_color=MD3_PRIMARY
        )
        lbl_s.pack(anchor="w", padx=16, pady=(12, 4))

        desc_s = ctk.CTkLabel(
            card_safety,
            text="Automatically backs up .java files to .java.bak before conversion. If any compiler errors remain after auto-repair iterations, the broken .kt file is removed and .java is instantly restored, guaranteeing 0 corrupted files.",
            font=ctk.CTkFont(family="Roboto", size=12),
            text_color=MD3_TEXT_MUTED,
            wraplength=850,
            justify="left"
        )
        desc_s.pack(anchor="w", padx=16, pady=(0, 10))

        self.chk_rollback = ctk.CTkCheckBox(card_safety, text="Enable Automatic Rollback on Build Failure", height=24)
        self.chk_rollback.select()
        self.chk_rollback.pack(anchor="w", padx=16, pady=(0, 14))

        # Iterations Card
        card_iter = ctk.CTkFrame(settings_frame, fg_color=MD3_SURFACE_VARIANT, corner_radius=12)
        card_iter.pack(fill="x", pady=10, padx=10)

        lbl_i = ctk.CTkLabel(
            card_iter,
            text="Compiler Auto-Repair Iteration Limit",
            font=ctk.CTkFont(family="Roboto", size=15, weight="bold"),
            text_color=MD3_PRIMARY
        )
        lbl_i.pack(anchor="w", padx=16, pady=(12, 4))

        self.slider_iter = ctk.CTkSlider(card_iter, from_=1, to=10, number_of_steps=9, width=300)
        self.slider_iter.set(3)
        self.slider_iter.pack(anchor="w", padx=16, pady=6)

        self.lbl_slider_val = ctk.CTkLabel(card_iter, text="Max Auto-Repair Iterations: 3", text_color=MD3_TEXT)
        self.lbl_slider_val.pack(anchor="w", padx=16, pady=(0, 14))
        self.slider_iter.configure(command=lambda val: self.lbl_slider_val.configure(text=f"Max Auto-Repair Iterations: {int(val)}"))

    # ==========================================================================
    # LOGGING & CONSOLE STREAMING
    # ==========================================================================

    def log(self, message: str):
        self.log_queue.put(message)

    def _process_log_queue(self):
        while not self.log_queue.empty():
            msg = self.log_queue.get_nowait()
            self.txt_console.insert("end", msg + "\n")
            if self.chk_autoscroll.get():
                self.txt_console.see("end")

        self.after(80, self._process_log_queue)

    def _clear_console(self):
        self.txt_console.delete("1.0", "end")

    # ==========================================================================
    # WORKER THREAD WRAPPER
    # ==========================================================================

    def _run_in_background(self, target_fn, *args):
        if self.is_task_running:
            self.log("[BUSY] A task is already in progress. Please wait or cancel.")
            return

        self.is_task_running = True
        self.cancel_requested = False
        self.btn_stop.configure(state="normal")
        self.btn_verify.configure(state="disabled")
        self.btn_batch.configure(state="disabled")
        self.btn_full.configure(state="disabled")

        def wrapper():
            try:
                target_fn(*args)
            except Exception as e:
                self.log(f"[TASK EXCEPTION] {e}")
            finally:
                self.is_task_running = False
                self.btn_stop.configure(state="disabled")
                self.btn_verify.configure(state="normal")
                self.btn_batch.configure(state="normal")
                self.btn_full.configure(state="normal")
                self.action_scan()

        t = threading.Thread(target=wrapper, daemon=True)
        t.start()

    # ==========================================================================
    # CORE ACTIONS
    # ==========================================================================

    def action_scan(self):
        def worker():
            self.log("[SCAN] Scanning project repository...")
            data = scan_modules()
            self.scan_data = data
            stats = data["stats"]

            pct = stats.get("progress_percent", 0.0)
            text_stats = f"Kotlin: {pct}% ({stats.get('converted_files', 0)}/{stats.get('total_files', 0)} files, {stats.get('converted_loc', 0):,} LOC)"

            self.after(0, lambda: self._update_scan_ui(text_stats, pct / 100.0))
            self.after(0, self._refresh_modules_view)
            self.log(f"[SCAN COMPLETE] {text_stats}")

        threading.Thread(target=worker, daemon=True).start()

    def _update_scan_ui(self, stats_text: str, progress_val: float):
        self.lbl_stats.configure(text=stats_text)
        self.progress_bar.set(progress_val)

    def action_verify_build(self):
        def worker():
            self.tabview.set("💻 Live Build Terminal")
            self.log("=" * 60)
            self.log("[VERIFY] Starting Gradle verification...")
            self.lbl_build_status.configure(text="STATUS: BUILDING...", text_color=MD3_WARNING)

            ok, out, errs = run_gradle_compile(timeout_sec=120, log_callback=self.log)

            if ok:
                self.log("=" * 60)
                self.log("[BUILD SUCCESSFUL] All Kotlin & Java files compiled with 0 errors!")
                self.lbl_build_status.configure(text="STATUS: BUILD SUCCESSFUL", text_color=MD3_SUCCESS)
            else:
                self.log("=" * 60)
                self.log(f"[BUILD FAILED] Detected {len(errs)} compiler errors.")
                self.lbl_build_status.configure(text=f"STATUS: BUILD FAILED ({len(errs)} errors)", text_color=MD3_ERROR)

        self._run_in_background(worker)

    def action_migrate_batch(self):
        selected_pkg = self.pkg_dropdown.get()
        if selected_pkg == "All Packages":
            selected_pkg = "exploit"

        max_iters = int(self.slider_iter.get())

        def worker():
            self.tabview.set("💻 Live Build Terminal")
            self.log("=" * 60)
            self.log(f"[BATCH] Starting safe migration for package: {selected_pkg}...")
            s, f = migrate_batch_safe(
                selected_pkg,
                max_files=5,
                max_fix_iterations=max_iters,
                skip_files=self.skip_files,
                log_callback=self.log
            )
            self.log(f"[BATCH FINISHED] Successfully converted: {s}, Rolled back/Failed: {f}")

        self._run_in_background(worker)

    def action_full_pipeline(self):
        max_iters = int(self.slider_iter.get())
        packages_to_migrate = ["exploit", "combat", "components", "screens", "settings"]

        def worker():
            self.tabview.set("💻 Live Build Terminal")
            self.log("=" * 70)
            self.log("[PIPELINE] Starting Full Autonomous Safe Migration Pipeline...")
            self.log("Packages to process sequentially: " + ", ".join(packages_to_migrate))

            for pkg in packages_to_migrate:
                if self.cancel_requested:
                    self.log("[PIPELINE] Migration cancelled by user.")
                    break

                self.log("-" * 60)
                self.log(f"[PIPELINE] Migrating package '{pkg}'...")
                s, f = migrate_batch_safe(
                    pkg,
                    max_files=10,
                    max_fix_iterations=max_iters,
                    skip_files=self.skip_files,
                    log_callback=self.log
                )
                self.log(f"[PIPELINE] Package '{pkg}' completed: {s} converted, {f} failed/reverted.")

            self.log("=" * 70)
            self.log("[PIPELINE COMPLETE] Final verification check...")
            run_gradle_compile(timeout_sec=120, log_callback=self.log)

        self._run_in_background(worker)

    def action_stop_task(self):
        self.cancel_requested = True
        self.log("[CANCEL] Cancellation requested. Finishing current atomic step...")

    # ==========================================================================
    # MODULES VIEW RENDERING
    # ==========================================================================

    def _refresh_modules_view(self):
        for widget in self.modules_scroll.winfo_children():
            widget.destroy()

        if not self.scan_data:
            return

        search_kw = self.search_entry.get().lower().strip()
        pkg_filter = self.pkg_dropdown.get()
        status_filter = self.status_dropdown.get()

        all_entries = []
        for e in self.scan_data.get("unconverted", []):
            e_copy = dict(e)
            e_copy["status"] = "Java"
            all_entries.append(e_copy)

        for e in self.scan_data.get("converted", []):
            e_copy = dict(e)
            e_copy["status"] = "Kotlin"
            all_entries.append(e_copy)

        for e in self.scan_data.get("excluded", []):
            e_copy = dict(e)
            e_copy["status"] = "Excluded"
            all_entries.append(e_copy)

        # Apply Filters
        filtered = []
        for e in all_entries:
            if search_kw and search_kw not in e["name"].lower() and search_kw not in e.get("package", "").lower():
                continue
            if pkg_filter != "All Packages" and e.get("package") != pkg_filter:
                continue
            if status_filter == "Java (Unconverted)" and e["status"] != "Java":
                continue
            if status_filter == "Kotlin (Converted)" and e["status"] != "Kotlin":
                continue
            if status_filter == "Excluded" and e["status"] != "Excluded":
                continue
            filtered.append(e)

        self.lbl_module_count.configure(text=f"Showing: {len(filtered)} / {len(all_entries)} modules")

        # Render rows (limit to 120 for instant rendering)
        for e in filtered[:120]:
            row = ctk.CTkFrame(self.modules_scroll, fg_color=MD3_SURFACE, height=40, corner_radius=8)
            row.pack(fill="x", padx=4, pady=3)

            # Status Badge
            badge_color = MD3_SUCCESS if e["status"] == "Kotlin" else (MD3_WARNING if e["status"] == "Java" else "#5F6368")
            lbl_badge = ctk.CTkLabel(
                row,
                text=f" {e['status']} ",
                font=ctk.CTkFont(family="Roboto", size=10, weight="bold"),
                fg_color=badge_color,
                text_color="#000000",
                corner_radius=4,
                width=60
            )
            lbl_badge.pack(side="left", padx=(10, 8), pady=8)

            # Name & Package
            lbl_name = ctk.CTkLabel(
                row,
                text=e["name"],
                font=ctk.CTkFont(family="Roboto", size=13, weight="bold"),
                text_color=MD3_TEXT
            )
            lbl_name.pack(side="left", padx=4)

            lbl_pkg = ctk.CTkLabel(
                row,
                text=f"({e.get('package', '')})",
                font=ctk.CTkFont(family="Roboto", size=11),
                text_color=MD3_TEXT_MUTED
            )
            lbl_pkg.pack(side="left", padx=4)

            # LOC
            lbl_loc = ctk.CTkLabel(
                row,
                text=f"{e.get('loc', 0)} LOC",
                font=ctk.CTkFont(family="Roboto", size=11),
                text_color=MD3_TEXT_MUTED
            )
            lbl_loc.pack(side="right", padx=16)

            # Single-file action button if Java
            if e["status"] == "Java":
                file_abs = e.get("abs_path")
                btn_mig = ctk.CTkButton(
                    row,
                    text="Migrate",
                    width=70,
                    height=24,
                    corner_radius=6,
                    fg_color=MD3_PRIMARY,
                    text_color=MD3_ON_PRIMARY,
                    hover_color="#AECBFA",
                    command=lambda p=file_abs: self._migrate_file_action(p)
                )
                btn_mig.pack(side="right", padx=6)

    def _migrate_file_action(self, abs_path: str):
        if not abs_path:
            return
        p = Path(abs_path)
        max_iters = int(self.slider_iter.get())

        def worker():
            self.tabview.set("💻 Live Build Terminal")
            self.log("=" * 60)
            self.log(f"[SINGLE MIGRATE] Converting {p.name}...")
            ok = migrate_single_file_safe(p, max_fix_iterations=max_iters, log_callback=self.log)
            if ok:
                self.log(f"[SUCCESS] {p.name} converted successfully!")
            else:
                self.log(f"[FAILED] {p.name} failed and was safely rolled back.")

        self._run_in_background(worker)


def main():
    app = MigratorApp()
    app.mainloop()


if __name__ == "__main__":
    main()
