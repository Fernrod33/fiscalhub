package com.fiscalhub.backend.lgpd;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Todas as tabelas com dados de um cliente, achadas pelas chaves estrangeiras a partir de {@code cliente} (o próprio
 * banco é a fonte: tabela nova com FK para o cliente, um documento ou uma escrituração entra sozinha na exportação e
 * na exclusão). Cada tabela vem com o filtro SQL que acha as linhas do cliente, com um parâmetro só: o id do cliente.
 */
@Component
public class GrafoCliente {

    /** filtro: condição do WHERE (ex.: {@code "cliente_id" = ?} ou {@code "escrituracao_id" IN (SELECT ...)}). */
    public record Tabela(String nome, String filtro) {
    }

    private record Fk(String tabela, String coluna, String referencia) {
    }

    private final JdbcTemplate jdbc;
    private volatile List<Tabela> descoberta;
    private volatile List<Tabela> ordemExclusao;

    public GrafoCliente(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Pais antes dos filhos, começando pelo próprio cliente (ordem da exportação). */
    public List<Tabela> tabelas() {
        if (descoberta == null) {
            montar();
        }
        return descoberta;
    }

    /** Filhos antes dos pais, terminando no cliente (ordem da exclusão, sem violar FK). */
    public List<Tabela> ordemExclusao() {
        if (ordemExclusao == null) {
            montar();
        }
        return ordemExclusao;
    }

    private synchronized void montar() {
        // pg_catalog: o information_schema só mostra as FKs de tabelas do próprio papel, e a aplicação não é a dona
        List<Fk> fks = jdbc.query("""
                SELECT filha.relname, coluna.attname, pai.relname
                FROM pg_constraint c
                JOIN pg_class filha ON filha.oid = c.conrelid
                JOIN pg_class pai ON pai.oid = c.confrelid
                JOIN pg_attribute coluna ON coluna.attrelid = c.conrelid AND coluna.attnum = c.conkey[1]
                JOIN pg_attribute referida ON referida.attrelid = c.confrelid AND referida.attnum = c.confkey[1]
                WHERE c.contype = 'f' AND c.connamespace = 'public'::regnamespace AND referida.attname = 'id'
                ORDER BY 1, 2""", (rs, i) -> new Fk(rs.getString(1), rs.getString(2), rs.getString(3)));

        Map<String, String> filtros = new LinkedHashMap<>();
        filtros.put("cliente", "\"id\" = ?");
        Deque<String> fila = new ArrayDeque<>(List.of("cliente"));
        while (!fila.isEmpty()) {
            String pai = fila.poll();
            for (Fk fk : fks) {
                // a primeira referência achada define o filtro (cliente_id direto vence o caminho pela escrituração)
                if (fk.referencia().equals(pai) && !filtros.containsKey(fk.tabela())) {
                    filtros.put(fk.tabela(), id(fk.coluna()) + " IN (SELECT \"id\" FROM " + id(pai) + " WHERE " + filtros.get(pai) + ")");
                    fila.add(fk.tabela());
                }
            }
        }
        // o caminho direto é mais simples para quem tem a coluna cliente_id
        fks.stream().filter(fk -> fk.referencia().equals("cliente") && fk.coluna().equals("cliente_id"))
                .forEach(fk -> filtros.put(fk.tabela(), "\"cliente_id\" = ?"));

        List<Tabela> tabelas = filtros.entrySet().stream().map(e -> new Tabela(e.getKey(), e.getValue())).toList();

        // exclusão: Kahn sobre "quem referencia quem" dentro do conjunto (auto-referência não conta)
        Set<String> restantes = new HashSet<>(filtros.keySet());
        List<Tabela> ordem = new ArrayList<>();
        while (!restantes.isEmpty()) {
            List<Tabela> livres = tabelas.stream().filter(t -> restantes.contains(t.nome()) && fks.stream().noneMatch(fk ->
                    fk.referencia().equals(t.nome()) && !fk.tabela().equals(t.nome()) && restantes.contains(fk.tabela()))).toList();
            if (livres.isEmpty()) {
                throw new IllegalStateException("Ciclo de chaves estrangeiras entre " + restantes);
            }
            livres.forEach(t -> restantes.remove(t.nome()));
            ordem.addAll(livres);
        }
        this.descoberta = tabelas;
        this.ordemExclusao = List.copyOf(ordem);
    }

    private static String id(String nome) {
        return "\"" + nome.replace("\"", "\"\"") + "\"";
    }
}
