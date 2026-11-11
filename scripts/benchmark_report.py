#!/usr/bin/env python3
"""Summarize Helium frame and workload CSV captures without third-party packages."""
from __future__ import annotations

import argparse
import csv
import math
import statistics
from collections import defaultdict
from pathlib import Path

REQUIRED_COLUMNS = {"scenario", "variant", "run"}


def number(row: dict[str, str], column: str) -> float | None:
    value = row.get(column, "").strip()
    if not value:
        return None
    try:
        parsed = float(value)
    except ValueError as error:
        raise ValueError(f"invalid {column} value: {value!r}") from error
    if not math.isfinite(parsed):
        raise ValueError(f"invalid {column} value: {value!r}")
    return parsed


def percentile(values: list[float], fraction: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    position = (len(ordered) - 1) * fraction
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return ordered[lower]
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower)


def median_column(rows: list[dict[str, str]], column: str) -> float | None:
    values = [value for row in rows if (value := number(row, column)) is not None]
    return statistics.median(values) if values else None


def summarize(rows: list[dict[str, str]]) -> dict[str, float | int | None]:
    frame_times = [
        value for row in rows
        if (value := number(row, "frame_time_ms")) is not None and value > 0
    ]
    elapsed_seconds = sum(frame_times) / 1000.0
    allocated_bytes = sum(
        value for row in rows
        if (value := number(row, "allocated_bytes")) is not None and value > 0
    )
    p99 = percentile(frame_times, 0.99)
    heap_values = [value for row in rows if (value := number(row, "heap_used_bytes")) is not None]
    native_values = [value for row in rows if (value := number(row, "native_memory_bytes")) is not None]
    return {
        "avg_fps": 1000.0 / statistics.mean(frame_times) if frame_times else None,
        "low_1_fps": 1000.0 / p99 if p99 else None,
        "p50_ms": percentile(frame_times, 0.50),
        "p95_ms": percentile(frame_times, 0.95),
        "p99_ms": p99,
        "allocation_mb_s": allocated_bytes / elapsed_seconds / (1024 * 1024) if elapsed_seconds else None,
        "peak_heap_mb": max(heap_values) / (1024 * 1024) if heap_values else None,
        "peak_native_mb": max(native_values) / (1024 * 1024) if native_values else None,
        "cpu_ms": median_column(rows, "cpu_frame_ms"),
        "gpu_ms": median_column(rows, "gpu_frame_ms"),
        "startup_ms": median_column(rows, "startup_ms"),
        "world_load_ms": median_column(rows, "world_load_ms"),
        "ping_ms": median_column(rows, "ping_duration_ms"),
        "peak_entities": max((value for row in rows if (value := number(row, "entity_count")) is not None), default=None),
        "peak_particles": max((value for row in rows if (value := number(row, "particle_count")) is not None), default=None),
        "peak_chunk_queue": max((value for row in rows if (value := number(row, "chunk_queue_size")) is not None), default=None),
    }


def display(value: float | int | None, precision: int = 1) -> str:
    return "n/a" if value is None else f"{value:.{precision}f}"


def report(path: Path) -> str:
    with path.open(newline="", encoding="utf-8-sig") as source:
        reader = csv.DictReader(source)
        columns = set(reader.fieldnames or ())
        missing = REQUIRED_COLUMNS - columns
        if missing:
            raise ValueError(f"missing CSV columns: {', '.join(sorted(missing))}")
        groups: dict[tuple[str, str], list[dict[str, str]]] = defaultdict(list)
        for row in reader:
            groups[(row["scenario"].strip(), row["variant"].strip())].append(row)

    summaries = {key: summarize(rows) for key, rows in groups.items()}
    baselines = {
        scenario: values
        for (scenario, variant), values in summaries.items()
        if variant.casefold() == "sodium"
    }
    headers = (
        "Scenario", "Variant", "Avg FPS", "1% low", "P50/P95/P99 ms", "Alloc MB/s",
        "Peak heap/native MB", "CPU/GPU ms", "Startup ms", "World load ms", "Ping ms",
        "Peak entities / particles / chunks", "FPS vs Sodium", "1% low vs Sodium",
    )
    lines = ["| " + " | ".join(headers) + " |", "|" + "|".join(["---"] * len(headers)) + "|"]
    for (scenario, variant), values in sorted(summaries.items()):
        pcts = "/".join(display(values[key], 2) for key in ("p50_ms", "p95_ms", "p99_ms"))
        timings = f"{display(values['cpu_ms'], 2)} / {display(values['gpu_ms'], 2)}"
        baseline = baselines.get(scenario, {}).get("avg_fps")
        fps_delta = None
        if baseline and values["avg_fps"] is not None:
            fps_delta = (values["avg_fps"] / baseline - 1.0) * 100.0
        baseline_low = baselines.get(scenario, {}).get("low_1_fps")
        low_delta = None
        if baseline_low and values["low_1_fps"] is not None:
            low_delta = (values["low_1_fps"] / baseline_low - 1.0) * 100.0
        cells = (
            scenario, variant, display(values["avg_fps"]), display(values["low_1_fps"]), pcts,
            display(values["allocation_mb_s"], 2),
            f"{display(values['peak_heap_mb'])} / {display(values['peak_native_mb'])}", timings,
            display(values["startup_ms"], 0), display(values["world_load_ms"], 0),
            display(values["ping_ms"], 1),
            " / ".join(display(values[key], 0) for key in ("peak_entities", "peak_particles", "peak_chunk_queue")),
            "n/a" if fps_delta is None else f"{fps_delta:+.1f}%",
            "n/a" if low_delta is None else f"{low_delta:+.1f}%",
        )
        lines.append("| " + " | ".join(cells) + " |")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv_file", type=Path, help="per-frame and workload metrics CSV")
    args = parser.parse_args()
    try:
        print(report(args.csv_file))
    except (OSError, ValueError) as error:
        parser.error(str(error))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
