# -*- coding: utf-8 -*-
"""EFD ICMS/IPI e EFD-Contribuições de 08/2026 (mesmos documentos fiscais). Dados fictícios."""
import datetime as dt
import os
from collections import defaultdict

from common import (D, ZERO, q, v, dts, util_antes, Sped, EMP_A, CONTADOR, MUN, cnpj, ie_pr, chave_nfe)

ANO, MES = 2026, 8
INI, FIM = dt.date(ANO, MES, 1), dt.date(ANO, MES, 31)
ALQ_INT = D("19.50")
MVA = D("0.50")          # MVA fictícia para o cenário de teste
SALDO_CREDOR_ANT = D("480000.00")

PART = {  # cod: nome, cnpj, ie, mun, uf, end
    "F001": ("ACOS PAULISTA DISTRIBUIDORA S.A.", cnpj("33444555"), "110042490114", "SAO PAULO", "SP", "AV DO ESTADO"),
    "F002": ("TUBOFORT INDUSTRIA DE TUBOS LTDA", cnpj("55666777"), ie_pr("90555111"), "CURITIBA", "PR", "RUA JOAO BETTEGA"),
    "F003": ("LUBRIPAR COMERCIO DE LUBRIFICANTES LTDA", cnpj("66777888"), ie_pr("90777222"), "CASCAVEL", "PR", "RUA PARANA"),
    "C001": ("AGRIMAQ IMPLEMENTOS AGRICOLAS LTDA", cnpj("22333444"), ie_pr("90888333"), "TOLEDO", "PR", "ROD PR 317"),
    "C002": ("AUTOPECAS OESTE DISTRIBUIDORA LTDA", cnpj("88999000"), ie_pr("90999444"), "CASCAVEL", "PR", "AV TANCREDO NEVES"),
    "C003": ("CATARINENSE MOTOPECAS LTDA", cnpj("12121212"), "255432109", "JOINVILLE", "SC", "RUA DONA FRANCISCA"),
    "C004": ("CAMPINAS FROTAS E PECAS LTDA", cnpj("34343434"), "244123456119", "CAMPINAS", "SP", "AV JOHN BOYD DUNLOP"),
}

ITEM = {  # cod: descr, unid, tipo, ncm, cest
    "P001": ("SUPORTE METALICO PARA ESCAPAMENTO VEICULAR", "PC", "04", "87089200", "0107500"),
    "P002": ("BRACADEIRA ZINCADA REFORCADA 2 POL", "PC", "04", "73269090", ""),
    "M001": ("BOBINA ACO LAMINADO A FRIO 1,5MM", "KG", "01", "72091700", ""),
    "M002": ("TUBO ACO CARBONO 1 1/2 POL", "KG", "01", "73063090", ""),
    "U001": ("OLEO LUBRIFICANTE INDUSTRIAL ISO 68", "LT", "07", "27101932", ""),
}

# entrada/saida, part, item, qtd, valor merc, cfop, cst_icms, aliq icms, st?, ipi?, credito pis/cofins?, dia, num
DOCS_DEF = [
    ("E", "F001", "M001", 42000, D("300000.00"), "2101", "000", D("12.00"), False, True, True, 3, 88101),
    ("E", "F001", "M001", 42000, D("300000.00"), "2101", "000", D("12.00"), False, True, True, 10, 88177),
    ("E", "F001", "M001", 42000, D("300000.00"), "2101", "000", D("12.00"), False, True, True, 17, 88254),
    ("E", "F001", "M001", 42000, D("300000.00"), "2101", "000", D("12.00"), False, True, True, 24, 88330),
    ("E", "F002", "M002", 15800, D("125000.00"), "1101", "000", ALQ_INT, False, True, True, 6, 5512),
    ("E", "F002", "M002", 15800, D("125000.00"), "1101", "000", ALQ_INT, False, True, True, 20, 5590),
    ("E", "F003", "U001", 400, D("6800.00"), "1556", "090", ZERO, False, False, False, 12, 33021),
    ("S", "C001", "P002", 25000, D("125000.00"), "5101", "000", ALQ_INT, False, True, True, 4, 12001),
    ("S", "C001", "P002", 25000, D("125000.00"), "5101", "000", ALQ_INT, False, True, True, 11, 12008),
    ("S", "C001", "P002", 25000, D("125000.00"), "5101", "000", ALQ_INT, False, True, True, 18, 12015),
    ("S", "C001", "P002", 25000, D("125000.00"), "5101", "000", ALQ_INT, False, True, True, 25, 12022),
    ("S", "C002", "P001", 2500, D("100000.00"), "5401", "010", ALQ_INT, True, True, True, 5, 12003),
    ("S", "C002", "P001", 2500, D("100000.00"), "5401", "010", ALQ_INT, True, True, True, 12, 12010),
    ("S", "C002", "P001", 2500, D("100000.00"), "5401", "010", ALQ_INT, True, True, True, 19, 12017),
    ("S", "C002", "P001", 2500, D("100000.00"), "5401", "010", ALQ_INT, True, True, True, 26, 12024),
    ("S", "C003", "P001", 6875, D("275000.00"), "6101", "000", D("12.00"), False, True, True, 7, 12005),
    ("S", "C003", "P002", 55000, D("275000.00"), "6101", "000", D("12.00"), False, True, True, 14, 12012),
    ("S", "C004", "P001", 6875, D("275000.00"), "6101", "000", D("12.00"), False, True, True, 21, 12019),
    ("S", "C004", "P002", 55000, D("275000.00"), "6101", "000", D("12.00"), False, True, True, 28, 12026),
]

UF_COD = {"PR": "41", "SP": "35", "SC": "42"}


def montar_docs():
    docs = []
    for i, (es, part, item, qtd, merc, cfop, cst, aliq, st, tem_ipi, cred, dia, num) in enumerate(DOCS_DEF):
        data = dt.date(ANO, MES, dia)
        ipi = q(merc * D("0.05")) if tem_ipi else ZERO
        bc = merc if aliq > 0 else ZERO
        icms = q(bc * aliq / 100)
        bc_st = q((merc + ipi) * (1 + MVA)) if st else ZERO
        icms_st = q(bc_st * ALQ_INT / 100) - icms if st else ZERO
        bc_pc = merc - icms if (es == "S" or cred) else ZERO
        pis = q(bc_pc * D("0.0165"))
        cof = q(bc_pc * D("0.076"))
        emit_cnpj = EMP_A["cnpj"] if es == "S" else PART[part][1]
        uf = "PR" if es == "S" else PART[part][4]
        chave = chave_nfe(UF_COD[uf], data, emit_cnpj, 1, num, 10000000 + i * 7919)
        docs.append(dict(es=es, part=part, item=item, qtd=D(qtd), merc=merc, cfop=cfop, cst=cst, aliq=aliq,
                         st=st, ipi=ipi, bc=bc, icms=icms, bc_st=bc_st, icms_st=icms_st, bc_pc=bc_pc, pis=pis,
                         cof=cof, cred=cred, data=data, num=num, chave=chave,
                         total=merc + ipi + icms_st))
    return docs


def c100(s, d):
    s.add("C100", "0" if d["es"] == "E" else "1", "1" if d["es"] == "E" else "0", d["part"], "55", "00", "1",
          d["num"], d["chave"], dts(d["data"]), dts(d["data"]), v(d["total"]), "1", v(0), v(0), v(d["merc"]),
          "0", v(0), v(0), v(0), v(d["bc"]), v(d["icms"]), v(d["bc_st"]), v(d["icms_st"]), v(d["ipi"]),
          v(d["pis"]), v(d["cof"]), v(0), v(0))


def c170(s, d, contrib=False):
    cst_ipi = ("00" if d["es"] == "E" else "50") if d["ipi"] else ("49" if d["es"] == "E" else "99")
    if d["es"] == "S":
        cst_pc = "01"
    else:
        cst_pc = "50" if d["cred"] else "70"
    aliq_pis = "1,6500" if d["bc_pc"] else ""
    aliq_cof = "7,6000" if d["bc_pc"] else ""
    s.add("C170", "1", d["item"], "", f"{d['qtd']:.5f}".replace(".", ","), ITEM[d["item"]][1], v(d["merc"]),
          v(0), "0", d["cst"], d["cfop"], "", v(d["bc"]), v(d["aliq"]), v(d["icms"]), v(d["bc_st"]),
          v(ALQ_INT if d["st"] else 0), v(d["icms_st"]), "0", cst_ipi, "999",
          v(d["merc"] if d["ipi"] else 0), v(5 if d["ipi"] else 0), v(d["ipi"]),
          cst_pc, v(d["bc_pc"]), aliq_pis, "", "", v(d["pis"]),
          cst_pc, v(d["bc_pc"]), aliq_cof, "", "", v(d["cof"]),
          "1.1.03.001" if d["es"] == "E" else "3.1.01.001", v(0))


def bloco0_comum(s, docs, contrib):
    e = EMP_A
    s.add("0001", "0")
    if not contrib:
        s.add("0005", e["fantasia"], e["cep"], e["end"], e["num"], e["compl"], e["bairro"], e["fone"], "", e["email"])
    s.add("0100", CONTADOR["nome"], CONTADOR["cpf"], CONTADOR["crc"].replace("PR-", "").replace("/O-", "O"),
          CONTADOR["cnpj"], e["cep"], "RUA SETE DE SETEMBRO", "2100", "", "CENTRO", CONTADOR["fone"], "",
          CONTADOR["email"], e["cod_mun"])
    if contrib:
        s.add("0110", "1", "1", "1", "")
        s.add("0140", "", e["nome"], e["cnpj"], e["uf"], e["ie"], e["cod_mun"], e["im"], "")
    usados = sorted({d["part"] for d in docs})
    for p in usados:
        nome, cn, ie, mun, uf, end = PART[p]
        s.add("0150", p, nome, "01058", cn, "", ie, MUN[mun], "", end, "100", "", "CENTRO")
    for u, desc in (("KG", "QUILOGRAMA"), ("LT", "LITRO"), ("PC", "PECA")):
        s.add("0190", u, desc)
    for cod, (desc, un, tipo, ncm, cest) in ITEM.items():
        if contrib:
            s.add("0200", cod, desc, "", "", un, tipo, ncm, "", ncm[:2], "", v(ALQ_INT))
        else:
            s.add("0200", cod, desc, "", "", un, tipo, ncm, "", ncm[:2], "", v(ALQ_INT), cest)


# --------------------------------------------------------------------------- EFD ICMS/IPI
def gera_icms_ipi(docs):
    e = EMP_A
    s = Sped()
    s.add("0000", "020", "0", dts(INI), dts(FIM), e["nome"], e["cnpj"], "", e["uf"], e["ie"], e["cod_mun"],
          e["im"], "", "A", "0")
    bloco0_comum(s, docs, contrib=False)
    s.close("0")
    s.add("B001", "1"); s.close("B")
    s.add("C001", "0")
    for d in sorted(docs, key=lambda x: (x["data"], x["num"])):
        c100(s, d)
        if d["es"] == "E":
            c170(s, d)
        s.add("C190", d["cst"], d["cfop"], v(d["aliq"]), v(d["total"]), v(d["bc"]), v(d["icms"]),
              v(d["bc_st"]), v(d["icms_st"]), v(0), v(d["ipi"]), "")
    s.close("C")
    s.add("D001", "1"); s.close("D")
    deb = sum(d["icms"] for d in docs if d["es"] == "S")
    cred = sum(d["icms"] for d in docs if d["es"] == "E")
    sld = deb - cred - SALDO_CREDOR_ANT
    a_rec = max(sld, ZERO)
    transp = max(-sld, ZERO)
    st = sum(d["icms_st"] for d in docs)
    ipi_deb = sum(d["ipi"] for d in docs if d["es"] == "S")
    ipi_cred = sum(d["ipi"] for d in docs if d["es"] == "E")
    venc_st = util_antes(dt.date(ANO, MES + 1, 10))
    s.add("E001", "0")
    s.add("E100", dts(INI), dts(FIM))
    s.add("E110", v(deb), v(0), v(0), v(0), v(cred), v(0), v(0), v(0), v(SALDO_CREDOR_ANT),
          v(a_rec), v(0), v(a_rec), v(transp), v(0))
    s.add("E200", "PR", dts(INI), dts(FIM))
    s.add("E210", "1", v(0), v(0), v(0), v(0), v(0), v(st), v(0), v(0), v(st), v(0), v(st), v(0), v(0))
    s.add("E250", "002", v(st), dts(venc_st), "1023", "", "", "", "ICMS-ST retido nas saidas internas - CFOP 5401",
          f"{MES:02d}{ANO}")
    s.add("E500", "0", dts(INI), dts(FIM))
    e510 = defaultdict(lambda: [ZERO, ZERO, ZERO])
    for d in docs:
        if d["ipi"]:
            k = (d["cfop"], "00" if d["es"] == "E" else "50")
            e510[k][0] += d["merc"] + d["ipi"]
            e510[k][1] += d["merc"]
            e510[k][2] += d["ipi"]
    for (cfop, cst), (vc, bc, vi) in sorted(e510.items()):
        s.add("E510", cfop, cst, v(vc), v(bc), v(vi))
    s.add("E520", v(0), v(ipi_deb), v(ipi_cred), v(0), v(0), v(0), v(ipi_deb - ipi_cred))
    s.close("E")
    s.add("G001", "1"); s.close("G")
    s.add("H001", "1"); s.close("H")
    s.add("K001", "1"); s.close("K")
    s.add("1001", "0")
    s.add("1010", *(["N"] * 13))
    s.close("1")
    s.finish()
    return s, dict(deb=deb, cred=cred, saldo_ant=SALDO_CREDOR_ANT, transp=transp, a_recolher=a_rec, st=st,
                   venc_st=venc_st, ipi_deb=ipi_deb, ipi_cred=ipi_cred, ipi_rec=ipi_deb - ipi_cred)


# --------------------------------------------------------------------------- EFD-Contribuições
def gera_contrib(docs):
    e = EMP_A
    s = Sped()
    s.add("0000", "006", "0", "", "", dts(INI), dts(FIM), e["nome"], e["cnpj"], e["uf"], e["cod_mun"], "", "00", "0")
    bloco0_comum(s, docs, contrib=True)
    s.close("0")
    s.add("A001", "1"); s.close("A")
    s.add("C001", "0")
    s.add("C010", e["cnpj"], "2")
    for d in sorted(docs, key=lambda x: (x["data"], x["num"])):
        c100(s, d)
        c170(s, d, contrib=True)
    s.close("C")
    s.add("D001", "1"); s.close("D")
    s.add("F001", "1"); s.close("F")
    s.add("I001", "1"); s.close("I")
    rec_brt = sum(d["merc"] for d in docs if d["es"] == "S")
    bc_deb = sum(d["bc_pc"] for d in docs if d["es"] == "S")
    bc_cred = sum(d["bc_pc"] for d in docs if d["es"] == "E" and d["cred"])
    out = {}
    s.add("M001", "0")
    for trib, aliq, rc, rm in (("PIS", D("1.65"), "M100", "M200"), ("COFINS", D("7.60"), "M500", "M600")):
        cred = q(bc_cred * aliq / 100)
        deb = q(bc_deb * aliq / 100)
        dev = deb - cred
        cod_rec = "691201" if trib == "PIS" else "585601"
        if trib == "COFINS":
            s.add("M500", "101", "0", v(bc_cred), "7,6000", "", "", v(cred), v(0), v(0), v(0), v(cred), "0",
                  v(cred), v(0))
            s.add("M505", "02", "50", v(bc_cred), v(0), v(bc_cred), v(bc_cred), "", "", "")
            s.add("M600", v(deb), v(cred), v(0), v(dev), v(0), v(0), v(dev), v(0), v(0), v(0), v(0), v(dev))
            s.add("M605", "08", cod_rec, v(dev))
            s.add("M610", "01", v(rec_brt), v(bc_deb), v(0), v(0), v(bc_deb), "7,6000", "", "", v(deb), v(0), v(0),
                  v(0), v(0), v(deb))
        else:
            s.add("M100", "101", "0", v(bc_cred), "1,6500", "", "", v(cred), v(0), v(0), v(0), v(cred), "0",
                  v(cred), v(0))
            s.add("M105", "02", "50", v(bc_cred), v(0), v(bc_cred), v(bc_cred), "", "", "")
            s.add("M200", v(deb), v(cred), v(0), v(dev), v(0), v(0), v(dev), v(0), v(0), v(0), v(0), v(dev))
            s.add("M205", "08", cod_rec, v(dev))
            s.add("M210", "01", v(rec_brt), v(bc_deb), v(0), v(0), v(bc_deb), "1,6500", "", "", v(deb), v(0), v(0),
                  v(0), v(0), v(deb))
        out[trib] = dict(debito=deb, credito=cred, devido=dev, bc_deb=bc_deb, bc_cred=bc_cred)
    # reordena: bloco M exige M100..M210 antes de M500..M610
    mlines = [l for l in s.lines if l[0].startswith("M") and l[0] != "M001"]
    s.lines = [l for l in s.lines if not (l[0].startswith("M") and l[0] != "M001")]
    order = ["M100", "M105", "M200", "M205", "M210", "M500", "M505", "M600", "M605", "M610"]
    s.lines += sorted(mlines, key=lambda l: order.index(l[0]))
    s.close("M")
    s.add("P001", "1"); s.close("P")
    s.add("1001", "1"); s.close("1")
    s.finish()
    out["receita_bruta"] = rec_brt
    return s, out


def gerar(base):
    os.makedirs(f"{base}/efd-icms-ipi", exist_ok=True)
    os.makedirs(f"{base}/efd-contribuicoes", exist_ok=True)
    docs = montar_docs()
    s1, icms = gera_icms_ipi(docs)
    s1.save(f"{base}/efd-icms-ipi/EFD_ICMS_IPI_{MES:02d}{ANO}_{EMP_A['cnpj']}.txt")
    s2, pc = gera_contrib(docs)
    s2.save(f"{base}/efd-contribuicoes/EFD_CONTRIBUICOES_{MES:02d}{ANO}_{EMP_A['cnpj']}.txt")
    return dict(icms=icms, pc=pc, docs=docs)
