import { initializeApp } from "firebase-admin/app";
import { onRequest } from "firebase-functions/v2/https";
import {
  ensureVersionCounter,
  syncforgePull as runSyncforgePull,
  syncforgePush as runSyncforgePush,
  OutboxEntryDto,
} from "./syncStore";

initializeApp();

interface PushRequestBody {
  entries: OutboxEntryDto[];
  now_millis?: number;
}

interface PullRequestBody {
  since_millis?: number;
  entity_types?: string[] | null;
  page_limit?: number;
  page_cursor?: string | null;
  now_millis?: number;
}

function resolveNowMillis(bodyValue: number | undefined): number {
  return bodyValue ?? Date.now();
}

function requirePost(
  req: { method?: string },
  res: { status: (code: number) => { send: (msg: string) => void } },
): boolean {
  if (req.method !== "POST") {
    res.status(405).send("Method Not Allowed");
    return false;
  }
  return true;
}

export const syncforgePush = onRequest({ cors: true }, async (req, res) => {
  if (!requirePost(req, res)) return;

  try {
    await ensureVersionCounter();
    const body = req.body as PushRequestBody;
    const entries = body.entries ?? [];
    const response = await runSyncforgePush(entries, resolveNowMillis(body.now_millis));
    res.status(200).json(response);
  } catch (error) {
    console.error("syncforgePush failed", error);
    res.status(500).json({ error: String(error) });
  }
});

export const syncforgePull = onRequest({ cors: true }, async (req, res) => {
  if (!requirePost(req, res)) return;

  try {
    const body = req.body as PullRequestBody;
    const response = await runSyncforgePull(
      body.since_millis ?? 0,
      body.entity_types,
      body.page_limit ?? Number.MAX_SAFE_INTEGER,
      body.page_cursor,
      resolveNowMillis(body.now_millis),
    );
    res.status(200).json(response);
  } catch (error) {
    console.error("syncforgePull failed", error);
    res.status(500).json({ error: String(error) });
  }
});