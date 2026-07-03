#!/usr/bin/env bash
# Gradle maven-publish uploads to the OSSRH Staging API compatibility layer.
# A separate POST is required before deployments appear in Central Portal.
# https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/
set -euo pipefail

NAMESPACE="${1:-studio.syncforge}"
USERNAME="${MAVEN_CENTRAL_USERNAME:?MAVEN_CENTRAL_USERNAME is required}"
PASSWORD="${MAVEN_CENTRAL_PASSWORD:?MAVEN_CENTRAL_PASSWORD is required}"

AUTH="$(printf '%s:%s' "$USERNAME" "$PASSWORD" | base64 | tr -d '\n')"
BASE="https://ossrh-staging-api.central.sonatype.com"
DROP_INVALID_ON_FAILURE="${DROP_INVALID_ON_FAILURE:-true}"

auth_header() {
  printf 'Authorization: Bearer %s' "$AUTH"
}

finalize_current_ip_upload() {
  echo "Finalizing staging upload for namespace ${NAMESPACE} (current CI IP)..."
  if curl -sf -X POST -H "$(auth_header)" \
    "${BASE}/manual/upload/defaultRepository/${NAMESPACE}"; then
    echo
    return 0
  fi
  echo "No current-IP staging upload to finalize (ok before a fresh publish)."
}

promote_orphaned_uploads() {
  echo "Searching for orphaned staging repositories (any IP)..."
  DROP_INVALID="$DROP_INVALID_ON_FAILURE" \
  DROP_ALL_OPEN_STAGING="${DROP_ALL_OPEN_STAGING:-false}" \
  python3 - "$NAMESPACE" "$BASE" "$AUTH" <<'PY'
import json
import os
import sys
import urllib.error
import urllib.request

namespace, base, auth = sys.argv[1:4]
drop_invalid = os.environ.get("DROP_INVALID", "true").lower() == "true"
drop_all_open = os.environ.get("DROP_ALL_OPEN_STAGING", "false").lower() == "true"

def request(method, path):
    req = urllib.request.Request(
        f"{base}{path}",
        method=method,
        headers={"Authorization": f"Bearer {auth}"},
    )
    with urllib.request.urlopen(req) as resp:
        return resp.read().decode().strip()

search_url = f"/manual/search/repositories?ip=any&profile_id={namespace}"
data = json.loads(request("GET", search_url))
repos = data.get("repositories", [])
if not repos:
    print("No staging repositories found.")
    raise SystemExit(0)

promoted = dropped = skipped = failed = 0
for repo in repos:
    key = repo.get("key")
    state = repo.get("state")
    portal_id = repo.get("portal_deployment_id")
    print(f"  repo={key} state={state} portal_deployment_id={portal_id}")
    if drop_all_open and not portal_id and state == "open":
        try:
            drop_body = request("DELETE", f"/manual/drop/repository/{key}")
            print(f"  -> dropped open staging repo {key}: {drop_body}")
            dropped += 1
            continue
        except urllib.error.HTTPError as drop_err:
            drop_msg = drop_err.read().decode(errors="replace")
            print(f"  -> failed to drop open repo {key}: HTTP {drop_err.code} {drop_msg}")
            failed += 1
            continue
    if portal_id:
        skipped += 1
        continue
    try:
        body = request("POST", f"/manual/upload/repository/{key}")
        print(f"  -> promoted {key}: {body}")
        promoted += 1
    except urllib.error.HTTPError as err:
        body = err.read().decode(errors="replace")
        print(f"  -> failed to promote {key}: HTTP {err.code}")
        print(body[:2000])
        if drop_invalid and err.code in (400, 422):
            try:
                drop_body = request("DELETE", f"/manual/drop/repository/{key}")
                print(f"  -> dropped invalid staging repo {key}: {drop_body}")
                dropped += 1
                continue
            except urllib.error.HTTPError as drop_err:
                drop_msg = drop_err.read().decode(errors="replace")
                print(f"  -> failed to drop {key}: HTTP {drop_err.code} {drop_msg}")
        failed += 1

print(f"Summary: promoted={promoted} dropped={dropped} skipped={skipped} failed={failed}")
raise SystemExit(1 if failed else 0)
PY
}

if [[ "${FINALIZE_CURRENT_IP:-true}" == "true" ]]; then
  if [[ "${STRICT:-false}" == "true" ]]; then
    finalize_current_ip_upload
  else
    finalize_current_ip_upload || true
  fi
fi
if [[ "${STRICT:-false}" == "true" ]]; then
  promote_orphaned_uploads
else
  promote_orphaned_uploads || true
fi