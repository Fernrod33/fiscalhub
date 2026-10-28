# -*- coding: utf-8 -*-
"""Gera toda a massa de teste fictícia do FiscalHub.  Uso: python gerar_tudo.py [pasta_saida]"""
import json
import os
import sys
from collections import defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import ecd_ecf  # noqa: E402
import efd  # noqa: E402
import pdfs  # noqa: E402
from common import D, ZERO, q  # noqa: E402

# DFC pelo método direto: categoria pela conta referencial da contrapartida (prefixo mais longo). Mesma regra do
# de-para padrão do backend (resources/capacidade/dfc-depara.txt), escrita aqui de forma independente.
DEPARA_DFC = [
    ("1.01.01", "CAIXA"), ("1.01.02.03", "TRIBUTOS"), ("1.01.02", "RECEBIMENTO_CLIENTES"),
    ("1.01.03", "PAGAMENTO_FORNECEDORES"), ("1.02.03", "IMOBILIZADO"), ("1.02", "INVESTIMENTOS"),
    ("2.01.01.01", "SALARIOS_ENCARGOS"), ("2.01.01.03", "PAGAMENTO_FORNECEDORES"), ("2.01.01.07", "EMPRESTIMOS"),
    ("2.01.01.09.09", "PARCELAMENTOS_TRIBUTARIOS"), ("2.01.01.09", "TRIBUTOS"),
    ("2.02.01.10", "PARCELAMENTOS_TRIBUTARIOS"), ("2.02", "EMPRESTIMOS"), ("2.03", "CAPITAL_DIVIDENDOS"),
    ("3.01.01.01.01", "RECEBIMENTO_CLIENTES"), ("3.01.01.01.02", "TRIBUTOS"), ("3.01.01.07.01.02", "SALARIOS_ENCARGOS"),
    ("3.02", "TRIBUTOS"), ("3.01", "OUTROS_OPERACIONAIS"),
]
ATIVIDADE = {"RECEBIMENTO_CLIENTES": "OPERACIONAL", "PAGAMENTO_FORNECEDORES": "OPERACIONAL",
             "SALARIOS_ENCARGOS": "OPERACIONAL", "TRIBUTOS": "OPERACIONAL", "OUTROS_OPERACIONAIS": "OPERACIONAL",
             "IMOBILIZADO": "INVESTIMENTO", "INVESTIMENTOS": "INVESTIMENTO", "EMPRESTIMOS": "FINANCIAMENTO",
             "PARCELAMENTOS_TRIBUTARIOS": "FINANCIAMENTO", "CAPITAL_DIVIDENDOS": "FINANCIAMENTO", "OUTROS": "OUTROS"}


def categoria(conta):
    ref = ecd_ecf.REF_OFICIAL[conta]
    melhor = max((p for p, _ in DEPARA_DFC if ref == p or ref.startswith(p + ".")), key=len, default=None)
    return dict(DEPARA_DFC)[melhor] if melhor else "OUTROS"


def dfc(ledger):
    """Cada lançamento que movimenta o caixa: a variação líquida do caixa é rateada entre as contrapartidas do mesmo
    sentido (crédito, se entrou caixa; débito, se saiu), na proporção dos valores. Só caixa (transferência): ignorado."""
    por_cat = defaultdict(lambda: ZERO)
    for _, _, partes, _, _ in ledger.entries:
        caixa = sum(v if s == "D" else -v for c, v, s in partes if categoria(c) == "CAIXA")
        if caixa == 0:
            continue
        lado = "C" if caixa > 0 else "D"
        contra = [(c, v) for c, v, s in partes if categoria(c) != "CAIXA" and s == lado]
        tot = sum(v for _, v in contra)
        resto = caixa
        for i, (c, v) in enumerate(contra):
            parte = resto if i == len(contra) - 1 else q(caixa * v / tot)
            por_cat[categoria(c)] += parte
            resto -= parte
    variacao = sum(ledger.bal[c] - ledger.ini[c] for c in ("1.1.01.001", "1.1.01.002"))
    assert sum(por_cat.values()) == variacao, (sum(por_cat.values()), variacao)
    ativ = defaultdict(lambda: ZERO)
    for k, v in por_cat.items():
        ativ[ATIVIDADE[k]] += v
    return dict(categorias={k: str(v) for k, v in sorted(por_cat.items())},
                atividades={k: str(v) for k, v in sorted(ativ.items())}, variacao_caixa=str(variacao))


def main(base):
    contabil = ecd_ecf.gerar(base)
    fisc = efd.gerar(base)
    efd_2025 = efd.gerar_2025(base, contabil["ledgers"][2025])
    assert efd_2025[-1]["icms"]["transp"] == 0
    efd_2026 = efd.gerar_2026_icms(base, pdfs.rti_2026(), ZERO)
    pdf = pdfs.gerar(base, fisc, contabil, efd_2025, efd_2026)
    # carga tributária de 2025: valor a recolher das EFD (ICMS, IPI, PIS, COFINS) e da ECF (IRPJ e CSLL zerados:
    # prejuízo fiscal) sobre a receita bruta da ECD
    tributos = sum(x["icms"]["a_recolher"] + x["icms"]["ipi_rec"] + x["pc"]["PIS"]["devido"] + x["pc"]["COFINS"]["devido"]
                   for x in efd_2025)
    receita = sum(mm["R"] for mm in contabil["ledgers"][2025].meses)
    resumo = {
        "ecd_resultado_por_ano": {str(a): str(r["resultado"]) for a, r in contabil["resumo"].items()},
        "ecf_lucro_real_2025": {k: str(v) for k, v in contabil["ecf_real"].items()},
        "prejuizo_fiscal_saldo_inicial_2025": str(contabil["prej_ant"]),
        "efd_icms": {k: str(v) for k, v in fisc["icms"].items()},
        "efd_contribuicoes": {t: {k: str(v) for k, v in fisc["pc"][t].items()} for t in ("PIS", "COFINS")},
        "dctfweb_082026_total": str(pdf["dctfweb_total"]),
        "situacao_fiscal": {k: str(v) for k, v in pdf["sit"].items()},
        "pgfn_passivo_total": str(pdf["pgfn"]["passivo"]),
        "carga_tributaria_2025": {"tributos": str(tributos), "receita_bruta": str(receita),
                                  "carga": str((tributos * 100 / receita).quantize(D("0.01"), "ROUND_HALF_EVEN"))},
        "dfc_direta": {"2024": dfc(contabil["ledgers"][2024]), "2025": dfc(contabil["ledgers"][2025])},
        "classificacao_pr": pdf["pr"]["classificacao"],
        "capag_oficial": {"classe": "C", "capacidade": str(pdfs.CAPAG_VALOR)},
        "irrecuperaveis_pgfn": [],
    }
    with open(os.path.join(base, "_gerador", "valores_esperados.json"), "w", encoding="utf-8") as fh:
        json.dump(resumo, fh, ensure_ascii=False, indent=2, default=str)
    return resumo


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    print(json.dumps(main(out), ensure_ascii=False, indent=2, default=str))
