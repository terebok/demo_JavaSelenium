#!/usr/bin/env python3
"""Восстанавливает проект из дампа, расшифровывая метки обезличивания."""

import re
from pathlib import Path

# ==== НАСТРОЙКИ ====

FOLDER_PATH = Path(__file__).resolve().parent

DUMP_FILE =  FOLDER_PATH / "project_dump.txt"      # имя файла дампа (обезличенного)
KEYS_FILE =  FOLDER_PATH / "project_dump_keys.txt" # имя файла со словарем меток
TARGET_DIR = FOLDER_PATH                    # каталог, куда восстанавливать
# ===================

FILE_HEADER = "### ФАЙЛ:"
CONTENT_HEADER = "## СОДЕРЖИМОЕ ФАЙЛОВ"

def load_mapping(keys_file: Path) -> dict:
    """Читает файл ключей и возвращает словарь для расшифровки меток."""
    mapping = {}
    if not keys_file.is_file():
        print(f"Внимание: Файл ключей {keys_file} не найден. Метки не будут заменены.")
        return mapping

    # Читаем файл построчно
    for line in keys_file.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        # Пропускаем пустые строки, заголовки и разделители
        if not line or line.startswith("#") or line.startswith("="):
            continue
        # Разделяем строку по первому знаку "="
        if "=" in line:
            mask, original = line.split("=", 1)
            # Сохраняем в словарь: ключ - метка, значение - оригинал
            mapping[mask.strip()] = original.strip()
    return mapping

def replace_masks(text: str, mapping: dict) -> str:
    """Заменяет все метки в тексте на оригинальные значения из словаря."""
    if not text or not mapping:
        return text
    
    # Проходим по всем парам "метка: оригинал" и делаем замену
    for mask, original in mapping.items():
        text = text.replace(mask, original)
        
    return text

def parse_dump(text: str):
    """Парсит текст дампа и извлекает пути файлов, маркеры и их содержимое."""
    lines = text.splitlines()
    try:
        start = next(i for i, l in enumerate(lines) if l.strip() == CONTENT_HEADER)
    except StopIteration:
        raise SystemExit("В дампе не найден раздел '## СОДЕРЖИМОЕ ФАЙЛОВ'")

    i = start + 1
    files = []

    while i < len(lines):
        line = lines[i]
        if not line.startswith(FILE_HEADER):
            i += 1
            continue

        raw_title = line[len(FILE_HEADER):].strip()
        marker = None
        # Ищем маркер пропуска (например, [пропущен: двоичный])
        m = re.match(r"^(.*?)\s*\[(.*?)\]\s*$", raw_title)
        if m:
            path_str = m.group(1).strip()
            marker = m.group(2).strip()
        else:
            path_str = raw_title

        i += 1
        # Пропускаем открывающие ``` 
        if i < len(lines) and lines[i].lstrip().startswith("```"):
            i += 1

        content_lines = []
        while i < len(lines):
            cur = lines[i]
            if cur == "```":
                k = i + 1
                while k < len(lines) and lines[k].strip() == "":
                    k += 1
                # Проверяем, является ли ``` закрывающим для текущего файла
                if k >= len(lines) or lines[k].startswith(FILE_HEADER) or lines[k].startswith("="):
                    i += 1
                    while i < len(lines) and lines[i].strip() == "":
                        i += 1
                    break
            content_lines.append(cur)
            i += 1

        files.append((path_str, marker, "\n".join(content_lines)))

    return files

def safe_target(target_dir: Path, rel_path: str) -> Path:
    """Проверяет безопасность пути, чтобы файлы не записались вне целевого каталога."""
    rel_path = rel_path.replace("\\", "/").lstrip("/")
    if re.match(r"^[A-Za-z]:", rel_path):
        raise ValueError(f"Абсолютный путь запрещён: {rel_path}")
    if ".." in Path(rel_path).parts:
        raise ValueError(f"Выход за пределы каталога запрещён: {rel_path}")
    dst = (target_dir / rel_path).resolve()
    if target_dir.resolve() not in dst.parents and dst != target_dir.resolve():
        raise ValueError(f"Путь вне целевого каталога: {rel_path}")
    return dst

def main():
    dump_file = Path(DUMP_FILE)
    keys_file = Path(KEYS_FILE)
    target_dir = Path(TARGET_DIR)

    if not dump_file.is_file():
        raise SystemExit(f"Дамп не найден: {dump_file.resolve()}")

    # Загружаем словарь с оригинальными значениями
    mapping = load_mapping(keys_file)

    target_dir.mkdir(parents=True, exist_ok=True)
    text = dump_file.read_text(encoding="utf-8", errors="replace")
    files = parse_dump(text)

    created = skipped = errors = 0

    for rel_path, marker, content in files:
        # Пропускаем бинарные файлы и файлы без доступа
        if marker:
            print(f"[SKIP] {rel_path}  ({marker})")
            skipped += 1
            continue

        # Восстанавливаем оригинальный путь файла
        real_rel_path = replace_masks(rel_path, mapping)
        
        # Восстанавливаем оригинальное содержимое файла
        real_content = replace_masks(content, mapping)

        try:
            dst = safe_target(target_dir, real_rel_path)
        except ValueError as e:
            print(f"[ERR ] {e}")
            errors += 1
            continue

        # Создаем директории и записываем файл
        dst.parent.mkdir(parents=True, exist_ok=True)
        with open(dst, "w", encoding="utf-8", newline="\n") as f:
            f.write(real_content)
            
        print(f"[OK  ] {real_rel_path}")
        created += 1

    print("=" * 60)
    print(f"Создано файлов: {created}")
    print(f"Пропущено:      {skipped}")
    print(f"Ошибок:         {errors}")

if __name__ == "__main__":
    main()