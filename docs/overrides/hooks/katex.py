from __future__ import annotations

import base64
import hashlib
import io
import os
import shutil
import tarfile
import tempfile
import urllib.request

from mkdocs.config.defaults import MkDocsConfig
from mkdocs.exceptions import PluginError
from mkdocs.structure.files import File, Files

VERSION = "0.16.22"
TARBALL = f"https://registry.npmjs.org/katex/-/katex-{VERSION}.tgz"
# The `dist.integrity` of the version in the npm registry
INTEGRITY = "sha512-XCHRdUw4lf3SKBaJe4EvgqIuWwkPSo9XoeO8GjQW94Bp7TWv9hNhzZjZ+OH9yf1UmLygb7DIT5GSFQiyt16zYg=="
SITE_PATH = "vendor/katex"
# What the pages load (see extra_javascript and extra_css), and the license, which the minified files don't carry
_FILES = {"package/LICENSE", "package/dist/katex.min.js", "package/dist/katex.min.css",
          "package/dist/contrib/auto-render.min.js"}


# -----------------------------------------------------------------------------
# Hook to serve KaTeX (the math on the pages) from the site itself. The privacy plugin can't do it from a CDN, because
# the stylesheet loads its fonts by relative URLs, which the plugin doesn't download. The package is downloaded once
# into the cache, instead of keeping a copy in the repository.
# -----------------------------------------------------------------------------
def on_files(files: Files, *, config: MkDocsConfig) -> Files:
    cache = os.path.join(os.path.dirname(config.config_file_path), ".cache", "katex", VERSION)
    if not os.path.isdir(cache):
        _download(cache)
    for root, _, names in os.walk(cache):
        for name in names:
            path = os.path.join(root, name)
            uri = f"{SITE_PATH}/{os.path.relpath(path, cache).replace(os.sep, '/')}"
            files.append(File.generated(config, uri, abs_src_path=path))
    return files


def _download(cache: str):
    with urllib.request.urlopen(TARBALL, timeout=60) as response:
        data = response.read()
    algorithm, digest = INTEGRITY.split("-", 1)
    if base64.b64encode(hashlib.new(algorithm, data).digest()).decode() != digest:
        raise PluginError(f"The integrity check of {TARBALL} failed")
    os.makedirs(os.path.dirname(cache), exist_ok=True)
    # Extracted next to the cache first, so an interrupted build doesn't leave a half-filled cache behind
    staging = tempfile.mkdtemp(dir=os.path.dirname(cache))
    try:
        with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as archive:
            for member in archive.getmembers():
                # Every browser in use reads woff2, so the other formats of the fonts aren't needed
                is_font = member.name.startswith("package/dist/fonts/") and member.name.endswith(".woff2")
                if member.isfile() and (member.name in _FILES or is_font):
                    target = os.path.join(staging, member.name.removeprefix("package/dist/").removeprefix("package/"))
                    os.makedirs(os.path.dirname(target), exist_ok=True)
                    with archive.extractfile(member) as source, open(target, "wb") as out:
                        shutil.copyfileobj(source, out)
        os.replace(staging, cache)
    finally:
        shutil.rmtree(staging, ignore_errors=True)
