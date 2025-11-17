package br.gov.pb.der.netnotify.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class DashboardDataDto implements java.io.Serializable {

    private static final long serialVersionUID = 1L;
    private Long totalMessages;

    private List<CountByItemDto> totalMessagesByLevel = new ArrayList<>();

    private List<CountByItemDto> totalMessagesByType = new ArrayList<>();

}
