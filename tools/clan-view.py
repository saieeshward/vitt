"""Render a .clan file's structured members as its human view.

    python3 tools/clan-view.py vitt.clan          # render and apply, in place
    python3 tools/clan-view.py vitt.clan --print  # just the HTML, to stdout

Kept as a script rather than hand-written HTML because the view has to stay
true to `shared/data.yaml`: a status page that drifts from the data it claims
to show is worse than no page. Run it after any `clan patch-data`, and the two
can never disagree.

It applies its own patch rather than leaving an HTML file for somebody to pass
to `clan patch-html`, because a two-step regeneration is one somebody skips.
The patch replaces <body> whole and carries no decision of its own: rendering
is not a change of state, and a chain entry per render would bury the entries
that are.
"""
import html
import os
import subprocess
import sys
import tempfile
import yaml
import zipfile

clan_file = os.path.abspath(sys.argv[1])
to_stdout = "--print" in sys.argv[2:]

work = tempfile.mkdtemp(prefix="clan-view-")
with zipfile.ZipFile(clan_file) as z:
    z.extractall(work)

data = yaml.safe_load(open(f"{work}/shared/data.yaml"))
chain = yaml.safe_load(open(f"{work}/agent/decision-chain.yaml"))["decisions"]
manifest = yaml.safe_load(open(f"{work}/manifest.yaml"))

e = html.escape

# The six currency hues, in assignment order, straight from ThemeTokens.
HUES = ["#2D9D75", "#BB7F0F", "#2C8CF6", "#EB558B", "#6B5BFF", "#169B9B"]
HUES_DARK = ["#5EE0AC", "#FFC94D", "#78BBFF", "#D95C88", "#9C8DFF", "#4FD1D1"]

phase = data["phase"]
# The phase line is written as "stage — detail". Split so the stage can lead.
stage, _, detail = phase.partition(" — ")
# The phase line is written for a YAML reader, so its detail half starts
# lowercase. As a standfirst under the title it wants a capital.
detail = detail[:1].upper() + detail[1:]

RECENT = 11  # the entries from the latest session, shown open


def items(key):
    return list(data.get(key) or [])


def li(text, index=None):
    """One row, optionally carrying a currency hue as its marker.

    Both hues are set inline and the dark one is selected in CSS, rather than
    picking here: the page has no idea which side it will be read on, and the
    two sets are not interchangeable. The dark hues are the light ones lifted,
    because a stroke chosen against cream disappears against Slate.
    """
    if index is None:
        return f"<li>{e(text)}</li>"
    light = HUES[index % len(HUES)]
    dark = HUES_DARK[index % len(HUES_DARK)]
    return f'<li style="--hue:{light};--hue-dark:{dark}">{e(text)}</li>'


done = items("done")
recent, earlier = done[-RECENT:][::-1], done[:-RECENT][::-1]

decisions = chain[:12]

css = """
:root {
  --ground: #FFFDF7; --surface: #F2EFE7; --ink: #241F33; --muted: #6A6480;
  --faint: #8A85A0; --accent: #6B5BFF; --wash: #F3F0FF; --line: #E5E1D8;
}
@media (prefers-color-scheme: dark) {
  :root {
    --ground: #17141F; --surface: #241F33; --ink: #DAD5E6; --muted: #A7A1BC;
    --faint: #6F6886; --accent: #9C8DFF; --wash: #1E1A2B; --line: #2B2539;
  }
  ul.rows li[style] { border-left-color: var(--hue-dark); }
  ul.plain li[style]::before { background: var(--hue-dark); }
}
* { box-sizing: border-box; }
body {
  background: var(--ground); color: var(--ink);
  font: 16px/1.6 ui-sans-serif, -apple-system, "Segoe UI", system-ui, sans-serif;
  margin: 0; padding: 0 16px 96px;
  -webkit-font-smoothing: antialiased;
}
.wrap { max-width: 760px; margin: 0 auto; }
header { padding: 72px 0 8px; }
.eyebrow {
  font-size: 12px; letter-spacing: .14em; text-transform: uppercase;
  color: var(--muted); margin: 0 0 12px;
}
h1 { font-size: 44px; line-height: 1.05; letter-spacing: -.02em; margin: 0 0 20px; }
.stage {
  display: inline-block; background: var(--wash); color: var(--accent);
  border-radius: 999px; padding: 6px 14px; font-size: 13px; font-weight: 600;
}
.lede { color: var(--muted); font-size: 18px; margin: 18px 0 0; max-width: 60ch; }
.facts {
  display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px; margin: 32px 0 0;
}
.fact {
  background: var(--surface); border-radius: 14px; padding: 14px 16px;
}
.fact dt {
  font-size: 11px; letter-spacing: .1em; text-transform: uppercase;
  color: var(--muted); margin: 0 0 6px;
}
.fact dd { margin: 0; font-size: 22px; font-weight: 600; font-variant-numeric: tabular-nums; }
.fact dd small { display: block; font-size: 13px; font-weight: 400; color: var(--muted); margin-top: 2px; }
h2 {
  font-size: 12px; letter-spacing: .14em; text-transform: uppercase;
  color: var(--muted); margin: 56px 0 4px; font-weight: 600;
}
h2 + p.note { color: var(--faint); font-size: 14px; margin: 0 0 18px; }
ul { list-style: none; padding: 0; margin: 0; }
ul.rows li {
  border-left: 3px solid var(--hue, var(--accent));
  background: var(--surface);
  border-radius: 0 12px 12px 0;
  padding: 14px 18px; margin: 0 0 10px;
}
ul.plain li {
  padding: 12px 0 12px 26px; border-bottom: 1px solid var(--line);
  position: relative;
}
ul.plain li::before {
  content: ""; position: absolute; left: 4px; top: 21px;
  width: 8px; height: 8px; border-radius: 50%; background: var(--hue, var(--accent));
}
ul.plain li:last-child { border-bottom: 0; }
.blocked li::before { background: #BB7F0F; }
details { margin-top: 12px; }
summary {
  cursor: pointer; color: var(--accent); font-size: 14px; font-weight: 600;
  padding: 10px 0; list-style: none;
}
summary::-webkit-details-marker { display: none; }
summary::before { content: "▸ "; }
details[open] summary::before { content: "▾ "; }
.decisions li {
  padding: 14px 0; border-bottom: 1px solid var(--line);
}
.decisions .act { font-weight: 600; }
.decisions .why { color: var(--muted); font-size: 14px; margin-top: 4px; }
.decisions .when { color: var(--faint); font-size: 12px; font-variant-numeric: tabular-nums; }
.pin { color: var(--accent); font-size: 12px; font-weight: 600; }
footer {
  margin-top: 72px; padding-top: 20px; border-top: 1px solid var(--line);
  color: var(--faint); font-size: 13px;
}
footer code { font-size: 12px; }
@media (max-width: 520px) { h1 { font-size: 34px; } header { padding-top: 48px; } }
"""

out = []
out.append('<div class="wrap">')
out.append("<header>")
out.append('<p class="eyebrow">CLAN · project state</p>')
out.append(f'<h1 data-adf-id="render-title">{e(manifest.get("title", "VITT"))}</h1>')
out.append(f'<span class="stage">{e(stage)}</span>')
if detail:
    out.append(f'<p class="lede">{e(detail)}</p>')

out.append('<dl class="facts">')
out.append(
    f'<div class="fact"><dt>Tests</dt><dd>639<small>{e(data["tests"].split(";")[0])}</small></dd></div>'
)
out.append(
    f'<div class="fact"><dt>Shipped</dt><dd>{len(done)}<small>logged with reasoning</small></dd></div>'
)
out.append(
    f'<div class="fact"><dt>Next up</dt><dd>{len(items("next"))}<small>queued</small></dd></div>'
)
out.append(
    f'<div class="fact"><dt>On you</dt><dd>{len(items("blocked_on_maintainer"))}'
    "<small>cannot be automated</small></dd></div>"
)
out.append("</dl>")
out.append("</header>")

out.append("<h2>In progress</h2>")
out.append('<ul class="rows">')
for i, t in enumerate(items("in_progress")):
    out.append(li(t, i))
out.append("</ul>")

out.append("<h2>Next</h2>")
out.append('<p class="note">In the order it is worth doing.</p>')
out.append('<ul class="plain">')
for t in items("next"):
    out.append(li(t))
out.append("</ul>")

out.append("<h2>Blocked on the maintainer</h2>")
out.append('<p class="note">Needs a human with an account, a card or a domain. Nothing here can be automated.</p>')
out.append('<ul class="plain blocked">')
for t in items("blocked_on_maintainer"):
    out.append(li(t))
out.append("</ul>")

out.append("<h2>Latest session</h2>")
out.append('<p class="note">Newest first.</p>')
out.append('<ul class="rows">')
for i, t in enumerate(recent):
    out.append(li(t, i))
out.append("</ul>")

out.append("<details>")
out.append(f"<summary>Everything before that · {len(earlier)} entries</summary>")
out.append('<ul class="plain">')
for t in earlier:
    out.append(li(t))
out.append("</ul>")
out.append("</details>")

out.append("<h2>Decision chain</h2>")
out.append(f'<p class="note">The twelve most recent of {len(chain)}. Attribution is enforced at write time.</p>')
out.append('<ul class="decisions">')
for d in decisions:
    when = str(d.get("timestamp", ""))[:10]
    pin = ' <span class="pin">PINNED</span>' if d.get("pinned") else ""
    out.append("<li>")
    out.append(f'<div class="act">{e(str(d.get("action", "")))}{pin}</div>')
    if d.get("rationale"):
        out.append(f'<div class="why">{e(str(d["rationale"]))}</div>')
    out.append(f'<div class="when">{e(str(d.get("agent", "")))} · {e(when)}</div>')
    out.append("</li>")
out.append("</ul>")
out.append("<details>")
out.append(f"<summary>The rest of the chain · {len(chain) - len(decisions)} entries</summary>")
out.append('<ul class="decisions">')
for d in chain[len(decisions):]:
    when = str(d.get("timestamp", ""))[:10]
    out.append(
        f'<li><div class="act">{e(str(d.get("action", "")))}</div>'
        f'<div class="when">{e(str(d.get("agent", "")))} · {e(when)}</div></li>'
    )
out.append("</ul></details>")

out.append(
    "<footer>Rendered from <code>shared/data.yaml</code> and "
    "<code>agent/decision-chain.yaml</code>. The structured members are the "
    "source; this page is a view of them. Regenerate with "
    "<code>tools/clan-view.py</code>.</footer>"
)
out.append("</div>")

page = "\n".join(
    ["---", "mode: patch-html", "---", "<body>", f"<style>{css}</style>"]
    + out
    + ["</body>"]
)

if to_stdout:
    print(page)
    raise SystemExit

patch = os.path.join(work, "view.html")
with open(patch, "w") as f:
    f.write(page)

# The head is replaced too, and every time: the default render writes its own
# <style> there, so a body-only patch leaves two stylesheets fighting and the
# older one wins wherever this file does not happen to override it.
head = os.path.join(work, "head.html")
with open(head, "w") as f:
    f.write(
        "---\nmode: patch-html\n---\n"
        "<head>\n"
        '<meta charset="utf-8">\n'
        '<meta name="viewport" content="width=device-width, initial-scale=1">\n'
        f"<title>{e(manifest.get('title', 'CLAN'))}</title>\n"
        "</head>\n"
    )

for fragment, selector in ((head, "head"), (patch, "body")):
    subprocess.run(
        [
            "clan", "patch-html", clan_file, fragment,
            "--selector", selector, "--patch-action", "replace",
            "--no-decision", "--quiet",
        ],
        check=True,
        stdout=subprocess.DEVNULL,
    )
print(f"rendered {os.path.basename(clan_file)} from its structured members")
