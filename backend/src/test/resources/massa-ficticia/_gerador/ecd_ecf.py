# -*- coding: utf-8 -*-
"""ECD 2022-2025 (+ substituta de 2024) e ECF 2025 (Lucro Real e Lucro Presumido). Dados fictícios."""
import datetime as dt
import os
import random
from collections import defaultdict

from common import (D, ZERO, q, v, dts, util_antes, ultimo_dia, Sped,
                    EMP_A, EMP_B, CONTADOR, SOCIO1, SOCIO2, SOCIO_B1, SOCIO_B2)

# --------------------------------------------------------------------------- plano de contas
# cod, nome, cod_nat, tipo, nivel, superior, aglutinação (J100/J150), código referencial
ACC = [
    ("1", "ATIVO", "01", "S", 1, "", None, None),
    ("1.1", "ATIVO CIRCULANTE", "01", "S", 2, "1", None, None),
    ("1.1.01", "DISPONIBILIDADES", "01", "S", 3, "1.1", None, None),
    ("1.1.01.001", "CAIXA GERAL", "01", "A", 4, "1.1.01", "1.1.1", "1.01.01.01"),
    ("1.1.01.002", "BANCO CONTA MOVIMENTO", "01", "A", 4, "1.1.01", "1.1.1", "1.01.01.02"),
    ("1.1.02", "CREDITOS", "01", "S", 3, "1.1", None, None),
    ("1.1.02.001", "CLIENTES NACIONAIS", "01", "A", 4, "1.1.02", "1.1.2", "1.01.02.01"),
    ("1.1.03", "ESTOQUES", "01", "S", 3, "1.1", None, None),
    ("1.1.03.001", "MATERIAS-PRIMAS", "01", "A", 4, "1.1.03", "1.1.3", "1.01.03.01"),
    ("1.1.03.002", "PRODUTOS ACABADOS", "01", "A", 4, "1.1.03", "1.1.3", "1.01.03.02"),
    ("1.1.04", "TRIBUTOS A RECUPERAR", "01", "S", 3, "1.1", None, None),
    ("1.1.04.001", "ICMS A RECUPERAR", "01", "A", 4, "1.1.04", "1.1.4", "1.01.02.02"),
    ("1.1.04.002", "IPI A RECUPERAR", "01", "A", 4, "1.1.04", "1.1.4", "1.01.02.02"),
    ("1.1.04.003", "PIS A RECUPERAR", "01", "A", 4, "1.1.04", "1.1.4", "1.01.02.02"),
    ("1.1.04.004", "COFINS A RECUPERAR", "01", "A", 4, "1.1.04", "1.1.4", "1.01.02.02"),
    ("1.2", "ATIVO NAO CIRCULANTE", "01", "S", 2, "1", None, None),
    ("1.2.01", "IMOBILIZADO", "01", "S", 3, "1.2", None, None),
    ("1.2.01.001", "MAQUINAS E EQUIPAMENTOS", "01", "A", 4, "1.2.01", "1.2.1", "1.02.01.01"),
    ("1.2.01.002", "VEICULOS", "01", "A", 4, "1.2.01", "1.2.1", "1.02.01.02"),
    ("1.2.01.901", "(-) DEPRECIACAO ACUMULADA", "01", "A", 4, "1.2.01", "1.2.1", "1.02.01.03"),
    ("2", "PASSIVO", "02", "S", 1, "", None, None),
    ("2.1", "PASSIVO CIRCULANTE", "02", "S", 2, "2", None, None),
    ("2.1.01", "FORNECEDORES", "02", "S", 3, "2.1", None, None),
    ("2.1.01.001", "FORNECEDORES NACIONAIS", "02", "A", 4, "2.1.01", "2.1.1", "2.01.01"),
    ("2.1.02", "OBRIGACOES TRABALHISTAS", "02", "S", 3, "2.1", None, None),
    ("2.1.02.001", "SALARIOS A PAGAR", "02", "A", 4, "2.1.02", "2.1.2", "2.01.02"),
    ("2.1.02.002", "INSS A RECOLHER", "02", "A", 4, "2.1.02", "2.1.2", "2.01.02"),
    ("2.1.02.003", "FGTS A RECOLHER", "02", "A", 4, "2.1.02", "2.1.2", "2.01.02"),
    ("2.1.03", "OBRIGACOES TRIBUTARIAS", "02", "S", 3, "2.1", None, None),
    ("2.1.03.001", "ICMS A RECOLHER", "02", "A", 4, "2.1.03", "2.1.3", "2.01.03"),
    ("2.1.03.003", "IPI A RECOLHER", "02", "A", 4, "2.1.03", "2.1.3", "2.01.03"),
    ("2.1.03.004", "PIS A RECOLHER", "02", "A", 4, "2.1.03", "2.1.3", "2.01.03"),
    ("2.1.03.005", "COFINS A RECOLHER", "02", "A", 4, "2.1.03", "2.1.3", "2.01.03"),
    ("2.1.03.006", "IRPJ A RECOLHER", "02", "A", 4, "2.1.03", "2.1.3", "2.01.03"),
    ("2.1.03.007", "CSLL A RECOLHER", "02", "A", 4, "2.1.03", "2.1.3", "2.01.03"),
    ("2.1.03.009", "PARCELAMENTOS TRIBUTARIOS - PGFN", "02", "A", 4, "2.1.03", "2.1.3", "2.01.04"),
    ("2.1.04", "EMPRESTIMOS E FINANCIAMENTOS", "02", "S", 3, "2.1", None, None),
    ("2.1.04.001", "EMPRESTIMOS BANCARIOS", "02", "A", 4, "2.1.04", "2.1.4", "2.01.05"),
    ("2.2", "PASSIVO NAO CIRCULANTE", "02", "S", 2, "2", None, None),
    ("2.2.01", "PARCELAMENTOS TRIBUTARIOS LP", "02", "S", 3, "2.2", None, None),
    ("2.2.01.001", "PARCELAMENTOS TRIBUTARIOS - PGFN LP", "02", "A", 4, "2.2.01", "2.2.1", "2.02.01"),
    ("2.3", "PATRIMONIO LIQUIDO", "03", "S", 2, "2", None, None),
    ("2.3.01", "CAPITAL SOCIAL", "03", "S", 3, "2.3", None, None),
    ("2.3.01.001", "CAPITAL SOCIAL INTEGRALIZADO", "03", "A", 4, "2.3.01", "2.3.1", "2.03.01"),
    ("2.3.02", "LUCROS OU PREJUIZOS ACUMULADOS", "03", "S", 3, "2.3", None, None),
    ("2.3.02.001", "LUCROS OU PREJUIZOS ACUMULADOS", "03", "A", 4, "2.3.02", "2.3.2", "2.03.02"),
    ("3", "RESULTADO", "04", "S", 1, "", None, None),
    ("3.1", "RECEITAS", "04", "S", 2, "3", None, None),
    ("3.1.01", "RECEITA BRUTA", "04", "S", 3, "3.1", None, None),
    ("3.1.01.001", "VENDA DE PRODUTOS DE FABRICACAO PROPRIA", "04", "A", 4, "3.1.01", "3.01", "3.01.01"),
    ("3.1.02", "DEDUCOES DA RECEITA", "04", "S", 3, "3.1", None, None),
    ("3.1.02.001", "ICMS SOBRE VENDAS", "04", "A", 4, "3.1.02", "3.02", "3.02.01"),
    ("3.1.02.002", "PIS SOBRE VENDAS", "04", "A", 4, "3.1.02", "3.02", "3.02.01"),
    ("3.1.02.003", "COFINS SOBRE VENDAS", "04", "A", 4, "3.1.02", "3.02", "3.02.01"),
    ("3.2", "CUSTOS", "04", "S", 2, "3", None, None),
    ("3.2.01", "CUSTO DOS PRODUTOS VENDIDOS", "04", "S", 3, "3.2", None, None),
    ("3.2.01.001", "CUSTO DOS PRODUTOS VENDIDOS", "04", "A", 4, "3.2.01", "3.04", "3.03.01"),
    ("3.3", "DESPESAS OPERACIONAIS", "04", "S", 2, "3", None, None),
    ("3.3.01", "DESPESAS OPERACIONAIS", "04", "S", 3, "3.3", None, None),
    ("3.3.01.001", "SALARIOS E ENCARGOS", "04", "A", 4, "3.3.01", "3.06", "3.04.01"),
    ("3.3.01.002", "DESPESAS GERAIS E ADMINISTRATIVAS", "04", "A", 4, "3.3.01", "3.06", "3.04.02"),
    ("3.3.01.003", "DEPRECIACAO", "04", "A", 4, "3.3.01", "3.06", "3.04.03"),
    ("3.3.01.004", "FRETES E DESPESAS COM VENDAS", "04", "A", 4, "3.3.01", "3.06", "3.04.04"),
    ("3.3.01.005", "MULTAS POR INFRACOES FISCAIS", "04", "A", 4, "3.3.01", "3.06", "3.04.05"),
    ("3.4", "RESULTADO FINANCEIRO", "04", "S", 2, "3", None, None),
    ("3.4.01", "RESULTADO FINANCEIRO", "04", "S", 3, "3.4", None, None),
    ("3.4.01.001", "RECEITAS FINANCEIRAS", "04", "A", 4, "3.4.01", "3.07", "3.05.01"),
    ("3.4.01.002", "JUROS E ENCARGOS FINANCEIROS", "04", "A", 4, "3.4.01", "3.07", "3.05.02"),
    ("3.5", "TRIBUTOS SOBRE O LUCRO", "04", "S", 2, "3", None, None),
    ("3.5.01", "PROVISAO IRPJ E CSLL", "04", "S", 3, "3.5", None, None),
    ("3.5.01.001", "IRPJ", "04", "A", 4, "3.5.01", "3.09", "3.06.01"),
    ("3.5.01.002", "CSLL", "04", "A", 4, "3.5.01", "3.09", "3.06.02"),
]
# Códigos do plano referencial OFICIAL da RFB (tabelas dinâmicas L100A/L300A, ECF 2021 — PJ em geral, Lucro Real),
# lidos de backend/src/main/resources/plano-referencial. O mapeamento conta da empresa -> conta referencial é fictício.
REF_OFICIAL = {
    "1.1.01.001": "1.01.01.01.01", "1.1.01.002": "1.01.01.02.01", "1.1.02.001": "1.01.02.02.01",
    "1.1.03.001": "1.01.03.02.01", "1.1.03.002": "1.01.03.02.04", "1.1.04.001": "1.01.02.03.02",
    "1.1.04.002": "1.01.02.03.01", "1.1.04.003": "1.01.02.03.03", "1.1.04.004": "1.01.02.03.05",
    "1.2.01.001": "1.02.03.01.06", "1.2.01.002": "1.02.03.01.08", "1.2.01.901": "1.02.03.01.30",
    "2.1.01.001": "2.01.01.03.01", "2.1.02.001": "2.01.01.01.01", "2.1.02.002": "2.01.01.01.03",
    "2.1.02.003": "2.01.01.01.04", "2.1.03.001": "2.01.01.09.03", "2.1.03.003": "2.01.01.09.02",
    "2.1.03.004": "2.01.01.09.04", "2.1.03.005": "2.01.01.09.05", "2.1.03.006": "2.01.01.09.13",
    "2.1.03.007": "2.01.01.09.14", "2.1.03.009": "2.01.01.09.09", "2.1.04.001": "2.01.01.07.02",
    "2.2.01.001": "2.02.01.10.09", "2.3.01.001": "2.03.01.01.01", "2.3.02.001": "2.03.04.01.01",
    "3.1.01.001": "3.01.01.01.01.04", "3.1.02.001": "3.01.01.01.02.03", "3.1.02.002": "3.01.01.01.02.05",
    "3.1.02.003": "3.01.01.01.02.04", "3.2.01.001": "3.01.01.03.01.01", "3.3.01.001": "3.01.01.07.01.02",
    "3.3.01.002": "3.01.01.09.01.99", "3.3.01.003": "3.01.01.07.01.23", "3.3.01.004": "3.01.01.07.01.04",
    "3.3.01.005": "3.01.01.07.01.22", "3.4.01.001": "3.01.01.05.01.05", "3.4.01.002": "3.01.01.09.01.08",
    "3.5.01.001": "3.02.01.01.01.02", "3.5.01.002": "3.02.01.01.01.01",
}
ACC = [a[:7] + (REF_OFICIAL[a[0]],) if a[3] == "A" else a for a in ACC]
ACCD = {a[0]: a for a in ACC}
ANALIT = [a[0] for a in ACC if a[3] == "A"]
RESULT = [a for a in ANALIT if a.startswith("3")]
PATRIM = [a for a in ANALIT if not a.startswith("3")]

# plano referencial: linhas oficiais usadas pelo mapeamento + ancestrais, na ordem da tabela oficial
# a massa fica em backend/src/test/resources/massa-ficticia/_gerador: sobe até backend/src
_TAB = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "..", "main", "resources",
                    "plano-referencial")


def _carregar_ref():
    tab = {}
    for n, nome in enumerate(("SPEDECF_DINAMICA_L100_A_2021.txt", "SPEDECF_DINAMICA_L300_A_2021.txt")):
        with open(os.path.join(_TAB, nome), encoding="latin-1") as fh:
            next(fh)
            for linha in fh:
                c = linha.rstrip("\r\n").split("|")
                if len(c) >= 9 and c[0]:
                    tab[c[0]] = (c[1].strip(), c[5], c[8], c[6], (n, int(c[4])))
    usados = set()
    for cod in REF_OFICIAL.values():
        assert cod in tab, f"código referencial {cod} ausente da tabela oficial"
        while cod:
            usados.add(cod)
            cod = tab[cod][3]
    return [(c, tab[c][0], tab[c][1], tab[c][2]) for c in sorted(usados, key=lambda c: tab[c][4])]


REF = _carregar_ref()


def ref_parent(c):
    return c.rsplit(".", 1)[0] if "." in c else ""


def dc(x):
    """(valor absoluto, indicador D/C) a partir de saldo com sinal (débito positivo)."""
    return (abs(q(x)), "D" if x >= 0 else "C")


# --------------------------------------------------------------------------- simulação contábil
SALDO_INICIAL_2022 = {
    "1.1.01.001": D("5200.00"), "1.1.01.002": D("348750.42"), "1.1.02.001": D("421380.15"),
    "1.1.03.001": D("281940.00"), "1.1.03.002": D("188615.30"), "1.2.01.001": D("1812400.00"),
    "1.2.01.002": D("261900.00"), "1.2.01.901": D("-624318.40"), "2.1.01.001": D("-309880.71"),
    "2.1.04.001": D("-600000.00"), "2.1.03.009": D("-96000.00"), "2.2.01.001": D("-412000.00"),
    "2.3.01.001": D("-1200000.00"),
}
SALDO_INICIAL_2022["2.3.02.001"] = -sum(SALDO_INICIAL_2022.values())

PARAM = {  # receita mensal base, CPV/receita, folha, adm
    2022: (D("1200000"), D("0.540"), D("140000"), D("55000")),
    2023: (D("1250000"), D("0.540"), D("146000"), D("57000")),
    2024: (D("1150000"), D("0.575"), D("151000"), D("58500")),
    2025: (D("1050000"), D("0.552"), D("156000"), D("60000")),
}
SAZON = [0.86, 0.92, 1.02, 0.98, 1.04, 1.00, 1.03, 1.08, 1.05, 1.07, 1.03, 0.92]


class Ledger:
    def __init__(self, ano, saldo_ini):
        self.ano = ano
        self.ini = defaultdict(lambda: ZERO, saldo_ini)
        self.bal = defaultdict(lambda: ZERO, saldo_ini)
        self.entries = []  # (num, data, partes[(conta, valor, D/C, hist)], ind)
        self.meses = []    # valores lançados por mês (base das EFD de 2025, coerentes com a ECD)

    def lanc(self, data, partes, hist, ind="N"):
        partes = [(c, q(val), s) for c, val, s in partes if q(val) != 0]
        td = sum(p[1] for p in partes if p[2] == "D")
        tc = sum(p[1] for p in partes if p[2] == "C")
        assert td == tc, (hist, td, tc)
        if td == 0:
            return
        for c, val, s in partes:
            assert ACCD[c][3] == "A", c
            self.bal[c] += val if s == "D" else -val
        self.entries.append((len(self.entries) + 1, data, partes, hist, ind))


def simula_ano(ano, saldo_ini, substituta=False):
    rnd = random.Random(1000 + ano)
    L = Ledger(ano, saldo_ini)
    rev, cpv_r, folha_b, adm_b = PARAM[ano]
    for m in range(1, 13):
        d = lambda day: util_antes(dt.date(ano, m, min(day, ultimo_dia(ano, m).day)))
        # pagamentos do início do mês (obrigações do mês anterior)
        folha_pend = [(c, L.bal[c]) for c in ("2.1.02.001", "2.1.02.002", "2.1.02.003")]
        tot = -sum(b for _, b in folha_pend)
        if tot > 0:
            L.lanc(d(5), [(c, -b, "D") for c, b in folha_pend] + [("1.1.01.002", tot, "C")],
                   "PAGTO FOLHA E ENCARGOS DO MES ANTERIOR")
        if L.bal["1.1.02.001"] > 0:
            L.lanc(d(10), [("1.1.01.002", L.bal["1.1.02.001"], "D"), ("1.1.02.001", L.bal["1.1.02.001"], "C")],
                   "RECEBIMENTO DE CLIENTES - COBRANCA BANCARIA")
        if L.bal["2.1.01.001"] < 0:
            L.lanc(d(15), [("2.1.01.001", -L.bal["2.1.01.001"], "D"), ("1.1.01.002", -L.bal["2.1.01.001"], "C")],
                   "PAGAMENTO A FORNECEDORES")
        # tributos do mês anterior: compensação de créditos e recolhimento
        for rec, pag, nome in (("1.1.04.001", "2.1.03.001", "ICMS"), ("1.1.04.002", "2.1.03.003", "IPI"),
                               ("1.1.04.003", "2.1.03.004", "PIS"), ("1.1.04.004", "2.1.03.005", "COFINS")):
            off = min(L.bal[rec], -L.bal[pag])
            if off > 0:
                L.lanc(d(20), [(pag, off, "D"), (rec, off, "C")], f"COMPENSACAO DE CREDITOS DE {nome} NA APURACAO")
            if L.bal[pag] < 0:
                L.lanc(d(20), [(pag, -L.bal[pag], "D"), ("1.1.01.002", -L.bal[pag], "C")], f"RECOLHIMENTO DE {nome}")
        for pag, nome in (("2.1.03.006", "IRPJ"), ("2.1.03.007", "CSLL")):
            if L.bal[pag] < 0:
                L.lanc(d(28), [(pag, -L.bal[pag], "D"), ("1.1.01.002", -L.bal[pag], "C")],
                       f"RECOLHIMENTO DE {nome} - AJUSTE ANUAL")
        # parcelamento PGFN
        if L.bal["2.1.03.009"] < 0:
            p = min(D("8000"), -L.bal["2.1.03.009"])
            L.lanc(d(25), [("2.1.03.009", p, "D"), ("1.1.01.002", p, "C")], "PAGTO PARCELA TRANSACAO PGFN")
            if L.bal["2.2.01.001"] <= -p:
                L.lanc(d(25), [("2.2.01.001", p, "D"), ("2.1.03.009", p, "C")], "TRANSFERENCIA PARCELAMENTO LP PARA CP")
        # operações do mês
        R = q(rev * D(str(SAZON[m - 1])) * D(str(1 + rnd.uniform(-0.04, 0.04))))
        ipi = q(R * D("0.05"))
        icms = q(R * D("0.12"))
        pis = q((R - icms) * D("0.0165"))
        cof = q((R - icms) * D("0.076"))
        G = q(R * cpv_r / (D("1") - D("0.12") - D("0.0925") * D("0.88")))
        icms_c = q(G * D("0.12"))
        pis_c = q((G - icms_c) * D("0.0165"))
        cof_c = q((G - icms_c) * D("0.076"))
        ipi_c = q(G * D("0.05"))
        mp = G - icms_c - pis_c - cof_c
        L.meses.append(dict(mes=m, R=R, ipi=ipi, icms=icms, pis=pis, cof=cof, G=G, icms_c=icms_c, pis_c=pis_c,
                            cof_c=cof_c, ipi_c=ipi_c))
        L.lanc(d(12), [("1.1.03.001", mp, "D"), ("1.1.04.001", icms_c, "D"), ("1.1.04.003", pis_c, "D"),
                       ("1.1.04.004", cof_c, "D"), ("1.1.04.002", ipi_c, "D"), ("2.1.01.001", G + ipi_c, "C")],
               "COMPRA DE MATERIAS-PRIMAS CONF. NOTAS FISCAIS DO MES")
        L.lanc(d(26), [("1.1.03.002", mp, "D"), ("1.1.03.001", mp, "C")], "TRANSFERENCIA PARA PRODUCAO ACABADA")
        L.lanc(d(28), [("1.1.02.001", R + ipi, "D"), ("3.1.01.001", R, "C"), ("2.1.03.003", ipi, "C")],
               "VENDAS DE PRODUTOS CONF. NOTAS FISCAIS DO MES")
        L.lanc(d(28), [("3.1.02.001", icms, "D"), ("3.1.02.002", pis, "D"), ("3.1.02.003", cof, "D"),
                       ("2.1.03.001", icms, "C"), ("2.1.03.004", pis, "C"), ("2.1.03.005", cof, "C")],
               "TRIBUTOS INCIDENTES SOBRE VENDAS")
        L.lanc(d(28), [("3.2.01.001", mp, "D"), ("1.1.03.002", mp, "C")], "BAIXA DO CUSTO DOS PRODUTOS VENDIDOS")
        F = q(folha_b * D(str(1 + rnd.uniform(-0.02, 0.03))) * (D("2") if m == 12 else D("1")))
        L.lanc(ultimo_dia(ano, m), [("3.3.01.001", F, "D"), ("2.1.02.001", q(F * D("0.72")), "C"),
                                    ("2.1.02.002", q(F * D("0.20")), "C"),
                                    ("2.1.02.003", F - q(F * D("0.72")) - q(F * D("0.20")), "C")],
               "APROPRIACAO FOLHA DE PAGAMENTO E ENCARGOS" + (" - INCLUI 13 SALARIO" if m == 12 else ""))
        A = q(adm_b * D(str(1 + rnd.uniform(-0.05, 0.05))))
        L.lanc(d(18), [("3.3.01.002", A, "D"), ("1.1.01.002", A, "C")], "DESPESAS ADMINISTRATIVAS DIVERSAS")
        Fr = q(R * D("0.021"))
        L.lanc(d(22), [("3.3.01.004", Fr, "D"), ("1.1.01.002", Fr, "C")], "FRETES SOBRE VENDAS E COMISSOES")
        L.lanc(ultimo_dia(ano, m), [("3.3.01.003", D("17840.00"), "D"), ("1.2.01.901", D("17840.00"), "C")],
               "DEPRECIACAO DO MES")
        juros = q(-L.bal["2.1.04.001"] * D("0.0135") + (-L.bal["2.1.03.009"] - L.bal["2.2.01.001"]) * D("0.0095"))
        L.lanc(d(27), [("3.4.01.002", juros, "D"), ("1.1.01.002", juros, "C")], "JUROS E ENCARGOS FINANCEIROS")
        rf = q(max(L.bal["1.1.01.002"], ZERO) * D("0.0045"))
        L.lanc(d(27), [("1.1.01.002", rf, "D"), ("3.4.01.001", rf, "C")], "RENDIMENTOS DE APLICACAO AUTOMATICA")
        if (ano, m) == (2023, 6):
            L.lanc(d(14), [("3.3.01.005", D("4210.55"), "D"), ("1.1.01.002", D("4210.55"), "C")],
                   "MULTA POR ATRASO NA ENTREGA DE OBRIGACAO ACESSORIA")
        if (ano, m) == (2025, 3):
            L.lanc(d(17), [("3.3.01.005", D("18450.00"), "D"), ("1.1.01.002", D("18450.00"), "C")],
                   "MULTA DE OFICIO - AUTO DE INFRACAO ICMS-ST (SEFA/PR)")
        if substituta and m == 11:
            L.lanc(d(30), [("3.3.01.004", D("12500.00"), "D"), ("3.3.01.002", D("12500.00"), "C")],
                   "RECLASSIFICACAO - FRETES CONTABILIZADOS COMO DESP. ADMINISTRATIVAS (RETIFICACAO)")
        # caixa: empréstimo/amortização
        if L.bal["1.1.01.002"] < D("80000"):
            L.lanc(d(28), [("1.1.01.002", D("300000"), "D"), ("2.1.04.001", D("300000"), "C")],
                   "CAPTACAO DE CAPITAL DE GIRO")
        elif L.bal["1.1.01.002"] > D("900000") and L.bal["2.1.04.001"] < 0:
            a = min(D("200000"), -L.bal["2.1.04.001"])
            L.lanc(d(28), [("2.1.04.001", a, "D"), ("1.1.01.002", a, "C")], "AMORTIZACAO DE EMPRESTIMO")
    # provisão IRPJ/CSLL (lucro real anual - simplificado)
    fim = dt.date(ano, 12, 31)
    lair = -sum(L.bal[c] for c in RESULT)
    adic = L.bal["3.3.01.005"]
    base = lair + adic
    if base > 0:
        irpj = q(base * D("0.15") + max(base - D("240000"), ZERO) * D("0.10"))
        csll = q(base * D("0.09"))
        L.lanc(fim, [("3.5.01.001", irpj, "D"), ("2.1.03.006", irpj, "C")], "PROVISAO PARA IRPJ")
        L.lanc(fim, [("3.5.01.002", csll, "D"), ("2.1.03.007", csll, "C")], "PROVISAO PARA CSLL")
    pre_enc = {c: L.bal[c] for c in RESULT}
    res = -sum(pre_enc.values())
    partes = [(c, abs(b), "C" if b > 0 else "D") for c, b in pre_enc.items() if b != 0]
    partes.append(("2.3.02.001", abs(res), "C" if res > 0 else "D"))
    L.lanc(fim, partes, "ENCERRAMENTO DAS CONTAS DE RESULTADO DO EXERCICIO", ind="E")
    L.pre_enc = pre_enc
    L.resultado = res
    L.adicoes = adic
    return L


# --------------------------------------------------------------------------- ECD
def agl_bp(bal):
    out = defaultdict(lambda: ZERO)
    for c in PATRIM:
        out[ACCD[c][6]] += bal[c]
    return out


BP_AGL = [  # código, descrição, T/D, nível, superior, grupo
    ("1", "ATIVO", "T", 1, "", "A"), ("1.1", "ATIVO CIRCULANTE", "T", 2, "1", "A"),
    ("1.1.1", "Caixa e equivalentes de caixa", "D", 3, "1.1", "A"), ("1.1.2", "Clientes", "D", 3, "1.1", "A"),
    ("1.1.3", "Estoques", "D", 3, "1.1", "A"), ("1.1.4", "Tributos a recuperar", "D", 3, "1.1", "A"),
    ("1.2", "ATIVO NAO CIRCULANTE", "T", 2, "1", "A"), ("1.2.1", "Imobilizado liquido", "D", 3, "1.2", "A"),
    ("2", "PASSIVO E PATRIMONIO LIQUIDO", "T", 1, "", "P"), ("2.1", "PASSIVO CIRCULANTE", "T", 2, "2", "P"),
    ("2.1.1", "Fornecedores", "D", 3, "2.1", "P"), ("2.1.2", "Obrigacoes trabalhistas", "D", 3, "2.1", "P"),
    ("2.1.3", "Obrigacoes tributarias e parcelamentos", "D", 3, "2.1", "P"),
    ("2.1.4", "Emprestimos e financiamentos", "D", 3, "2.1", "P"),
    ("2.2", "PASSIVO NAO CIRCULANTE", "T", 2, "2", "P"), ("2.2.1", "Parcelamentos tributarios", "D", 3, "2.2", "P"),
    ("2.3", "PATRIMONIO LIQUIDO", "T", 2, "2", "P"), ("2.3.1", "Capital social", "D", 3, "2.3", "P"),
    ("2.3.2", "Lucros ou prejuizos acumulados", "D", 3, "2.3", "P"),
]

DRE_AGL = [  # código, descrição, T/D, fórmula (lista de aglutinações detalhe somadas)
    ("3.01", "RECEITA BRUTA DE VENDAS", "D", ["3.01"]),
    ("3.02", "(-) DEDUCOES DA RECEITA BRUTA", "D", ["3.02"]),
    ("3.03", "RECEITA LIQUIDA", "T", ["3.01", "3.02"]),
    ("3.04", "(-) CUSTO DOS PRODUTOS VENDIDOS", "D", ["3.04"]),
    ("3.05", "LUCRO BRUTO", "T", ["3.01", "3.02", "3.04"]),
    ("3.06", "(-) DESPESAS OPERACIONAIS", "D", ["3.06"]),
    ("3.07", "RESULTADO FINANCEIRO", "D", ["3.07"]),
    ("3.08", "RESULTADO ANTES DO IRPJ E CSLL", "T", ["3.01", "3.02", "3.04", "3.06", "3.07"]),
    ("3.09", "(-) PROVISAO PARA IRPJ E CSLL", "D", ["3.09"]),
    ("3.10", "RESULTADO LIQUIDO DO EXERCICIO", "T", ["3.01", "3.02", "3.04", "3.06", "3.07", "3.09"]),
]


def dre_vals(pre_enc):
    det = defaultdict(lambda: ZERO)
    for c, b in pre_enc.items():
        det[ACCD[c][6]] += -b  # crédito positivo
    return {cod: sum(det[x] for x in f) for cod, _, _, f in DRE_AGL}


def gera_ecd(L, prev_dre, num_ordem, substituta=False):
    e = EMP_A
    ano = L.ano
    ini, fim = dt.date(ano, 1, 1), dt.date(ano, 12, 31)
    s = Sped()
    s.add("0000", "LECD", dts(ini), dts(fim), e["nome"], e["cnpj"], e["uf"], e["ie"], e["cod_mun"], e["im"],
          "", "0", "1", "1" if substituta else "0",
          "8F3C2A9D41E07B65C1D2E3F4A5B6C7D8E9F0A1B2" if substituta else "", "0", "0", "", "N", "N", "0", "0", "1")
    s.add("0001", "0")
    s.add("0007", "00", "")
    s.close("0")
    s.add("I001", "0")
    s.add("I010", "G", "9.00")
    s.add("I030", "TERMO DE ABERTURA", num_ordem, "DIARIO GERAL", "@QTD@", e["nome"], e["nire"], e["cnpj"],
          "14031994", "", "CASCAVEL", dts(fim))
    for cod, nome, nat, tipo, niv, sup, agl, ref in ACC:
        s.add("I050", "01012015", nat, tipo, niv, cod, sup, nome)
        if tipo == "A":
            s.add("I051", "", ref)
    # saldos periódicos mensais
    bal = defaultdict(lambda: ZERO, L.ini)
    by_m = defaultdict(list)
    for en in L.entries:
        by_m[en[1].month].append(en)
    for m in range(1, 13):
        s.add("I150", dts(dt.date(ano, m, 1)), dts(ultimo_dia(ano, m)))
        deb = defaultdict(lambda: ZERO)
        cre = defaultdict(lambda: ZERO)
        for _, _, partes, _, _ in by_m[m]:
            for c, val, sd in partes:
                (deb if sd == "D" else cre)[c] += val
        for c in ANALIT:
            si = bal[c]
            sf = si + deb[c] - cre[c]
            if si == 0 and deb[c] == 0 and cre[c] == 0:
                continue
            a, ia = dc(si)
            b, ib = dc(sf)
            s.add("I155", c, "", v(a), ia, v(deb[c]), v(cre[c]), v(b), ib)
            bal[c] = sf
    for num, data, partes, hist, ind in L.entries:
        tot = sum(p[1] for p in partes if p[2] == "D")
        s.add("I200", str(num), dts(data), v(tot), ind, "")
        for c, val, sd in partes:
            s.add("I250", c, "", v(val), sd, "", "", hist, "")
    s.add("I350", dts(fim))
    for c in RESULT:
        if L.pre_enc.get(c, ZERO) != 0:
            a, i = dc(L.pre_enc[c])
            s.add("I355", c, "", v(a), i)
    s.close("I")
    s.add("J001", "0")
    s.add("J005", dts(ini), dts(fim), "1", "")
    bpi, bpf = agl_bp(L.ini), agl_bp(L.bal)
    tot_i, tot_f = defaultdict(lambda: ZERO), defaultdict(lambda: ZERO)
    for cod, _, t, _, _, _ in BP_AGL:
        if t == "D":
            for anc in [cod.rsplit(".", 1)[0], cod.split(".")[0]]:
                tot_i[anc] += bpi[cod]
                tot_f[anc] += bpf[cod]
    for cod, desc, t, niv, sup, grp in BP_AGL:
        vi = bpi[cod] if t == "D" else tot_i[cod]
        vf = bpf[cod] if t == "D" else tot_f[cod]
        a, ia = dc(vi)
        b, ib = dc(vf)
        s.add("J100", cod, t, niv, sup, grp, desc, v(a), ia, v(b), ib, "")
    dv = dre_vals(L.pre_enc)
    for i, (cod, desc, t, _) in enumerate(DRE_AGL, 1):
        pi = prev_dre.get(cod, ZERO) if prev_dre else ZERO
        a, ia = dc(-pi)
        b, ib = dc(-dv[cod])
        s.add("J150", str(i), cod, t, "1", "", desc, v(a), ia, v(b), ib, "R" if dv[cod] >= 0 else "D", "")
    s.add("J900", "TERMO DE ENCERRAMENTO", num_ordem, "DIARIO GERAL", e["nome"], "@QTD@", dts(ini), dts(fim))
    s.add("J930", SOCIO1["nome"], SOCIO1["cpf"], "Administrador", "205", "", "", "", "", "", "", "S")
    s.add("J930", CONTADOR["nome"], CONTADOR["cpf"], "Contador", "900", "1", CONTADOR["email"], CONTADOR["fone"],
          "PR", CONTADOR["crc"], "31122027", "N")
    s.close("J")
    s.add("K001", "1")
    s.close("K")
    total = s.finish()
    for l in s.lines:
        if l[0] in ("I030", "J900"):
            l[:] = [str(total) if x == "@QTD@" else x for x in l]
    return s, dv


# --------------------------------------------------------------------------- ECF Lucro Real
def ecf_0020(s, csll_aliq="1"):
    s.add("0020", csll_aliq, "0", *(["N"] * 32))


def ecf_head(s, e, ano, forma_trib, forma_apur, mes_bal, tip_esc_pre, forma_trib_per, apur_csll):
    s.add("0000", "LECF", "0012", e["cnpj"], e["nome"], "0", "0", "", "", dts(dt.date(ano, 1, 1)),
          dts(dt.date(ano, 12, 31)), "N", "", "0", "")
    s.add("0001", "0")
    s.add("0010", "", "N", forma_trib, forma_apur, "01", forma_trib_per, mes_bal, tip_esc_pre, "", "", apur_csll, "")
    ecf_0020(s)
    s.add("0030", "2062", e["cnae"], e["end"], e["num"], e["compl"], e["bairro"], e["uf"], e["cod_mun"],
          e["cep"], e["fone"], e["email"])
    s.add("0930", CONTADOR["nome"], CONTADOR["cpf"], "900", CONTADOR["email"], CONTADOR["fone"])
    s.close("0")


def ecf_tail(s, e, receita, socios, ini_blocks):
    for b in ini_blocks:
        s.add(f"{b}001", "1")
        s.close(b)
    s.add("Y001", "0")
    s.add("Y540", e["cnpj"], v(receita), e["cnae"])
    for so in socios:
        s.add("Y600", "01012015", "", "105", "PF", so["cpf"], so["nome"], "05", v(so["perc"]), v(so["perc"]),
              "", "", v(D("0")), v(D("0")), v(D("0")), v(D("0")), v(D("0")))
    s.close("Y")
    s.finish()


def gera_ecf_real(L, L_prev, prej_ant_ir, prej_ant_cs):
    e = EMP_A
    ano = L.ano
    s = Sped()
    ecf_head(s, e, ano, "1", "A", "BBBBBBBBBBBB", "", "", "A")
    s.add("C001", "1"); s.close("C")
    s.add("E001", "1"); s.close("E")
    s.add("J001", "0")
    for cod, nome, nat, tipo, niv, sup, agl, ref in ACC:
        s.add("J050", "01012015", nat, tipo, niv, cod, sup, nome)
        if tipo == "A":
            s.add("J051", "", ref)
    s.close("J")
    # K - saldos contábeis
    deb, cre = defaultdict(lambda: ZERO), defaultdict(lambda: ZERO)
    for _, _, partes, _, ind in L.entries:
        if ind == "E":
            continue
        for c, val, sd in partes:
            (deb if sd == "D" else cre)[c] += val
    s.add("K001", "0")
    s.add("K030", dts(dt.date(ano, 1, 1)), dts(dt.date(ano, 12, 31)), "A00")
    refval = defaultdict(lambda: [ZERO, ZERO, ZERO, ZERO])
    for c in PATRIM:
        si, sf = L.ini[c], L.ini[c] + deb[c] - cre[c]
        if si == 0 and deb[c] == 0 and cre[c] == 0:
            continue
        a, ia = dc(si)
        b, ib = dc(sf)
        s.add("K155", c, "", v(a), ia, v(deb[c]), v(cre[c]), v(b), ib)
        s.add("K156", ACCD[c][7], v(a), ia, v(deb[c]), v(cre[c]), v(b), ib)
        r = refval[ACCD[c][7]]
        r[0] += si; r[1] += deb[c]; r[2] += cre[c]; r[3] += sf
    for c in RESULT:
        b = L.pre_enc.get(c, ZERO)
        if b == 0:
            continue
        a, i = dc(b)
        s.add("K355", c, "", v(a), i)
        s.add("K356", ACCD[c][7], v(a), i)
        refval[ACCD[c][7]][3] += b
    s.close("K")
    # L - BP e DRE referenciais
    agg = defaultdict(lambda: [ZERO, ZERO, ZERO, ZERO])
    for code, vals in refval.items():
        node = code
        while node:
            for k in range(4):
                agg[node][k] += vals[k]
            node = ref_parent(node)
    s.add("L001", "0")
    s.add("L030", dts(dt.date(ano, 1, 1)), dts(dt.date(ano, 12, 31)), "A00")
    for code, desc, tipo, nat in REF:
        if code.startswith("3"):
            continue
        si, db, cr, sf = agg[code]
        a, ia = dc(si)
        b, ib = dc(sf)
        s.add("L100", code, desc, tipo, code.count(".") + 1, nat, ref_parent(code), v(a), ia, v(db), v(cr), v(b), ib)
    for code, desc, tipo, nat in REF:
        if not code.startswith("3"):
            continue
        a, i = dc(agg[code][3])
        s.add("L300", code, desc, tipo, code.count(".") + 1, nat, ref_parent(code), v(a), i)
    s.close("L")
    # M - e-Lalur / e-Lacs
    ll = L.resultado  # já sem IRPJ/CSLL (prejuízo => não há provisão)
    adic = L.adicoes
    lucro_real = ll + adic
    assert lucro_real < 0, "cenário exige prejuízo fiscal em 2025"
    pf = -lucro_real
    s.add("M001", "0")
    s.add("M010", "PF01", "Prejuizo fiscal de periodos anteriores", "31122019", "1000", "", "I", v(prej_ant_ir), "D", "")
    s.add("M010", "BN01", "Base negativa da CSLL de periodos anteriores", "31122019", "1003", "", "C", v(prej_ant_cs), "D", "")
    s.add("M030", dts(dt.date(ano, 1, 1)), dts(dt.date(ano, 12, 31)), "A00")
    # linhas totalizadoras com os códigos oficiais (2, 93, 168, 169, 171, 173, 175); o código da linha de adição
    # da multa (30) é ILUSTRATIVO — conferir na tabela dinâmica M300A/M350A oficial
    for reg, reg_det, total in (("M300", "M310", "LUCRO REAL"), ("M350", "M360", "BASE DE CALCULO DA CSLL")):
        s.add(reg, "2", "Lucro Liquido Antes do IRPJ" if reg == "M300" else "Lucro Antes da CSLL", "L", "4", v(ll), "")
        s.add(reg, "30", "Multas por infracoes fiscais nao dedutiveis", "A", "2", v(adic),
              "Multa de oficio ICMS-ST lancada em 03/2025")
        s.add(reg_det, "3.3.01.005", "", v(adic), "D")
        s.add(reg, "93", "SOMA DAS ADICOES", "L", "4", v(adic), "")
        s.add(reg, "168", "SOMA DAS EXCLUSOES", "L", "4", v(0), "")
        s.add(reg, "169", "LUCRO ANTES DA COMPENSACAO DE PREJUIZOS", "L", "4", v(lucro_real), "")
        s.add(reg, "171", "LUCRO APOS A COMPENSACAO DOS PREJUIZOS DO PROPRIO PERIODO", "L", "4", v(lucro_real), "")
        s.add(reg, "173", "Compensacao de prejuizos de periodos anteriores", "P", "1", v(0), "")
        s.add(reg, "175", total, "L", "4", v(lucro_real), "")
    s.add("M410", "PF01", "I", v(pf), "D", "", "Prejuizo fiscal apurado no ano-calendario " + str(ano), "N")
    s.add("M410", "BN01", "C", v(pf), "D", "", "Base negativa apurada no ano-calendario " + str(ano), "N")
    s.add("M500", "PF01", "I", v(prej_ant_ir), "D", v(pf), "D", v(0), "D", v(prej_ant_ir + pf), "D")
    s.add("M500", "BN01", "C", v(prej_ant_cs), "D", v(pf), "D", v(0), "D", v(prej_ant_cs + pf), "D")
    s.close("M")
    # N - cálculo IRPJ/CSLL
    s.add("N001", "0")
    s.add("N030", dts(dt.date(ano, 1, 1)), dts(dt.date(ano, 12, 31)), "A00")
    # base negativa = prejuízo do período (mesma regra da conferência do backend: base = linha 175 quando <= 0)
    for reg, c, d_, val in (("N500", "1", "Valor da base de calculo do IRPJ", lucro_real),
                            ("N630", "1", "BASE DE CALCULO DO IRPJ", lucro_real),
                            ("N630", "3", "A Aliquota de 15%", ZERO), ("N630", "4", "Adicional", ZERO),
                            ("N630", "24", "(-) Imposto de Renda Mensal Efetivamente Pago por Estimativa", ZERO),
                            ("N630", "26", "IMPOSTO DE RENDA A PAGAR", ZERO),
                            ("N650", "1", "Valor da Base de Calculo da CSLL", lucro_real),
                            ("N670", "1", "BASE DE CALCULO DA CSLL", lucro_real),
                            ("N670", "4", "TOTAL DA CONTRIBUICAO SOCIAL SOBRE O LUCRO LIQUIDO", ZERO),
                            ("N670", "19", "(-) CSLL Mensal Efetivamente Paga por Estimativa", ZERO),
                            ("N670", "21", "CSLL A PAGAR", ZERO)):
        s.add(reg, c, d_, v(val))
    s.close("N")
    receita = sum(p[1] for _, _, partes, _, ind in L.entries if ind == "N"
                  for p in partes if p[0] == "3.1.01.001" and p[2] == "C")
    ecf_tail(s, e, receita, [SOCIO1, SOCIO2], ["P", "Q", "T", "U", "V", "W", "X"])
    return s, dict(ll=ll, adic=adic, pf=pf, receita=receita)


def gera_ecf_presumido(ano=2025):
    e = EMP_B
    s = Sped()
    ecf_head(s, e, ano, "5", "T", "", "L", "PPPP", "T")
    for b in "CEJKLMN":
        s.add(f"{b}001", "1")
        s.close(b)
    receitas = [D("518400.00"), D("611250.00"), D("579930.00"), D("642780.00")]
    fin = [D("2140.33"), D("2895.10"), D("3012.47"), D("3380.95")]
    s.add("P001", "0")
    tri = []
    for i, (rb, rf) in enumerate(zip(receitas, fin), 1):
        di = dt.date(ano, 3 * i - 2, 1)
        df = ultimo_dia(ano, 3 * i)
        s.add("P030", dts(di), dts(df), f"T0{i}")
        bc = q(rb * D("0.32"))
        base = bc + rf
        s.add("P200", "11", "Receita bruta - prestacao de servicos em geral (32%)", v(rb))
        s.add("P200", "12", "Resultado da aplicacao do percentual (32%)", v(bc))
        s.add("P200", "21", "Rendimentos e ganhos liquidos de aplicacoes financeiras", v(rf))
        s.add("P200", "26", "BASE DE CALCULO DO IMPOSTO SOBRE O LUCRO PRESUMIDO", v(base))
        irpj = q(base * D("0.15"))
        adic = q(max(base - D("60000"), ZERO) * D("0.10"))
        irrf = q(rb * D("0.015") * D("0.62"))
        ir_pag = irpj + adic - irrf
        s.add("P300", "1", "BASE DE CALCULO DO IMPOSTO SOBRE O LUCRO PRESUMIDO", v(base))
        s.add("P300", "3", "A Aliquota de 15%", v(irpj))
        s.add("P300", "4", "Adicional", v(adic))
        s.add("P300", "10", "(-) Imposto de Renda Retido na Fonte", v(irrf))  # código ILUSTRATIVO
        s.add("P300", "15", "IMPOSTO DE RENDA A PAGAR", v(ir_pag))
        bcs = q(rb * D("0.32")) + rf
        s.add("P400", "11", "Receita bruta - prestacao de servicos em geral (32%)", v(rb))
        s.add("P400", "12", "Resultado da aplicacao do percentual (32%)", v(q(rb * D("0.32"))))
        s.add("P400", "15", "Rendimentos e ganhos liquidos de aplicacoes financeiras", v(rf))
        s.add("P400", "21", "BASE DE CALCULO DA CSLL", v(bcs))
        cs = q(bcs * D("0.09"))
        csr = q(rb * D("0.01") * D("0.62"))
        s.add("P500", "1", "BASE DE CALCULO DA CSLL", v(bcs))
        s.add("P500", "4", "TOTAL DA CONTRIBUICAO SOCIAL SOBRE O LUCRO LIQUIDO", v(cs))
        s.add("P500", "8", "(-) CSLL Retida na Fonte", v(csr))  # código ILUSTRATIVO
        s.add("P500", "13", "CSLL A PAGAR", v(cs - csr))
        tri.append(dict(tri=i, receita=rb, base_ir=base, irpj=irpj, adicional=adic, irrf=irrf, ir_pagar=ir_pag,
                        base_cs=bcs, csll=cs, csll_ret=csr, cs_pagar=cs - csr))
    s.close("P")
    ecf_tail(s, e, sum(receitas), [SOCIO_B1, SOCIO_B2], ["Q", "T", "U", "V", "W", "X"])
    return s, tri


# --------------------------------------------------------------------------- orquestração
def gerar(base):
    import os
    os.makedirs(f"{base}/ecd", exist_ok=True)
    os.makedirs(f"{base}/ecf", exist_ok=True)
    saldo = dict(SALDO_INICIAL_2022)
    prev_dre = None
    ledgers = {}
    num_ord = {2022: "17", 2023: "18", 2024: "19", 2025: "20"}
    for ano in (2022, 2023, 2024, 2025):
        L = simula_ano(ano, saldo)
        ledgers[ano] = L
        s, dv = gera_ecd(L, prev_dre, num_ord[ano])
        s.save(f"{base}/ecd/ECD_{ano}_{EMP_A['cnpj']}_ORIGINAL.txt")
        if ano == 2024:
            Ls = simula_ano(ano, saldo, substituta=True)
            ss, _ = gera_ecd(Ls, prev_dre, num_ord[ano], substituta=True)
            ss.save(f"{base}/ecd/ECD_{ano}_{EMP_A['cnpj']}_SUBSTITUTA.txt")
            assert Ls.resultado == L.resultado
        prev_dre = dv
        saldo = {c: b for c, b in L.bal.items() if b != 0}
    L24 = ledgers[2024]
    pf24 = max(-(L24.resultado + L24.adicoes), ZERO)
    prej_ir = D("152300.00") + pf24
    s, info = gera_ecf_real(ledgers[2025], L24, prej_ir, prej_ir)
    s.save(f"{base}/ecf/ECF_2025_LUCRO_REAL_{EMP_A['cnpj']}.txt")
    sp, tri = gera_ecf_presumido(2025)
    sp.save(f"{base}/ecf/ECF_2025_LUCRO_PRESUMIDO_{EMP_B['cnpj']}.txt")
    resumo = {a: dict(resultado=l.resultado, adicoes=l.adicoes,
                      ativo=sum(l.bal[c] for c in PATRIM if c.startswith("1")),
                      pl=-sum(l.bal[c] for c in PATRIM if c.startswith("2.3")))
              for a, l in ledgers.items()}
    return dict(resumo=resumo, ecf_real=info, prej_ant=prej_ir, pf24=pf24, presumido=tri, ledgers=ledgers)
