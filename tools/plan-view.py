"""Render a plan clan (findings, decisions, phases) as its human view.

    python3 tools/plan-view.py docs/ipad-plan.clan
    python3 tools/plan-view.py docs/ipad-plan.clan --print

For plans whose data has `findings`, `decisions` and `phases`, the shape
docs/ipad-plan.clan was created with. As a phase moves, patch its `status` and
run this; the page follows the data rather than being edited by hand.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from clan_page import apply, e, inline, load, markdown  # noqa: E402

clan_file = os.path.abspath(sys.argv[1])
work, context, data, manifest = load(clan_file)

findings = data.get("findings") or []
decisions = data.get("decisions") or []
phases = data.get("phases") or []
STATUS = {"open": "Open", "decided": "Decided", "not-started": "Not started", "in-progress": "In progress", "done": "Done"}


def finding(f):
    return (
        f"<li class='card decided'><div class='top'><span class='n'>{e(f['id'])}</span>"
        f"<span class='name'>{inline(f['fact'])}</span></div>"
        + (f"<p class='why'>{inline(f['consequence'])}</p>" if f.get("consequence") else "")
        + f"<p class='detail'>{inline(f.get('where', ''))}</p></li>"
    )


def decision(d):
    return (
        f"<li class='card {e(d['status'])}'><div class='top'><span class='n'>{e(d['id'])}</span>"
        f"<span class='name'>{inline(d['question'])}</span>"
        f"<span class='pill'>{STATUS.get(d['status'], e(d['status']))}</span></div>"
        f"<p class='claim'>{inline(d['recommendation'])}</p>"
        f"<p class='why'>{inline(d['why'])}</p></li>"
    )


def phase(p):
    work_items = "".join(f"<li>{inline(w)}</li>" for w in p.get("work") or [])
    return (
        f"<li class='card {e(p['status'])}'><div class='top'><span class='n'>{p['n']}</span>"
        f"<span class='name'>{inline(p['title'])}</span>"
        f"<span class='pill'>{e(p['size'])} · {STATUS.get(p['status'], e(p['status']))}</span></div>"
        f"<p class='claim'>{inline(p['goal'])}</p>"
        f"<ul class='work'>{work_items}</ul>"
        f"<p class='detail'>Done when: {inline(p['done_when'])}</p></li>"
    )


open_count = sum(1 for d in decisions if d["status"] == "open")
done_count = sum(1 for p in phases if p["status"] == "done")
apply(clan_file, work, manifest, [
    "<div class='wrap'>",
    "<header>",
    "<p class='eyebrow'>VITT · plan</p>",
    f"<h1>{e(manifest.get('title', 'Plan').split(' — ')[-1])}</h1>",
    f"<p class='lede'>{inline(data.get('summary', ''))}</p>",
    "<dl class='facts'>",
    f"<div class='fact'><dt>Phases done</dt><dd>{done_count} of {len(phases)}</dd></div>",
    f"<div class='fact'><dt>Open decisions</dt><dd>{open_count}</dd></div>",
    f"<div class='fact'><dt>Findings</dt><dd>{len(findings)}</dd></div>",
    "</dl>",
    "</header>",
    "<h2>Why, in prose</h2>",
    f"<div class='doc'>{markdown(context)}</div>",
    "<h2>What the code does today</h2>",
    "<ul>" + "".join(finding(f) for f in findings) + "</ul>",
    "<h2>Decisions</h2>",
    "<ul>" + "".join(decision(d) for d in decisions) + "</ul>",
    "<h2>Phases</h2>",
    "<ul>" + "".join(phase(p) for p in phases) + "</ul>",
    "<h2>Risks</h2>",
    "<ul class='plain'>" + "".join(f"<li>{inline(r)}</li>" for r in data.get("risks") or []) + "</ul>",
    "<h2>The store</h2>",
    "<ul class='plain'>" + "".join(f"<li>{inline(s)}</li>" for s in data.get("store") or []) + "</ul>",
    "</div>",
], to_stdout="--print" in sys.argv[2:])
