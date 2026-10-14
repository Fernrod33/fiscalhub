package com.fiscalhub.backend.importacao;

import com.fiscalhub.backend.entity.enums.EtapaProcessamento;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava etapa e progresso do job em transação própria, para o /status mostrar o andamento enquanto a transação do
 * processamento (que só confirma no fim) ainda está aberta. Só esta classe escreve nessas colunas durante o
 * processamento, e o processador não toca na linha do job: sem disputa de lock entre as duas transações.
 */
@Component
public class RegistroProgresso {

    private final EntityManager em;

    public RegistroProgresso(EntityManager em) {
        this.em = em;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void atualizar(Long jobId, EtapaProcessamento etapa, int progresso) {
        em.createQuery("update JobProcessamento j set j.etapa = :etapa, j.progresso = :progresso where j.id = :id")
                .setParameter("etapa", etapa)
                .setParameter("progresso", progresso)
                .setParameter("id", jobId)
                .executeUpdate();
    }
}
