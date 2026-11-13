import hashlib
import json
import re
from pathlib import Path
from urllib.parse import unquote, urlparse


PROJECT_ROOT = Path(__file__).resolve().parents[1]
ROOT_FILES = (
    "README.md", "AGENTS.md", ".gitignore", ".gitattributes", ".editorconfig",
    "THIRD_PARTY_NOTICES.md", "Apache-2.0.txt", "settings.gradle", "build.gradle",
    "gradle.properties", "gradlew", "gradlew.bat", "app/build.gradle",
)
SOURCE_DIRECTORIES = ("app/src", "gradle/wrapper", "docs", "scripts", ".github")
REQUIRED_FILES = (
    "app/src/main/AndroidManifest.xml", "app/src/main/assets/config.html",
    "app/src/main/assets/deepseek-chan.webp", "app/src/main/assets/deepseek-chan-source.json",
    "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties",
    "docs/user-guide.md", "docs/development.md", "docs/publishing.md",
    "docs/verification/v0.1.4.txt", "scripts/check_repository.py", "scripts/package_source.py",
    ".github/workflows/android.yml",
)
PRIVATE_SUFFIXES = {".p12", ".jks", ".keystore", ".pem", ".key", ".apk", ".aab", ".idsig", ".zip"}
PRIVATE_NAMES = {"local.properties", "secrets.properties", "keystore.properties", ".env", "store-password.txt"}
SECRET_PATTERNS = (
    re.compile(r"-----BEGIN (?:[A-Z0-9]+ )?PRIVATE KEY-----"),
    re.compile(r"\bgh[pousr]_[A-Za-z0-9]{20,}\b"),
    re.compile(r"\bgithub_pat_[A-Za-z0-9_]{30,}\b"),
    re.compile(r"\bsk-(?:proj-)?[A-Za-z0-9_-]{24,}\b"),
    re.compile(r"\bAKIA[A-Z0-9]{16}\b"),
)


def source_files(root=PROJECT_ROOT):
    for name in ROOT_FILES + REQUIRED_FILES:
        if not (root / name).is_file():
            raise ValueError(f"Missing required repository file: {name}")
    files = {root / name for name in ROOT_FILES}
    for directory in SOURCE_DIRECTORIES:
        files.update(path for path in (root / directory).rglob("*") if path.is_file())
    result = []
    for path in files:
        relative = path.relative_to(root)
        if "__pycache__" in relative.parts or path.suffix in {".pyc", ".pyo"}:
            continue
        if path.is_symlink() or not path.resolve().is_relative_to(root):
            raise ValueError(f"Source file points outside repository: {relative.as_posix()}")
        if path.suffix.lower() in PRIVATE_SUFFIXES or path.name in PRIVATE_NAMES or path.name.startswith(".env."):
            raise ValueError(f"Private or generated file in source directories: {relative.as_posix()}")
        if any(part in {"build", ".gradle", ".idea", "data", "dist"} for part in relative.parts):
            raise ValueError(f"Generated directory in source selection: {relative.as_posix()}")
        result.append(path)
    return sorted(result, key=lambda path: path.relative_to(root).as_posix())


def version(root=PROJECT_ROOT):
    source = (root / "app/build.gradle").read_text(encoding="utf-8")
    matched = re.search(r"versionName\s+['\"]([0-9]+\.[0-9]+\.[0-9]+)['\"]", source)
    if not matched:
        raise ValueError("Cannot read application version from app/build.gradle")
    return matched.group(1)


def check_repository(root=PROJECT_ROOT):
    root = root.resolve()
    files = source_files(root)
    links = 0
    for path in files:
        if path.suffix.lower() in {".jar", ".webp"}:
            continue
        try:
            content = path.read_text(encoding="utf-8")
        except UnicodeDecodeError as error:
            raise ValueError(f"Unexpected binary or non-UTF-8 file: {path.relative_to(root).as_posix()}") from error
        if any(pattern.search(content) for pattern in SECRET_PATTERNS):
            raise ValueError(f"Potential credential found; inspect locally: {path.relative_to(root).as_posix()}")
        if path.suffix.lower() != ".md":
            continue
        for target in re.findall(r"\[[^\]]*\]\(([^\s)]+)\)", content):
            parsed = urlparse(target)
            if parsed.scheme or target.startswith("#"):
                continue
            resolved = (path.parent / unquote(parsed.path)).resolve()
            if not resolved.is_relative_to(root) or not resolved.exists():
                raise ValueError(f"Broken local link in {path.relative_to(root).as_posix()}: {target}")
            links += 1
    assets = root / "app/src/main/assets"
    metadata = json.loads((assets / "deepseek-chan-source.json").read_text(encoding="utf-8"))
    digest = hashlib.sha256((assets / "deepseek-chan.webp").read_bytes()).hexdigest()
    if metadata.get("id") != "deepseek-chan" or digest != metadata.get("sheet_sha256"):
        raise ValueError("Character source metadata or spritesheet checksum mismatch")
    if "peiban@520" in (assets / "config.html").read_text(encoding="utf-8"):
        raise ValueError("Public configuration page contains the default password hint")
    if b"\r\n" in (root / "gradlew").read_bytes():
        raise ValueError("Unix Gradle wrapper must use LF line endings")
    return {"version": version(root), "source_files": len(files), "local_markdown_links": links, "status": "passed"}


if __name__ == "__main__":
    print(json.dumps(check_repository(), ensure_ascii=False, indent=2))
