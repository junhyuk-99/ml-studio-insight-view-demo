#!/usr/bin/env python3
"""Load synthetic demo seed JSON into MongoDB when pymongo is available."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


DEFAULT_URI = "mongodb://localhost:27017"
DEFAULT_DB = "ml_studio_demo"
ROOT_DIR = Path(__file__).resolve().parents[1]
SEED_DIR = ROOT_DIR / "demo-data" / "seed"


def collection_name(path: Path) -> str:
    return path.name.removesuffix(".sample.json")


def load_json(path: Path) -> list[dict[str, Any]]:
    with path.open("r", encoding="utf-8") as file:
        data = json.load(file)
    if isinstance(data, list):
        return data
    if isinstance(data, dict):
        return [data]
    raise ValueError(f"{path} must contain a JSON object or array.")


def main() -> int:
    parser = argparse.ArgumentParser(description="Load synthetic ML Studio demo seed data into MongoDB.")
    parser.add_argument("--uri", default=DEFAULT_URI, help=f"MongoDB URI. Default: {DEFAULT_URI}")
    parser.add_argument("--db", default=DEFAULT_DB, help=f"Database name. Default: {DEFAULT_DB}")
    parser.add_argument("--seed-dir", default=str(SEED_DIR), help="Directory containing *.sample.json files.")
    parser.add_argument("--dry-run", action="store_true", help="Validate and list collections without writing.")
    args = parser.parse_args()

    seed_dir = Path(args.seed_dir)
    seed_files = sorted(seed_dir.glob("*.sample.json"))
    if not seed_files:
        print(f"No seed files found in {seed_dir}")
        return 1

    loaded = [(collection_name(path), load_json(path)) for path in seed_files]
    for name, rows in loaded:
        print(f"{name}: {len(rows)} document(s)")

    if args.dry_run:
        print("Dry run complete. No MongoDB writes were performed.")
        return 0

    try:
        from pymongo import MongoClient
    except ImportError:
        print("pymongo is not installed. Install it with: python -m pip install pymongo")
        print("Then rerun this script, or use --dry-run to validate seed files only.")
        return 0

    client = MongoClient(args.uri)
    database = client[args.db]
    for name, rows in loaded:
        collection = database[name]
        collection.delete_many({})
        if rows:
            collection.insert_many(rows)
        print(f"Loaded {len(rows)} document(s) into {args.db}.{name}")

    print("Synthetic demo seed load complete.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
