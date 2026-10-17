#!/data/data/com.termux/files/usr/bin/env python3
import termuxgui as tg
import subprocess
import threading
import time
import re
import os
import sys

class GrannyMacroApp:
    def __init__(self):
        self.touch_device = self.detect_touchscreen()
        self.rec_proc = None
        self.play_proc = None
        self.is_recording = False
        self.is_playing = False
        self.current_preset = "preset_1.txt"

    def detect_touchscreen(self):
        try:
            res = subprocess.run(["adb", "shell", "getevent", "-lp"], capture_output=True, text=True, timeout=5).stdout
            dev = None
            for line in res.splitlines():
                if line.startswith("add device"):
                    dev = line.split(":")[1].strip()
                elif "ABS_MT_POSITION_X" in line and dev:
                    return dev
        except Exception:
            pass
        return "/dev/input/event2"

    def start_recording(self, status_lbl, c):
        if self.is_recording or self.is_playing:
            return
        self.is_recording = True
        
        def run_rec():
            for i in range(3, 0, -1):
                if not self.is_recording:
                    return
                status_lbl.settext(c, f"⏳ Siap ({i} dtk)...")
                time.sleep(1)
            
            status_lbl.settext(c, f"🔴 MEREKAM: {self.current_preset}")
            with open(self.current_preset, "w") as f:
                self.rec_proc = subprocess.Popen(
                    ["adb", "shell", "getevent", "-t", self.touch_device],
                    stdout=f
                )
                self.rec_proc.wait()
            status_lbl.settext(c, f"✅ Tersimpan: {self.current_preset}")
            self.is_recording = False

        threading.Thread(target=run_rec, daemon=True).start()

    def stop_action(self, status_lbl, c):
        if self.is_recording and self.rec_proc:
            self.rec_proc.terminate()
            subprocess.run(["adb", "shell", "pkill -f getevent"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            self.is_recording = False
            status_lbl.settext(c, "⏹️ Rekaman Disimpan!")
        elif self.is_playing:
            subprocess.run(["adb", "shell", "pkill -f /data/local/tmp/granny_play.sh"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            self.is_playing = False
            status_lbl.settext(c, "⏹️ Auto-Play Dihentikan!")
        else:
            status_lbl.settext(c, "🟢 Siap (Idle)")

    def start_playing(self, status_lbl, c):
        if self.is_recording or self.is_playing:
            return
        if not os.path.exists(self.current_preset):
            status_lbl.settext(c, f"❌ {self.current_preset} Kosong!")
            return

        self.is_playing = True

        def run_play():
            status_lbl.settext(c, "⚙️ Memproses Macro...")
            pattern = re.compile(r"\[\s*(\d+\.\d+)\]\s+([0-9a-fA-F]+)\s+([0-9a-fA-F]+)\s+([0-9a-fA-F]+)")
            events = []
            with open(self.current_preset, "r") as f:
                for line in f:
                    m = pattern.search(line)
                    if m:
                        ts = float(m.group(1))
                        etype = int(m.group(2), 16)
                        ecode = int(m.group(3), 16)
                        eval_ = int(m.group(4), 16)
                        if eval_ >= 0x80000000:
                            eval_ -= 0x100000000
                        events.append((ts, etype, ecode, eval_))

            if not events:
                status_lbl.settext(c, "❌ Data Rekaman Kosong!")
                self.is_playing = False
                return

            sh_lines = ["#!/system/bin/sh"]
            last_ts = events[0][0]
            cmd_buf = []

            for ts, etype, ecode, eval_ in events:
                dt = ts - last_ts
                if dt > 0.015:
                    if cmd_buf:
                        sh_lines.append("; ".join(cmd_buf))
                        cmd_buf = []
                    sh_lines.append(f"sleep {dt:.3f}")
                cmd_buf.append(f"sendevent {self.touch_device} {etype} {ecode} {eval_}")
                last_ts = ts

            if cmd_buf:
                sh_lines.append("; ".join(cmd_buf))

            with open("granny_play.sh", "w") as f:
                f.write("\n".join(sh_lines) + "\n")

            subprocess.run(["adb", "push", "granny_play.sh", "/data/local/tmp/granny_play.sh"], stdout=subprocess.DEVNULL)
            subprocess.run(["adb", "shell", "chmod +x /data/local/tmp/granny_play.sh"])

            for i in range(3, 0, -1):
                if not self.is_playing:
                    return
                status_lbl.settext(c, f"⏳ Mulai ({i} dtk)...")
                time.sleep(1)

            status_lbl.settext(c, f"▶️ AUTO-PLAY: {self.current_preset}")
            self.play_proc = subprocess.Popen(["adb", "shell", "/data/local/tmp/granny_play.sh"])
            self.play_proc.wait()
            if self.is_playing:
                status_lbl.settext(c, "✅ Selesai!")
                self.is_playing = False

        threading.Thread(target=run_play, daemon=True).start()

    def launch_gui(self):
        with tg.Connection() as c:
            a = tg.Activity(c, dialog=True)
            layout = tg.LinearLayout(c, a)
            
            title = tg.TextView(c, "👵 GRANNY AUTO-BOT", layout)
            title.settextsize(c, 16)
            
            dev_lbl = tg.TextView(c, f"Sensor: {self.touch_device}", layout)
            dev_lbl.settextsize(c, 11)

            status_lbl = tg.TextView(c, "🟢 Status: Siap", layout)
            status_lbl.settextsize(c, 13)

            preset_row = tg.LinearLayout(c, layout, vertical=False)
            btn_presets = {}
            for i in range(1, 6):
                b = tg.Button(c, f"P{i}", preset_row)
                btn_presets[b.id] = f"preset_{i}.txt"

            action_row = tg.LinearLayout(c, layout, vertical=False)
            btn_rec = tg.Button(c, "🔴 REKAM", action_row)
            btn_play = tg.Button(c, "▶️ PLAY", action_row)
            btn_stop = tg.Button(c, "⏹️ STOP", action_row)
            
            btn_exit = tg.Button(c, "❌ Tutup", layout)

            for ev in c.events():
                if ev.type == tg.Event.click:
                    clicked_id = ev.value["id"]
                    if clicked_id in btn_presets:
                        self.current_preset = btn_presets[clicked_id]
                        status_lbl.settext(c, f"📂 Aktif: {self.current_preset}")
                    elif clicked_id == btn_rec.id:
                        self.start_recording(status_lbl, c)
                    elif clicked_id == btn_play.id:
                        self.start_playing(status_lbl, c)
                    elif clicked_id == btn_stop.id:
                        self.stop_action(status_lbl, c)
                    elif clicked_id == btn_exit.id:
                        self.stop_action(status_lbl, c)
                        a.finish(c)
                        sys.exit(0)

if __name__ == "__main__":
    app = GrannyMacroApp()
    app.launch_gui()
