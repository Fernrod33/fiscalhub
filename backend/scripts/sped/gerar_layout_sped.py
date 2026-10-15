"""
Gera src/main/resources/sped/<tipo>-leiautes.properties a partir dos manuais oficiais do SPED (portal
http://sped.rfb.gov.br). Nada é digitado: a ordem dos campos sai das tabelas de campos do manual.

Uso (na pasta backend/):
    python -I scripts/sped/gerar_layout_sped.py ecd 9.00=manual-l9.docx 8.00=manual-l8.docx 7.00=manual-l7.docx
    python -I scripts/sped/gerar_layout_sped.py ecf 0012=ecf-l12.docx 0011=ecf-l11.docx ... 0008=ecf-l8.docx
    python -I scripts/sped/gerar_layout_sped.py efd-contribuicoes 006=guia-1.35.docx 005=guia-1.35.docx
    python -I scripts/sped/gerar_layout_sped.py efd-icms-ipi 020=guia-3.2.2.pdf 019=guia-3.2.2.pdf ...

Aceita o manual em Word (.docx) ou PDF (.pdf, convertido com `pdftotext -raw`, do poppler). Quando o manual traz mais
de uma tabela de campos para o mesmo registro (leiaute antigo e novo, como o M210 da EFD-Contribuições), vale a última:
os manuais publicam a vigente por último. Para cobrir uma versão nova: baixe o manual, rode de novo com mais um par
versão=arquivo e confira o diff. As fontes de cada tipo ficam em FONTES (vão para o cabeçalho do .properties).
"""
import re
import subprocess
import sys
import unicodedata
import zipfile
import xml.etree.ElementTree as ET
from datetime import date
from pathlib import Path

W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'

TIPOS = {
    'ecd': ['0000', 'I010', 'I050', 'I051', 'I150', 'I155', 'I350', 'I355', 'J005', 'J100', 'J150', '9900', '9999'],
    'ecf': ['0000', '0010', 'L030', 'L100', 'L300', 'P030', 'P100', 'P150', 'M010', 'M030', 'M300', 'M350', 'M410',
            'M500', 'N030', 'N500', 'N620', 'N630', 'N650', 'N660', 'N670', 'P200', 'P300', 'P400', 'P500', '9900',
            '9999'],
    'efd-contribuicoes': ['0000', '0110', 'M100', 'M105', 'M200', 'M210', 'M500', 'M505', 'M600', 'M610', '1100',
                          '1500', '9900', '9999'],
    'efd-icms-ipi': ['0000', 'E100', 'E110', 'E111', 'E116', 'E200', 'E210', 'E250', 'E300', 'E310', 'E316', 'E500',
                     'E520', 'E530', '9900', '9999'],
}

FONTES = {
    'ecd': [
        'Leiaute dos registros da ECD lidos pelo FiscalHub, por versão (I010.COD_VER_LC).',
        'Manual de Orientação do Leiaute da ECD, http://sped.rfb.gov.br/pasta/show/1569:',
        '  9.00: Leiaute 9 (atualização maio/2026), arquivo/download/7989 - a partir do ano-calendário 2020',
        '  8.00: Leiaute 8, arquivo/download/4217 - ano-calendário 2019',
        '  7.00: Leiaute 7, arquivo/download/2855 - ano-calendário 2018',
    ],
    'ecf': [
        'Leiaute dos registros da ECF lidos pelo FiscalHub, por versão (0000.COD_VER).',
        'Manual de Orientação do Leiaute da ECF (Word), http://sped.rfb.gov.br/item/show/1644:',
        '  0012: Leiaute 12 (20/05/2026), arquivo/download/8004 - ano-calendário 2025',
        '  0011: Leiaute 11 (29/09/2025), arquivo/download/7626 - ano-calendário 2024',
        '  0010: Leiaute 10 (21/12/2023), arquivo/download/7306 - ano-calendário 2023',
        '  0009: Leiaute 9 (12/12/2022), arquivo/download/7145 - ano-calendário 2022',
        '  0008: Leiaute 8 (janeiro/2022), arquivo/download/5971 - ano-calendário 2021',
    ],
    'efd-contribuicoes': [
        'Leiaute dos registros da EFD-Contribuições lidos pelo FiscalHub, por versão (0000.COD_VER).',
        'Guia Prático da EFD-Contribuições versão 1.35 (18/06/2021, a mais recente publicada), Word,',
        'http://sped.rfb.gov.br/item/show/1989, arquivo/download/5837. Tabela 3.1.1 do guia: 005 a partir de 01/2019,',
        '006 a partir de 01/2020 (só inclui os registros 0900 e 1011: nenhum dos lidos aqui muda). M210/M610 no leiaute',
        'aplicável aos fatos geradores a partir de 01/01/2019.',
    ],
    'efd-icms-ipi': [
        'Leiaute dos registros da EFD ICMS/IPI lidos pelo FiscalHub, por versão (0000.COD_VER).',
        'Guia Prático da EFD ICMS/IPI versão 3.2.2 (11/02/2026), PDF, http://sped.rfb.gov.br/item/show/1573,',
        'arquivo/download/8112. O guia é único para todas as versões (Tabela de versão do leiaute: 016 = 2022,',
        '017 = 2023, 018 = 2024, 019 = 2025, 020 = 2026); os registros do bloco E lidos aqui não mudam entre elas.',
    ],
}


def linhas_docx(caminho):
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


def linhas_pdf(caminho):
    """Texto do PDF em ordem de leitura; nome de campo quebrado em duas linhas ('VL_SLD_CREDOR_TRA' + 'NSPORTAR') é
    juntado e a linha da tabela vira 'NN | NOME |', o mesmo formato das tabelas do Word."""
    texto = subprocess.run(['pdftotext', '-raw', '-enc', 'UTF-8', str(caminho), '-'], capture_output=True, check=True,
                           text=True, encoding='utf-8').stdout
    linhas = texto.split('\n')
    for i, l in enumerate(linhas):
        m = re.match(r'^(\d{2}) ([A-Z][A-ZÀ-Ý_0-9]*)\*?(?: (.*))?$', l.strip())  # MES_REF*: campo marcado no guia
        if not m:
            yield l
            continue
        nome = m.group(2)
        if m.group(3) is None and i + 1 < len(linhas) and re.fullmatch(r'[A-Z_0-9]+', linhas[i + 1].strip()):
            nome += linhas[i + 1].strip()
        yield f'{m.group(1)} | {nome} | {m.group(3) or ""}'


def nome_campo(bruto):
    """Erros de digitação dos manuais: 'IND_ SD_INI_LAL' -> 'IND_SD_INI_LAL', 'VL_LCTO_PARTE B' -> 'VL_LCTO_PARTE_B',
    'VL_RETENÇAO_ST' -> 'VL_RETENCAO_ST' (no arquivo os nomes não têm acento)."""
    sem_acento = unicodedata.normalize('NFKD', bruto).encode('ascii', 'ignore').decode()
    return re.sub(r'\s+', '_', re.sub(r'_\s+', '_', sem_acento.strip())).rstrip('_')


def campos_por_registro(caminho, registros):
    linhas = list(linhas_pdf(caminho) if str(caminho).lower().endswith('.pdf') else linhas_docx(caminho))
    # títulos das seções de registro (o sumário termina com o número da página e fica de fora)
    titulos = [(i, m.group(1).upper()) for i, l in enumerate(linhas)
               if (m := re.match(r'^\s*registro ([0-9a-z]{4})\s*:', l, re.I)) and not re.search(r'\d+$', l.strip())]
    resultado = {}
    for reg in registros:
        # uma tabela começa na linha "1 | REG | ..." e termina no fim da tabela do Word (linha vazia) ou nas observações
        # do PDF; depois disso só continua com o próximo número (campos adicionais, como os de moeda funcional do I155
        # da ECD, vêm numa tabela à parte). Vale a última que cita este registro na linha do REG (a seção pode emendar
        # na do registro seguinte) e não numera dois campos diferentes com o mesmo número (erro de digitação do manual).
        tabelas, aberta = [], None
        for k, (inicio, r) in enumerate(titulos):
            if r != reg:
                continue
            fim = next((i for i, r2 in titulos[k + 1:] if r2 != reg), len(linhas))
            for l in linhas[inicio:fim]:
                if not l.strip() or re.match(r'^\s*(observa|n[íi]vel hier|campo 0?1\b|exemplo)', l, re.I):
                    aberta = None
                    continue
                m = re.match(r'^(\d{1,3})\s*\|\s*([A-Z][A-ZÀ-Ý_0-9 ]*?)\*?\s*\|\s*(\S*)', l)
                if not m or 'REGRA_' in m.group(2) + m.group(3)[:6]:  # tabela de regras de validação: numeração própria
                    continue
                numero, nome = int(m.group(1)), nome_campo(m.group(2))
                if numero == 1 and nome == 'REG' and reg in l:
                    aberta = {}
                    tabelas.append(aberta)
                elif aberta is None and tabelas and numero == len(tabelas[-1]) + 1:
                    aberta = tabelas[-1]
                elif aberta is None:
                    continue
                if aberta.get(numero, nome) != nome:
                    tabelas.remove(aberta)
                    aberta = None
                else:
                    aberta[numero] = nome
        campos = tabelas[-1] if tabelas else {}
        numeros = sorted(campos)
        if not numeros or numeros != list(range(1, len(numeros) + 1)):
            raise SystemExit(f'{caminho}: campos do {reg} fora de sequência: {numeros}')
        resultado[reg] = [campos[n] for n in numeros]
    return resultado


def main(tipo, pares):
    saida = [f'# {FONTES[tipo][0]}',
             f'# Gerado por scripts/sped/gerar_layout_sped.py em {date.today().isoformat()} a partir dos manuais oficiais.']
    saida += ['# ' + l for l in FONTES[tipo][1:]]
    saida.append('# Formato: <versão>.<registro>=<campos na ordem do manual, começando por REG (campo 01)>')
    cache = {}
    for par in pares:
        versao, caminho = par.split('=', 1)
        if caminho not in cache:
            cache[caminho] = campos_por_registro(caminho, TIPOS[tipo])
        for reg, campos in cache[caminho].items():
            saida.append(f'{versao}.{reg}={",".join(campos)}')
    destino = Path(f'src/main/resources/sped/{tipo}-leiautes.properties')
    destino.parent.mkdir(parents=True, exist_ok=True)
    destino.write_text('\n'.join(saida) + '\n', encoding='utf-8')
    print(destino, len(saida))


main(sys.argv[1], sys.argv[2:])
