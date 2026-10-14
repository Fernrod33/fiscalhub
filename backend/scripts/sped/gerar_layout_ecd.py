"""
Gera src/main/resources/sped/ecd-leiautes.properties a partir dos Manuais de Orientação do Leiaute da ECD (.docx),
publicados em http://sped.rfb.gov.br/pasta/show/1569. Nada é digitado: a ordem dos campos sai das tabelas do manual.

Uso (na pasta backend/):
    python -I scripts/sped/gerar_layout_ecd.py 9.00=manual-l9.docx 8.00=manual-l8.docx 7.00=manual-l7.docx

Para cobrir um leiaute novo: baixe o manual em Word, rode de novo com mais um par versão=arquivo e confira o diff.
"""
import re
import sys
import zipfile
import xml.etree.ElementTree as ET
from datetime import date
from pathlib import Path

W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
REGISTROS = ['0000', 'I010', 'I050', 'I051', 'I150', 'I155', 'I350', 'I355', 'J005', 'J100', 'J150', '9900', '9999']
SAIDA = Path('src/main/resources/sped/ecd-leiautes.properties')


def linhas_do_docx(caminho):
    """Parágrafos e linhas de tabela ('c1 | c2 | ...') na ordem do documento."""
    with zipfile.ZipFile(caminho) as z:
        if z.getinfo('word/document.xml').file_size > 200 * 1024 * 1024:
            raise SystemExit('document.xml grande demais')
        corpo = ET.fromstring(z.read('word/document.xml')).find(W + 'body')
    texto = lambda el: ''.join(t.text or '' for t in el.iter(W + 't')).strip()
    for el in corpo:
        if el.tag == W + 'p':
            yield texto(el)
        elif el.tag == W + 'tbl':
            for tr in el.iter(W + 'tr'):
                yield ' | '.join(texto(tc) for tc in tr.findall(W + 'tc'))


def campos_por_registro(caminho):
    linhas = list(linhas_do_docx(caminho))
    # títulos das seções de registro (o sumário termina com o número da página e fica de fora)
    titulos = [(i, m.group(1).upper()) for i, l in enumerate(linhas)
               if (m := re.match(r'^registro ([0-9a-z]{4})\s*:', l, re.I)) and not re.search(r'\d+$', l.strip())]
    resultado = {}
    for reg in REGISTROS:
        campos = {}
        for k, (inicio, r) in enumerate(titulos):
            if r != reg:
                continue
            fim = next((i for i, r2 in titulos[k + 1:] if r2 != reg), len(linhas))
            for l in linhas[inicio:fim]:
                m = re.match(r'^(\d{2})\s*\|\s*([A-Z][A-Z_0-9]*)\s*\|', l)
                if m and m.group(1) not in campos and not m.group(2).startswith('REGRA_'):
                    campos[m.group(1)] = m.group(2).rstrip('_')
        numeros = sorted(campos, key=int)
        if not numeros or [int(n) for n in numeros] != list(range(1, len(numeros) + 1)):
            raise SystemExit(f'{caminho}: campos do {reg} fora de sequência: {numeros}')
        resultado[reg] = [campos[n] for n in numeros]
    return resultado


def main(pares):
    saida = [
        '# Leiaute dos registros da ECD lidos pelo FiscalHub, por versão (I010.COD_VER_LC).',
        '# Gerado por scripts/sped/gerar_layout_ecd.py em ' + date.today().isoformat() + ' a partir dos manuais oficiais',
        '# (Manual de Orientação do Leiaute da ECD, http://sped.rfb.gov.br/pasta/show/1569):',
        '#   9.00: Leiaute 9 (atualização maio/2026), arquivo/download/7989 - a partir do ano-calendário 2020',
        '#   8.00: Leiaute 8, arquivo/download/4217 - ano-calendário 2019',
        '#   7.00: Leiaute 7, arquivo/download/2855 - ano-calendário 2018',
        '# Formato: <versão>.<registro>=<campos na ordem do manual, começando por REG (campo 01)>',
    ]
    for par in pares:
        versao, caminho = par.split('=', 1)
        for reg, campos in campos_por_registro(caminho).items():
            saida.append(f'{versao}.{reg}={",".join(campos)}')
    SAIDA.parent.mkdir(parents=True, exist_ok=True)
    SAIDA.write_text('\n'.join(saida) + '\n', encoding='utf-8')
    print(SAIDA, len(saida))


main(sys.argv[1:])
