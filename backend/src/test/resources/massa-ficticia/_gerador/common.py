# -*- coding: utf-8 -*-
"""Utilitários comuns do gerador de massa de teste fictícia do FiscalHub."""
import datetime as dt
from collections import Counter
from decimal import Decimal, ROUND_HALF_UP

D = Decimal
ZERO = D("0.00")


def q(x):
    return D(x).quantize(D("0.01"), ROUND_HALF_UP)


def v(x):
    """Valor no formato SPED (vírgula decimal, sem milhar)."""
    return f"{q(x):.2f}".replace(".", ",")


def brl(x):
    s = f"{q(x):,.2f}"
    return s.replace(",", "X").replace(".", ",").replace("X", ".")


def dts(d):
    return d.strftime("%d%m%Y")


def dbr(d):
    return d.strftime("%d/%m/%Y")


FERIADOS = {(1, 1), (21, 4), (1, 5), (7, 9), (12, 10), (2, 11), (15, 11), (20, 11), (25, 12)}


def util_antes(d):
    """Antecipa para o último dia útil (fins de semana e feriados nacionais fixos)."""
    while d.weekday() >= 5 or (d.day, d.month) in FERIADOS:
        d -= dt.timedelta(days=1)
    return d


def util_depois(d):
    while d.weekday() >= 5 or (d.day, d.month) in FERIADOS:
        d += dt.timedelta(days=1)
    return d


def ultimo_dia(y, m):
    nxt = dt.date(y + (m == 12), m % 12 + 1, 1)
    return nxt - dt.timedelta(days=1)


# ---------------------------------------------------------------- documentos
def _dv_cnpj(base12):
    def dv(nums, pesos):
        s = sum(int(n) * p for n, p in zip(nums, pesos))
        r = s % 11
        return "0" if r < 2 else str(11 - r)
    d1 = dv(base12, [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2])
    d2 = dv(base12 + d1, [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2])
    return base12 + d1 + d2


def cnpj(raiz8, filial="0001"):
    return _dv_cnpj(raiz8 + filial)


def cpf(base9):
    s = sum(int(n) * p for n, p in zip(base9, range(10, 1, -1)))
    d1 = 0 if s % 11 < 2 else 11 - s % 11
    b = base9 + str(d1)
    s = sum(int(n) * p for n, p in zip(b, range(11, 1, -1)))
    d2 = 0 if s % 11 < 2 else 11 - s % 11
    return b + str(d2)


def ie_pr(base8):
    s = sum(int(n) * p for n, p in zip(base8, [3, 2, 7, 6, 5, 4, 3, 2]))
    d1 = 11 - s % 11
    d1 = 0 if d1 >= 10 else d1
    b = base8 + str(d1)
    s = sum(int(n) * p for n, p in zip(b, [4, 3, 2, 7, 6, 5, 4, 3, 2]))
    d2 = 11 - s % 11
    d2 = 0 if d2 >= 10 else d2
    return b + str(d2)


def fmt_cnpj(c):
    return f"{c[:2]}.{c[2:5]}.{c[5:8]}/{c[8:12]}-{c[12:]}"


def fmt_cpf(c):
    return f"{c[:3]}.{c[3:6]}.{c[6:9]}-{c[9:]}"


def chave_nfe(cuf, data, cnpj_emit, serie, numero, cnf):
    base = f"{cuf}{data:%y%m}{cnpj_emit}55{serie:03d}{numero:09d}1{cnf:08d}"
    pesos = [2, 3, 4, 5, 6, 7, 8, 9]
    s = sum(int(n) * pesos[i % 8] for i, n in enumerate(reversed(base)))
    r = s % 11
    dv = 0 if r < 2 else 11 - r
    return base + str(dv)


# ---------------------------------------------------------------- SPED writer
class Sped:
    """Acumula registros SPED e fecha blocos/bloco 9 com contagens corretas."""

    def __init__(self):
        self.lines = []

    def add(self, *f):
        self.lines.append(["" if x is None else str(x) for x in f])

    def close(self, blk):
        n = sum(1 for l in self.lines if l[0][0] == blk) + 1
        self.add(f"{blk}990", n)

    def finish(self):
        cnt = Counter(l[0] for l in self.lines)
        regs = list(dict.fromkeys(l[0] for l in self.lines)) + ["9001", "9900", "9990", "9999"]
        cnt.update({"9001": 1, "9900": len(regs), "9990": 1, "9999": 1})
        b9 = [["9001", "0"]] + [["9900", r, str(cnt[r])] for r in regs]
        qtd9 = len(b9) + 2
        total = len(self.lines) + qtd9
        b9 += [["9990", str(qtd9)], ["9999", str(total)]]
        self.lines += b9
        return total

    def text(self):
        return "".join("|" + "|".join(l) + "|\r\n" for l in self.lines)

    def save(self, path):
        with open(path, "w", encoding="latin-1", newline="") as fh:
            fh.write(self.text())


# ---------------------------------------------------------------- cadastro fictício
MUN = {
    "CASCAVEL": "4104808", "CURITIBA": "4106902", "TOLEDO": "4127700",
    "SAO PAULO": "3550308", "CAMPINAS": "3509502", "JOINVILLE": "4209102",
}

EMP_A = dict(  # Lucro Real, indústria
    nome="METALURGICA PINHEIRAL INDUSTRIA E COMERCIO LTDA",
    fantasia="PINHEIRAL METAIS",
    cnpj=cnpj("11222333"),
    ie=ie_pr("90123456"),
    im="0045871",
    nire="41208877651",
    cep="85813450",
    end="RUA DAS INDUSTRIAS", num="1450", compl="BARRACAO 2", bairro="DISTRITO INDUSTRIAL",
    mun="CASCAVEL", cod_mun=MUN["CASCAVEL"], uf="PR",
    fone="4532250000", email="fiscal@pinheiral.exemplo",
    cnae="2949299",
)

EMP_B = dict(  # Lucro Presumido, serviços
    nome="VALE DO IGUACU SERVICOS TECNICOS LTDA",
    fantasia="VALE TEC",
    cnpj=cnpj("44555666"),
    ie="",
    im="0067120",
    nire="41209988120",
    cep="85810020",
    end="AVENIDA BRASIL", num="5120", compl="SALA 804", bairro="CENTRO",
    mun="CASCAVEL", cod_mun=MUN["CASCAVEL"], uf="PR",
    fone="4532260000", email="contato@valetec.exemplo",
    cnae="7112000",
)

CONTADOR = dict(nome="MARIANA COSTA ALBUQUERQUE", cpf=cpf("111444777"), crc="PR-071234/O-5",
                cnpj=cnpj("77888999"), email="mariana@contabil.exemplo", fone="4532240000")
SOCIO1 = dict(nome="ROBERTO PINHEIRO SALDANHA", cpf=cpf("529982247"), perc=D("60.00"))
SOCIO2 = dict(nome="HELENA PINHEIRO SALDANHA", cpf=cpf("362159881"), perc=D("40.00"))
SOCIO_B1 = dict(nome="CARLOS EDUARDO MENEGHETTI", cpf=cpf("248366193"), perc=D("50.00"))
SOCIO_B2 = dict(nome="PATRICIA LUZ MENEGHETTI", cpf=cpf("735416029"), perc=D("50.00"))
