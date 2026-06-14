#!/usr/bin/env python3
"""Load synthetic demo seed JSON into MongoDB when pymongo is available."""

from __future__ import annotations

import argparse
import json
import math
import re
from pathlib import Path
from typing import Any
from datetime import datetime, timedelta, timezone


DEFAULT_URI = "mongodb://localhost:27017"
DEFAULT_DB = "ml_studio_demo"
DEMO_DATASET_KEY = "DEMO_DATASET_MANUFACTURING_AI"
RAW_SOURCE_COLLECTION = "THISHMIDATA"
SYNTHETIC_HMI_ROW_COUNT = 120
ROOT_DIR = Path(__file__).resolve().parents[1]
SEED_DIR = ROOT_DIR / "demo-data" / "seed"
COLLECTION_ALIASES = {
    "tmstmc": "TMSTMC",
    "thishmidata": RAW_SOURCE_COLLECTION,
}
ISO_INSTANT_PATTERN = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$")


def collection_name(path: Path) -> str:
    name = path.name.removesuffix(".sample.json")
    return COLLECTION_ALIASES.get(name.lower(), name)


def load_json(path: Path) -> list[dict[str, Any]]:
    with path.open("r", encoding="utf-8") as file:
        data = json.load(file)
    if isinstance(data, list):
        return data
    if isinstance(data, dict):
        return [data]
    raise ValueError(f"{path} must contain a JSON object or array.")


def normalize_seed_value(value: Any) -> Any:
    if isinstance(value, dict):
        return {key: normalize_seed_value(nested) for key, nested in value.items()}
    if isinstance(value, list):
        return [normalize_seed_value(item) for item in value]
    if isinstance(value, str) and ISO_INSTANT_PATTERN.match(value):
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    return value


def build_synthetic_hmi_rows(min_count: int = SYNTHETIC_HMI_ROW_COUNT) -> list[dict[str, Any]]:
    base_time = datetime(2026, 6, 10, 9, 0, tzinfo=timezone.utc)
    rows: list[dict[str, Any]] = []
    equipment_ids = ["DEMO-MC-001", "DEMO-MC-002", "DEMO-MC-003"]

    for index in range(min_count):
        equipment_id = equipment_ids[index % len(equipment_ids)]
        phase = index / 6.0
        rows.append(
            {
                "dataset_key": DEMO_DATASET_KEY,
                "datasetId": DEMO_DATASET_KEY,
                "SOURCE_TYPE_CODE": "DATABASE",
                "SOURCE_DTL_CODE": "MONGODB",
                "SOURCE_FILE": "synthetic-hmi",
                "PRDTIME": base_time + timedelta(minutes=index),
                "timestamp": base_time + timedelta(minutes=index),
                "MCCODE": equipment_id,
                "equipment_id": equipment_id,
                "TEMP_PV": round(735.0 + math.sin(phase) * 8.0 + (index % 5) * 0.4, 2),
                "TEMP_SV": 740.0,
                "PRESSURE_PV": round(2.08 + math.cos(phase / 2.0) * 0.09, 3),
                "PRESSURE_SV": 2.1,
                "MOTOR_CURRENT_PV": round(17.4 + math.sin(phase / 1.5) * 1.2, 3),
                "VIBRATION_RMS_PV": round(0.16 + (index % 12) * 0.006, 3),
                "GAS_FLOW_PV": round(39.5 + math.cos(phase) * 2.4, 2),
                "CYCLE_TIME_PV": round(48.0 + (index % 9) * 0.7, 2),
                "ANAL_STAT": "WARN" if index % 37 == 0 else "NORMAL",
                "OPSTAT": "RUN",
                "lot_no": f"DEMO-LOT-{(index // 30) + 1:03d}",
                "part_no": f"DEMO-PART-{(index % 4) + 1:03d}",
            }
        )

    return rows


def normalize_loaded_rows(collection: str, rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    if collection == RAW_SOURCE_COLLECTION:
        return build_synthetic_hmi_rows(max(SYNTHETIC_HMI_ROW_COUNT, len(rows)))
    return [normalize_seed_value(row) for row in rows]


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

    loaded = [
        (name, normalize_loaded_rows(name, load_json(path)))
        for path in seed_files
        for name in [collection_name(path)]
    ]
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
