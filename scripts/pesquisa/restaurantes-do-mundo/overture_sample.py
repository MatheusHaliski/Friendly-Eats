"""Baixa uma amostra pequena do Overture Maps Places (lugares de comida e bebida) por bbox.

Uso: python -I overture_sample.py <release> <dir_saida>
  ex.: python -I overture_sample.py 2026-10-21.0 ../data

- Lê os GeoParquet do bucket público (sem chave) por HTTPS, com filtro de bbox empurrado para as estatísticas
  dos row groups (só baixa os pedaços que cruzam a caixa).
- Grava um Parquet e um CSV por região em <dir_saida>. Os dados baixados são tratados como não confiáveis:
  nada deles é executado, só lido como colunas.
"""
import re
import sys
import urllib.request

import duckdb

BUCKET = "https://overturemaps-us-west-2.s3.us-west-2.amazonaws.com"

REGIOES = {
    # Pinheiros + Vila Madalena, São Paulo (aprox. 3 km x 3,3 km)
    "sp_pinheiros_vila_madalena": (-46.705, -23.575, -46.675, -23.545),
    # Baixa, Chiado, Bairro Alto e Príncipe Real, Lisboa (aprox. 2,2 km x 1,7 km)
    "lisboa_baixa_chiado": (-9.155, 38.705, -9.130, 38.720),
}


def listar_arquivos(release: str) -> list[str]:
    prefix = f"release/{release}/theme=places/type=place/"
    url = f"{BUCKET}/?list-type=2&prefix={prefix}"
    with urllib.request.urlopen(url, timeout=60) as r:  # noqa: S310 (URL fixa)
        xml = r.read().decode()
    keys = re.findall(r"<Key>([^<]+\.parquet)</Key>", xml)
    if not keys:
        raise SystemExit(f"nenhum parquet em {prefix}")
    return [f"{BUCKET}/{k}" for k in keys]


def main() -> None:
    release, out = sys.argv[1], sys.argv[2]
    files = listar_arquivos(release)
    print(f"{len(files)} arquivos no release {release}")
    con = duckdb.connect()
    con.sql("LOAD httpfs")
    con.sql("SET http_proxy='127.0.0.1:36891'")  # proxy da sessão
    con.sql("SET ca_cert_file='/root/.ccr/ca-bundle.crt'")
    lista = ", ".join(f"'{f}'" for f in files)
    for nome, (xmin, ymin, xmax, ymax) in REGIOES.items():
        sql = f"""
        COPY (
          SELECT id,
                 names.primary                           AS nome,
                 basic_category,
                 taxonomy.primary                        AS tax_primary,
                 array_to_string(taxonomy.hierarchy,'>') AS tax_hierarchy,
                 array_to_string(taxonomy.alternates,'|') AS tax_alternates,
                 operating_status,
                 confidence,
                 (bbox.ymin + bbox.ymax) / 2 AS lat, (bbox.xmin + bbox.xmax) / 2 AS lon,
                 addresses[1].freeform AS endereco, addresses[1].locality AS localidade,
                 addresses[1].postcode AS cep, addresses[1].region AS regiao, addresses[1].country AS pais,
                 phones[1]   AS telefone,
                 websites[1] AS site,
                 len(socials) AS n_socials,
                 brand.names.primary AS marca, brand.wikidata AS marca_wikidata,
                 array_to_string(list_distinct([s.dataset FOR s IN sources]), '|') AS datasets,
                 array_to_string(list_distinct([s.license FOR s IN sources]), '|') AS licencas,
                 list_max([s.update_time FOR s IN sources]) AS ultima_atualizacao_fonte,
                 version
          FROM read_parquet([{lista}], hive_partitioning=false)
          WHERE bbox.xmin >= {xmin} AND bbox.xmax <= {xmax}
            AND bbox.ymin >= {ymin} AND bbox.ymax <= {ymax}
        ) TO '{out}/{nome}_todos.parquet' (FORMAT parquet)
        """
        con.sql(sql)
        n = con.sql(f"SELECT count(*) FROM '{out}/{nome}_todos.parquet'").fetchall()[0][0]
        print(f"{nome}: {n} lugares (todas as categorias)")


if __name__ == "__main__":
    main()
