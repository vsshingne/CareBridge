"""Deploy a tested commit through an authorized Render hook; never print secrets."""
import json
import os
import re
import time
import urllib.error
import urllib.parse
import urllib.request


def main():
    hook = os.environ.get("RENDER_DEPLOY_HOOK", "")
    origin = os.environ.get("PRODUCTION_API_ORIGIN", "").rstrip("/")
    revision = os.environ.get("GITHUB_SHA", "")
    parsed = urllib.parse.urlsplit(hook)
    api = urllib.parse.urlsplit(origin)
    if parsed.scheme != "https" or parsed.hostname != "api.render.com" or parsed.username:
        raise SystemExit("Configure a valid HTTPS Render deploy hook secret.")
    if api.scheme != "https" or not api.hostname or api.username or api.query or api.fragment or api.path:
        raise SystemExit("Configure PRODUCTION_API_ORIGIN as an HTTPS origin without a path.")
    if not re.fullmatch(r"[0-9a-f]{40}", revision):
        raise SystemExit("A full tested Git commit SHA is required.")
    query = dict(urllib.parse.parse_qsl(parsed.query))
    query["ref"] = revision
    target = urllib.parse.urlunsplit(parsed._replace(query=urllib.parse.urlencode(query)))
    try:
        with urllib.request.urlopen(urllib.request.Request(target, method="POST"), timeout=30) as response:
            if response.status >= 300:
                raise SystemExit("Render did not accept the deployment request.")
    except (urllib.error.URLError, TimeoutError):
        raise SystemExit("Render deployment request failed. Inspect the provider dashboard.") from None
    print("Deployment requested for the tested commit. Waiting for readiness and revision.")
    deadline = time.monotonic() + 900
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(origin + "/actuator/health/readiness", timeout=10) as response:
                healthy = json.load(response).get("status") == "UP"
            with urllib.request.urlopen(origin + "/actuator/info", timeout=10) as response:
                deployed = json.load(response).get("app", {}).get("revision")
            if healthy and deployed == revision:
                print("The tested revision is deployed and readiness is UP.")
                return
        except (urllib.error.URLError, TimeoutError, ValueError):
            pass
        time.sleep(15)
    raise SystemExit("Deployment verification timed out. No success is claimed; inspect Render before retrying.")


if __name__ == "__main__":
    main()
