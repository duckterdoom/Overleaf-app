"""IELTS Writing evaluation manager, persistent store, and LaTeX report generator."""

import json
import threading
from datetime import datetime, timezone
from pathlib import Path
from .store import Problem, atomic_write


def round_ielts(val: float) -> float:
    frac = val - int(val)
    if frac < 0.25:
        return float(int(val))
    elif frac < 0.75:
        return float(int(val)) + 0.5
    else:
        return float(int(val) + 1)


DEFAULT_IELTS_DATA = {
    "studentName": "IELTS Candidate",
    "tutorName": "IELTS Tutor",
    "examDate": "",
    "task1": {
        "title": "Task 1: Academic / General Report",
        "scores": {
            "TA": 6.5,
            "CC": 6.5,
            "LR": 6.5,
            "GRA": 6.0,
        },
        "feedback": {
            "TA": "Well-structured overview. Ensure key statistics are compared accurately.",
            "CC": "Good paragraph division and cohesive devices.",
            "LR": "Flexible range of vocabulary with good academic collocations.",
            "GRA": "Mix of complex sentences, watch out for minor article slips.",
        },
        "overallFeedback": "Solid performance in Task 1.",
    },
    "task2": {
        "title": "Task 2: Discursive Essay",
        "scores": {
            "TR": 7.0,
            "CC": 7.0,
            "LR": 6.5,
            "GRA": 6.5,
        },
        "feedback": {
            "TR": "Addresses all parts of prompt with a clear position throughout.",
            "CC": "Logical progression of ideas with clear topic sentences.",
            "LR": "Good thematic vocabulary and idiom awareness.",
            "GRA": "Variety of complex structures with frequent error-free clauses.",
        },
        "overallFeedback": "Strong arguments and convincing reasoning.",
    },
    "generalAssessment": "The candidate demonstrates strong command of written English. Focus on minor grammatical accuracy and more varied linking devices to reach Band 7.5+.",
    "recommendations": "1. Practice timed writing for Task 1 (under 20 minutes).\n2. Expand lexical resource around abstract topics.\n3. Proofread for subject-verb agreement and preposition slips.",
    "updatedAt": "",
}


class IeltsManager:
    def __init__(self, store):
        self.store = store
        self.lock = threading.RLock()

    def path(self, project: str) -> Path:
        p = self.store.root(project) / ".overleaf-ielts.json"
        if p.is_symlink():
            raise Problem("IELTS store không được là symbolic link.")
        return p

    def read(self, project: str) -> dict:
        with self.lock:
            p = self.path(project)
            if not p.exists():
                return DEFAULT_IELTS_DATA.copy()
            try:
                data = json.loads(p.read_text(encoding="utf-8"))
                return data
            except Exception:
                return DEFAULT_IELTS_DATA.copy()

    def save(self, project: str, data: dict) -> dict:
        with self.lock:
            if not isinstance(data, dict):
                raise Problem("Dữ liệu IELTS không hợp lệ.")
            data["updatedAt"] = datetime.now(timezone.utc).isoformat()
            payload = json.dumps(data, ensure_ascii=False, indent=2).encode("utf-8")
            if len(payload) > 5 * 1024 * 1024:
                raise Problem("Dữ liệu vượt quá giới hạn 5 MiB.")
            atomic_write(self.path(project), payload)
            return data

    def generate_report_tex(self, project: str, data: dict) -> str:
        with self.lock:
            t1 = data.get("task1", {})
            t1_scores = t1.get("scores", {})
            t1_fb = t1.get("feedback", {})
            t1_ta = float(t1_scores.get("TA", 6.5))
            t1_cc = float(t1_scores.get("CC", 6.5))
            t1_lr = float(t1_scores.get("LR", 6.5))
            t1_gra = float(t1_scores.get("GRA", 6.0))
            t1_band = round_ielts((t1_ta + t1_cc + t1_lr + t1_gra) / 4.0)

            t2 = data.get("task2", {})
            t2_scores = t2.get("scores", {})
            t2_fb = t2.get("feedback", {})
            t2_tr = float(t2_scores.get("TR", 7.0))
            t2_cc = float(t2_scores.get("CC", 7.0))
            t2_lr = float(t2_scores.get("LR", 6.5))
            t2_gra = float(t2_scores.get("GRA", 6.5))
            t2_band = round_ielts((t2_tr + t2_cc + t2_lr + t2_gra) / 4.0)

            overall = round_ielts((t1_band * 1.0 + t2_band * 2.0) / 3.0)

            student = data.get("studentName", "IELTS Candidate")
            tutor = data.get("tutorName", "IELTS Tutor")
            now_str = datetime.now().strftime("%B %d, %Y")

            def esc(txt):
                if not txt:
                    return ""
                return (
                    str(txt)
                    .replace("\\", "\\textbackslash{}")
                    .replace("&", "\\&")
                    .replace("%", "\\%")
                    .replace("$", "\\$")
                    .replace("#", "\\#")
                    .replace("_", "\\_")
                    .replace("{", "\\{")
                    .replace("}", "\\}")
                    .replace("~", "\\textasciitilde{}")
                    .replace("^", "\\textasciicircum{}")
                )

            lines = [
                r"\documentclass[11pt,a4paper]{article}",
                r"\usepackage[utf8]{inputenc}",
                r"\usepackage[margin=2cm]{geometry}",
                r"\usepackage{xcolor}",
                r"\usepackage{tcolorbox}",
                r"\usepackage{tabularx}",
                r"\usepackage{booktabs}",
                r"\usepackage{enumitem}",
                r"\usepackage{hyperref}",
                r"\definecolor{ieltsgreen}{RGB}{23, 98, 63}",
                r"\definecolor{ieltssubtle}{RGB}{240, 246, 242}",
                r"\hypersetup{colorlinks=true, linkcolor=ieltsgreen, urlcolor=ieltsgreen}",
                r"\begin{document}",
                r"\begin{center}",
                r"    {\LARGE\bfseries\color{ieltsgreen} IELTS WRITING EVALUATION REPORT}\\[2mm]",
                r"    {\small Official Diagnostic Assessment \& Feedback}",
                r"\end{center}",
                r"\vspace{2mm}",
                r"\noindent\hrulefill",
                r"\vspace{3mm}",
                r"\noindent",
                rf"\begin{{tabularx}}{{\textwidth}}{{X X}}",
                rf"    \textbf{{Candidate:}} {esc(student)} & \textbf{{Evaluator/Tutor:}} {esc(tutor)} \\",
                rf"    \textbf{{Date:}} {esc(now_str)} & \textbf{{Framework:}} IELTS Public Band Descriptors \\",
                r"\end{tabularx}",
                r"\vspace{4mm}",
                r"\begin{tcolorbox}[colback=ieltssubtle,colframe=ieltsgreen,title={\large\bfseries Overall Score Summary},arc=3mm]",
                r"    \centering",
                r"    \begin{tabularx}{0.9\textwidth}{XXX}",
                r"        \centering \textbf{Task 1 (Weight 1/3)} & \centering \textbf{Task 2 (Weight 2/3)} & \centering \textbf{OVERALL WRITING BAND} \tabularnewline",
                r"        \midrule",
                rf"        \centering {{\Huge\bfseries {t1_band:.1f}}} & \centering {{\Huge\bfseries {t2_band:.1f}}} & \centering {{\Huge\bfseries\color{{ieltsgreen}} {overall:.1f}}} \tabularnewline",
                r"    \end{tabularx}",
                r"\end{tcolorbox}",
                r"\vspace{4mm}",
                rf"\section*{{Task 1: Report Evaluation (Band {t1_band:.1f})}}",
                r"\begin{tabularx}{\textwidth}{l c X}",
                r"    \toprule",
                r"    \textbf{Criterion} & \textbf{Score} & \textbf{Tutor Comments \& Feedback} \\",
                r"    \midrule",
                rf"    Task Achievement (TA) & \textbf{{{t1_ta:.1f}}} & {esc(t1_fb.get('TA', ''))} \\",
                rf"    Coherence \& Cohesion (CC) & \textbf{{{t1_cc:.1f}}} & {esc(t1_fb.get('CC', ''))} \\",
                rf"    Lexical Resource (LR) & \textbf{{{t1_lr:.1f}}} & {esc(t1_fb.get('LR', ''))} \\",
                rf"    Grammatical Range \& Accuracy (GRA) & \textbf{{{t1_gra:.1f}}} & {esc(t1_fb.get('GRA', ''))} \\",
                r"    \bottomrule",
                r"\end{tabularx}",
                r"\vspace{2mm}",
                rf"\noindent\textbf{{Task 1 Summary:}} {esc(t1.get('overallFeedback', ''))}",
                r"\vspace{4mm}",
                rf"\section*{{Task 2: Essay Evaluation (Band {t2_band:.1f})}}",
                r"\begin{tabularx}{\textwidth}{l c X}",
                r"    \toprule",
                r"    \textbf{Criterion} & \textbf{Score} & \textbf{Tutor Comments \& Feedback} \\",
                r"    \midrule",
                rf"    Task Response (TR) & \textbf{{{t2_tr:.1f}}} & {esc(t2_fb.get('TR', ''))} \\",
                rf"    Coherence \& Cohesion (CC) & \textbf{{{t2_cc:.1f}}} & {esc(t2_fb.get('CC', ''))} \\",
                rf"    Lexical Resource (LR) & \textbf{{{t2_lr:.1f}}} & {esc(t2_fb.get('LR', ''))} \\",
                rf"    Grammatical Range \& Accuracy (GRA) & \textbf{{{t2_gra:.1f}}} & {esc(t2_fb.get('GRA', ''))} \\",
                r"    \bottomrule",
                r"\end{tabularx}",
                r"\vspace{2mm}",
                rf"\noindent\textbf{{Task 2 Summary:}} {esc(t2.get('overallFeedback', ''))}",
                r"\vspace{4mm}",
                r"\section*{General Assessment \& Tutor Recommendations}",
                r"\begin{tcolorbox}[colback=white,colframe=gray!60,arc=2mm]",
                r"    \noindent\textbf{General Evaluation:}\\[1mm]",
                rf"    {esc(data.get('generalAssessment', ''))}",
                r"    \vspace{3mm}",
                r"    \noindent\textbf{Key Recommendations:}\\[1mm]",
                rf"    {esc(data.get('recommendations', ''))}",
                r"\end{tcolorbox}",
                r"\vfill",
                r"\begin{center}",
                r"    {\footnotesize Generated by Overleaf Desktop IELTS Writing Assessment Engine}",
                r"\end{center}",
                r"\end{document}",
            ]
            tex = "\n".join(lines)
            out_file = self.store.root(project) / "ielts_report.tex"
            atomic_write(out_file, tex.encode("utf-8"))
            return "ielts_report.tex"
