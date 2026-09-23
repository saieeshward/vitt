"""What the per-document clan views share: reading a clan, a small Markdown
subset, the colour tokens, and applying the page back into the file.

clan-view.py predates this and keeps its own copy, because it renders the build
state with a richer Markdown than the other documents use. verification-view.py
and plan-view.py are built on this, so a third document type is a page layout
rather than a third copy of the plumbing.
"""
import html
import os
import re
import subprocess
import tempfile
import yaml
import zipfile

e = html.escape


def load(clan_file):
    """The context, the data and the manifest of a clan, read without the CLI."""
    work = tempfile.mkdtemp(prefix="clan-page-")
    with zipfile.ZipFile(clan_file) as z:
        z.extractall(work)
    context = open(f"{work}/agent/context.md").read()
    data = yaml.safe_load(open(f"{work}/shared/data.yaml"))
    manifest = yaml.safe_load(open(f"{work}/manifest.yaml"))
    return work, context, data, manifest


def inline(text):
    """Bold, italics and inline code, on escaped text."""
    out = e(str(text))
    out = re.sub(r"`([^`]+)`", r"<code>\1</code>", out)
    out = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", out)
    out = re.sub(r"(?<!\*)\*([^*\n]+)\*(?!\*)", r"<em>\1</em>", out)
    return out


def markdown(src):
    """Headings, paragraphs, bullet lists and fences.

    Paragraphs are joined across their hard wraps, and a list item takes its
    indented continuation lines with it. The top-level title is left out,
    because the page header already says it.
    """
    out, para, items, pre = [], [], [], None

    def flush():
        if para:
            out.append(f"<p>{inline(' '.join(para))}</p>")
            para.clear()
        if items:
            out.append("<ul class='md'>" + "".join(f"<li>{inline(' '.join(i))}</li>" for i in items) + "</ul>")
            items.clear()

    for line in src.split("\n"):
        s = line.strip()
        if pre is not None:
            if s.startswith("```"):
                out.append(f"<pre><code>{e(chr(10).join(pre))}</code></pre>")
                pre = None
            else:
                pre.append(line)
            continue
        if s.startswith("```"):
            flush()
            pre = []
        elif not s:
            flush()
        elif s.startswith("#"):
            flush()
            level = len(s) - len(s.lstrip("#"))
            if level > 1:
                out.append(f"<h{level + 1} class='md'>{inline(s.lstrip('# '))}</h{level + 1}>")
        elif s.startswith("- "):
            if para:
                flush()
            items.append([s[2:]])
        elif items and line.startswith("  "):
            items[-1].append(s)
        else:
            para.append(s)
    flush()
    return "\n".join(out)


# The project's tokens, the same values clan-view.py uses, plus three status hues.
BASE_CSS = """
:root {
  --ground: #FFFDF7; --surface: #F2EFE7; --ink: #241F33; --muted: #6A6480;
  --faint: #8A85A0; --accent: #6B5BFF; --wash: #F3F0FF; --line: #E5E1D8;
  --pass: #2D9D75; --fail: #D14B4B; --wait: #BB7F0F;
}
@media (prefers-color-scheme: dark) {
  :root {
    --ground: #17141F; --surface: #241F33; --ink: #DAD5E6; --muted: #A7A1BC;
    --faint: #6F6886; --accent: #9C8DFF; --wash: #1E1A2B; --line: #2B2539;
    --pass: #5EE0AC; --fail: #FF8A8A; --wait: #FFC94D;
  }
}
* { box-sizing: border-box; }
body {
  background: var(--ground); color: var(--ink);
  font: 16px/1.6 ui-sans-serif, -apple-system, "Segoe UI", system-ui, sans-serif;
  margin: 0; padding: 0 16px 96px; -webkit-font-smoothing: antialiased;
}
.wrap { max-width: 760px; margin: 0 auto; }
header { padding: 72px 0 8px; }
.eyebrow { font-size: 12px; letter-spacing: .14em; text-transform: uppercase; color: var(--muted); margin: 0 0 12px; }
h1 { font-size: 40px; line-height: 1.05; letter-spacing: -.02em; margin: 0 0 16px; }
.lede { color: var(--muted); font-size: 18px; margin: 0; max-width: 60ch; }
.facts { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 12px; margin: 32px 0 0; }
.fact { background: var(--surface); border-radius: 14px; padding: 14px 16px; }
.fact dt { font-size: 11px; letter-spacing: .1em; text-transform: uppercase; color: var(--muted); margin: 0 0 6px; }
.fact dd { margin: 0; font-size: 22px; font-weight: 600; font-variant-numeric: tabular-nums; }
h2 { font-size: 12px; letter-spacing: .14em; text-transform: uppercase; color: var(--muted); margin: 56px 0 14px; font-weight: 600; }
ul { list-style: none; padding: 0; margin: 0; }
.card { background: var(--surface); border-radius: 12px; padding: 14px 18px; margin: 0 0 10px; border-left: 3px solid var(--wait); }
.card.pass, .card.done, .card.decided { border-left-color: var(--pass); }
.card.fail { border-left-color: var(--fail); }
.top { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; }
.n { color: var(--faint); font-variant-numeric: tabular-nums; min-width: 1.5em; }
.name { font-size: 15px; font-weight: 600; }
.pill { margin-left: auto; font-size: 12px; font-weight: 600; border-radius: 999px; padding: 2px 10px; color: var(--wait); border: 1px solid currentColor; white-space: nowrap; }
.pass .pill, .done .pill, .decided .pill { color: var(--pass); }
.fail .pill { color: var(--fail); }
.claim { margin: 6px 0 0; }
.why { color: var(--muted); font-size: 14px; margin: 4px 0 0; }
.detail { color: var(--faint); font-size: 13px; margin: 6px 0 0; font-variant-numeric: tabular-nums; }
.card ul.work { list-style: disc; padding-left: 20px; margin: 8px 0 0; font-size: 15px; }
.card ul.work li { margin: 0 0 4px; }
ul.plain li { padding: 12px 0; border-bottom: 1px solid var(--line); }
ul.plain li:last-child { border-bottom: 0; }
.run { font-weight: 600; }
code { font: 13px/1.5 ui-monospace, SFMono-Regular, Menlo, monospace; background: var(--wash); border-radius: 6px; padding: 1px 5px; overflow-wrap: anywhere; }
pre { background: var(--surface); border-radius: 12px; padding: 14px 16px; overflow-x: auto; }
pre code { background: none; padding: 0; overflow-wrap: normal; }
.doc h3.md { font-size: 22px; letter-spacing: -.01em; margin: 40px 0 10px; font-weight: 650; }
.doc p { margin: 0 0 14px; }
.doc ul.md { list-style: disc; padding-left: 22px; margin: 0 0 14px; }
.doc ul.md li { margin: 0 0 8px; }
"""


def apply(clan_file, work, manifest, body_lines, css=BASE_CSS, to_stdout=False):
    """Writes the page into the clan's human view, head and body both.

    The head is replaced every time, because the default render leaves its own
    stylesheet there to fight this one. No decision entry: rendering is not a
    change of state.
    """
    page = "\n".join(["---", "mode: patch-html", "---", "<body>", f"<style>{css}</style>"] + body_lines + ["</body>"])
    if to_stdout:
        print(page)
        return
    body = os.path.join(work, "view.html")
    with open(body, "w") as f:
        f.write(page)
    head = os.path.join(work, "head.html")
    with open(head, "w") as f:
        f.write(
            "---\nmode: patch-html\n---\n<head>\n"
            '<meta charset="utf-8">\n'
            '<meta name="viewport" content="width=device-width, initial-scale=1">\n'
            f"<title>{e(manifest.get('title', 'CLAN'))}</title>\n</head>\n"
        )
    def patch(fragment, selector):
        return subprocess.run(
            ["clan", "patch-html", clan_file, fragment, "--selector", selector,
             "--patch-action", "replace", "--no-decision", "--quiet"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        ).returncode == 0

    # A clan fresh from `clan create` has no human view to patch until it has
    # been rendered once, so the first patch failing means render, then retry.
    if not patch(head, "head"):
        subprocess.run(["clan", "render", "--quiet", clan_file], check=True, stdout=subprocess.DEVNULL)
        if not patch(head, "head"):
            raise SystemExit(f"could not patch the head of {clan_file}")
    if not patch(body, "body"):
        raise SystemExit(f"could not patch the body of {clan_file}")
    print(f"rendered {os.path.basename(clan_file)} from its structured members")
