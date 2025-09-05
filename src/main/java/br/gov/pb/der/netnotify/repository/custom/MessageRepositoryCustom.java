package br.gov.pb.der.netnotify.repository.custom;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import br.gov.pb.der.netnotify.filter.MessageFilter;
import br.gov.pb.der.netnotify.response.MessageResponseDto;

public interface MessageRepositoryCustom {

    Page<MessageResponseDto> findAllMessages(MessageFilter filter, Pageable pageable);
}
