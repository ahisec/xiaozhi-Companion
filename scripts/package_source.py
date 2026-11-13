import argparse
import hashlib
import json
import sys
import zipfile
from pathlib import Path

sys.dont_write_bytecode = True
from check_repository import PROJECT_ROOT, check_repository, source_files, version


def package_source(output):
    report = check_repository()
    files = source_files()
    output = output.resolve()
    if output.suffix.lower() != ".zip":
        raise ValueError("Source archive output must end in .zip")
    if output.is_relative_to(PROJECT_ROOT) and not output.is_relative_to(PROJECT_ROOT / "dist"):
        raise ValueError("Inside the repository, write archives only under dist/")
    manifest = PROJECT_ROOT / "SHA256SUMS.txt"
    manifest_content = "".join(
        f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.relative_to(PROJECT_ROOT).as_posix()}\n"
        for path in files
    )
    with manifest.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write(manifest_content)
    files.append(manifest)
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in files:
            name = path.relative_to(PROJECT_ROOT).as_posix()
            entry = zipfile.ZipInfo("companion-android/" + name)
            entry.create_system = 3
            entry.external_attr = (0o100755 if name == "gradlew" else 0o100644) << 16
            entry.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(entry, path.read_bytes())
    with zipfile.ZipFile(output) as archive:
        if archive.testzip() is not None or len(archive.namelist()) != len(files):
            raise ValueError("Source archive integrity failed")
        for path in files:
            if archive.read("companion-android/" + path.relative_to(PROJECT_ROOT).as_posix()) != path.read_bytes():
                raise ValueError("Source archive content differs from source files")
    return {**report, "archive": str(output), "archive_files": len(files), "bytes": output.stat().st_size,
        "sha256": hashlib.sha256(output.read_bytes()).hexdigest()}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Package the Companion repository without credentials or build files")
    parser.add_argument("--output", type=Path, default=PROJECT_ROOT / "dist" / f"companion-android-v{version()}-source.zip")
    arguments = parser.parse_args()
    print(json.dumps(package_source(arguments.output), ensure_ascii=False, indent=2))
