import { expect, test } from './teste';
import { assuntosDosEmails, codigoNovo, criarEscritorioEEntrar, entrar, linkDoEmail, semViolacoesGraves } from './apoio';

test('Convite: o ADMIN convida, o convidado define a senha pelo link do e-mail e entra; o link não vale duas vezes', async ({ page, browser }) => {
  await criarEscritorioEEntrar(page);
  const email = `convidado-${Date.now()}${Math.floor(Math.random() * 1000)}@teste.com`;
  await page.goto('/configuracoes/usuarios');
  await page.getByRole('button', { name: 'Novo usuário' }).click();
  const novo = page.getByRole('dialog', { name: 'Novo usuário' });
  await novo.getByLabel('Nome').fill('Pessoa Convidada');
  await novo.getByLabel('E-mail').fill(email);
  await novo.getByRole('button', { name: 'Enviar convite' }).click();
  await expect(page.getByText('Convite enviado. O link vale por 72 horas.')).toBeVisible();
  await expect(page.getByRole('row', { name: /Pessoa Convidada/ }).getByText('Convite pendente')).toBeVisible();

  // o convidado abre o link num navegador sem a sessão do ADMIN
  const caminho = await linkDoEmail(email, '/convite');
  const convidado = await (await browser.newContext()).newPage();
  await convidado.goto(caminho);
  await expect(convidado.getByRole('heading', { name: 'Defina sua senha' })).toBeVisible();
  await expect(convidado).not.toHaveURL(/#/); // o token sai da barra de endereços
  await semViolacoesGraves(convidado, 'convite');
  await convidado.getByLabel('Nova senha', { exact: true }).fill('frase-do-convidado-e2e');
  await convidado.getByLabel('Confirme a nova senha').fill('frase-do-convidado-e2e');
  await convidado.getByRole('button', { name: 'Definir senha' }).click();
  await expect(convidado).toHaveURL(/\/login$/);

  // uso único: o mesmo link não define outra senha
  await convidado.goto(caminho);
  await convidado.getByLabel('Nova senha', { exact: true }).fill('outra-frase-do-convidado');
  await convidado.getByLabel('Confirme a nova senha').fill('outra-frase-do-convidado');
  await convidado.getByRole('button', { name: 'Definir senha' }).click();
  await expect(convidado.getByText('Link inválido ou expirado. Peça um novo.')).toBeVisible();

  await convidado.goto('/login');
  await convidado.getByLabel('E-mail').fill(email);
  await convidado.getByLabel('Senha').fill('frase-do-convidado-e2e');
  await convidado.getByRole('button', { name: 'Entrar' }).click();
  await expect(convidado.getByRole('heading', { level: 1, name: 'Termos de Uso e Política de Privacidade' })).toBeVisible();
});

test('Recuperação de senha: mesma resposta para qualquer e-mail, link de uso único e o código do 2º fator continua exigido', async ({ page, browser }) => {
  const conta = await criarEscritorioEEntrar(page);
  const anonimo = await (await browser.newContext()).newPage();
  await anonimo.goto('/login');
  await anonimo.getByRole('link', { name: 'Esqueci minha senha' }).click();
  await semViolacoesGraves(anonimo, 'esqueci a senha');
  await anonimo.getByLabel('E-mail').fill(conta.email);
  await anonimo.getByRole('button', { name: 'Enviar link' }).click();
  const resposta = anonimo.getByRole('status').filter({ hasText: 'Se houver uma conta ativa com este e-mail' });
  await expect(resposta).toBeVisible();

  const caminho = await linkDoEmail(conta.email, '/redefinir-senha');
  await anonimo.goto(caminho);
  const novaSenha = 'frase-recuperada-e2e';
  await anonimo.getByLabel('Nova senha', { exact: true }).fill(novaSenha);
  await anonimo.getByLabel('Confirme a nova senha').fill(novaSenha);
  await anonimo.getByRole('button', { name: 'Salvar senha' }).click(); // sem o código: recusado, o link continua valendo
  await expect(anonimo.getByText('Informe o código atual do aplicativo autenticador', { exact: false })).toBeVisible();
  await anonimo.getByLabel('Código do aplicativo autenticador').fill(await codigoNovo(conta.chave));
  await anonimo.getByRole('button', { name: 'Salvar senha' }).click();
  await expect(anonimo).toHaveURL(/\/login$/);

  await entrar(anonimo, { ...conta, senha: novaSenha });
  await expect(anonimo.getByRole('heading', { level: 1, name: 'Olá, Pessoa' })).toBeVisible();
  await expect.poll(() => assuntosDosEmails(conta.email), { timeout: 30_000 }).toContain('Sua senha foi alterada');
});
