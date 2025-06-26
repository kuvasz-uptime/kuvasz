# Copyright (c) 2016-2025 Martin Donath <martin.donath@squidfunk.com>

# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to
# deal in the Software without restriction, including without limitation the
# rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
# sell copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:

# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.

# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
# FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
# IN THE SOFTWARE.

from __future__ import annotations

import posixpath
import re
from mkdocs.config.defaults import MkDocsConfig
from mkdocs.structure.files import File, Files
from mkdocs.structure.pages import Page
from re import Match


# -----------------------------------------------------------------------------
# Hooks, copied over from mkdocs-material to render custom shortcodes as badges (e.g. version, types, flags, etc.)
# Original: https://github.com/squidfunk/mkdocs-material/blob/master/src/overrides/hooks/shortcodes.py
# -----------------------------------------------------------------------------
def on_page_markdown(
        markdown: str, *, page: Page, config: MkDocsConfig, files: Files
):
    # Replace callback
    def replace(match: Match):
        type, args = match.groups()
        args = args.strip()
        if type == "version":
            return _badge_for_version(args, page, files)
        elif type == "flag":
            return flag(args, page, files)
        elif type == "config":
            return _badge_for_configuration(args)
        elif type == "type":
            return _badge_for_type(args, page, files)
        elif type == "default":
            return _badge_for_default(args, page, files)
        elif type == "required_if":
            return _badge_for_required_if(args, page, files)

        # Otherwise, raise an error
        raise RuntimeError(f"Unknown shortcode: {type}")

    # Find and replace all external asset URLs in current page
    return re.sub(
        r"<!-- md:(\w+)(.*?) -->",
        replace, markdown, flags=re.I | re.M
    )


# -----------------------------------------------------------------------------
# Helper functions
# -----------------------------------------------------------------------------

# Create a flag of a specific type
def flag(args: str, page: Page, files: Files):
    type, *_ = args.split(" ", 1)
    if type == "experimental":
        return _badge_for_experimental(page, files)
    elif type == "required":
        return _badge_for_required(page, files)
    raise RuntimeError(f"Unknown type: {type}")


# # Create a linkable option
# def option(type: str):
#     _, *_, name = re.split(r"[.:]", type)
#     return f"[`{name}`](#+{type}){{ #+{type} }}\n\n"
#
#
# # Create a linkable setting - @todo append them to the bottom of the page
# def setting(type: str):
#     _, *_, name = re.split(r"[.*]", type)
#     return f"`{name}` {{ #{type} }}\n\n[{type}]: #{type}\n\n"


# -----------------------------------------------------------------------------

# Resolve path of file relative to given page - the posixpath always includes
# one additional level of `..` which we need to remove
def _resolve_path(path: str, page: Page, files: Files):
    path, anchor, *_ = f"{path}#".split("#")
    path = _resolve(files.get_file_from_path(path), page)
    return "#".join([path, anchor]) if anchor else path


# Resolve path of file relative to given page - the posixpath always includes
# one additional level of `..` which we need to remove
def _resolve(file: File, page: Page):
    path = posixpath.relpath(file.src_uri, page.file.src_uri)
    return posixpath.sep.join(path.split(posixpath.sep)[1:])


# -----------------------------------------------------------------------------

# Create badge
def _badge(icon: str, text: str = "", type: str = ""):
    classes = f"mdx-badge mdx-badge--{type}" if type else "mdx-badge"
    return "".join([
        f"<span class=\"{classes}\">",
        *([f"<span class=\"mdx-badge__icon\">{icon}</span>"] if icon else []),
        *([f"<span class=\"mdx-badge__text\">{text}</span>"] if text else []),
        f"</span>",
    ])


# Create badge for version
def _badge_for_version(text: str, page: Page, files: Files):
    spec = text
    path = f"changelog.md#{spec}"

    # Return badge
    icon = "material-tag-outline"
    href = _resolve_path("conventions.md#version", page, files)
    return _badge(
        icon=f"[:{icon}:]({href} 'Minimum version')",
        text=f"[{text}]({_resolve_path(path, page, files)})" if spec else ""
    )


# Create badge for configuration docs link
def _badge_for_configuration(href: str):
    icon = "material-cog"
    return _badge(
        icon=f"[:{icon}:]({href} 'Configuration')",
    )


# Create badge for type
def _badge_for_type(text: str, page: Page, files: Files):
    icon = "material-function"
    href = _resolve_path("conventions.md#type", page, files)
    return _badge(
        icon=f"[:{icon}:]({href} 'Type')",
        text=text
    )


# Create badge for default value
def _badge_for_default(text: str, page: Page, files: Files):
    icon = "material-backup-restore"
    href = _resolve_path("conventions.md#default", page, files)
    return _badge(
        icon=f"[:{icon}:]({href} 'Default value')",
        text=text
    )


# Create badge for required value flag
def _badge_for_required(page: Page, files: Files):
    icon = "material-alert"
    href = _resolve_path("conventions.md#required", page, files)
    return _badge(
        icon=f"[:{icon}:]({href} 'Required')"
    )


# Create badge for conditionally required value flag
def _badge_for_required_if(text: str, page: Page, files: Files):
    icon = "material-alert-minus"
    href = _resolve_path("conventions.md#required_if", page, files)
    return _badge(
        icon=f"[:{icon}:]({href} 'Required if')",
        text=text
    )


# Create badge for experimental flag
def _badge_for_experimental(page: Page, files: Files):
    icon = "material-flask-outline"
    href = _resolve_path("conventions.md#experimental", page, files)
    return _badge(
        icon=f"[:{icon}:]({href} 'Experimental')"
    )
