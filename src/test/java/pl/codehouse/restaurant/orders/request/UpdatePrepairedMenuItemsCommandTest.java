package pl.codehouse.restaurant.orders.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static pl.codehouse.restaurant.orders.shelf.PackingStatus.READY_TO_COLLECT;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import pl.codehouse.commons.Context;
import pl.codehouse.commons.ExecutionResult;
import pl.codehouse.restaurant.orders.shelf.PackingStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class UpdatePrepairedMenuItemsCommandTest {

    private static final String TEST_TOPIC = "test-topic";
    @Mock
    private RequestRepository requestRepository;

    @Mock
    private RequestMenuItemRepository requestMenuItemRepository;

    @Mock
    private RequestStatusChangePublisher requestStatusChangePublisher;

    @Captor
    private ArgumentCaptor<Message<RequestStatusChangeMessage>> messageCaptor;


    private UpdatePrepairedMenuItemsCommand updateCommand;

    @BeforeEach
    void setUp() {
        updateCommand = new UpdatePrepairedMenuItemsCommand(
                requestRepository,
                requestMenuItemRepository,
                requestStatusChangePublisher
        );
    }

    @Test
    void Should_UpdatePreparedCountAndSetStatusToInProgress_When_NotAllItemsAreCollected() {
        // Given
        int requestId = 1;
        int menuItemId = 101;
        int preparedQuantity = 2;
        UpdatePreparedMenuItemsDto updateDto = new UpdatePreparedMenuItemsDto(requestId, menuItemId, preparedQuantity);
        Context<UpdatePreparedMenuItemsDto> context = new Context<>(updateDto);

        RequestMenuItemEntity existingItem = new RequestMenuItemEntity(1, requestId, menuItemId, 3, 0, false);
        RequestMenuItemEntity updatedItem = existingItem.withUpdatedPreparedCnt(preparedQuantity);

        given(requestMenuItemRepository.findByRequestIdAndMenuItemId(requestId, menuItemId)).willReturn(Mono.just(existingItem));
        given(requestMenuItemRepository.save(any(RequestMenuItemEntity.class))).willReturn(Mono.just(updatedItem));
        given(requestMenuItemRepository.findByRequestId(requestId)).willReturn(Flux.just(updatedItem));
        given(requestRepository.updateStatusById(anyInt(), any(RequestStatus.class))).willReturn(Mono.just(true));

        // When
        Mono<ExecutionResult<PackingStatus>> result = updateCommand.execute(context);

        // Then
        StepVerifier.create(result)
                .assertNext(executionResult -> {
                    assertThat(executionResult.isSuccess()).isTrue();
                    assertThat(executionResult.handle()).isEqualTo(PackingStatus.IN_PROGRESS);
                })
                .verifyComplete();

        // And
        then(requestRepository).should(times(1)).updateStatusById(requestId, RequestStatus.IN_PROGRESS);
        then(requestStatusChangePublisher).should(times(1)).publishChange(requestId, RequestStatus.IN_PROGRESS, PackingStatus.IN_PROGRESS);
    }

    @Test
    void Should_UpdatePreparedCountAndSetStatusToReadyToCollect_When_AllItemsAreCollected() {
        // Given
        int requestId = 1;
        int menuItemId = 101;
        int preparedQuantity = 3;
        UpdatePreparedMenuItemsDto updateDto = new UpdatePreparedMenuItemsDto(requestId, menuItemId, preparedQuantity);
        Context<UpdatePreparedMenuItemsDto> context = new Context<>(updateDto);

        RequestMenuItemEntity existingItem = new RequestMenuItemEntity(1, requestId, menuItemId, 3, 0, false);
        RequestMenuItemEntity updatedItem = existingItem.withUpdatedPreparedCnt(preparedQuantity);

        given(requestMenuItemRepository.findByRequestIdAndMenuItemId(requestId, menuItemId)).willReturn(Mono.just(existingItem));
        given(requestMenuItemRepository.save(any(RequestMenuItemEntity.class))).willReturn(Mono.just(updatedItem));
        given(requestMenuItemRepository.findByRequestId(requestId)).willReturn(Flux.just(updatedItem));
        given(requestRepository.updateStatusById(anyInt(), any(RequestStatus.class))).willReturn(Mono.just(true));

        // When
        Mono<ExecutionResult<PackingStatus>> result = updateCommand.execute(context);

        // Then
        StepVerifier.create(result)
                .assertNext(executionResult -> {
                    assertThat(executionResult.isSuccess()).isTrue();
                    assertThat(executionResult.handle()).isEqualTo(READY_TO_COLLECT);
                })
                .verifyComplete();

        // And
        then(requestRepository).should().updateStatusById(requestId, RequestStatus.READY_TO_COLLECT);
        then(requestStatusChangePublisher).should(times(1)).publishChange(requestId, RequestStatus.READY_TO_COLLECT, READY_TO_COLLECT);
    }

    @Test
    void Should_HandleErrorGracefully_When_RepositoryOperationFails() {
        // Given
        int requestId = 1;
        int menuItemId = 101;
        int preparedQuantity = 2;
        UpdatePreparedMenuItemsDto updateDto = new UpdatePreparedMenuItemsDto(requestId, menuItemId, preparedQuantity);
        Context<UpdatePreparedMenuItemsDto> context = new Context<>(updateDto);

        given(requestMenuItemRepository.findByRequestIdAndMenuItemId(requestId, menuItemId)).willReturn(Mono.error(new RuntimeException("Database error")));

        // When
        Mono<ExecutionResult<PackingStatus>> result = updateCommand.execute(context);

        // Then
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }
}
