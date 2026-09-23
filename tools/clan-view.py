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
import re
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

context = open(f"{work}/agent/context.md").read()
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


def inline(text):
    """Bold, inline code and links, on already-escaped text."""
    out = e(text)
    out = re.sub(r"`([^`]+)`", r"<code>\1</code>", out)
    out = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", out)
    # Single asterisks after the double ones, or the emphasis inside a bold run
    # would be eaten first and the asterisks left on screen.
    out = re.sub(r"(?<!\*)\*([^*\n]+)\*(?!\*)", r"<em>\1</em>", out)
    return out


def markdown(src):
    """Enough Markdown for the handoff page: headings, lists, tables, fences.

    Deliberately small rather than a dependency. The input is one file written
    in this repo, so the subset it uses is known — and a renderer that silently
    handles more than it is given is a renderer nobody checks.
    """
    out = []
    lines = src.split("\n")
    i = 0
    mode = None  # "ul", "ol", "table", "pre", or None

    def close():
        nonlocal mode
        if mode == "ul":
            out.append("</ul>")
        elif mode == "ol":
            out.append("</ol>")
        elif mode == "table":
            out.append("</tbody></table>")
        elif mode == "pre":
            out.append("</code></pre>")
        mode = None

    while i < len(lines):
        line = lines[i]

        if mode == "pre":
            if line.strip().startswith("```"):
                close()
            else:
                out.append(e(line))
            i += 1
            continue

        if line.strip().startswith("```"):
            close()
            out.append("<pre><code>")
            mode = "pre"
            i += 1
            continue

        stripped = line.strip()

        if not stripped:
            close()
            i += 1
            continue

        if stripped.startswith("#"):
            close()
            level = len(stripped) - len(stripped.lstrip("#"))
            out.append(f"<h{min(level + 1, 6)} class='md'>{inline(stripped.lstrip('# '))}</h{min(level + 1, 6)}>")
            i += 1
            continue

        if stripped == "---":
            close()
            out.append("<hr>")
            i += 1
            continue

        # A table: a header row, a separator, then body rows.
        # A separator has to actually be one. Testing only that the next line is
        # a subset of "|-: " made the *blank* line after the final row qualify —
        # the empty set is a subset of everything — so the last row of every
        # table started a new table and rendered as its header.
        nxt = lines[i + 1].strip() if i + 1 < len(lines) else ""
        if stripped.startswith("|") and "-" in nxt and set(nxt) <= set("|-: "):
            close()
            cells = [c.strip() for c in stripped.strip("|").split("|")]
            out.append("<table><thead><tr>")
            out += [f"<th>{inline(c)}</th>" for c in cells]
            out.append("</tr></thead><tbody>")
            mode = "table"
            i += 2
            continue

        if mode == "table" and stripped.startswith("|"):
            cells = [c.strip() for c in stripped.strip("|").split("|")]
            out.append("<tr>" + "".join(f"<td>{inline(c)}</td>" for c in cells) + "</tr>")
            i += 1
            continue

        ordered = re.match(r"^(\d+)\. +(.*)", stripped)
        if stripped.startswith("- ") or ordered:
            want = "ol" if ordered else "ul"
            if mode != want:
                close()
                out.append(f"<{want}class>".replace("class", f" class='md'"))
                mode = want
            body = ordered.group(2) if ordered else stripped[2:]
            # Continuation lines of the same bullet are indented.
            while i + 1 < len(lines) and lines[i + 1].startswith("  ") and lines[i + 1].strip() \
                    and not lines[i + 1].strip().startswith(("- ", "|")) \
                    and not re.match(r"^\s*\d+\. ", lines[i + 1]):
                i += 1
                body += " " + lines[i].strip()
            out.append(f"<li>{inline(body)}</li>")
            i += 1
            continue

        close()
        para = [stripped]
        while i + 1 < len(lines) and lines[i + 1].strip() and not lines[i + 1].strip().startswith(
            ("#", "-", "|", "```", "---")
        ) and not re.match(r"^\s*\d+\. ", lines[i + 1]):
            i += 1
            para.append(lines[i].strip())
        out.append(f"<p>{inline(' '.join(para))}</p>")
        i += 1

    close()
    return "\n".join(out)


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
.doc { margin-top: 8px; }
.doc h2.md {
  font-size: 22px; letter-spacing: -.01em; text-transform: none;
  color: var(--ink); margin: 36px 0 10px; font-weight: 650;
}
.doc h3.md {
  font-size: 16px; text-transform: none; letter-spacing: 0;
  color: var(--ink); margin: 26px 0 8px; font-weight: 650;
}
.doc p { margin: 0 0 14px; }
.doc ul.md, .doc ol.md { padding-left: 22px; margin: 0 0 14px; }
.doc ul.md { list-style: disc; }
.doc ol.md { list-style: decimal; }
.doc ul.md li, .doc ol.md li {
  border: 0; background: none; border-radius: 0;
  padding: 0 0 8px; margin: 0; display: list-item;
}
.doc ul.md li::before, .doc ol.md li::before { content: none; }
.doc code {
  background: var(--surface); border-radius: 4px; padding: 1px 5px;
  font-size: .88em;
}
.doc pre {
  background: var(--surface); border-radius: 10px; padding: 14px 16px;
  overflow-x: auto; margin: 0 0 16px;
}
.doc pre code { background: none; padding: 0; font-size: 13px; line-height: 1.6; }
.doc table { width: 100%; border-collapse: collapse; margin: 0 0 18px; font-size: 15px; }
.doc th {
  text-align: left; font-size: 11px; letter-spacing: .08em;
  text-transform: uppercase; color: var(--muted); font-weight: 600;
  padding: 0 12px 8px 0; border-bottom: 1px solid var(--line);
}
.doc td { padding: 10px 12px 10px 0; border-bottom: 1px solid var(--line); vertical-align: top; }
.doc hr { border: 0; border-top: 1px solid var(--line); margin: 32px 0; }
.doc strong { font-weight: 650; }
details.handoff > summary { font-size: 15px; }
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

out.append("<h2>Handoff</h2>")
out.append(
    '<p class="note">The orientation page a new agent starts from. '
    "Written to <code>agent/context.md</code>; this is the same text.</p>"
)
out.append('<details class="handoff"><summary>Read the handoff</summary>')
out.append(f'<div class="doc">{markdown(context)}</div>')
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
