"""도서 본문을 읽지 않는 CSV 대비 후보 검토 양식 생성."""

import argparse
import csv
import json
import re
from pathlib import Path

AXES = {"결": "texture", "시선": "perspective", "어조": "tone", "태도": "attitude", "결말": "ending", "시대·배경": "setting"}


def prepare(source, output, metadata_path=None):
    with source.open(encoding="utf-8-sig", newline="") as stream:
        rows = list(csv.DictReader(stream))
    titles = {"".join(row["책"].split()): row["id"] for row in rows}
    if len(titles) != len(rows) or len({row["id"] for row in rows}) != len(rows):
        raise ValueError("작품 ID·제목 중복")
    hashes = {}
    if metadata_path:
        # prepare_catalog.py가 생성한 본문 제외 메타데이터만 입력
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        if any("text" in book or "pages" in book for book in metadata["books"]):
            raise ValueError("본문 제외 메타데이터 파일 필요")
        hashes = {book["id"]: book["textSHA256"] for book in metadata["books"]}
    candidates, seen = [], set()
    for row in rows:
        for column in ("대비 1", "대비 2", "대비 3"):
            match = re.fullmatch(r"(.+?)\s*\(([^)]+)\)\s*", row[column])
            if not match or match[2] not in AXES:
                raise ValueError("후보 형식·대비 축 확인 필요")
            target = titles["".join(match[1].split())]
            key = (row["id"], target, AXES[match[2]])
            if key in seen or row["id"] == target:
                raise ValueError("중복·동일 작품 후보")
            seen.add(key)
            candidates.append({"sourceBookId": row["id"], "targetBookId": target, "axis": key[2],
                               "commonTopic": row["주제"], "reason": None,
                               "sourceContentHash": hashes.get(row["id"]), "targetContentHash": hashes.get(target),
                               "sourceEvidence": None, "targetEvidence": None,
                               "reviewStatus": "pending", "reviewedBy": None})
    # 기존 사람 검수 내용을 덮어쓰지 않는 배타적 생성
    with output.open("x", encoding="utf-8") as stream:
        for candidate in candidates:
            stream.write(json.dumps(candidate, ensure_ascii=False, allow_nan=False) + "\n")
    return len(candidates)


def main():
    parser = argparse.ArgumentParser(description="CSV 후보를 pending JSONL 검토 양식으로 변환")
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--catalog-metadata", type=Path)
    args = parser.parse_args()
    try:
        count = prepare(args.input, args.output, args.catalog_metadata)
        print(f"대비 후보 양식 생성: {count}개, 모두 검토 대기")
    except Exception as error:
        print(f"양식 생성 실패: {type(error).__name__}")
        raise SystemExit(1) from None


if __name__ == "__main__":
    main()
