#!/usr/bin/env python3
"""Checks the links between the documents of the repository: the file that a relative link names has to be there, and so has the heading that
its anchor names. Links to other sites are not checked, since a site that is down is not a mistake of the pull request.

usage: bin/check_docs.py
"""
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SKIP = ('external/', 'build/', 'android/build/', 'examples/android/build/')
LINK = re.compile(r'(?<!\!)\[[^\]]*\]\(([^)\s]+)\)|<img[^>]*src="([^"]+)"|<a[^>]*href="([^"]+)"')


def documents():
    names = subprocess.run(['git', 'ls-files', '*.md'], cwd=ROOT, capture_output=True, text=True, check=True).stdout.split()
    return [n for n in names if not n.startswith(SKIP)]


def slug(heading):
    """The anchor that GitHub makes of a heading."""
    s = re.sub(r'[`*_]', '', heading.strip().lower())
    s = re.sub(r'\[([^\]]*)\]\([^)]*\)', r'\1', s)
    s = re.sub(r'[^\w\- ]', '', s)
    return s.replace(' ', '-')


def anchors(path):
    found, seen = set(), {}
    fenced = False
    for line in open(path, encoding='utf-8'):
        if line.lstrip().startswith('```'):
            fenced = not fenced
        m = None if fenced else re.match(r'^#{1,6}\s+(.*?)\s*#*\s*$', line)
        if m:
            base = slug(m.group(1))
            n = seen.get(base, 0)
            seen[base] = n + 1
            found.add(base if n == 0 else '%s-%d' % (base, n))
    return found


def main():
    problems = 0
    for doc in documents():
        text = open(os.path.join(ROOT, doc), encoding='utf-8').read()
        text = re.sub(r'```.*?```', '', text, flags=re.S)
        for m in LINK.finditer(text):
            target = next(g for g in m.groups() if g)
            if re.match(r'^(https?:|mailto:|data:)', target):
                continue
            path, _, anchor = target.partition('#')
            file = os.path.normpath(os.path.join(ROOT, os.path.dirname(doc), path)) if path else os.path.join(ROOT, doc)
            if not os.path.exists(file):
                print('%s: %s is not there' % (doc, target))
                problems += 1
            elif anchor and file.endswith('.md') and anchor not in anchors(file):
                print('%s: %s has no heading %s' % (doc, os.path.relpath(file, ROOT), anchor))
                problems += 1
    print('%d documents checked, %d problems' % (len(documents()), problems))
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
