"""Conservative code colours, applied at build time without changing code text.

Plain paths, CLI options and unknown identifiers stay neutral. Java types come
from source declarations/imports and explicit documentation, not capitalisation
guesses; methods need visible call syntax. This is syntax assistance, not an
IDE's semantic analysis. Existing Pygments output keeps its line anchors,
whitespace and copy-button behaviour.
"""

import html
import re
from pathlib import Path

import markdown
from markdown.treeprocessors import Treeprocessor


JAVA_TYPES = set()
JAVA_KEYWORDS = set("boolean byte char double float int long short void true false null this super".split())
TOKEN = re.compile(r'"(?:[^"\\]|\\.)*"|\'(?:[^\'\\]|\\.)*\'|[A-Za-z_$][\w$]*|\d+(?:\.\d+)?|.', re.S)
CODE_OR_PRE = re.compile(r"<pre\b[^>]*>.*?</pre>|<code>([^<]*)</code>", re.S)
JAVA_BLOCK = re.compile(r'(<div class="language-java highlight"[^>]*>)(.*?)(</div>)', re.S)
JAVA_NAME = re.compile(r'<span class="(?:n|na)">([A-Za-z_$][\w$]*)</span>')
JAVA_CALL = re.compile(r'(?:<span class="w">\s*</span>)*<span class="[po]">\(')
JAVA_NON_CODE = re.compile(r'//[^\n]*|/\*.*?\*/|""".*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'', re.S)
JAVA_DECLARATION = re.compile(r"\b(?:class|interface|enum|record)\s+([A-Za-z_$][\w$]*)")
JAVA_FENCE = re.compile(r'<pre><code class="language-java">(.*?)</code></pre>', re.S)
TYPE_HEADERS = {"class", "interface", "enum", "entity"}
TYPE_NAME = re.compile(r"[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*")


def declared_types(source):
    """Include nested types without treating comments or strings as declarations."""
    return set(JAVA_DECLARATION.findall(JAVA_NON_CODE.sub(" ", source)))


class DocumentedTypes(Treeprocessor):
    """Read semantic table columns, leaving ordinary labels and filenames alone."""

    def __init__(self, md):
        super().__init__(md)
        self.names = set()

    def run(self, root):
        for table in root.iter("table"):
            headers = table.findall("./thead/tr/th")
            columns = {i for i, header in enumerate(headers)
                       if "".join(header.itertext()).strip().casefold() in TYPE_HEADERS}
            for row in table.findall("./tbody/tr"):
                for i, cell in enumerate(row):
                    if i in columns:
                        for code in cell.iter("code"):
                            name = "".join(code.itertext()).strip()
                            if TYPE_NAME.fullmatch(name):
                                self.names.add(name.rsplit(".", 1)[-1])


def documented_types(source):
    """Recognise documented classes and Java examples before colouring any page."""
    md = markdown.Markdown(extensions=["tables", "fenced_code", "md_in_html"])
    collector = DocumentedTypes(md)
    md.treeprocessors.register(collector, "documented_types", 15)
    md.convert(source)
    for block in md.htmlStash.rawHtmlBlocks:
        match = JAVA_FENCE.fullmatch(block)
        if match:
            collector.names.update(declared_types(html.unescape(match.group(1))))
    return collector.names


def on_config(config):
    """Build one type inventory so tables, prose and examples agree site-wide."""
    JAVA_TYPES.clear()
    JAVA_TYPES.update("String Object Boolean Byte Character Double Float Integer Long Short Void Math System Exception Map List Set Collection Optional".split())
    root = Path(config.config_file_path).parent
    for source in (root / "src/main/java").rglob("*.java"):
        JAVA_TYPES.add(source.stem)
        text = source.read_text(encoding="utf-8")
        JAVA_TYPES.update(declared_types(text))
        JAVA_TYPES.update(re.findall(r"^import\s+(?!static\b)[\w.]+\.([A-Z]\w*);", text, re.M))
    docs = Path(getattr(config, "docs_dir", root / "documentation/wiki"))
    if not docs.is_absolute():
        docs = root / docs
    for source in docs.rglob("*.md"):
        JAVA_TYPES.update(documented_types(source.read_text(encoding="utf-8")))
    return config


def highlight_inline(encoded):
    value = html.unescape(encoded)
    # Keep whole paths, file names, CLI fragments and versions uncoloured.
    quoted = re.fullmatch(r'"(?:[^"\\]|\\.)*"|\'(?:[^\'\\]|\\.)*\'', value)
    if not quoted and (
        re.search(r"[/\\]|(?:^|\s)--?[A-Za-z]|\.(?:java|jar|xlsx?|csv|ya?ml|md|txt|do|xml|zip|png|log|h2|db|svg|css|json|html)$", value, re.I)
        or re.fullmatch(r"v?\d+(?:\.\d+){2,}", value)
    ):
        return encoded
    parts = []
    for token in TOKEN.finditer(value):
        word = token.group()
        kind = None
        if word.startswith(('"', "'")) and len(word) > 1:
            kind = "string"
        elif word in JAVA_TYPES:
            kind = "type"
        elif re.fullmatch(r"[a-z_$][\w$]*", word) and re.match(r"\s*\(", value[token.end():]):
            kind = "function"
        elif word in JAVA_KEYWORDS:
            kind = "keyword"
        elif re.fullmatch(r"\d+(?:\.\d+)?", word):
            kind = "number"
        escaped = html.escape(word, quote=False)
        parts.append(f'<span class="sp-code-{kind}">{escaped}</span>' if kind else escaped)
    return "".join(parts)


def highlight_java(match):
    opening, body, closing = match.groups()

    def name(token):
        word = token.group(1)
        kind = "nc" if word in JAVA_TYPES else "nf" if JAVA_CALL.match(body[token.end():]) else "n"
        return f'<span class="{kind}">{word}</span>'

    return opening + JAVA_NAME.sub(name, body) + closing


def on_page_content(content, page, **kwargs):
    # Validation deliberately uses the same typeface/colour for prose and code.
    if page.file.src_uri == "validation/index.md":
        return content

    def inline(match):
        if match.group(1) is None:
            return match.group()  # Do not colour untyped fenced/preformatted code.
        return "<code>" + highlight_inline(match.group(1)) + "</code>"

    return JAVA_BLOCK.sub(highlight_java, CODE_OR_PRE.sub(inline, content))
