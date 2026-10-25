#!/usr/bin/env python3
"""Garante que cada RF tem sua pasta docs/RFn/ com os seis diagramas e que ela acompanha o código.

Uso:
    python3 scripts/check_rf_docs.py                    # estrutura
    python3 scripts/check_rf_docs.py --base origin/main # + desatualização em relação a um ref

Estrutura de cada RF (regras completas em docs/README.md):
    docs/RFn/README.md         cabeçalho (rf, titulo, status, cards, paths), critérios e rastreabilidade
    docs/RFn/casos-de-uso.md   docs/RFn/atividades.md   docs/RFn/classes.md
    docs/RFn/componentes.md    docs/RFn/sequencia.md    docs/RFn/estados.md
Cada arquivo de diagrama precisa ter ao menos um bloco Mermaid. Sai com código 1 se algo falhar.

Exceção por commit (o marcador precisa começar a linha da mensagem):
    [rf-docs-skip] motivo            ignora a desatualização de todos os RFs
    [rf-docs-skip: RF2, RF3] motivo  ignora só os RFs listados
"""
from __future__ import annotations

import argparse
import fnmatch
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
CATALOG = DOCS / "README.md"
REQUIRED = ("rf", "titulo", "status", "paths")
DIAGRAMS = ("casos-de-uso.md", "atividades.md", "classes.md", "componentes.md", "sequencia.md", "estados.md")
STATUSES = {"planejado", "parcial", "pronto"}
SKIP_MARK = "[rf-docs-skip]"
# Só no início de uma linha: citar o marcador no meio de um texto não deve desligar a verificação.
SKIP_RE = re.compile(r"^\[rf-docs-skip(?::\s*([^\]]*))?\]", re.M)


def parse_front_matter(text: str) -> dict:
    """Lê o cabeçalho YAML simples (chave: valor, listas com '- ' e [a, b])."""
    m = re.match(r"^---\n(.*?)\n---\n", text, re.S)
    if not m:
        return {}
    data: dict = {}
    key = None
    for line in m.group(1).splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        item = re.match(r"^\s+-\s+(.*)$", line)
        if item and key:
            data.setdefault(key, []).append(item.group(1).split("  #")[0].strip())
            continue
        kv = re.match(r"^([A-Za-z_]+):\s*(.*)$", line)
        if kv:
            key, val = kv.group(1), kv.group(2).split("  #")[0].strip()
            if val.startswith("[") and val.endswith("]"):
                data[key] = [v.strip() for v in val[1:-1].split(",") if v.strip()]
            elif val:
                data[key] = val
            else:
                data[key] = []
    return data


def rf_folders() -> list[Path]:
    return sorted((p for p in DOCS.glob("RF*") if p.is_dir()), key=lambda p: int(re.sub(r"\D", "", p.name) or 0))


def load_docs() -> list[tuple[Path, dict]]:
    out = []
    for folder in rf_folders():
        readme = folder / "README.md"
        text = readme.read_text(encoding="utf-8") if readme.exists() else ""
        out.append((folder, parse_front_matter(text)))
    return out


def tracked_files() -> list[str]:
    out = subprocess.run(["git", "ls-files"], cwd=ROOT, capture_output=True, text=True, check=True).stdout
    return out.splitlines()


def matches(path: str, patterns: list[str]) -> bool:
    return any(fnmatch.fnmatch(path, pat) for pat in patterns)


def check_structure(docs, files: list[str]) -> list[str]:
    errors: list[str] = []
    catalog = CATALOG.read_text(encoding="utf-8") if CATALOG.exists() else ""
    for folder, fm in docs:
        rel = folder.relative_to(ROOT)
        if not re.fullmatch(r"RF\d+", folder.name):
            errors.append(f"{rel}: pasta fora do padrão docs/RFn")
            continue
        if not (folder / "README.md").exists():
            errors.append(f"{rel}: falta README.md com o cabeçalho do RF")
            continue
        missing = [k for k in REQUIRED if not fm.get(k)]
        if missing:
            errors.append(f"{rel}/README.md: cabeçalho sem {', '.join(missing)}")
            continue
        if fm["rf"] != folder.name:
            errors.append(f"{rel}/README.md: rf '{fm['rf']}' diferente do nome da pasta")
        if fm["status"] not in STATUSES:
            errors.append(f"{rel}/README.md: status '{fm['status']}' inválido ({', '.join(sorted(STATUSES))})")
        for name in DIAGRAMS:
            diagram = folder / name
            if not diagram.exists():
                errors.append(f"{rel}: falta {name}")
            elif "```mermaid" not in diagram.read_text(encoding="utf-8"):
                errors.append(f"{rel}/{name}: nenhum diagrama Mermaid")
        for pat in fm["paths"]:
            if not any(fnmatch.fnmatch(f, pat) for f in files):
                errors.append(f"{rel}/README.md: path '{pat}' não corresponde a nenhum arquivo versionado")
        if f"({folder.name}/" not in catalog:
            errors.append(f"{rel}: {folder.name} não está no catálogo docs/README.md")
    for link in re.findall(r"\((RF\d+)/", catalog):
        if not (DOCS / link).is_dir():
            errors.append(f"docs/README.md: link para {link}/ inexistente")
    return errors


def check_drift(docs, base: str) -> list[str]:
    rng = f"{base}...HEAD"
    changed = subprocess.run(["git", "diff", "--name-only", rng], cwd=ROOT,
                             capture_output=True, text=True, check=True).stdout.splitlines()
    messages = subprocess.run(["git", "log", "--format=%B", rng], cwd=ROOT,
                              capture_output=True, text=True, check=True).stdout
    skipped: set[str] = set()
    for m in SKIP_RE.finditer(messages):
        if not m.group(1):
            print(f"aviso: {SKIP_MARK} encontrado nos commits de {rng}; verificação de desatualização ignorada")
            return []
        skipped |= {rf.strip().upper() for rf in m.group(1).split(",") if rf.strip()}
    if skipped:
        print(f"aviso: desatualização ignorada para {', '.join(sorted(skipped))} ({SKIP_MARK} nos commits de {rng})")
    errors: list[str] = []
    for folder, fm in docs:
        rel = str(folder.relative_to(ROOT))
        if not fm.get("paths") or fm.get("rf", "").upper() in skipped:
            continue
        if any(f.startswith(rel + "/") for f in changed):
            continue
        touched = sorted(f for f in changed if matches(f, fm["paths"]))
        if touched:
            errors.append(f"{fm['rf']}: código mudou ({', '.join(touched)}) mas nada em {rel}/ foi atualizado")
    return errors


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--base", help="ref para comparar (ex.: origin/main ou um SHA)")
    args = ap.parse_args()

    docs = load_docs()
    errors = check_structure(docs, tracked_files())
    if args.base:
        errors += check_drift(docs, args.base)

    print(f"{len(docs)} pasta(s) de RF verificadas")
    for e in errors:
        print(f"ERRO {e}")
    if errors:
        print(f"\nVeja as regras em docs/README.md (use {SKIP_MARK} ou [rf-docs-skip: RFn] no início de uma linha "
              "do commit só para mudanças sem efeito no comportamento daquele RF).")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
