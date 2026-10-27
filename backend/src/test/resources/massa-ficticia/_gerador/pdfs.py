# -*- coding: utf-8 -*-
"""PDFs fictícios (e-CAC, PGFN, DCTF, DCTFWeb, DARF, Receita Estadual PR). Todos marcados como fictícios."""
import datetime as dt
import math
import os

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle, KeepTogether

from common import (D, ZERO, q, brl, dbr, util_antes, ultimo_dia, fmt_cnpj, fmt_cpf, EMP_A, EMP_B, SOCIO1)

EMISSAO = dt.datetime(2026, 10, 14, 9, 42, 17)
AVISO = "DOCUMENTO FICTÍCIO PARA TESTE DE SOFTWARE (FiscalHub) — SEM VALOR LEGAL"

ST_T = ParagraphStyle("t", fontName="Helvetica-Bold", fontSize=12, leading=15, spaceAfter=2)
ST_ST = ParagraphStyle("st", fontName="Helvetica", fontSize=9, leading=11, textColor=colors.HexColor("#444444"))
ST_H = ParagraphStyle("h", fontName="Helvetica-Bold", fontSize=9.5, leading=12, spaceBefore=8, spaceAfter=3,
                      textColor=colors.HexColor("#1F3A5F"))
ST_P = ParagraphStyle("p", fontName="Helvetica", fontSize=8, leading=10)
ST_C = ParagraphStyle("c", fontName="Helvetica", fontSize=7.2, leading=8.6)
ST_CB = ParagraphStyle("cb", fontName="Helvetica-Bold", fontSize=7.2, leading=8.6)


def _deco(c, doc):
    c.saveState()
    w, h = A4
    c.setFillColor(colors.HexColor("#B00020"))
    c.setFont("Helvetica-Bold", 7)
    c.drawCentredString(w / 2, h - 9 * mm, AVISO)
    c.setFont("Helvetica", 6.5)
    c.setFillColor(colors.HexColor("#666666"))
    c.drawString(15 * mm, 8 * mm, f"Emitido em {EMISSAO:%d/%m/%Y %H:%M:%S} (dados fictícios)")
    c.drawRightString(w - 15 * mm, 8 * mm, f"Página {doc.page}")
    c.translate(w / 2, h / 2)
    c.rotate(35)
    c.setFillColor(colors.Color(0.75, 0.1, 0.1, alpha=0.07))
    c.setFont("Helvetica-Bold", 60)
    c.drawCentredString(0, 0, "DOCUMENTO FICTÍCIO")
    c.restoreState()


def build(path, story):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    doc = SimpleDocTemplate(path, pagesize=A4, leftMargin=14 * mm, rightMargin=14 * mm, topMargin=15 * mm,
                            bottomMargin=14 * mm, title=os.path.basename(path), author="Gerador de massa de teste")
    doc.build(story, onFirstPage=_deco, onLaterPages=_deco)


def head(orgao, titulo, sub=None):
    out = [Paragraph(orgao, ST_ST), Paragraph(titulo, ST_T)]
    if sub:
        out.append(Paragraph(sub, ST_ST))
    out.append(Spacer(1, 4))
    return out


def tbl(rows, widths, header=True, bold_last=False, align_right=()):
    data = [[Paragraph(str(c), ST_CB if (header and i == 0) or (bold_last and i == len(rows) - 1) else ST_C)
             for c in r] for i, r in enumerate(rows)]
    t = Table(data, colWidths=[w * mm for w in widths], repeatRows=1 if header else 0)
    st = [("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#9AA5B1")),
          ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
          ("TOPPADDING", (0, 0), (-1, -1), 2), ("BOTTOMPADDING", (0, 0), (-1, -1), 2)]
    if header:
        st.append(("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#E3E8EF")))
    t.setStyle(TableStyle(st))
    return t


def kv(pairs, widths=(45, 137)):
    return tbl([[f"<b>{k}</b>", v] for k, v in pairs], widths, header=False)


def ident(e):
    return kv([("CNPJ", fmt_cnpj(e["cnpj"])), ("Nome empresarial", e["nome"]),
               ("Endereço", f'{e["end"]}, {e["num"]} - {e["compl"]} - {e["bairro"]} - {e["mun"].title()}/{e["uf"]}')])


# --------------------------------------------------------------------------- dados de cenário
def cenario(efd, contabil):
    a = EMP_A
    venc_cp = util_antes(dt.date(2026, 9, 20))
    venc_pc = util_antes(dt.date(2026, 9, 25))
    folha = D("185000.00")
    cp = [("1082-01", "CP descontada do segurado", D("17950.00")),
          ("1138-01", "CP patronal", q(folha * D("0.20"))),
          ("1646-01", "CP GILRAT (RAT x FAP)", q(folha * D("0.03"))),
          ("1170-01", "Salário-educação", q(folha * D("0.025"))),
          ("1176-01", "INCRA", q(folha * D("0.002"))),
          ("1181-01", "SENAI", q(folha * D("0.01"))),
          ("1184-01", "SESI", q(folha * D("0.015"))),
          ("1191-01", "SEBRAE", q(folha * D("0.006")))]
    irrf = ("0561-07", "IRRF - Rendimento do trabalho assalariado", D("9815.40"))
    pis, cof = efd["pc"]["PIS"]["devido"], efd["pc"]["COFINS"]["devido"]
    ipi = efd["icms"]["ipi_rec"]
    mit = [("6912-01", "PIS - Não cumulativo", pis), ("5856-01", "COFINS - Não cumulativa", cof),
           ("5123-01", "IPI - Demais produtos", ipi)]
    cof_pago = D("28000.00")
    # DCTF Mensal 11/2024
    dctf = [("5123-01", "IPI - Demais produtos", D("41200.00"), util_antes(dt.date(2024, 12, 25))),
            ("6912-01", "PIS - Não cumulativo", D("6120.33"), util_antes(dt.date(2024, 12, 25))),
            ("5856-01", "COFINS - Não cumulativa", D("28190.11"), util_antes(dt.date(2024, 12, 25))),
            ("0561-07", "IRRF - Trabalho assalariado", D("8950.40"), util_antes(dt.date(2024, 12, 20)))]
    ipi_pg = dt.date(2025, 2, 10)
    # saldo da transação PGFN (contábil fim de 2025 menos 9 parcelas de 2026)
    L25 = contabil["ledgers"][2025]
    sld_neg = -(L25.bal["2.1.03.009"] + L25.bal["2.2.01.001"])
    # parcelas pagas da transação = lançamentos na contabilidade (2022-2025) + 9 em 2026; última parcela pode ser parcial
    pagas = sum(1 for L in contabil["ledgers"].values() for e in L.entries if "PAGTO PARCELA TRANSACAO PGFN" in str(e)) + 9
    restantes = math.ceil((sld_neg - 9 * D("8000")) / D("8000"))
    return dict(venc_cp=venc_cp, venc_pc=venc_pc, folha=folha, cp=cp, irrf=irrf, mit=mit, pis=pis, cof=cof,
                ipi=ipi, cof_pago=cof_pago, dctf=dctf, ipi_pg=ipi_pg, sld_neg=sld_neg, pagas=pagas, restantes=int(restantes))


# CAPAG fictícia: entre 0,5x e 1x o passivo -> classe C (Portaria PGFN 6.757/2022: A >= 2x; B >= 1x; C >= 0,5x; D < 0,5x)
CAPAG_VALOR = D("400000.00")


def classe_capag(capag, passivo):
    r = capag / passivo
    return "A" if r >= 2 else "B" if r >= 1 else "C" if r >= D("0.5") else "D"


INSCR = [  # número, receita, natureza, PA, principal, multa, juros, encargo, situação, negociada
    ("90 2 23 004512-38", "IRPJ - Lucro Real (auto de infração)", "Tributária", "2019", D("86420.15"),
     D("64815.11"), D("41210.40"), D("38489.13"), "ATIVA AJUIZADA - NEGOCIADA", True),
    ("90 6 23 004513-19", "COFINS - Não cumulativa", "Tributária", "03/2022 a 06/2022", D("52300.00"),
     D("10460.00"), D("18240.50"), D("16200.10"), "ATIVA EM COBRANÇA - NEGOCIADA", True),
    ("90 3 24 002201-55", "IPI - Demais produtos", "Tributária", "05/2023", D("38750.00"), D("7750.00"),
     D("6980.12"), D("10696.02"), "ATIVA A SER AJUIZADA", False),
    ("15.678.912-3 (DEBCAD)", "Contribuições previdenciárias (patronal)", "Previdenciária", "01/2022 a 04/2022",
     D("71200.00"), D("14240.00"), D("15040.30"), D("20000.00"), "ATIVA EM COBRANÇA - NEGOCIADA", True),
]


# --------------------------------------------------------------------------- relatórios federais
def situacao_fiscal(base, c):
    a = EMP_A
    dias = (EMISSAO.date() - c["venc_pc"]).days
    multa_cof = q((c["cof"] - c["cof_pago"]) * min(D("0.0033") * dias, D("0.20")))
    juros_cof = q((c["cof"] - c["cof_pago"]) * D("0.01"))
    multa_ipi, juros_ipi = q(D("41200.00") * D("0.20")), q(D("41200.00") * D("0.0208"))
    s = head("MINISTÉRIO DA FAZENDA — SECRETARIA ESPECIAL DA RECEITA FEDERAL / PGFN (simulação)",
             "INFORMAÇÕES DE APOIO PARA EMISSÃO DE CERTIDÃO",
             f"Por meio do e-CAC — CNPJ do certificado: {fmt_cnpj(a['cnpj'])} — {EMISSAO:%d/%m/%Y %H:%M}")
    s += [ident(a), Paragraph("Diagnóstico Fiscal na Receita Federal e Procuradoria-Geral da Fazenda Nacional", ST_H)]
    s.append(Paragraph("Pendência - Débito (SIEF)", ST_H))
    rows = [["Receita", "PA/Exerc.", "Dt. Vcto", "Vl. Original", "Sdo. Devedor", "Multa", "Juros",
             "Sdo. Dev. Cons.", "Situação"],
            ["5123-01 - IPI", "11/2024", dbr(c["dctf"][0][3]), brl(41200), brl(0), brl(multa_ipi), brl(juros_ipi),
             brl(multa_ipi + juros_ipi), "DEVEDOR"],
            ["5856-01 - COFINS", "08/2026", dbr(c["venc_pc"]), brl(c["cof"]), brl(c["cof"] - c["cof_pago"]),
             brl(multa_cof), brl(juros_cof), brl(c["cof"] - c["cof_pago"] + multa_cof + juros_cof), "DEVEDOR"]]
    s.append(tbl(rows, [26, 15, 17, 20, 20, 17, 17, 21, 19]))
    s.append(Paragraph("Pendência - Omissão de Declaração", ST_H))
    s.append(tbl([["Declaração", "Período", "Situação"],
                  ["EFD-Contribuições", "06/2026", "OMISSA"]], [70, 40, 72]))
    s.append(Paragraph("Pendência - Inscrição (SIDA)", ST_H))
    i = INSCR[2]
    s.append(tbl([["Inscrição", "Receita", "Inscrito em", "Ajuizado em", "Processo", "Situação"],
                  [i[0], i[1], "18/03/2024", "-", "10980.700112/2024-31", i[8]]], [30, 38, 20, 20, 34, 40]))
    s.append(Paragraph("Débitos com Exigibilidade Suspensa / Negociados", ST_H))
    rows = [["Inscrição", "Receita", "Situação", "Negociação"]]
    for i in INSCR:
        if i[9]:
            rows.append([i[0], i[1], i[8], "Transação por adesão (edital fictício) - parcelas em dia"])
    s.append(tbl(rows, [34, 50, 46, 52]))
    s.append(Paragraph("Processo Fiscal com Exigibilidade Suspensa (SIEF)", ST_H))
    s.append(tbl([["Processo", "Assunto", "Situação"],
                  ["10980.731122/2025-48", "Auto de infração - IPI 2021", "EM IMPUGNAÇÃO - EXIGIBILIDADE SUSPENSA"]],
                 [40, 70, 72]))
    s.append(Spacer(1, 6))
    s.append(Paragraph("Final do Relatório. Todas as informações deste relatório são fictícias e foram geradas para "
                       "testes de importação; não correspondem a nenhum contribuinte real.", ST_P))
    build(f"{base}/situacao-fiscal/Relatorio_Situacao_Fiscal_{a['cnpj']}_20261014.pdf", s)
    return dict(multa_cof=multa_cof, juros_cof=juros_cof, multa_ipi=multa_ipi, juros_ipi=juros_ipi)


def pgfn(base, c):
    a = EMP_A
    s = head("PROCURADORIA-GERAL DA FAZENDA NACIONAL — REGULARIZE (simulação)",
             "EXTRATO DE INSCRIÇÕES EM DÍVIDA ATIVA DA UNIÃO", f"Devedor: {fmt_cnpj(a['cnpj'])} - {a['nome']}")
    rows = [["Inscrição", "Receita / Natureza", "PA", "Principal", "Multa", "Juros", "Encargo legal",
             "Total consolidado", "Situação"]]
    tot = [ZERO] * 5
    for i in INSCR:
        t = i[4] + i[5] + i[6] + i[7]
        rows.append([i[0], f"{i[1]} ({i[2]})", i[3], brl(i[4]), brl(i[5]), brl(i[6]), brl(i[7]), brl(t), i[8]])
        for k, x in enumerate((i[4], i[5], i[6], i[7], t)):
            tot[k] += x
    rows.append(["TOTAL", "", "", *[brl(x) for x in tot], ""])
    s.append(tbl(rows, [25, 33, 16, 17, 16, 16, 17, 19, 23], bold_last=True))
    s.append(Paragraph("Negociações", ST_H))
    s.append(tbl([["Negociação", "Modalidade", "Inscrições", "Parcelas", "Valor da parcela", "Saldo devedor",
                   "Situação"],
                  ["2021.009871-4", "Transação por adesão (edital fictício)", "90 2 23 004512-38; 90 6 23 004513-19; "
                   "15.678.912-3", f"Pagas {c['pagas']} de {c['pagas'] + c['restantes']}", brl(8000), brl(c["sld_neg"] - 9 * 8000), "EM DIA"]],
                 [22, 34, 40, 18, 20, 22, 26]))
    s.append(Paragraph("Ajuizamentos", ST_H))
    s.append(tbl([["Inscrição", "Execução fiscal", "Vara", "Garantia"],
                  ["90 2 23 004512-38", "5012345-67.2023.4.04.7005", "Vara Federal de Execução Fiscal (fictícia)",
                   "Sem garantia - exigibilidade suspensa pela negociação"]], [32, 45, 55, 50]))
    build(f"{base}/pgfn/Extrato_Inscricoes_Regularize_{a['cnpj']}.pdf", s)
    # CAPAG
    passivo = tot[4]
    s = head("PROCURADORIA-GERAL DA FAZENDA NACIONAL — REGULARIZE (simulação)",
             "CAPACIDADE DE PAGAMENTO (CAPAG)", f"Contribuinte: {fmt_cnpj(a['cnpj'])} - {a['nome']}")
    s.append(kv([("Classificação da CAPAG", f"<b>{classe_capag(CAPAG_VALOR, passivo)}</b>"),
                 ("Capacidade de pagamento estimada", brl(CAPAG_VALOR)),
                 ("Passivo fiscal total na PGFN", brl(passivo)),
                 ("Débitos já negociados (saldo)", brl(c["sld_neg"] - 9 * 8000)),
                 ("Débitos passíveis de negociação", brl(INSCR[2][4] + INSCR[2][5] + INSCR[2][6] + INSCR[2][7])),
                 ("Data de referência do cálculo", "01/10/2026"),
                 ("Fontes consideradas (simulação)", "ECF 2025, ECD 2025, DCTFWeb, eSocial/EFD-Reinf, bens declarados")]))
    s.append(Spacer(1, 6))
    s.append(Paragraph("Tela de consulta reproduzida para teste. Valores e classificação fictícios; a regra de cálculo "
                       "real deve seguir a regulamentação vigente da PGFN.", ST_P))
    build(f"{base}/pgfn/CAPAG_{a['cnpj']}.pdf", s)
    return dict(passivo=passivo)


def dctf(base, c):
    a = EMP_A
    os.makedirs(f"{base}/dctf", exist_ok=True)
    # .dec (layout APROXIMADO, apenas para teste de leitura)
    L = []
    L.append(f"DCTFM2024{a['cnpj']}0112024N" + "ORIGINAL".ljust(12) + a["nome"].ljust(60) + "PR4104808")
    L.append("R01" + a["cnpj"] + "11" + "2024" + "0" + "1" + SOCIO1["cpf"] + SOCIO1["nome"].ljust(60))
    for cod, nome, val, venc in c["dctf"]:
        L.append("R10" + cod.replace("-", "") + "112024" + venc.strftime("%d%m%Y") + f"{int(val * 100):015d}" + "M")
        L.append("R11" + cod.replace("-", "") + "DARF" + f"{int(val * 100):015d}" + venc.strftime("%d%m%Y"))
    L.append("R99" + f"{len(L) + 1:06d}")
    with open(f"{base}/dctf/DCTF_Mensal_112024_{a['cnpj']}.dec", "w", encoding="latin-1", newline="") as fh:
        fh.write("\r\n".join(L) + "\r\n")
    s = head("RECEITA FEDERAL (simulação) — PGD DCTF Mensal", "DECLARAÇÃO DE DÉBITOS E CRÉDITOS TRIBUTÁRIOS FEDERAIS",
             "Período: Novembro/2024 — Declaração Original — Recibo nº 24.11.98.765.432-1 (fictício)")
    s.append(ident(a))
    s.append(Paragraph("Dados iniciais", ST_H))
    s.append(kv([("Qualificação da PJ", "PJ em geral"), ("Forma de tributação do lucro", "Lucro Real - Anual (suspensão/redução)"),
                 ("Situação da PJ no mês", "Normal"), ("Responsável", f"{SOCIO1['nome']} - CPF {fmt_cpf(SOCIO1['cpf'])}")]))
    s.append(Paragraph("Débitos apurados e créditos vinculados", ST_H))
    rows = [["Código", "Tributo", "Período", "Vencimento", "Débito apurado", "Créditos vinculados", "Tipo"]]
    for cod, nome, val, venc in c["dctf"]:
        rows.append([cod, nome, "11/2024", dbr(venc), brl(val), brl(val), "Pagamento (DARF)"])
    s.append(tbl(rows, [17, 50, 17, 20, 25, 27, 26]))
    s.append(Paragraph("Observação: IRPJ/CSLL por estimativa não informados (balancete de suspensão no mês).", ST_P))
    build(f"{base}/dctf/DCTF_Mensal_112024_{a['cnpj']}.pdf", s)


def dctfweb(base, c):
    a = EMP_A
    s = head("RECEITA FEDERAL (simulação) — DCTFWeb", "DCTFWeb - DECLARAÇÃO COMPLETA",
             "Categoria: Geral — Período de apuração: 08/2026 — Situação: Ativa — Origem: eSocial / EFD-Reinf / MIT")
    s.append(ident(a))
    s.append(Paragraph("Resumo dos débitos", ST_H))
    rows = [["Código", "Descrição", "Débito apurado", "Créditos vinculados", "Saldo a pagar", "Vencimento"]]
    tot = ZERO
    for cod, nome, val in c["cp"]:
        rows.append([cod, nome, brl(val), brl(0), brl(val), dbr(c["venc_cp"])])
        tot += val
    cod, nome, val = c["irrf"]
    rows.append([cod, nome, brl(val), brl(0), brl(val), dbr(c["venc_cp"])])
    tot += val
    for cod, nome, val in c["mit"]:
        rows.append([cod, nome + " (MIT)", brl(val), brl(0), brl(val), dbr(c["venc_pc"])])
        tot += val
    rows.append(["", "TOTAL", brl(tot), brl(0), brl(tot), ""])
    s.append(tbl(rows, [18, 66, 26, 26, 26, 20], bold_last=True))
    s.append(Paragraph("Bases de cálculo informadas (eSocial)", ST_H))
    s.append(kv([("Remuneração total de segurados empregados", brl(c["folha"])),
                 ("Alíquota RAT / FAP", "3,00% / 1,0000"), ("FPAS / Terceiros", "507 / 0079"),
                 ("Recibo de transmissão", "26.08.11.223.344-5 (fictício)")]))
    build(f"{base}/dctfweb/DCTFWeb_082026_Declaracao_Completa_{a['cnpj']}.pdf", s)
    return tot


def darf(path, a, numero, pa, vcto, itens, pago_em, multa=ZERO, juros=ZERO, obs=""):
    principal = sum(i[2] for i in itens)
    total = principal + multa + juros
    s = head("RECEITA FEDERAL (simulação)", "DOCUMENTO DE ARRECADAÇÃO DE RECEITAS FEDERAIS - DARF",
             f"Número do documento: {numero}")
    s.append(kv([("01 Contribuinte", f"{a['nome']} — Tel. {a['fone']}"), ("02 Período de apuração", pa),
                 ("03 CNPJ", fmt_cnpj(a["cnpj"])), ("06 Data de vencimento", dbr(vcto)),
                 ("Pago em (autenticação bancária)", dbr(pago_em))]))
    s.append(Paragraph("Composição do documento", ST_H))
    rows = [["Código", "Denominação", "Principal", "Multa", "Juros", "Total"]]
    for cod, nome, val in itens:
        rows.append([cod, nome, brl(val), brl(0), brl(0), brl(val)])
    if multa or juros:
        rows.append(["", "Acréscimos legais", brl(0), brl(multa), brl(juros), brl(multa + juros)])
    rows.append(["", "TOTAL A PAGAR", brl(principal), brl(multa), brl(juros), brl(total)])
    s.append(tbl(rows, [20, 72, 24, 20, 20, 26], bold_last=True))
    s.append(Spacer(1, 6))
    s.append(Paragraph("Linha digitável (fictícia): 85800000000 0 " + f"{int(total * 100):011d} 0 " +
                       "38500000000 0 " + numero.replace(".", "").replace("-", "")[-11:] + " 0", ST_P))
    if obs:
        s.append(Paragraph(obs, ST_P))
    build(path, s)
    return total


def pagamentos(base, c):
    a = EMP_A
    p = f"{base}/pagamentos"
    d1 = darf(f"{p}/DARF_DCTFWeb_082026_Previdenciario_IRRF.pdf", a, "07.18.26258.1234567-8", "08/2026",
              c["venc_cp"], c["cp"] + [c["irrf"]], c["venc_cp"])
    mit_pago = [(cod, nome, c["cof_pago"] if cod.startswith("5856") else val) for cod, nome, val in c["mit"]]
    d2 = darf(f"{p}/DARF_DCTFWeb_082026_PIS_COFINS_IPI.pdf", a, "07.18.26263.7654321-0", "08/2026",
              c["venc_pc"], mit_pago, c["venc_pc"],
              obs="DARF emitido com valor de COFINS editado manualmente (divergente do débito declarado).")
    d3 = darf(f"{p}/DARF_Sicalc_IPI_112024_pago_em_atraso.pdf", a, "07.18.25041.0099887-6", "11/2024",
              c["dctf"][0][3], [("5123", "IPI - Demais produtos", D("41200.00"))], c["ipi_pg"],
              obs="Recolhido após o vencimento sem multa e juros de mora.")
    rows = [["Data arrecad.", "Nº documento", "Código", "Denominação", "PA", "Vencimento", "Principal", "Multa",
             "Juros", "Total"]]
    tot = ZERO

    def r(data, num, cod, nome, pa, venc, val, mu=ZERO, ju=ZERO):
        nonlocal tot
        rows.append([dbr(data), num, cod, nome, pa, dbr(venc), brl(val), brl(mu), brl(ju), brl(val + mu + ju)])
        tot += val + mu + ju

    for cod, nome, val, venc in c["dctf"]:
        if cod.startswith("5123"):
            continue
        r(venc, "07.18.24" + venc.strftime("%j") + ".11" + cod[:4] + "-0", cod[:4], nome, "11/2024", venc, val)
    r(c["ipi_pg"], "07.18.25041.0099887-6", "5123", "IPI - Demais produtos", "11/2024", c["dctf"][0][3], D("41200.00"))
    for m in range(1, 10):
        dtp = util_antes(dt.date(2026, m, 28))
        r(dtp, f"07.18.26{dtp:%j}.40{m:02d}000-1", "TRANS", "Parcela transação PGFN 2021.009871-4", f"{m:02d}/2026",
          dtp, D("8000.00"))
    venc_cp7, venc_pc7 = util_antes(dt.date(2026, 8, 20)), util_antes(dt.date(2026, 8, 25))
    r(venc_cp7, "07.18.26232.1112223-4", "DCTFWeb", "DARF DCTFWeb 07/2026 - CP e IRRF", "07/2026", venc_cp7,
      D("103884.12"))
    r(venc_pc7, "07.18.26237.5556667-8", "DCTFWeb", "DARF DCTFWeb 07/2026 - PIS/COFINS/IPI (MIT)", "07/2026",
      venc_pc7, D("61340.27"))
    r(c["venc_cp"], "07.18.26258.1234567-8", "DCTFWeb", "DARF DCTFWeb 08/2026 - CP e IRRF", "08/2026", c["venc_cp"],
      sum(i[2] for i in c["cp"]) + c["irrf"][2])
    r(c["venc_pc"], "07.18.26263.7654321-0", "DCTFWeb", "DARF DCTFWeb 08/2026 - PIS/COFINS/IPI (MIT)", "08/2026",
      c["venc_pc"], sum(i[2] for i in mit_pago))
    rows.append(["", "", "", "TOTAL", "", "", "", "", "", brl(tot)])
    s = head("RECEITA FEDERAL (simulação) — e-CAC", "RELATÓRIO CONSOLIDADO DE PAGAMENTOS",
             f"Contribuinte: {fmt_cnpj(a['cnpj'])} — Período consultado: 01/12/2024 a 14/10/2026")
    s.append(tbl(rows, [15, 29, 13, 37, 15, 16, 18, 10, 10, 19], bold_last=True))
    build(f"{p}/Relatorio_Consolidado_Pagamentos_{a['cnpj']}.pdf", s)
    return dict(darf_cp=d1, darf_mit=d2, darf_ipi=d3)


# --------------------------------------------------------------------------- Paraná
def parana(base, efd):
    a = EMP_A
    p = f"{base}/parana"
    meses = [(2026, m) for m in range(3, 9)]
    st_vals = {(2026, 3): D("38412.20"), (2026, 4): D("41870.55"), (2026, 5): D("43105.90"),
               (2026, 6): D("40288.10"), (2026, 7): D("45930.75"), (2026, 8): efd["icms"]["st"]}
    sld = {(2026, 3): D("402115.40"), (2026, 4): D("431980.12"), (2026, 5): D("455320.77"),
           (2026, 6): D("462011.30"), (2026, 7): D("480000.00"), (2026, 8): efd["icms"]["transp"]}
    venc = {k: util_antes(dt.date(k[0], k[1] + 1, 10)) for k in meses}
    # conta corrente / dívida ativa
    s = head("SECRETARIA DA FAZENDA DO PARANÁ — RECEITA ESTADUAL (simulação)",
             "CONTA CORRENTE FISCAL E DÍVIDA ATIVA - ICMS",
             f"CAD/ICMS {a['ie']} — CNPJ {fmt_cnpj(a['cnpj'])} — {a['nome']}")
    rows = [["Referência", "Origem", "Tributo", "Vencimento", "Valor declarado", "Valor recolhido", "Saldo",
             "Situação"]]
    for k in meses:
        pago = st_vals[k] if k != (2026, 8) else ZERO
        rows.append([f"{k[1]:02d}/{k[0]}", "EFD", "ICMS-ST (substituto)", dbr(venc[k]), brl(st_vals[k]), brl(pago),
                     brl(st_vals[k] - pago), "QUITADO" if pago else "EM ABERTO"])
        rows.append([f"{k[1]:02d}/{k[0]}", "EFD", "ICMS próprio", "-", brl(0), brl(0), brl(0),
                     f"SALDO CREDOR {brl(sld[k])}"])
    s.append(tbl(rows, [17, 12, 32, 18, 24, 24, 20, 35]))
    s.append(Paragraph("Dívida ativa", ST_H))
    s.append(tbl([["CDA", "Origem", "Tributo / período", "Valor original", "Valor atualizado", "Situação"],
                  ["2024.0012345-8", "Auto de infração 6.789.012-3", "ICMS próprio - 07/2022 a 12/2022",
                   brl(D("96400.00")), brl(D("141872.66")), "PARCELADA - parcela 12 de 36 em dia"]],
                 [24, 34, 42, 24, 24, 34]))
    s.append(Paragraph("Parcelamentos", ST_H))
    s.append(tbl([["Nº parcelamento", "Débitos", "Parcelas", "Valor da parcela", "Saldo", "Situação"],
                  ["PR-2025-0045871", "CDA 2024.0012345-8", "36 (12 pagas)", brl(D("4120.35")),
                   brl(D("98884.20")), "ATIVO - EM DIA"]], [30, 36, 24, 26, 26, 40]))
    build(f"{p}/Conta_Corrente_e_Divida_Ativa_ICMS_{a['ie']}.pdf", s)
    # RTI
    s = head("SECRETARIA DA FAZENDA DO PARANÁ — RECEITA ESTADUAL (simulação)", "RTI",
             "Resumo por período de apuração do ICMS (layout fictício para teste)")
    s.append(ident(a))
    rows = [["Período", "Débitos", "Créditos", "Saldo credor anterior", "ICMS a recolher",
             "Saldo credor a transportar", "ICMS-ST retido", "ICMS-ST recolhido"]]
    prev = D("371880.20")
    for k in meses:
        deb = D("298000.00") + D(k[1]) * D("1500")
        if k == (2026, 8):
            deb, cred = efd["icms"]["deb"], efd["icms"]["cred"]
        else:
            cred = deb + sld[k] - prev
        pago = st_vals[k] if k != (2026, 8) else ZERO
        rows.append([f"{k[1]:02d}/{k[0]}", brl(deb), brl(cred), brl(prev), brl(0), brl(sld[k]), brl(st_vals[k]),
                     brl(pago)])
        prev = sld[k]
    s.append(tbl(rows, [16, 22, 22, 25, 20, 27, 22, 26]))
    build(f"{p}/RTI_{a['ie']}.pdf", s)
    # pagamentos GR-PR
    rows = [["Data pagto.", "Guia", "Código receita", "Referência", "Descrição", "Valor"]]
    tot = ZERO
    for k in meses[:-1]:
        rows.append([dbr(venc[k]), f"GR-PR 41{k[1]:02d}2026{int(st_vals[k]):07d}", "1023", f"{k[1]:02d}/{k[0]}",
                     "ICMS-ST - substituição tributária", brl(st_vals[k])])
        tot += st_vals[k]
    for n, m in zip(range(7, 13), range(3, 9)):
        d = util_antes(dt.date(2026, m, 15))
        rows.append([dbr(d), f"GR-PR 49{m:02d}2026{n:07d}", "2011", f"{n:02d}/36", "Parcelamento PR-2025-0045871",
                     brl(D("4120.35"))])
        tot += D("4120.35")
    rows.append(["", "", "", "", "TOTAL", brl(tot)])
    s = head("SECRETARIA DA FAZENDA DO PARANÁ — RECEITA ESTADUAL (simulação)", "PAGAMENTOS DE ICMS - GR-PR",
             f"CAD/ICMS {a['ie']} — Período: 01/03/2026 a 14/10/2026")
    s.append(tbl(rows, [20, 38, 20, 18, 60, 26], bold_last=True))
    s.append(Paragraph("Não consta recolhimento do ICMS-ST de 08/2026.", ST_P))
    build(f"{p}/Pagamentos_ICMS_GR-PR_{a['ie']}.pdf", s)
    for nome, ref, cod, desc, val, dv in (
            ("GR-PR_ICMS-ST_072026.pdf", "07/2026", "1023", "ICMS-ST - substituição tributária", st_vals[(2026, 7)],
             venc[(2026, 7)]),
            ("GR-PR_Parcelamento_parcela_12-36.pdf", "12/36", "2011", "Parcelamento PR-2025-0045871",
             D("4120.35"), util_antes(dt.date(2026, 8, 15)))):
        s = head("SECRETARIA DA FAZENDA DO PARANÁ (simulação)", "GUIA DE RECOLHIMENTO DO ESTADO DO PARANÁ - GR-PR")
        s.append(kv([("Contribuinte", a["nome"]), ("CAD/ICMS", a["ie"]), ("CNPJ", fmt_cnpj(a["cnpj"])),
                     ("Código da receita", f"{cod} - {desc}"), ("Referência", ref), ("Vencimento", dbr(dv)),
                     ("Valor principal", brl(val)), ("Multa / Juros", f"{brl(0)} / {brl(0)}"),
                     ("Valor total", f"<b>{brl(val)}</b>"), ("Pago em", dbr(dv))]))
        s.append(Spacer(1, 6))
        s.append(Paragraph("Código de barras (fictício): 8160000" + f"{int(val * 100):011d}" + " 0041 2026 0000", ST_P))
        build(f"{p}/{nome}", s)
    return dict(st_aberto=efd["icms"]["st"], venc_st=venc[(2026, 8)])


def gerar(base, efd, contabil):
    c = cenario(efd, contabil)
    out = dict(cen=c)
    out["sit"] = situacao_fiscal(base, c)
    out["pgfn"] = pgfn(base, c)
    dctf(base, c)
    out["dctfweb_total"] = dctfweb(base, c)
    out["pag"] = pagamentos(base, c)
    out["pr"] = parana(base, efd)
    return out
