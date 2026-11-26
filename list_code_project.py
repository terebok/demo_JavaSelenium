import datetime
from pathlib import Path
import re

# Определяем директорию, где находится сам скрипт, и пути для выходных файлов
FOLDER_PATH = Path(__file__).resolve().parent
OUTPUT_FILE = FOLDER_PATH / "project_dump.txt"
MAPPING_FILE = FOLDER_PATH / "project_dump_keys.txt"

# Список директорий, которые нужно игнорировать при обходе
SKIP_DIRS = {"target", ".idea", ".allure", ".git", ".venv", "venv", "node_modules"}

# Список бинарных расширений, содержимое которых не нужно читать
BINARY_EXTS = {".png", ".gif", ".jpg", ".jpeg", ".ico", ".jar", ".class",
               ".so", ".exe", ".dll", ".zip"}

# Список конкретных данных для обезличивания.
# Сортируем по убыванию длины, чтобы более длинные строки заменялись раньше коротких.
EXACT_MATCHES = sorted([
    r"C:\AT_Project\demo_JavaSelenium",
    "https://terebok.github.io/demo_JavaSelenium/",
    "https://www.saucedemo.com",
    "demo_JavaSelenium",
    "terebok",
    "standard_user",
    "secret_sauce"
], key=len, reverse=True)

# Регулярные выражения для поиска общих категорий данных (email, локальные пути, IP)
PATTERNS = [
    (r'[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}', 'EMAIL'),
    (r'[A-Za-z]:\\[^:*?"<>|\r\n]+', 'LOCAL_PATH'),
    (r'\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b', 'IP')
]

class Obfuscator:
    """Класс для замены чувствительных данных на уникальные метки."""
    
    def __init__(self):
        # Словарь для хранения соответствий: "метка" -> "оригинальное значение"
        self.mapping = {}
        # Счетчик для генерации уникальных меток
        self.counter = 1

    def mask_text(self, text: str) -> str:
        """Принимает текст, находит в нем секреты и заменяет их на метки."""
        if not text:
            return text

        # 1. Замена точных совпадений из списка
        for match in EXACT_MATCHES:
            if match in text:
                text = text.replace(match, self._get_mask(match, "MASK"))

        # 2. Замена по регулярным выражениям для общих категорий
        for pattern, label in PATTERNS:
            text = re.sub(pattern, lambda m: self._get_mask(m.group(0), label), text)

        return text

    def _get_mask(self, original: str, label: str) -> str:
        """Генерирует или возвращает существующую метку для оригинального значения."""
        # Проверяем, есть ли уже метка для этого значения, чтобы не дублировать
        for k, v in self.mapping.items():
            if v == original:
                return k
        
        # Создаем новую уникальную метку
        mask = f"[[{label}_{self.counter}]]"
        self.mapping[mask] = original
        self.counter += 1
        return mask

    def save_mapping(self, filepath: Path):
        """Сохраняет словарь с расшифровкой меток в отдельный файл."""
        with open(filepath, "w", encoding="utf-8") as f:
            f.write("# СЛОВАРЬ ОБЕЗЛИЧЕННЫХ ДАННЫХ\n")
            f.write("# Формат: [МЕТКА] = ОРИГИНАЛЬНОЕ ЗНАЧЕНИЕ\n")
            f.write("=" * 80 + "\n\n")
            for mask, original in self.mapping.items():
                f.write(f"{mask} = {original}\n")


def collect_files(root: Path):
    """Генератор для сбора всех файлов проекта с учетом исключений."""
    for path in sorted(root.rglob("*")):
        if any(part in SKIP_DIRS for part in path.relative_to(root).parts):
            continue
        if path.is_symlink():
            continue
        if path.is_file():
            yield path

def print_tree(directory: Path, obfuscator: Obfuscator, prefix: str = "", max_depth: int = None,
               depth: int = 0, lines=None):
    """Рекурсивно строит дерево каталогов, обезличивая имена файлов и папок."""
    if max_depth is not None and depth >= max_depth:
        return
    entries = sorted(
        (e for e in directory.iterdir() if e.name not in SKIP_DIRS and not e.is_symlink()),
        key=lambda e: (e.is_file(), e.name.lower()),
    )
    for i, entry in enumerate(entries):
        connector = "└── " if i == len(entries) - 1 else "├── "
        suffix = "/" if entry.is_dir() else ""
        
        # Обезличиваем имя текущего элемента (файла или папки)
        safe_name = obfuscator.mask_text(entry.name)
        lines.append(f"{prefix}{connector}{safe_name}{suffix}")
        
        if entry.is_dir():
            extension = "    " if i == len(entries) - 1 else "│   "
            print_tree(entry, obfuscator, prefix + extension, max_depth, depth + 1, lines)

def main():
    root = Path(FOLDER_PATH).resolve()
    if not root.is_dir():
        raise SystemExit(f"Ошибка: {root} не является каталогом")

    # Инициализируем объект для обезличивания
    obfuscator = Obfuscator()

    out_lines = []
    out_lines.append("# СТРУКТУРА ПРОЕКТА ДЛЯ АНАЛИЗА ИИ")
    
    # Обезличиваем корневой путь в заголовке файла
    safe_root_str = obfuscator.mask_text(str(root))
    out_lines.append(f"# Корневой каталог: {safe_root_str}")
    out_lines.append(f"# Сгенерировано: {datetime.datetime.now():%Y-%m-%d %H:%M:%S}")
    out_lines.append("=" * 80)
    out_lines.append("")

    out_lines.append("## ДЕРЕВО КАТАЛОГОВ")
    out_lines.append("```text")
    out_lines.append(f"{safe_root_str}/")
    # Передаем обезличиватель в функцию построения дерева
    print_tree(root, obfuscator, max_depth=None, lines=out_lines)
    out_lines.append("```")
    out_lines.append("")
    out_lines.append("=" * 80)
    out_lines.append("")

    out_lines.append("## СОДЕРЖИМОЕ ФАЙЛОВ")
    out_lines.append("")

    output_path = Path(OUTPUT_FILE).resolve()

    count = 0
    for path in collect_files(root):
        rel = path.relative_to(root)
        # Пропускаем сам дамп и файл словаря, чтобы не зациклиться
        if path.resolve() == output_path or path.resolve() == MAPPING_FILE.resolve():
            continue
        
        # Обезличиваем относительный путь файла в заголовке
        safe_rel = obfuscator.mask_text(rel.as_posix())

        if path.suffix.lower() in BINARY_EXTS:
            out_lines.append(f"### ФАЙЛ: {safe_rel}  [пропущен: двоичный]")
            out_lines.append("")
            continue
        try:
            content = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            content = path.read_text(encoding="cp1251", errors="replace")
        except PermissionError:
            out_lines.append(f"### ФАЙЛ: {safe_rel}  [нет доступа]")
            out_lines.append("")
            continue

        # Обезличиваем содержимое файла перед добавлением в дамп
        safe_content = obfuscator.mask_text(content)

        out_lines.append(f"### ФАЙЛ: {safe_rel}")
        out_lines.append("```")
        out_lines.append(safe_content)
        out_lines.append("```")
        out_lines.append("")
        count += 1

    out_lines.append("=" * 80)
    out_lines.append(f"# ИТОГО ФАЙЛОВ В ДАМПЕ: {count}")

    # Сохраняем обезличенный дамп
    Path(OUTPUT_FILE).write_text("\n".join(out_lines), encoding="utf-8")
    
    # Сохраняем словарь с оригинальными значениями для последующей расшифровки
    obfuscator.save_mapping(MAPPING_FILE)

    print(f"Готово: {count} файлов сохранено в {OUTPUT_FILE}")
    print(f"Словарь замен сохранен в {MAPPING_FILE}")

if __name__ == "__main__":
    main()