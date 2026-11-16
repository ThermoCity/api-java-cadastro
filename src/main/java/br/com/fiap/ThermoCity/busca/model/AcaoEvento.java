package br.com.fiap.ThermoCity.busca.model;

/** O que o processamento de um evento de nível de calor fez. */
public enum AcaoEvento {
    /** Nível 3 ou mais: listas do dia criadas ou ampliadas. */
    LISTAS_GERADAS,
    /** Nível abaixo de 3: listas abertas do dia encerradas. */
    LISTAS_ENCERRADAS,
    /** Nível abaixo de 3 e nenhuma lista aberta no dia. */
    SEM_ACAO,
    /** Evento com id já processado: ignorado (não é gravado de novo). */
    DUPLICADO
}
