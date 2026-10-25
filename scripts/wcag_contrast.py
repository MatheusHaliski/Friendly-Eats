#!/usr/bin/env python3
"""Verifica o contraste WCAG 2.x das cores do design system Friendly Eats.

Uso:
    python3 scripts/wcag_contrast.py            # tabela completa
    python3 scripts/wcag_contrast.py "#48A8FF" "#17120D"   # um par

Limiares: 4.5:1 texto normal (AA), 3:1 texto grande e elementos não textuais (1.4.11).
"""
import sys

DARK_BG = "#17120D"   # warm charcoal (fundo do tema escuro)
LIGHT_BG = "#FFFFFF"

# (nome, cor do tema escuro, variante do tema claro) · tipos de conta (UserCategoryEnum)
CATEGORIES = [
    ("COMENSAL",                  "#48A8FF", "#05558F"),
    ("CRIADOR",                   "#F7C638", "#876A06"),
    ("CHEF",                      "#D6C5FF", "#7D5BBA"),
    ("ESTABELECIMENTO",           "#E88F12", "#7F4D06"),
    ("VAREJO · marca",            "#EE559F", "#96075A"),
    ("COMUNIDADE",                "#08DABD", "#0B8170"),
    ("OFICIAL",                   "#0C0A07", "#0C0A07"),
    ("Anel p/ OFICIAL no escuro", "#D6D0C8", None),
]


def _channel(c: int) -> float:
    c /= 255.0
    return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4


def luminance(hex_color: str) -> float:
    h = hex_color.lstrip("#")
    r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4))
    return 0.2126 * _channel(r) + 0.7152 * _channel(g) + 0.0722 * _channel(b)


def contrast(fg: str, bg: str) -> float:
    l1, l2 = sorted((luminance(fg), luminance(bg)), reverse=True)
    return (l1 + 0.05) / (l2 + 0.05)


def verdict(ratio: float) -> str:
    if ratio >= 4.5:
        return "AA texto"
    if ratio >= 3.0:
        return "só não-textual"
    return "REPROVA"


def main(argv: list[str]) -> int:
    if len(argv) == 3:
        r = contrast(argv[1], argv[2])
        print(f"{argv[1]} sobre {argv[2]}: {r:.2f}:1 → {verdict(r)}")
        return 0

    print(f"{'Cor':<28}{'Hex':<10}{'escuro':>8}  {'veredito':<16}{'branco':>8}  {'veredito':<16}{'claro→':<10}{'branco':>8}  veredito")
    print("-" * 122)
    for name, hex_color, light_variant in CATEGORIES:
        on_dark = contrast(hex_color, DARK_BG)
        on_light = contrast(hex_color, LIGHT_BG)
        line = (f"{name:<28}{hex_color:<10}{on_dark:>7.2f}  {verdict(on_dark):<16}"
                f"{on_light:>7.2f}  {verdict(on_light):<16}")
        if light_variant and light_variant != hex_color:
            lv = contrast(light_variant, LIGHT_BG)
            line += f"{light_variant:<10}{lv:>7.2f}  {verdict(lv)}"
        print(line)

    print()
    print("Regra: no tema escuro a cor de cada tipo precisa de 4,5:1 sobre #17120D (o preto OFICIAL usa o anel);")
    print("no tema claro vale a variante. Cor + glifo + rótulo sempre juntos (WCAG 1.4.1).")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
