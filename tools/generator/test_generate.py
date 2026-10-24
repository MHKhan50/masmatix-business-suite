"""Run with: python3 -m unittest discover -s tools/generator"""
from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

import generate


def write_spec(folder: Path, spec: dict) -> None:
    (folder / "s.json").write_text(json.dumps(spec), encoding="utf-8")


GOOD = {"system": "demo", "entities": [{"name": "Room", "path": "rooms", "fields": [
    {"name": "number", "type": "string", "required": True},
    {"name": "rate", "type": "decimal", "min": "0.00"},
    {"name": "kind", "type": "enum", "values": ["SINGLE", "DOUBLE"]}]}]}


class GeneratorTest(unittest.TestCase):

    def test_builds_entity_repository_controller_and_menu(self) -> None:
        with tempfile.TemporaryDirectory() as d:
            write_spec(Path(d), GOOD)
            files = generate.build(Path(d))
        names = {p.name for p in files}
        self.assertTrue({"Room.java", "RoomRepository.java", "RoomController.java", "systems.json"} <= names)
        entity = next(c for p, c in files.items() if p.name == "Room.java")
        self.assertIn('@Table(name = "demo_room")', entity)
        self.assertIn("private BigDecimal rate;", entity)
        self.assertIn("SINGLE|DOUBLE", entity)
        controller = next(c for p, c in files.items() if p.name == "RoomController.java")
        self.assertIn('@RequestMapping("/api/demo/rooms")', controller)

    def test_rejects_bad_specs(self) -> None:
        bad_cases = [
            {"system": "Demo", "entities": GOOD["entities"]},
            {"system": "demo", "entities": [{"name": "room", "path": "rooms", "fields": [{"name": "a", "type": "string"}]}]},
            {"system": "demo", "entities": [{"name": "Room", "path": "rooms", "fields": [{"name": "id", "type": "int"}]}]},
            {"system": "demo", "entities": [{"name": "Room", "path": "rooms", "fields": [{"name": "a", "type": "money"}]}]},
            {"system": "demo", "entities": [{"name": "Room", "path": "rooms", "fields": [{"name": "k", "type": "enum"}]}]},
        ]
        for spec in bad_cases:
            with tempfile.TemporaryDirectory() as d:
                write_spec(Path(d), spec)
                with self.assertRaises(generate.SpecError):
                    generate.build(Path(d))


class UniqueFieldTest(unittest.TestCase):

    def test_unique_string_gets_unique_column(self) -> None:
        spec = json.loads(json.dumps(GOOD))
        spec["entities"][0]["fields"][0]["unique"] = True
        with tempfile.TemporaryDirectory() as d:
            write_spec(Path(d), spec)
            files = generate.build(Path(d))
        entity = next(c for p, c in files.items() if p.name == "Room.java")
        self.assertIn("@Column(length = 120, nullable = false, unique = true)", entity)

    def test_unique_on_number_is_rejected(self) -> None:
        spec = json.loads(json.dumps(GOOD))
        spec["entities"][0]["fields"][1]["unique"] = True
        with tempfile.TemporaryDirectory() as d:
            write_spec(Path(d), spec)
            with self.assertRaises(generate.SpecError):
                generate.build(Path(d))


class ClashTest(unittest.TestCase):

    def test_same_entity_name_in_two_specs_is_rejected(self) -> None:
        other = json.loads(json.dumps(GOOD))
        other["system"] = "other"
        with tempfile.TemporaryDirectory() as d:
            write_spec(Path(d), GOOD)
            (Path(d) / "t.json").write_text(json.dumps(other), encoding="utf-8")
            with self.assertRaises(generate.SpecError):
                generate.build(Path(d), Path(d) / "none")

    def test_name_of_hand_written_entity_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as d:
            write_spec(Path(d), GOOD)
            java = Path(d) / "java"
            java.mkdir()
            (java / "Room.java").write_text("@Entity\npublic class Room {}\n", encoding="utf-8")
            with self.assertRaises(generate.SpecError):
                generate.build(Path(d), java)

    def test_sql_reserved_field_name_is_rejected(self) -> None:
        spec = json.loads(json.dumps(GOOD))
        spec["entities"][0]["fields"].append({"name": "order", "type": "int"})
        with tempfile.TemporaryDirectory() as d:
            write_spec(Path(d), spec)
            with self.assertRaises(generate.SpecError):
                generate.build(Path(d), Path(d) / "none")

    def test_every_spec_in_the_repo_builds(self) -> None:
        files = generate.build(generate.SPECS)
        systems = json.loads(files[generate.FRONT_OUT])
        self.assertEqual(len(systems), len(list(generate.SPECS.glob("*.json"))))


if __name__ == "__main__":
    unittest.main()
