#!/usr/bin/env python3
"""Remove gomobile's shared go.* Java runtime from one AAR.

The app already ships the same runtime through libbox.aar. Keeping two copies
causes Android's duplicate-class check to fail; the datapath classes continue to
link against the runtime supplied by libbox.
"""
from pathlib import Path
import shutil
import sys
import tempfile
import zipfile


def strip_classes(jar_path: Path) -> None:
    with tempfile.TemporaryDirectory() as td:
        temp = Path(td) / "classes.jar"
        with zipfile.ZipFile(jar_path) as source, zipfile.ZipFile(temp, "w", zipfile.ZIP_DEFLATED) as target:
            for info in source.infolist():
                if info.filename.startswith("go/") and info.filename.endswith(".class"):
                    continue
                target.writestr(info, source.read(info.filename))
        shutil.move(temp, jar_path)


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: strip_gomobile_runtime.py <aar>", file=sys.stderr)
        return 2
    aar = Path(sys.argv[1]).resolve()
    if not aar.is_file():
        raise SystemExit(f"AAR does not exist: {aar}")
    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        with zipfile.ZipFile(aar) as source:
            source.extractall(root)
        strip_classes(root / "classes.jar")
        rebuilt = aar.with_suffix(".stripped.aar")
        with zipfile.ZipFile(rebuilt, "w", zipfile.ZIP_DEFLATED) as target:
            for item in sorted(root.rglob("*")):
                if item.is_file():
                    target.write(item, item.relative_to(root).as_posix())
        shutil.move(rebuilt, aar)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
