"""Conta lugares de comida e bebida do Overture por país (Brasil, Portugal e total mundial).

Uso: python -I overture_contagem_paises.py <release> <arquivo_saida.csv>
Lê só as colunas taxonomy, basic_category, addresses e confidence (projeção de colunas no Parquet remoto).
"""
import re
import sys
import urllib.request

import duckdb

BUCKET = "https://overturemaps-us-west-2.s3.us-west-2.amazonaws.com"


def main() -> None:
    release, out = sys.argv[1], sys.argv[2]
    prefix = f"release/{release}/theme=places/type=place/"
    with urllib.request.urlopen(f"{BUCKET}/?list-type=2&prefix={prefix}", timeout=60) as r:  # noqa: S310
        keys = re.findall(r"<Key>([^<]+\.parquet)</Key>", r.read().decode())
    con = duckdb.connect()
    con.sql("LOAD httpfs")
    con.sql("SET http_proxy='127.0.0.1:36891'")
    con.sql("SET ca_cert_file='/root/.ccr/ca-bundle.crt'")
    con.sql("SET memory_limit='3GB'")
    con.sql("SET threads=4")
    con.sql("SET preserve_insertion_order=false")
    con.sql("CREATE TABLE parcial (pais VARCHAR, lugares_comida BIGINT, conf_077 BIGINT, restaurantes BIGINT)")
    for i, k in enumerate(keys):  # um arquivo por vez para caber na memória
        con.sql(f"""
          INSERT INTO parcial
          SELECT addresses[1].country, count(*), count(*) FILTER (WHERE confidence >= 0.77),
                 count(*) FILTER (WHERE basic_category = 'restaurant')
          FROM read_parquet('{BUCKET}/{k}')
          WHERE taxonomy.hierarchy[1] = 'food_and_drink'
          GROUP BY ALL
        """)
        print(f"arquivo {i + 1}/{len(keys)} ok", flush=True)
    con.sql(f"""COPY (SELECT pais, sum(lugares_comida) lugares_comida, sum(conf_077) conf_077,
                     sum(restaurantes) restaurantes FROM parcial GROUP BY 1 ORDER BY 2 DESC) TO '{out}' (HEADER)""")
    print(con.sql(f"SELECT sum(lugares_comida), sum(conf_077), sum(restaurantes) FROM '{out}'").fetchall())
    print(con.sql(f"SELECT * FROM '{out}' WHERE pais IN ('BR','PT','US','FR','JP','IN','NG','MX') ").fetchall())


if __name__ == "__main__":
    main()
