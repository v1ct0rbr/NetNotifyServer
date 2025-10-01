package br.gov.pb.der.netnotify.filter;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MessageFilter extends AbstractFilter {

    private static final int SORT_BY_CREATEDAT = 1;

    private static final int SORT_ORDER_ASC = 1;
    private static final int SORT_ORDER_DESC = 2;

    {
        sortBy = SORT_BY_CREATEDAT;
        sortOrder = SORT_ORDER_DESC;
    }

    private String title;

    private String content;

    private Integer levelId;

    private Integer messageTypeId;

}
