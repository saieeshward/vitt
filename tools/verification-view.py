"""Render docs/live-verification.clan's structured members as its human view.

    python3 tools/verification-view.py docs/live-verification.clan
    python3 tools/verification-view.py docs/live-verification.clan --print

The companion to clan-view.py, for the verification record rather than the
build state. The default `clan render` dumps the data as YAML and leaves out the
prose, which for this file is most of the value: the reasoning about run 1, run 4
and the -12.50 bug is what stops somebody repeating them. Run this after any
patch-data. The plumbing is shared with plan-view.py in clan_page.py.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from clan_page import apply, e, inline, load, markdown  # noqa: E402

clan_file = os.path.abspath(sys.argv[1])
work, context, data, manifest = load(clan_file)

checks = data.get("checks") or []
runs = data.get("runs") or []
last = next((r for r in reversed(runs) if r.get("date")), None)
LABEL = {"pass": "Pass", "fail": "Fail", "not-run": "Not run"}


def count(status):
    return sum(1 for c in checks if c.get("status") == status)


def check(c):
    status = c.get("status", "not-run")
    detail = " · ".join(str(x) for x in (c.get("last_detail"), c.get("last_run")) if x)
    return (
        f"<li class='card {e(status)}'>"
        f"<div class='top'><span class='n'>{c.get('n', '')}</span>"
        f"<code class='name'>{e(c.get('name', ''))}</code>"
        f"<span class='pill'>{LABEL.get(status, e(status))}</span></div>"
        f"<p class='claim'>{e(c.get('claim', ''))}</p>"
        + (f"<p class='why'>{e(c['why'])}</p>" if c.get("why") else "")
        + (f"<p class='detail'>{e(detail)}</p>" if detail else "")
        + "</li>"
    )


def run(r):
    where = "".join(f" · {e(str(r[k]))}" for k in ("date", "device") if r.get(k))
    return f"<li><span class='run'>Run {r.get('run')}</span>{where}<div class='why'>{e(r.get('outcome', ''))}</div></li>"


how = data.get("how_to_run") or {}
apply(clan_file, work, manifest, [
    "<div class='wrap'>",
    "<header>",
    "<p class='eyebrow'>VITT · against real Google</p>",
    "<h1>Live verification</h1>",
    f"<p class='lede'>{e(data.get('summary', ''))}</p>",
    "<dl class='facts'>",
    f"<div class='fact'><dt>Passed</dt><dd>{count('pass')} of {len(checks)}</dd></div>",
    f"<div class='fact'><dt>Not run</dt><dd>{count('not-run')}</dd></div>",
    f"<div class='fact'><dt>Failed</dt><dd>{count('fail')}</dd></div>",
    f"<div class='fact'><dt>Last run</dt><dd>{e(str(last['date'])) if last else 'None'}</dd></div>",
    "</dl>",
    "</header>",
    "<h2>Checks</h2>",
    "<ul>" + "".join(check(c) for c in checks) + "</ul>",
    "<h2>Runs</h2>",
    "<ul class='plain'>" + "".join(run(r) for r in reversed(runs)) + "</ul>",
    "<h2>Running it</h2>",
    f"<pre><code>{e(how.get('command', ''))}</code></pre>",
    f"<p class='why'>{inline(how.get('output', ''))}</p>",
    "<ul class='plain'>" + "".join(f"<li>{inline(t)}</li>" for t in how.get("conditions") or []) + "</ul>",
    "<h2>Not tested by any run</h2>",
    "<ul class='plain'>" + "".join(f"<li>{e(t)}</li>" for t in data.get("not_tested") or []) + "</ul>",
    "<h2>The record</h2>",
    f"<div class='doc'>{markdown(context)}</div>",
    "</div>",
], to_stdout="--print" in sys.argv[2:])
