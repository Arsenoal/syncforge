import { getFirestore, Firestore } from "firebase-admin/firestore";

const SYNC_ENTITY = "sync_entity";
const VERSION_COUNTER = "metadata/version_counter";

export interface OutboxEntryDto {
  id: number;
  entityType: string;
  entityId: string;
  changeType: "CREATE" | "UPDATE" | "DELETE";
  payloadJson: string | null;
  localVersion: number;
  createdAtMillis: number;
}

export interface PushRejectionDto {
  outboxId: number;
  code: string;
  message: string;
}

export interface PushResponse {
  acknowledgedIds: number[];
  rejected: PushRejectionDto[];
}

export interface RemoteDeltaDto {
  entityType: string;
  entityId: string;
  payloadJson: string | null;
  serverVersion: number;
  updatedAtMillis: number;
  isDeleted: boolean;
}

export interface PullResponse {
  deltas: RemoteDeltaDto[];
  serverTimestampMillis: number;
  hasMore: boolean;
  nextPageCursor: string | null;
}

interface EntityRecord {
  entityType: string;
  entityId: string;
  payloadJson: string | null;
  serverVersion: number;
  updatedAtMillis: number;
  isDeleted: boolean;
}

function documentId(entityType: string, entityId: string): string {
  return `${entityType}:${entityId}`;
}

async function allocateServerVersion(db: Firestore): Promise<number> {
  const counterRef = db.doc(VERSION_COUNTER);
  return db.runTransaction(async (tx) => {
    const snap = await tx.get(counterRef);
    const next = (snap.exists ? (snap.data()?.nextVersion ?? 0) : 0) + 1;
    tx.set(counterRef, { nextVersion: next }, { merge: true });
    return next;
  });
}

async function loadRecord(
  db: Firestore,
  entityType: string,
  entityId: string,
): Promise<EntityRecord | null> {
  const snap = await db.collection(SYNC_ENTITY).doc(documentId(entityType, entityId)).get();
  if (!snap.exists) return null;
  const data = snap.data()!;
  return {
    entityType: data.entityType as string,
    entityId: data.entityId as string,
    payloadJson: (data.payloadJson as string | null) ?? null,
    serverVersion: data.serverVersion as number,
    updatedAtMillis: data.updatedAtMillis as number,
    isDeleted: data.isDeleted as boolean,
  };
}

function encodeCursor(index: number): string {
  return Buffer.from(String(index), "utf8").toString("base64url");
}

function decodeCursor(cursor: string | null | undefined): number {
  if (!cursor || cursor.trim() === "") return 0;
  const parsed = Number(Buffer.from(cursor, "base64url").toString("utf8"));
  return Number.isFinite(parsed) ? parsed : 0;
}

export async function syncforgePush(
  entries: OutboxEntryDto[],
  nowMillis: number,
): Promise<PushResponse> {
  const db = getFirestore();
  const acknowledgedIds: number[] = [];
  const rejected: PushRejectionDto[] = [];

  for (const entry of entries) {
    if (!entry.entityType?.trim() || !entry.entityId?.trim()) {
      rejected.push({
        outboxId: entry.id,
        code: "VALIDATION",
        message: "entityType and entityId are required",
      });
      continue;
    }

    const existing = await loadRecord(db, entry.entityType, entry.entityId);

    if (
      existing?.isDeleted &&
      (entry.changeType === "CREATE" || entry.changeType === "UPDATE")
    ) {
      rejected.push({
        outboxId: entry.id,
        code: "CONFLICT",
        message: "Entity was deleted on the server",
      });
      continue;
    }

    if (
      entry.changeType === "UPDATE" &&
      existing != null &&
      existing.serverVersion !== entry.localVersion - 1
    ) {
      rejected.push({
        outboxId: entry.id,
        code: "CONFLICT",
        message: `Server version ${existing.serverVersion} does not match expected base ${entry.localVersion - 1}`,
      });
      continue;
    }

    const nextVersion = await allocateServerVersion(db);
    const isDelete = entry.changeType === "DELETE";
    const record: EntityRecord = {
      entityType: entry.entityType,
      entityId: entry.entityId,
      payloadJson: isDelete ? null : entry.payloadJson,
      serverVersion: nextVersion,
      updatedAtMillis: nowMillis,
      isDeleted: isDelete,
    };

    await db.collection(SYNC_ENTITY).doc(documentId(record.entityType, record.entityId)).set(record);
    acknowledgedIds.push(entry.id);
  }

  return { acknowledgedIds, rejected };
}

export async function syncforgePull(
  sinceMillis: number,
  entityTypes: string[] | null | undefined,
  pageLimit: number,
  pageCursor: string | null | undefined,
  nowMillis: number,
): Promise<PullResponse> {
  const db = getFirestore();
  const typeFilter = entityTypes?.filter((t) => t.trim() !== "") ?? [];

  let query = db
    .collection(SYNC_ENTITY)
    .where("updatedAtMillis", ">", sinceMillis)
    .orderBy("updatedAtMillis", "asc");

  const snapshot = await query.get();
  let allDeltas: RemoteDeltaDto[] = snapshot.docs
    .map((doc) => doc.data() as EntityRecord)
    .filter((record) => typeFilter.length === 0 || typeFilter.includes(record.entityType))
    .map((record) => ({
      entityType: record.entityType,
      entityId: record.entityId,
      payloadJson: record.payloadJson,
      serverVersion: record.serverVersion,
      updatedAtMillis: record.updatedAtMillis,
      isDeleted: record.isDeleted,
    }));

  const startIndex = Math.max(0, Math.min(decodeCursor(pageCursor), allDeltas.length));
  const endIndex = Math.min(startIndex + pageLimit, allDeltas.length);
  const page = allDeltas.slice(startIndex, endIndex);
  const hasMore = endIndex < allDeltas.length;

  return {
    deltas: page,
    serverTimestampMillis: nowMillis,
    hasMore,
    nextPageCursor: hasMore ? encodeCursor(endIndex) : null,
  };
}

export async function ensureVersionCounter(): Promise<void> {
  const db = getFirestore();
  const ref = db.doc(VERSION_COUNTER);
  const snap = await ref.get();
  if (!snap.exists) {
    await ref.set({ nextVersion: 1 });
  }
}