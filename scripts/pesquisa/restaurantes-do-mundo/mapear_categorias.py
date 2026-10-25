"""Protótipo do mapeamento Overture taxonomy -> taxonomia do Friendly Eats (tipo, culinária, formato, estilo).

Uso: python -I mapear_categorias.py <dir_dados>
Lê <dir_dados>/*_comida.csv e grava <dir_dados>/mapeamento_resultado.csv e a cobertura em texto.

Regras (iguais às propostas no RECOMENDACAO.md, seção 4):
  1. Sobe a hierarquia do nó mais específico para a raiz; o primeiro nó com TIPO define o tipo.
  2. Formato: o primeiro nó com FORMATO define o formato. Se o nó mais específico é um formato
     (pizza_restaurant, sushi_restaurant), a culinária NÃO é herdada dos ancestrais: a culinária sugerida
     pelo formato nunca é aplicada sozinha (taxonomia, seção 4.1).
  3. Culinária: o primeiro nó com CULINÁRIA, só enquanto nenhum formato foi encontrado.
  4. Estilo (special_diet_restaurant > vegan...) vira dica de estilo, revisada pelo dono.
  5. Nada mapeado -> fila de revisão (tipo genérico pelo ramo).
"""
import csv
import glob
import os
import sys
from collections import Counter

TIPO = {
    "restaurant": "RESTAURANTE_A_LA_CARTE", "buffet_restaurant": "RESTAURANTE_BUFFET",
    "bistro": "BISTRO", "diner": "LANCHONETE", "casual_eatery": "LANCHONETE",
    "fast_food_restaurant": "FAST_FOOD", "sandwich_shop": "LANCHONETE", "food_truck_stand": "FOOD_TRUCK",
    "food_court": "PRACA_ALIMENTACAO", "cafe": "CAFETERIA", "coffee_shop": "CAFETERIA",
    "bakery": "PADARIA", "bagel_shop": "PADARIA", "dessert_shop": "CONFEITARIA", "ice_cream_shop": "SORVETERIA",
    "candy_store": "DOCES_BALAS", "smoothie_juice_bar": "ACAITERIA_SUCOS", "tea_room": "CASA_CHA",
    "bubble_tea_shop": "CASA_CHA", "bar": "BAR", "lounge": "BAR", "cocktail_bar": "BAR_COQUETELARIA",
    "speakeasy": "BAR_COQUETELARIA", "whiskey_bar": "BAR_COQUETELARIA", "wine_bar": "BAR_VINHOS",
    "pub": "GASTROBAR", "gastropub": "GASTROBAR", "beer_bar": "CERVEJARIA", "beer_garden": "CERVEJARIA",
    "brewery": "CERVEJARIA", "winery": "VINICOLA_DESTILARIA", "tapas_bar": "BAR",
    "grocery_store": "MERCEARIA", "supermarket": "SUPERMERCADO", "liquor_store": "ADEGA",
    "beer_wine_spirits_store": "ADEGA", "health_food_store": "LOJA_NATURAL",
    "organic_grocery_store": "LOJA_NATURAL", "butcher_shop": "ACOUGUE", "fishmonger": "PEIXARIA",
    "produce_store": "HORTIFRUTI", "cheese_shop": "QUEIJARIA_FRIOS", "delicatessen": "EMPORIO",
    "specialty_foods_store": "EMPORIO", "farmers_market": "FEIRA_LIVRE", "food_and_drink": None,
}
# categorias que NÃO entram (não são lugar de comida para o Friendly Eats)
EXCLUIR = {"tobacco_shop", "vitamin_and_supplement_store", "internet_cafe", "hookah_bar",
           "coffee_and_tea_supplies", "food_and_beverage_store", "hotel_bar"}

FORMATO = {
    "pizza_restaurant": "PIZZA", "burger_restaurant": "HAMBURGUER", "sushi_restaurant": "SUSHI",
    "poke_restaurant": "POKE", "steakhouse": "CHURRASCO", "barbecue_restaurant": "CHURRASCO",
    "chicken_restaurant": "FRANGO", "hot_dog_restaurant": "CACHORRO_QUENTE",
    "seafood_restaurant": "FRUTOS_DO_MAR", "ramen_restaurant": "LAMEN_NOODLES", "soup_restaurant": "SOPAS_CALDOS",
    "salad_bar": "SALADAS_BOWLS", "pancake_house": "CREPE_PANQUECA", "donut_shop": "DOCES_CONFEITARIA",
    "cupcake_shop": "DOCES_CONFEITARIA", "dessert_shop": "DOCES_CONFEITARIA", "chocolatier": "DOCES_CONFEITARIA",
    "ice_cream_shop": "SORVETE", "gelato_shop": "SORVETE", "frozen_yogurt_shop": "SORVETE",
    "bakery": "PAES", "bagel_shop": "PAES", "coffee_shop": "CAFE", "cafe": "CAFE", "tea_room": "CHA",
    "bubble_tea_shop": "CHA", "smoothie_juice_bar": "SUCOS_SMOOTHIES", "sandwich_shop": "SANDUICHE",
    "empanada_restaurant": "SALGADOS", "taco_restaurant": "TACOS_BURRITOS",
    "doner_kebab_restaurant": "KEBAB_SHAWARMA", "tapas_bar": "PETISCOS_BOTECO", "beer_bar": "CERVEJA",
    "brewery": "CERVEJA", "beer_garden": "CERVEJA", "wine_bar": "VINHO", "winery": "VINHO",
    "cocktail_bar": "COQUETEIS_DESTILADOS", "whiskey_bar": "COQUETEIS_DESTILADOS",
}

CULINARIA = {
    "brazilian_restaurant": "BRASILEIRA", "latin_american_restaurant": "LATINO_AMERICANA",
    "south_american_restaurant": "LATINO_AMERICANA", "argentine_restaurant": "ARGENTINA",
    "peruvian_restaurant": "PERUANA", "mexican_restaurant": "MEXICANA", "colombian_restaurant": "COLOMBIANA",
    "venezuelan_restaurant": "VENEZUELANA", "chilean_restaurant": "CHILENA", "caribbean_restaurant": "CARIBENHA",
    "cuban_restaurant": "CARIBENHA", "north_american_restaurant": "NORTE_AMERICANA",
    "american_restaurant": "NORTE_AMERICANA", "texmex_restaurant": "TEX_MEX",
    "southern_american_restaurant": "SUL_DOS_EUA", "hawaiian_restaurant": "HAVAIANA",
    "european_restaurant": "EUROPEIA", "italian_restaurant": "ITALIANA", "portuguese_restaurant": "PORTUGUESA",
    "french_restaurant": "FRANCESA", "spanish_restaurant": "ESPANHOLA", "basque_restaurant": "ESPANHOLA",
    "german_restaurant": "ALEMA", "austrian_restaurant": "ALEMA", "greek_restaurant": "GREGA",
    "mediterranean_restaurant": "MEDITERRANEA", "british_restaurant": "BRITANICA_IRLANDESA",
    "irish_pub": "BRITANICA_IRLANDESA", "polish_restaurant": "LESTE_EUROPEIA",
    "eastern_european_restaurant": "LESTE_EUROPEIA", "russian_restaurant": "LESTE_EUROPEIA",
    "middle_eastern_restaurant": "ORIENTE_MEDIO", "arabian_restaurant": "ARABE", "lebanese_restaurant": "ARABE",
    "syrian_restaurant": "ARABE", "turkish_restaurant": "TURCA", "persian_restaurant": "PERSA",
    "israeli_restaurant": "JUDAICA_ISRAELENSE", "armenian_restaurant": "ARMENIA",
    "moroccan_restaurant": "MARROQUINA", "african_restaurant": "AFRICANA", "ethiopian_restaurant": "ETIOPE",
    "senegalese_restaurant": "SENEGALESA", "asian_restaurant": "ASIATICA", "japanese_restaurant": "JAPONESA",
    "chinese_restaurant": "CHINESA", "taiwanese_restaurant": "TAIWANESA", "korean_restaurant": "COREANA",
    "thai_restaurant": "TAILANDESA", "vietnamese_restaurant": "VIETNAMITA", "indian_restaurant": "INDIANA",
    "asian_fusion_restaurant": "ASIATICA",
}
ESTILO = {"vegan_restaurant": ("VEGANO", "EXCLUSIVO"), "vegetarian_restaurant": ("VEGETARIANO", "EXCLUSIVO"),
          "halal_restaurant": ("HALAL", "OFERECE"), "gluten_free_restaurant": ("SEM_GLUTEN", "OFERECE")}
ABORDAGEM = {"asian_fusion_restaurant": "FUSION", "international_fusion_restaurant": "FUSION"}


def mapear(hierarquia: str) -> dict:
    nos = [n for n in (hierarquia or "").split(">") if n]
    if not nos:
        return {"status": "SEM_CATEGORIA"}
    if nos[-1] in EXCLUIR:
        return {"status": "EXCLUIDO"}
    tipo = formato = culinaria = estilo = abordagem = None
    for i, no in enumerate(reversed(nos)):
        if tipo is None and TIPO.get(no):
            tipo = TIPO[no]
        if formato is None and no in FORMATO:
            formato = FORMATO[no]
        if culinaria is None and formato is None and no in CULINARIA:
            culinaria = CULINARIA[no]
        if i == 0 and no in ESTILO:
            estilo = ESTILO[no]
        if i == 0 and no in ABORDAGEM:
            abordagem = ABORDAGEM[no]
    if tipo is None:
        tipo = "RESTAURANTE_A_LA_CARTE" if "restaurant" in nos else None
    if abordagem == "FUSION" and culinaria is None:
        abordagem = "VARIADA"  # FUSION sem culinária é recusado pelo RF7; vira VARIADA e vai para revisão
    status = "OK" if tipo and (culinaria or formato or estilo or tipo in VAREJO_POLO) else "REVISAR"
    return {"status": status, "tipo": tipo, "culinaria": culinaria, "formato": formato,
            "estilo": "=".join(estilo) if estilo else None, "abordagem": abordagem}


VAREJO_POLO = {"MERCEARIA", "SUPERMERCADO", "ADEGA", "LOJA_NATURAL", "ACOUGUE", "PEIXARIA", "HORTIFRUTI",
               "QUEIJARIA_FRIOS", "EMPORIO", "DOCES_BALAS", "FEIRA_LIVRE", "PRACA_ALIMENTACAO"}


def main() -> None:
    d = sys.argv[1]
    linhas = []
    for f in sorted(glob.glob(os.path.join(d, "*_comida.csv"))):
        regiao = os.path.basename(f).replace("_comida.csv", "")
        with open(f, newline="", encoding="utf-8") as fh:
            for row in csv.DictReader(fh):
                m = mapear(row["tax_hierarchy"])
                linhas.append({"regiao": regiao, "id": row["id"], "nome": row["nome"],
                               "overture": row["tax_primary"], **m})
    out = os.path.join(d, "mapeamento_resultado.csv")
    campos = ["regiao", "id", "nome", "overture", "status", "tipo", "culinaria", "formato", "estilo", "abordagem"]
    with open(out, "w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=campos)
        w.writeheader()
        w.writerows(linhas)
    for regiao in sorted({x["regiao"] for x in linhas}):
        xs = [x for x in linhas if x["regiao"] == regiao]
        n = len(xs)
        st = Counter(x["status"] for x in xs)
        ok = [x for x in xs if x["status"] == "OK"]
        com_cul = sum(1 for x in xs if x.get("culinaria"))
        com_fmt = sum(1 for x in xs if x.get("formato"))
        so_tipo = sum(1 for x in xs if x.get("tipo") and not x.get("culinaria") and not x.get("formato"))
        print(f"\n### {regiao}: {n} lugares")
        print(f"status: {dict(st)}")
        print(f"com culinária: {com_cul} ({100*com_cul/n:.1f}%) · com formato: {com_fmt} ({100*com_fmt/n:.1f}%)"
              f" · só tipo: {so_tipo} ({100*so_tipo/n:.1f}%) · OK: {len(ok)} ({100*len(ok)/n:.1f}%)")
        print("culinárias:", Counter(x["culinaria"] for x in xs if x.get("culinaria")).most_common(12))
        print("tipos:", Counter(x["tipo"] for x in xs if x.get("tipo")).most_common(12))


if __name__ == "__main__":
    main()
