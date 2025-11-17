package br.gov.pb.der.netnotify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CountSummaryDto implements java.io.Serializable {

    private static final long serialVersionUID = 1L;
    
    private String name;
    private Long count;
}
