package br.gov.pb.der.netnotify.model;

public enum AgentScope {
    /** Mensagem visível apenas para agentes na rede interna da instituição */
    INTERNAL,
    /** Mensagem visível apenas para agentes externos (via web) */
    EXTERNAL,
    /** Mensagem visível para todos os agentes (padrão) */
    BOTH
}
