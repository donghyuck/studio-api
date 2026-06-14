from pathlib import Path


def resolve_shared_path(raw_path: str, shared_root: Path) -> Path:
    root = shared_root.resolve(strict=True)
    candidate = Path(raw_path)
    if not candidate.is_absolute():
        candidate = root / candidate
    resolved = candidate.resolve(strict=False)
    if resolved != root and root not in resolved.parents:
        raise ValueError("local path escapes shared volume root")
    parent = resolved if resolved.exists() and resolved.is_dir() else resolved.parent
    existing_parent = parent
    while not existing_parent.exists() and existing_parent != root:
        existing_parent = existing_parent.parent
    if existing_parent.resolve(strict=True) != root and root not in existing_parent.resolve(strict=True).parents:
        raise ValueError("local path parent escapes shared volume root")
    return resolved
