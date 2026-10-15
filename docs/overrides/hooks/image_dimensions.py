from __future__ import annotations

import functools
import os
import posixpath
import re
from urllib.parse import unquote, urlsplit

from mkdocs.config.defaults import MkDocsConfig
from mkdocs.structure.files import Files
from mkdocs.structure.pages import Page
from PIL import Image

_IMG = re.compile(r"<img\b[^>]*>", flags=re.I)


# -----------------------------------------------------------------------------
# Hook to add the missing width/height of the local images from their files, so the browser can reserve their space
# before they're loaded, instead of shifting the layout when they arrive
# -----------------------------------------------------------------------------
def on_page_content(html: str, *, page: Page, config: MkDocsConfig, files: Files):
    def replace(match: re.Match):
        tag = match.group(0)
        if _attr(tag, "height") is not None:
            return tag
        attrs = _dimensions(tag, page, config.docs_dir)
        return "<img " + " ".join(attrs) + tag[4:] if attrs else tag

    return _IMG.sub(replace, html)


def _attr(tag: str, name: str):
    match = re.search(rf"""\s{name}=["']?([^"'\s>]*)""", tag, flags=re.I)
    return match.group(1) if match else None


def _dimensions(tag: str, page: Page, docs_dir: str):
    src = _attr(tag, "src")
    url = urlsplit(src or "")
    if not src or url.scheme or url.netloc or url.path.startswith("/"):
        return []

    # The URLs are already relative to the page's URL at this point, not to its markdown file
    path = posixpath.normpath(posixpath.join(posixpath.dirname(page.url), unquote(url.path)))
    file = os.path.join(docs_dir, path)
    if not os.path.isfile(file):
        return []
    size = _intrinsic_size(file, os.path.getmtime(file))
    if not size:
        return []

    intrinsic_width, intrinsic_height = size
    width = _attr(tag, "width")
    if width is None:
        return [f'width="{intrinsic_width}"', f'height="{intrinsic_height}"']
    if width.isdigit():
        return [f'height="{round(int(width) * intrinsic_height / intrinsic_width)}"']
    return []


# The modification time is part of the cache key, so `mkdocs serve` picks up the replaced images
@functools.lru_cache(maxsize=None)
def _intrinsic_size(file: str, mtime: float):
    try:
        with Image.open(file) as image:
            return image.size
    except OSError:
        return None
