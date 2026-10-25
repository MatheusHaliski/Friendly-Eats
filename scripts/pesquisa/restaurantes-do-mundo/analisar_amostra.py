"""Filtra comida e bebida na amostra do Overture e mede preenchimento e categorização.

Uso: python -I analisar_amostra.py <dir_dados>
Lê <dir_dados>/<regiao>_todos.parquet (gerado por overture_sample.py) e grava
<dir_dados>/<regiao>_comida.csv e imprime as métricas em Markdown.
"""
import sys

import duckdb

REGIOES = ["sp_pinheiros_vila_madalena", "lisboa_baixa_chiado"]

# raízes e categorias de comida e bebida que interessam ao Friendly Eats (tipos e formatos da taxonomia)
FILTRO = """
  split_part(tax_hierarchy, '>', 1) = 'food_and_drink'
  OR basic_category IN ('food_and_beverage_store','bakery','grocery_store','butcher_shop','supermarket',
                        'farmers_market','food_court','ice_cream_shop','liquor_store')
"""


def main() -> None:
    d = sys.argv[1]
    con = duckdb.connect()
    for r in REGIOES:
        src = f"'{d}/{r}_todos.parquet'"
        con.sql(f"CREATE OR REPLACE TABLE {r} AS SELECT * FROM {src} WHERE {FILTRO}")
        con.sql(f"COPY {r} TO '{d}/{r}_comida.csv' (HEADER)")
        q = lambda s: con.sql(s.format(t=r)).fetchall()  # noqa: E731
        n = q("SELECT count(*) FROM {t}")[0][0]
        print(f"\n### {r}: {n} lugares de comida e bebida")
        row = q("""
          SELECT round(100.0*count(telefone)/count(*),1),
                 round(100.0*count(site)/count(*),1),
                 round(100.0*count(*) FILTER (WHERE n_socials > 0)/count(*),1),
                 round(100.0*count(endereco)/count(*),1),
                 round(100.0*count(cep)/count(*),1),
                 round(100.0*count(marca)/count(*),1),
                 round(100.0*count(*) FILTER (WHERE tax_primary LIKE '%\\_restaurant' ESCAPE '\\'
                                                AND tax_primary <> 'restaurant')/count(*),1),
                 round(100.0*count(*) FILTER (WHERE confidence >= 0.77)/count(*),1),
                 round(100.0*count(*) FILTER (WHERE confidence < 0.5)/count(*),1),
                 round(median(confidence),3),
                 round(100.0*count(operating_status)/count(*),1),
                 count(*) FILTER (WHERE operating_status IN ('permanently_closed','temporarily_closed'))
          FROM {t}""")[0]
        print("| métrica | valor |\n|---|---|")
        nomes = ["% com telefone", "% com site", "% com rede social", "% com endereço (freeform)", "% com CEP",
                 "% com marca (rede)", "% com culinária no tipo (xxx_restaurant)", "% confiança >= 0,77",
                 "% confiança < 0,5", "mediana da confiança", "% com operating_status preenchido",
                 "fechados (temp. ou de vez)"]
        for k, v in zip(nomes, row):
            print(f"| {k} | {v} |")
        print("\nTop 25 taxonomy.primary:")
        print(q("SELECT tax_primary, count(*) n FROM {t} GROUP BY 1 ORDER BY 2 DESC LIMIT 25"))
        print("Top basic_category:")
        print(q("SELECT basic_category, count(*) n FROM {t} GROUP BY 1 ORDER BY 2 DESC LIMIT 15"))
        print("Distribuição por ano da última atualização da fonte:")
        print(q("SELECT substr(ultima_atualizacao_fonte,1,4) ano, count(*) FROM {t} GROUP BY 1 ORDER BY 1"))
        print("Distribuição por fonte:")
        print(q("SELECT datasets, count(*) FROM {t} GROUP BY 1 ORDER BY 2 DESC"))
        print("Amostra (12 com confiança alta):")
        for x in q("""SELECT nome, tax_primary, basic_category, round(confidence,2), localidade, telefone IS NOT NULL
                      FROM {t} WHERE confidence >= 0.77 ORDER BY hash(id) LIMIT 12"""):
            print("  ", x)
        print("Amostra (8 com confiança baixa):")
        for x in q("""SELECT nome, tax_primary, round(confidence,2), endereco
                      FROM {t} WHERE confidence < 0.5 ORDER BY hash(id) LIMIT 8"""):
            print("  ", x)


if __name__ == "__main__":
    main()
