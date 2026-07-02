#!/usr/bin/env python3
"""Generate short P0 checklist DOCX for SyncForge 1.0.0."""

from pathlib import Path

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.shared import Pt

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "docs" / "SyncForge-1.0-P0.docx"

SECTIONS = [
    (
        "Sample App Proof (0.8.0)",
        [
            "E2E tests for multi-entity flows (task + note)",
        ],
    ),
    (
        "Android & persistence (0.7.0)",
        [
            "Migrator hardening: partial failure, logging, large outbox tests",
        ],
    ),
    (
        "Documentation & distribution (0.6.0–0.9.0)",
        [
            "Publish 0.6.0+ to Maven Central with verified consumer Gradle snippet",
            "REST_API.md versioning and breaking-change policy",
            "Minimum Ktor reference backend starter (beyond mock-server)",
        ],
    ),
    (
        "Platform & network (0.7.0)",
        [
            "iOS background sync (BGTaskScheduler / BGAppRefreshTask hook)",
            "Token refresh on 401 (RefreshingSyncAuthProvider or documented pattern)",
        ],
    ),
    (
        "API stability (0.8.0–1.0.0)",
        [
            "Graduate SyncForge.android and core SyncManager APIs as stable",
            "Remove ConflictResolver, LastWriteWinsResolver, fromResolver at 1.0",
        ],
    ),
    (
        "CI & release (0.7.0–1.0.0)",
        [
            "Add androidE2e to nightly or main CI (emulator + mock-server)",
            "1.0 acceptance test matrix sign-off",
        ],
    ),
]

COMPLETED_0_6 = [
    "SQLDelight Android default + automatic RoomToSqlDelightMigrator",
    "Legacy Room internals removed from public API (useRoomPersistence() opt-in retained)",
    "Docs synced: README, ROADMAP, KMP_MIGRATION, GETTING_STARTED, MODULES, ANDROID_SETUP",
    "Pre-0.6 Room upgrade path documented in ANDROID_SETUP.md",
]

COMPLETED_SAMPLE = [
    "Three entities (tasks, notes, tags) with Room DAOs and KSP handlers (:sample)",
    "Compose Navigation: TasksRoute, NotesRoute, TagsRoute in SampleApp.kt",
    "Single SyncManager + multi-handler EntityRegistry (SyncForgeHandlers.registry)",
    "Per-entity conflict policies: tasks deferToUser(), notes/tags lastWriteWins()",
    "Acceptance: add task + note on separate screens; one sync() pushes both types",
    "Acceptance: task conflict does not block notes sync; debug panel lists both entity types",
]

COMPLETED_CI = [
    "macOS release workflow publishes iOS/macOS frameworks on tag (.github/workflows/publish-release.yml)",
]


def build() -> None:
    doc = Document()
    style = doc.styles["Normal"]
    style.font.name = "Arial"
    style.font.size = Pt(11)

    title = doc.add_heading("SyncForge 1.0.0 — P0 Checklist", level=0)
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER

    p = doc.add_paragraph()
    p.add_run("Required before first stable release (1.0.0). ").bold = False
    run = p.add_run("P1 and P2 are not release blockers.")
    run.italic = True

    doc.add_paragraph("Date: 2 July 2026")
    doc.add_paragraph("Current baseline: 0.6.0-SNAPSHOT")
    doc.add_paragraph()

    doc.add_heading("Completed in 0.6.0", level=1)
    for item in COMPLETED_0_6:
        doc.add_paragraph(item, style="List Bullet")

    doc.add_heading("Completed in :sample (multi-entity proof)", level=1)
    for item in COMPLETED_SAMPLE:
        doc.add_paragraph(item, style="List Bullet")

    doc.add_heading("Completed — CI & release", level=1)
    for item in COMPLETED_CI:
        doc.add_paragraph(item, style="List Bullet")

    doc.add_paragraph()
    doc.add_heading("Release gate", level=1)
    doc.add_paragraph(
        "Tag 1.0.0 only when every item below is complete and verified. "
        "The Sample App Proof demonstrates multiple entities, DAOs, and navigation "
        "routes on one SyncManager instance."
    )

    for heading, items in SECTIONS:
        doc.add_heading(heading, level=2)
        for item in items:
            doc.add_paragraph(item, style="List Bullet")

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    doc.save(OUTPUT)
    print(f"Wrote {OUTPUT}")


if __name__ == "__main__":
    build()