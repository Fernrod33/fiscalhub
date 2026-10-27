# -*- coding: utf-8 -*-
"""Gera toda a massa de teste fictícia do FiscalHub.  Uso: python gerar_tudo.py [pasta_saida]"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import ecd_ecf  # noqa: E402
import efd  # noqa: E402
import pdfs  # noqa: E402


def main(base):
    contabil = ecd_ecf.gerar(base)
    fisc = efd.gerar(base)
    pdf = pdfs.gerar(base, fisc, contabil)
    resumo = {
        "ecd_resultado_por_ano": {str(a): str(r["resultado"]) for a, r in contabil["resumo"].items()},
        "ecf_lucro_real_2025": {k: str(v) for k, v in contabil["ecf_real"].items()},
        "prejuizo_fiscal_saldo_inicial_2025": str(contabil["prej_ant"]),
        "efd_icms": {k: str(v) for k, v in fisc["icms"].items()},
        "efd_contribuicoes": {t: {k: str(v) for k, v in fisc["pc"][t].items()} for t in ("PIS", "COFINS")},
        "dctfweb_082026_total": str(pdf["dctfweb_total"]),
        "situacao_fiscal": {k: str(v) for k, v in pdf["sit"].items()},
        "pgfn_passivo_total": str(pdf["pgfn"]["passivo"]),
    }
    with open(os.path.join(base, "_gerador", "valores_esperados.json"), "w", encoding="utf-8") as fh:
        json.dump(resumo, fh, ensure_ascii=False, indent=2)
    return resumo


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    print(json.dumps(main(out), ensure_ascii=False, indent=2))
