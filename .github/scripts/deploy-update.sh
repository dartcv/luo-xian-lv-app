set -euo pipefail

key="$RUNNER_TEMP/deploy_key"
known_hosts="$RUNNER_TEMP/deploy_known_hosts"
trap 'rm -f "$key" "$known_hosts"' EXIT
umask 077
printf '%s\n' "$SERVER_SSH_KEY" | tr -d '\r' > "$key"
printf '%s\n' "$SERVER_KNOWN_HOSTS" > "$known_hosts"
ssh_args=(-i "$key" -o "UserKnownHostsFile=$known_hosts" -o StrictHostKeyChecking=yes -o BatchMode=yes -o ConnectTimeout=15)
remote="/app/public/updates/.stable-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}.json"
scp -q -P "$SERVER_PORT" "${ssh_args[@]}" dist/stable.json "root@$SERVER_HOST:$remote"
ssh -p "$SERVER_PORT" "${ssh_args[@]}" "root@$SERVER_HOST" "python3 - '$remote'" <<'PY'
import json
from pathlib import Path
import shutil
import sys

incoming = Path(sys.argv[1])
current = incoming.parent / "stable.json"
try:
    new = json.loads(incoming.read_text())
    if current.exists():
        old = json.loads(current.read_text())
        if old.get("latestVersionCode", 0) > new["latestVersionCode"]:
            raise ValueError("Refusing to downgrade stable")
        shutil.copy2(current, current.with_suffix(".previous.json"))
    incoming.chmod(0o644)
    incoming.replace(current)
    print("Stable manifest deployed atomically")
finally:
    incoming.unlink(missing_ok=True)
PY
printf 'GitHub and OSS published; stable manifest updated.\n' >> "$GITHUB_STEP_SUMMARY"
