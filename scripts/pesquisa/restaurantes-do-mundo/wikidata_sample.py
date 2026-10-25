"""Conta restaurantes notáveis no Wikidata (CC0) em São Paulo e Lisboa, com coordenada.

Uso: python -I wikidata_sample.py <dir_saida>
Grava <dir_saida>/wikidata_<cidade>.csv. Dado tratado como não confiável (só lido como texto).
"""
import csv
import json
import os
import sys
import urllib.parse
import urllib.request

# centro e raio de 12 km (wikibase:around usa o índice geográfico e não estoura o tempo)
CIDADES = {"sao_paulo": "Point(-46.6333 -23.5505)", "lisboa": "Point(-9.1393 38.7223)"}
QUERY = """
SELECT DISTINCT ?item ?itemLabel ?coord ?cuisineLabel ?site ?inception ?dissolved WHERE {{
  SERVICE wikibase:around {{ ?item wdt:P625 ?coord .
    bd:serviceParam wikibase:center "{qid}"^^geo:wktLiteral ; wikibase:radius "12" . }}
  ?item wdt:P31/wdt:P279* wd:Q11707 .
  OPTIONAL {{ ?item wdt:P2012 ?cuisine . }}
  OPTIONAL {{ ?item wdt:P856 ?site . }}
  OPTIONAL {{ ?item wdt:P571 ?inception . }}
  OPTIONAL {{ ?item wdt:P576 ?dissolved . }}
  SERVICE wikibase:label {{ bd:serviceParam wikibase:language "pt,en". }}
}}
"""


def main() -> None:
    out = sys.argv[1]
    for nome, qid in CIDADES.items():
        url = "https://query.wikidata.org/sparql?format=json&query=" + urllib.parse.quote(QUERY.format(qid=qid))
        req = urllib.request.Request(url, headers={"User-Agent": "FriendlyEatsResearch/0.1 (pesquisa; contato via repo)"})
        with urllib.request.urlopen(req, timeout=90) as r:  # noqa: S310
            data = json.load(r)
        rows = data["results"]["bindings"]
        itens = {b["item"]["value"] for b in rows}
        com_cul = {b["item"]["value"] for b in rows if "cuisineLabel" in b}
        fechados = {b["item"]["value"] for b in rows if "dissolved" in b}
        print(f"{nome}: {len(itens)} restaurantes com coordenada; {len(com_cul)} com culinária (P2012); "
              f"{len(fechados)} com data de dissolução (P576)")
        with open(os.path.join(out, f"wikidata_{nome}.csv"), "w", newline="", encoding="utf-8") as fh:
            w = csv.writer(fh)
            w.writerow(["item", "nome", "coord", "culinaria", "site", "fundacao", "dissolucao"])
            for b in rows:
                w.writerow([b.get(k, {}).get("value", "") for k in
                            ["item", "itemLabel", "coord", "cuisineLabel", "site", "inception", "dissolved"]])


if __name__ == "__main__":
    main()
