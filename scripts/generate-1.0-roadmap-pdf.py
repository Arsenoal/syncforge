#!/usr/bin/env python3
"""Generate SyncForge 1.0 roadmap PDF."""

from __future__ import annotations

from pathlib import Path

from fpdf import FPDF

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "docs" / "SyncForge-1.0-Roadmap.pdf"


class RoadmapPDF(FPDF):
    def header(self) -> None:
        self.set_font("Helvetica", "B", 10)
        self.set_text_color(90, 90, 90)
        self.cell(0, 8, "SyncForge - Roadmap to 1.0", align="R", new_x="LMARGIN", new_y="NEXT")
        self.ln(2)

    def footer(self) -> None:
        self.set_y(-15)
        self.set_font("Helvetica", "I", 8)
        self.set_text_color(120, 120, 120)
        self.cell(0, 10, f"Page {self.page_no()}/{{nb}}", align="C")

    def title_page(self) -> None:
        self.add_page()
        self.set_font("Helvetica", "B", 24)
        self.set_text_color(20, 40, 80)
        self.ln(40)
        self.multi_cell(0, 12, "SyncForge 1.0\nRoadmap & Job List", align="C")
        self.ln(8)
        self.set_font("Helvetica", "", 12)
        self.set_text_color(60, 60, 60)
        self.multi_cell(
            0,
            7,
            "All work required from the current 0.6.0-SNAPSHOT state\n"
            "through the first stable public release (1.0.0).\n\n"
            "Includes the Sample App Proof: multi-screen, multi-DAO integration.",
            align="C",
        )
        self.ln(20)
        self.set_font("Helvetica", "I", 10)
        self.cell(0, 8, "Document date: 30 June 2026", align="C", new_x="LMARGIN", new_y="NEXT")
        self.cell(0, 8, "Target stable version: 1.0.0", align="C")

    def h1(self, text: str) -> None:
        self.ln(4)
        self.set_font("Helvetica", "B", 16)
        self.set_text_color(20, 40, 80)
        self.multi_cell(0, 9, text)
        self.ln(2)

    def h2(self, text: str) -> None:
        self.ln(3)
        self.set_font("Helvetica", "B", 12)
        self.set_text_color(30, 70, 120)
        self.multi_cell(0, 7, text)
        self.ln(1)

    def h3(self, text: str) -> None:
        self.ln(2)
        self.set_x(self.l_margin)
        self.set_font("Helvetica", "B", 10)
        self.set_text_color(40, 40, 40)
        self.multi_cell(0, 6, text)

    def body(self, text: str) -> None:
        self.set_x(self.l_margin)
        self.set_font("Helvetica", "", 10)
        self.set_text_color(30, 30, 30)
        self.multi_cell(0, 5.5, text)
        self.ln(1)

    def bullet(self, text: str, indent: int = 0) -> None:
        x = self.l_margin + indent
        self.set_x(x)
        self.set_font("Helvetica", "", 10)
        self.set_text_color(30, 30, 30)
        width = self.w - self.l_margin - self.r_margin - indent
        self.multi_cell(width, 5.5, f"  -  {text}")

    def job_item(self, priority: str, job: str, milestone: str, owner: str = "Core") -> None:
        if self.get_y() > 265:
            self.add_page()
        self.set_x(self.l_margin)
        self.set_font("Helvetica", "B", 9)
        self.set_text_color(30, 30, 30)
        self.multi_cell(0, 5, f"[{priority}] {job}")
        self.set_x(self.l_margin)
        self.set_font("Helvetica", "I", 8)
        self.set_text_color(80, 80, 80)
        self.multi_cell(0, 4, f"Milestone: {milestone}  |  Area: {owner}")
        self.ln(1)


def build_pdf() -> None:
    pdf = RoadmapPDF()
    pdf.alias_nb_pages()
    pdf.set_auto_page_break(auto=True, margin=18)
    pdf.title_page()

    # 1. Executive summary
    pdf.add_page()
    pdf.h1("1. Executive summary")
    pdf.body(
        "SyncForge is a Kotlin Multiplatform offline-first sync library. Android is production-ready; "
        "iOS, desktop, and macOS targets compile and run but need hardening. Version 0.6.0-SNAPSHOT "
        "introduced SQLDelight as the Android default, automatic Room-to-SQLDelight migration, API "
        "stability annotations, Maven publishing infrastructure, and initial E2E tests."
    )
    pdf.body(
        "Version 1.0.0 is defined as: semver-stable public API, Maven Central artifacts, documented "
        "upgrade paths, CI confidence, and a Sample App Proof demonstrating multiple entities, DAOs, "
        "navigation routes, and per-entity conflict policies on a single SyncManager instance."
    )

    # 2. Done
    pdf.h1("2. What is already done")
    done_items = [
        "Core sync: outbox, optimistic writes, push/pull/sync, pagination, cursor, retry/backoff",
        "Conflict engine: ConflictPolicy, LWW, merge, defer-to-user, resolveConflict(), persisted conflicts",
        "Android DSL: SyncForge.android { }, WorkManager, Compose status + conflict UI, debug console",
        "KSP codegen: @SyncForgeEntity / @SyncForgeDao -> handlers + SyncForgeHandlers registry",
        "KMP platforms: ios, desktop, macos DSLs; SQLDelight persistence module",
        "Android 0.6.0: SQLDelight default, RoomToSqlDelightMigrator, useRoomPersistence() legacy opt-in",
        "Transport: KtorSyncTransport, REST DTOs, mock-server, bearer auth provider",
        "Distribution: Apache 2.0, BOM, publish workflows, @ExperimentalSyncForgeApi stability model",
        "Tests: JVM unit tests, Robolectric, migrator test, UiAutomator E2E (3 flows, mock-server)",
        "Docs: GETTING_STARTED, MODULES, REST_API, platform setup guides, MODULES stability section",
    ]
    for item in done_items:
        pdf.bullet(item)

    # 3. Sample App Proof
    pdf.add_page()
    pdf.h1("3. Sample App Proof (mandatory for 1.0)")
    pdf.body(
        "The :sample Android app must prove that SyncForge handles multiple entity types, multiple "
        "Room DAOs, and multiple navigation routes within one application - not a single-task demo."
    )
    pdf.h2("3.1 Proof requirements")
    proof_reqs = [
        "At least two distinct @SyncForgeEntity types (e.g. tasks + notes, or tasks + projects).",
        "Each entity has its own Room @Dao and TypedEntitySyncHandler (KSP-generated).",
        "EntityRegistry registers all handlers: SyncForgeHandlers.registry(taskDao, noteDao, ...).",
        "Navigation with separate screens/routes per entity (Compose Navigation or equivalent).",
        "Single SyncManager instance shared across screens (Application-scoped).",
        "Per-entity conflict policy in conflicts { } block (e.g. tasks deferToUser, notes lastWriteWins).",
        "Each screen: create/edit/delete, sync status observation, entity-specific conflict UI where applicable.",
        "E2E tests: at least one flow per entity type + one cross-entity sync status test.",
        "iOS sample (:sample-ios-shared) should mirror two entity types for KMP proof (stretch: 1.0-rc).",
    ]
    for r in proof_reqs:
        pdf.bullet(r)

    pdf.h2("3.2 Suggested sample structure")
    pdf.body("Target package layout:")
    pdf.bullet("dev.syncforge.sample.task - TaskEntity, TaskDao, TasksScreen, TasksViewModel (exists)")
    pdf.bullet("dev.syncforge.sample.note - NoteEntity, NoteDao, NotesScreen, NotesViewModel (new)")
    pdf.bullet("dev.syncforge.sample.navigation - AppNavHost, bottom nav or drawer: Tasks | Notes")
    pdf.bullet("SampleApplication - one SyncManager, registry with both handlers")
    pdf.bullet("SyncForgeHandlers.registry(taskDao, noteDao) - KSP generates combined registry")

    pdf.h2("3.3 Acceptance criteria (Proof)")
    proof_accept = [
        "User can add a task on Tasks screen and a note on Notes screen without app restart.",
        "Sync pushes both entity types in one sync() cycle (shared outbox).",
        "Conflict on tasks does not block notes sync (independent entity types).",
        "Debug panel shows outbox entries for both entity types.",
        "E2E: create task + note -> sync -> verify both on mock-server pull.",
    ]
    for a in proof_accept:
        pdf.bullet(a)

    # 4. Milestones
    pdf.add_page()
    pdf.h1("4. Release milestones to 1.0")
    milestones = [
        ("0.6.0", "Stable SQLDelight Android default", "Docs sync, remove internal Room code, Maven publish 0.6.0, E2E in CI nightly"),
        ("0.7.0", "Platform hardening", "iOS BGTaskScheduler background sync, token refresh on 401, expand integration tests"),
        ("0.8.0", "Sample App Proof + API graduation", "Multi-entity sample app, graduate stable APIs, finalize deprecations"),
        ("0.9.0", "Release candidate", "RC publish, iOS device testing, migration edge cases, starter backend kit"),
        ("1.0.0", "First stable", "Semver guarantee, remove deprecated APIs, public Maven Central, announcement"),
    ]
    for ver, name, scope in milestones:
        pdf.h3(f"{ver} - {name}")
        pdf.body(scope)

    # 5. Job list P0
    pdf.add_page()
    pdf.h1("5. Complete job list")
    pdf.h2("5.1 P0 - Must have before 1.0")

    p0_jobs = [
        ("P0", "Sample App Proof: second entity (Note/Project) + DAO + KSP handler", "0.8.0", "Sample"),
        ("P0", "Sample: Compose Navigation - separate screens/routes per entity", "0.8.0", "Sample"),
        ("P0", "Sample: single SyncManager + multi-handler EntityRegistry", "0.8.0", "Sample"),
        ("P0", "Sample: per-entity conflicts { } policies demonstrated", "0.8.0", "Sample"),
        ("P0", "E2E tests for multi-entity flows (task + note)", "0.8.0", "QA"),
        ("P0", "Remove internal Room outbox/conflict from :syncforge (keep useRoomPersistence window)", "0.6.0", "Android"),
        ("P0", "Sync README, ROADMAP, KMP_MIGRATION to 0.6.0+ reality", "0.6.0", "Docs"),
        ("P0", "Publish 0.6.0 to Maven Central (verified consumer Gradle snippet)", "0.6.0", "Dist"),
        ("P0", "iOS background sync (BGTaskScheduler / BGAppRefreshTask hook)", "0.7.0", "iOS"),
        ("P0", "Token refresh on 401 (RefreshingSyncAuthProvider or documented pattern)", "0.7.0", "Network"),
        ("P0", "Graduate SyncForge.android + core SyncManager APIs as stable (no annotation)", "0.8.0", "API"),
        ("P0", "Remove ConflictResolver, LastWriteWinsResolver, fromResolver at 1.0", "1.0.0", "API"),
        ("P0", "CI: add androidE2e to nightly or main (emulator + mock-server)", "0.7.0", "CI"),
        ("P0", "macOS release workflow: verify iOS/macOS framework publish on tag", "0.7.0", "CI"),
        ("P0", "Migrator hardening: partial failure, logging, large outbox tests", "0.7.0", "Android"),
        ("P0", "REST_API.md versioning + breaking-change policy", "0.9.0", "Docs"),
        ("P0", "Minimum Ktor reference backend starter (beyond mock-server)", "0.9.0", "Ecosystem"),
        ("P0", "1.0 acceptance test matrix sign-off (see Section 6)", "1.0.0", "QA"),
    ]
    for job in p0_jobs:
        pdf.job_item(*job)

    # P1
    pdf.add_page()
    pdf.h2("5.2 P1 - High importance (strongly recommended)")
    p1_jobs = [
        ("P1", "Graduate or document iOS/desktop/macos DSL stability (experimental until proven)", "0.9.0", "API"),
        ("P1", "iOS sample: two entity types in IosSampleController", "0.9.0", "Sample"),
        ("P1", "Desktop sample app (:sample-desktop) minimal CLI or Compose", "0.9.0", "Desktop"),
        ("P1", "DataStore Preferences cursor (KMP) - replace SP/UserDefaults", "1.0.x", "Persistence"),
        ("P1", "Conflict resolution recipes for 3+ entity types in RECIPES.md", "0.8.0", "Docs"),
        ("P1", "Upgrade guide: pre-0.6 Room users -> 0.6+ SQLDelight", "0.6.0", "Docs"),
        ("P1", "Integration tests: retry exhaustion, multi-page pull, offline queue", "0.7.0", "QA"),
        ("P1", "SKIE Swift API review + sample SwiftUI multi-screen", "0.9.0", "iOS"),
        ("P1", "Consumer ProGuard/R8 rules documented and tested", "0.8.0", "Android"),
        ("P1", "GitHub issue templates + CONTRIBUTING.md", "0.8.0", "Community"),
        ("P1", "Security: TLS, token storage guidance, no secrets in logs", "0.9.0", "Docs"),
        ("P1", "Performance test: 1000+ outbox entries, batch push", "0.9.0", "QA"),
    ]
    for job in p1_jobs:
        pdf.job_item(*job)

    # P2
    pdf.h2("5.3 P2 - Nice to have (post-1.0 or 1.0 if time permits)")
    p2_jobs = [
        ("P2", "Shake-to-open SyncDebugLauncher", "1.x", "DX"),
        ("P2", "Compose Multiplatform conflict/debug UI for iOS", "1.x", "UI"),
        ("P2", "Supabase transport adapter", "1.x", "Ecosystem"),
        ("P2", "Spring Boot backend starter", "1.x", "Ecosystem"),
        ("P2", "Gradle version catalog for consumers (in addition to BOM)", "1.x", "Dist"),
        ("P2", "Multi-device E2E (two emulators, concurrent edit)", "1.x", "QA"),
        ("P2", "CRDT / field-merge code generation via KSP", "1.x", "Conflict"),
        ("P2", "Structured tracing / OpenTelemetry hooks", "1.x", "Observability"),
        ("P2", "Packaged Swift Package Manager binary for iOS", "1.x", "iOS"),
        ("P2", "Full SyncHealth metrics dashboard UI", "1.x", "DX"),
    ]
    for job in p2_jobs:
        pdf.job_item(*job)

    # 6. Acceptance criteria
    pdf.add_page()
    pdf.h1("6. 1.0.0 acceptance criteria (sign-off checklist)")
    criteria = [
        "All P0 jobs completed and verified.",
        "Sample App Proof: two+ entities, two+ DAOs, two+ screens, shared SyncManager - E2E green.",
        "No @ExperimentalSyncForgeApi on SyncForge.android, SyncManager core, ConflictPolicy, Compose status UI.",
        "Deprecated APIs removed (ConflictResolver family, useSqlDelightPersistence, internal Room).",
        "Maven Central: syncforge, annotations, ksp, persistence, bom - all at 1.0.0.",
        "CI green: compile (Android+JVM), jvmTest, testDebugUnitTest, androidE2e (nightly minimum).",
        "macOS tag publish produces iOS/macOS frameworks without manual steps.",
        "CHANGELOG, MODULES, GETTING_STARTED reflect 1.0 APIs accurately.",
        "At least one external dogfood or documented third-party integration attempt.",
        "Migration tested: 0.4 Room -> 0.6 SQLDelight -> 1.0 on sample upgrade path.",
    ]
    for i, c in enumerate(criteria, 1):
        pdf.bullet(f"{i}. {c}")

    # 7. Risk register
    pdf.h1("7. Risk register")
    risks = [
        ("iOS background sync complexity", "High", "Ship BGTaskScheduler MVP; document foreground-only fallback"),
        ("API surface too large at 1.0", "Medium", "Keep iOS/desktop experimental; stable = Android + common contracts"),
        ("Room removal breaks legacy adopters", "Medium", "Keep useRoomPersistence() one release past 1.0 if needed"),
        ("E2E flaky in CI", "Medium", "UiAutomator + mock-server health gate; retries; nightly not blocking PR"),
        ("Maven Central signing/macOS publish", "Low", "Already wired; dry-run on 0.9.0-rc"),
    ]
    for risk, sev, mit in risks:
        pdf.h3(f"{risk} [{sev}]")
        pdf.body(f"Mitigation: {mit}")

    # 8. Timeline suggestion
    pdf.h1("8. Suggested timeline (indicative)")
    pdf.body("Assuming part-time maintenance or small team:")
    pdf.bullet("0.6.0 stable - 2-4 weeks: docs, Room removal, Maven publish, CI E2E nightly")
    pdf.bullet("0.7.0 - 4-6 weeks: iOS background sync, auth refresh, integration tests")
    pdf.bullet("0.8.0 - 4-6 weeks: Sample App Proof (multi-entity), API graduation")
    pdf.bullet("0.9.0-rc - 3-4 weeks: RC testing, starter backend, iOS device validation")
    pdf.bullet("1.0.0 - 2 weeks: final API freeze, deprecation removal, announcement")
    pdf.body("Total indicative: 15-22 weeks to 1.0.0 from 0.6.0-SNAPSHOT.")

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    pdf.output(str(OUTPUT))
    print(f"Wrote {OUTPUT}")


if __name__ == "__main__":
    build_pdf()