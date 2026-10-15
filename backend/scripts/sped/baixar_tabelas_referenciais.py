"""
Lista e baixa tabelas do Plano de Contas Referencial no portal Sped Tabelas (o mesmo serviço que o PVA usa).

Uso (na pasta backend/):
    python -I scripts/sped/baixar_tabelas_referenciais.py listar                  # pacotes do SpedContabil
    python -I scripts/sped/baixar_tabelas_referenciais.py listar 112              # tabelas de um pacote
    python -I scripts/sped/baixar_tabelas_referenciais.py baixar 112 1494 destino # baixa uma tabela
    SPED_SISTEMA=SpedEcf python -I scripts/sped/baixar_tabelas_referenciais.py listar  # pacotes da ECF

Em 2026-10-15 os dois sistemas (SpedContabil e SpedEcf) só tinham pacotes até 2021: rode de novo para ver se os de
2022 em diante foram publicados. Não use a tabela de outro ano para um exercício sem tabela oficial.

Depois de baixar, copie o arquivo para src/main/resources/plano-referencial/<NOME_OFICIAL>.txt e acrescente a linha
em tabelas.txt (com o SHA-256: sha256sum). A importação roda na subida do backend.
O portal só responde em http (o https recusa conexão).
"""
import html
import os
import http.cookiejar
import re
import sys
import urllib.parse
import urllib.request

URL = ('http://www.sped.fazenda.gov.br/spedtabelas/AppConsulta/publico/aspx/ConsultaTabelasExternas.aspx?CodSistema='
       + os.environ.get('SPED_SISTEMA', 'SpedContabil'))
abrir = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
abrir.addheaders = [('User-Agent', 'Mozilla/5.0')]


def campos(pagina):
    return {n: html.unescape(v) for n, v in re.findall(r'<input[^>]*name="([^"]+)"[^>]*value="([^"]*)"', pagina)}


def postar(pagina, alvo, extra):
    dados = campos(pagina)
    dados.pop('ctl00$ibtHome', None)
    if alvo:
        dados.pop('ctl00$ContentPlaceHolder1$btnBaixarTabela', None)
    dados.update({'__EVENTTARGET': alvo, '__EVENTARGUMENT': ''})
    dados.update(extra)
    return abrir.open(URL, urllib.parse.urlencode(dados).encode(), timeout=300)


def opcoes(pagina, select):
    bloco = re.search(r'name="ctl00\$ContentPlaceHolder1\$' + select + r'"(.*?)</select>', pagina, re.S)
    return [(v, html.unescape(t).strip()) for v, t in re.findall(r'<option[^>]*value="([^"]+)"[^>]*>([^<]*)', bloco.group(1))] if bloco else []


def main(args):
    pagina = abrir.open(URL, timeout=60).read().decode('utf-8', 'replace')
    if args[0] == 'listar' and len(args) == 1:
        for v, t in opcoes(pagina, 'ddlPacotes'):
            print(v, '|', t)
        return
    pacote = args[1]
    filtro = {'ctl00$ContentPlaceHolder1$ddlPacotes': pacote}
    pagina = postar(pagina, 'ctl00$ContentPlaceHolder1$ddlPacotes', filtro).read().decode('utf-8', 'replace')
    if args[0] == 'listar':
        for v, t in opcoes(pagina, 'ddlTabelas'):
            print(v, '|', t)
        return
    tabela, destino = args[2], args[3]
    filtro['ctl00$ContentPlaceHolder1$ddlTabelas'] = tabela
    pagina = postar(pagina, 'ctl00$ContentPlaceHolder1$ddlTabelas', filtro).read().decode('utf-8', 'replace')
    resposta = postar(pagina, '', filtro)  # botão "Baixar tabela"
    nome = re.search(r'filename="?([^";]+)', resposta.headers.get('Content-Disposition') or '')
    nome = re.sub(r'[^A-Za-z0-9_.-]', '_', nome.group(1) if nome else f'tabela-{tabela}.txt')
    corpo = resposta.read()
    with open(f'{destino}/{nome}', 'wb') as f:
        f.write(corpo)
    print(f'{destino}/{nome}', len(corpo), 'bytes')


main(sys.argv[1:])
