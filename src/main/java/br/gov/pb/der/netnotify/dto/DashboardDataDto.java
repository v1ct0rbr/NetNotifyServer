package br.gov.pb.der.netnotify.dto;

import java.util.Map;

import lombok.Data;

@Data
public class DashboardDataDto implements java.io.Serializable {

    private static final long serialVersionUID = 1L;
    private Long totalMessages;

    private Map<String, Long> totalMessagesByLevel;

    private Map<String, Long> totalMessagesByType;

}
