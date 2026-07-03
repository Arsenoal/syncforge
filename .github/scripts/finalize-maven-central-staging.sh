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

auth_header() {
  printf 'Authorization: Bearer %s' "$AUTH"
}

finalize_current_ip_upload() {
  echo "Finalizing staging upload for namespace ${NAMESPACE} (current CI IP)..."
  curl -sf -X POST \
    -H "$(auth_header)" \
    "${BASE}/manual/upload/defaultRepository/${NAMESPACE}"
  echo
}

promote_orphaned_uploads() {
  echo "Searching for orphaned staging repositories (any IP)..."
  python3 - "$NAMESPACE" "$BASE" "$AUTH" <<'PY'
import base64
import json
import sys
import urllib.error
import urllib.request

namespace, base, auth = sys.argv[1:4]
url = f"{base}/manual/search/repositories?ip=any&profile_id={namespace}"
req = urllib.request.Request(url, headers={"Authorization": f"Bearer {auth}"})
with urllib.request.urlopen(req) as resp:
    data = json.load(resp)

repos = data.get("repositories", [])
if not repos:
    print("No staging repositories found.")
    raise SystemExit(0)

for repo in repos:
    key = repo.get("key")
    state = repo.get("state")
    portal_id = repo.get("portal_deployment_id")
    print(f"  repo={key} state={state} portal_deployment_id={portal_id}")
    if portal_id:
        continue
    upload_url = f"{base}/manual/upload/repository/{key}"
    upload_req = urllib.request.Request(
        upload_url,
        method="POST",
        headers={"Authorization": f"Bearer {auth}"},
    )
    try:
        with urllib.request.urlopen(upload_req) as upload_resp:
            print(f"  -> promoted {key}: {upload_resp.read().decode().strip()}")
    except urllib.error.HTTPError as err:
        body = err.read().decode(errors="replace")
        print(f"  -> failed to promote {key}: HTTP {err.code} {body}")
        raise
PY
}

if [[ "${FINALIZE_CURRENT_IP:-true}" == "true" ]]; then
  finalize_current_ip_upload
fi
promote_orphaned_uploads