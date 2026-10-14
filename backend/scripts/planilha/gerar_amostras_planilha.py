"""
Gera os balancetes sintéticos de src/test/resources/amostras/ (mesma empresa fictícia das ECDs, 2024, antes do
encerramento: saldos das contas de resultado ainda abertos; a soma dos saldos finais dá zero).

  balancete.xlsx           cabeçalho na linha 3, valores numéricos e colunas D/C separadas
  balancete-formulas.xlsx  valores com sinal (credor negativo); saldo atual por fórmula, com o valor em cache
  balancete.csv            UTF-8, separador ",", números com ponto decimal e sinal, descrição entre aspas
  balancete-latin1.csv     Windows-1252, separador ";", "1.234,56 D" (indicador no próprio valor)

Uso (na pasta backend/): python -I scripts/planilha/gerar_amostras_planilha.py
"""
import zipfile
from pathlib import Path
from xml.sax.saxutils import escape

SAIDA = Path('src/test/resources/amostras')
CONTAS = [  # código, descrição, saldo anterior, D/C, débitos, créditos, saldo atual, D/C
    ('1', 'ATIVO', 1000, 'D', 8000, 3000, 6000, 'D'),
    ('1.1', 'ATIVO CIRCULANTE', 1000, 'D', 8000, 3000, 6000, 'D'),
    ('1.1.01', 'Caixa', 1000, 'D', 6000, 3000, 4000, 'D'),
    ('1.1.02', 'Bancos conta movimento', 0, 'D', 2000, 0, 2000, 'D'),
    ('2', 'PASSIVO', 500, 'C', 1000, 1500, 1000, 'C'),
    ('2.1', 'Fornecedores, nacionais', 500, 'C', 1000, 1500, 1000, 'C'),
    ('3', 'PATRIMÔNIO LÍQUIDO', 500, 'C', 0, 0, 500, 'C'),
    ('3.1', 'Capital social', 500, 'C', 0, 0, 500, 'C'),
    ('3.2', 'Lucros acumulados', 0, 'C', 0, 0, 0, 'C'),
    ('4', 'RESULTADO', 0, 'D', 3500, 8000, 4500, 'C'),
    ('4.1', 'Receita de vendas de mercadorias', 0, 'C', 0, 8000, 8000, 'C'),
    ('4.2', 'Custo das mercadorias vendidas', 0, 'D', 3500, 0, 3500, 'D'),
]
TITULO = ['Balancete de verificação - EMPRESA EXEMPLO LTDA', 'Período: 01/01/2024 a 31/12/2024']


def sinal(valor, dc):
    return -valor if dc == 'C' else valor


def col(i):
    return chr(ord('A') + i)


def celula(ref, valor, formula=None):
    if formula is not None:
        return f'<c r="{ref}"><f>{escape(formula)}</f><v>{valor}</v></c>'
    if isinstance(valor, (int, float)):
        return f'<c r="{ref}"><v>{valor}</v></c>'
    return f'<c r="{ref}" t="inlineStr"><is><t>{escape(str(valor))}</t></is></c>'


def xlsx(nome, linhas):
    """linhas: lista de listas de (valor) ou (valor, fórmula)."""
    corpo = []
    for r, linha in enumerate(linhas, start=1):
        cs = []
        for c, v in enumerate(linha):
            if v is None or v == '':
                continue
            valor, formula = v if isinstance(v, tuple) else (v, None)
            cs.append(celula(f'{col(c)}{r}', valor, formula))
        corpo.append(f'<row r="{r}">{"".join(cs)}</row>')
    partes = {
        '[Content_Types].xml': '<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
        '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
        '<Default Extension="xml" ContentType="application/xml"/>'
        '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
        '<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
        '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>',
        '_rels/.rels': '<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>',
        'xl/workbook.xml': '<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
        'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Balancete" sheetId="1" r:id="rId1"/></sheets></workbook>',
        'xl/_rels/workbook.xml.rels': '<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>'
        '<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>',
        'xl/styles.xml': '<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
        '<fonts count="1"><font/></fonts><fills count="1"><fill/></fills><borders count="1"><border/></borders>'
        '<cellStyleXfs count="1"><xf/></cellStyleXfs><cellXfs count="1"><xf/></cellXfs></styleSheet>',
        'xl/worksheets/sheet1.xml': '<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
        f'<sheetData>{"".join(corpo)}</sheetData></worksheet>',
    }
    SAIDA.mkdir(parents=True, exist_ok=True)
    # data fixa nas entradas do zip: o arquivo gerado é sempre igual (sha estável)
    with zipfile.ZipFile(SAIDA / nome, 'w', zipfile.ZIP_DEFLATED) as z:
        for caminho, xml in partes.items():
            z.writestr(zipfile.ZipInfo(caminho, date_time=(2024, 12, 31, 0, 0, 0)), xml)
    print(SAIDA / nome)


def br(valor):
    inteiro, centavos = divmod(round(abs(valor) * 100), 100)
    return f'{inteiro:,}'.replace(',', '.') + f',{centavos:02d}'


# 1. .xlsx com colunas D/C separadas
linhas = [[TITULO[0]], [TITULO[1]], ['Conta', 'Descrição', 'Saldo anterior', 'D/C', 'Débitos', 'Créditos', 'Saldo atual', 'D/C']]
for cod, desc, ant, dca, deb, cred, atual, dcf in CONTAS:
    linhas.append([cod, desc, ant, dca, deb, cred, atual, dcf])
linhas.append(['', 'TOTAIS', '', '', 12500, 12500, '', ''])
xlsx('balancete.xlsx', linhas)

# 2. .xlsx com sinal e fórmula no saldo atual (valor em cache = o que vale; a fórmula nunca é avaliada)
linhas = [['Conta', 'Descrição', 'Saldo anterior', 'Débitos', 'Créditos', 'Saldo atual']]
for r, (cod, desc, ant, dca, deb, cred, atual, dcf) in enumerate(CONTAS, start=2):
    linhas.append([cod, desc, sinal(ant, dca), deb, cred, (sinal(atual, dcf), f'C{r}+D{r}-E{r}')])
xlsx('balancete-formulas.xlsx', linhas)

# 3. CSV UTF-8 com vírgula e sinal
texto = ['Conta,Descrição,Saldo anterior,Débitos,Créditos,Saldo atual']
for cod, desc, ant, dca, deb, cred, atual, dcf in CONTAS:
    texto.append(f'{cod},"{desc}",{sinal(ant, dca):.2f},{deb:.2f},{cred:.2f},{sinal(atual, dcf):.2f}')
(SAIDA / 'balancete.csv').write_bytes(('\r\n'.join(texto) + '\r\n').encode('utf-8'))
print(SAIDA / 'balancete.csv')

# 4. CSV Latin-1 com ponto e vírgula e indicador no valor
texto = [TITULO[0], '', 'Código;Descrição da conta;Saldo anterior;Débito;Crédito;Saldo atual']
for cod, desc, ant, dca, deb, cred, atual, dcf in CONTAS:
    texto.append(f'{cod};{desc};{br(ant)} {dca};{br(deb)};{br(cred)};{br(atual)} {dcf}')
(SAIDA / 'balancete-latin1.csv').write_bytes(('\r\n'.join(texto) + '\r\n').encode('cp1252'))
print(SAIDA / 'balancete-latin1.csv')
