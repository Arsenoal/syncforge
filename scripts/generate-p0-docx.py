#!/usr/bin/env python3
"""Generate short P0 checklist DOCX for SyncForge 1.0.0."""

from pathlib import Path

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.shared import Pt

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "docs" / "SyncForge-1.0-P0.docx"

# Post-1.0.0 P1 / community items (P0 complete July 2026).
SECTIONS = [
    (
        "Documentation (P1-01)",
        [
            "Upgrade guide — pre-0.6 Room → SQLDelight (recommended; migration path in ANDROID_SETUP.md)",
        ],
    ),
    (
        "CI & QA (P1-03)",
        [
            "ProGuard/R8 — document and test consumer-rules.pro (P1-03)",
        ],
    ),
    (
        "Community soak (P1-07)",
        [
            "At least one external dogfood or documented third-party integration (#feed thread, GitHub issues)",
        ],
    ),
]


def build() -> None:
    doc = Document()
    style = doc.styles["Normal"]
    style.font.name = "Arial"
    style.font.size = Pt(11)

    title = doc.add_heading("SyncForge 1.0.0 — P0 Checklist", level=0)
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER

    doc.add_paragraph("Post-1.0.0 follow-ups (P1 / community; P0 release gate complete).")

    work_items = sum(len(items) for heading, items in SECTIONS if "sign-off" not in heading.lower())
    sign_off_items = sum(
        len(items) for heading, items in SECTIONS if "sign-off" in heading.lower()
    )

    doc.add_paragraph("Date: July 2026")
    doc.add_paragraph("Current baseline: 1.0.0 (Maven Central stable release)")
    doc.add_paragraph(f"Remaining work items: {work_items}")
    doc.add_paragraph(f"Release sign-off checks: {sign_off_items}")
    doc.add_paragraph()

    doc.add_heading("1.0.0 status", level=1)
    doc.add_paragraph(
        "GA on Maven Central (studio.syncforge:1.0.0) with tag v1.0.0. "
        "Items below are P1/community follow-ups, not release blockers."
    )

    for heading, items in SECTIONS:
        doc.add_heading(heading, level=2)
        for item in items:
            doc.add_paragraph(item, style="List Bullet")

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    doc.save(OUTPUT)
    print(f"Wrote {OUTPUT} ({work_items} work items, {sign_off_items} sign-off checks)")


if __name__ == "__main__":
    build()