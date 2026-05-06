package br.gov.pb.der.netnotify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.gov.pb.der.netnotify.model.Message;
import br.gov.pb.der.netnotify.repository.MessageRepository;

class MessageServiceTest {

    private final MessageRepository messageRepository = org.mockito.Mockito.mock(MessageRepository.class);
    private final DepartmentService departmentService = org.mockito.Mockito.mock(DepartmentService.class);
    private final RabbitmqService rabbitmqService = org.mockito.Mockito.mock(RabbitmqService.class);
    private final OfficeHoursSettingsService officeHoursSettingsService = org.mockito.Mockito.mock(OfficeHoursSettingsService.class);
    private final ApplicationTimeService applicationTimeService = org.mockito.Mockito.mock(ApplicationTimeService.class);

    private final MessageService service = new MessageService(
            messageRepository,
            departmentService,
            rabbitmqService,
            officeHoursSettingsService,
            applicationTimeService);

    @Test
    void updatePausedPersistsNewPausedState() {
        UUID id = UUID.randomUUID();
        Message message = new Message();
        message.setId(id);
        message.setPaused(false);

        when(messageRepository.findById(id)).thenReturn(Optional.of(message));

        Message updated = service.updatePaused(id, true);

        assertThat(updated).isSameAs(message);
        assertThat(updated.getPaused()).isTrue();
        verify(messageRepository).save(message);
    }

    @Test
    void updatePausedReturnsNullWhenMessageDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(messageRepository.findById(id)).thenReturn(Optional.empty());

        Message updated = service.updatePaused(id, true);

        assertThat(updated).isNull();
        verify(messageRepository, never()).save(org.mockito.ArgumentMatchers.any(Message.class));
    }

    @Test
    void sendNotificationUpdatesLastSentAtWhenPublishSucceeds() {
        UUID id = UUID.randomUUID();
        Message message = new Message();
        message.setId(id);
        message.setDepartments(List.of());
        message.setAgentScope(br.gov.pb.der.netnotify.model.AgentScope.BOTH);
        LocalDateTime now = LocalDateTime.now();

        when(rabbitmqService.basicPublish(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        when(applicationTimeService.nowDateTime()).thenReturn(now);

        service.sendNotification(message);

        verify(messageRepository).updateLastSentAtById(id, now);
    }

    @Test
    void sendNotificationDoesNotUpdateLastSentAtWhenPublishFails() {
        UUID id = UUID.randomUUID();
        Message message = new Message();
        message.setId(id);
        message.setDepartments(List.of());
        message.setAgentScope(br.gov.pb.der.netnotify.model.AgentScope.BOTH);

        when(rabbitmqService.basicPublish(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);

        service.sendNotification(message);

        verify(messageRepository, never()).updateLastSentAtById(
                org.mockito.ArgumentMatchers.any(UUID.class),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class));
    }
}
