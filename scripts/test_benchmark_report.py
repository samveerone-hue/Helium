import unittest

from scripts.benchmark_report import report, summarize


class BenchmarkReportTest(unittest.TestCase):
    def test_summarize_frame_and_workload_metrics(self):
        rows = [
            {
                "frame_time_ms": "10",
                "allocated_bytes": "100",
                "heap_used_bytes": str(2 * 1024 * 1024),
                "native_memory_bytes": str(256 * 1024),
                "entity_count": "40",
                "particle_count": "12",
                "chunk_queue_size": "6",
            },
            {
                "frame_time_ms": "20",
                "allocated_bytes": "200",
                "heap_used_bytes": str(3 * 1024 * 1024),
                "native_memory_bytes": str(512 * 1024),
                "entity_count": "45",
                "particle_count": "10",
                "chunk_queue_size": "4",
            },
        ]

        metrics = summarize(rows)

        self.assertAlmostEqual(66.6667, metrics["avg_fps"], places=3)
        self.assertEqual(3.0, metrics["peak_heap_mb"])
        self.assertEqual(0.5, metrics["peak_native_mb"])
        self.assertEqual(45.0, metrics["peak_entities"])
        self.assertEqual(12.0, metrics["peak_particles"])
        self.assertEqual(6.0, metrics["peak_chunk_queue"])

    def test_report_compares_variants_to_sodium(self):
        import tempfile
        from pathlib import Path

        csv_text = (
            "scenario,variant,run,frame_time_ms\n"
            "entity,sodium,1,20\n"
            "entity,sodium+helium,1,10\n"
        )
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "capture.csv"
            path.write_text(csv_text, encoding="utf-8")
            output = report(path)

        self.assertIn("sodium+helium", output)
        self.assertIn("+100.0%", output)


if __name__ == "__main__":
    unittest.main()
