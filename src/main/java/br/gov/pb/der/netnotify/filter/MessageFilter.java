package br.gov.pb.der.netnotify.filter;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MessageFilter extends AbstractFilter {

    private String content;

    private Integer levelId;

    private Integer messageTypeId;

}
