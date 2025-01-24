package pl.codehouse.restaurant.orders.request;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.codehouse.commons.Command;
import pl.codehouse.commons.Context;
import pl.codehouse.commons.ExecutionResult;
import pl.codehouse.restaurant.orders.shelf.PackingStatus;
import reactor.core.publisher.Mono;

/**
 * Command for updating the prepared count of menu items in a request.
 * This component handles the business logic for updating the preparation status
 * of menu items and notifying about status changes.
 */
@Component
class UpdatePrepairedMenuItemsCommand implements Command<UpdatePreparedMenuItemsDto, PackingStatus> {
    private static final Logger logger = LoggerFactory.getLogger(UpdatePrepairedMenuItemsCommand.class);
    private final RequestRepository requestRepository;
    private final RequestMenuItemRepository requestMenuItemRepository;
    private final RequestStatusChangePublisher statusChangePublisher;

    UpdatePrepairedMenuItemsCommand(RequestRepository requestRepository,
                                    RequestMenuItemRepository requestMenuItemRepository,
                                    RequestStatusChangePublisher statusChangePublisher) {
        this.requestRepository = requestRepository;
        this.requestMenuItemRepository = requestMenuItemRepository;
        this.statusChangePublisher = statusChangePublisher;
    }

    @Override
    @Transactional
    public Mono<ExecutionResult<PackingStatus>> execute(Context<UpdatePreparedMenuItemsDto> context) {
        UpdatePreparedMenuItemsDto updateDto = context.request();
        int requestId = updateDto.requestId();
        logger.info("Updating request menu item prepared count: {}", updateDto);
        return requestMenuItemRepository.findByRequestIdAndMenuItemId(requestId, updateDto.menuItemId())
                .flatMap(requestMenuItem -> requestMenuItemRepository.save(requestMenuItem.withUpdatedPreparedCnt(updateDto.preparedQuantity())))
                .flatMap(requestMenuItem -> requestMenuItemRepository.findByRequestId(requestId).collectList())
                .flatMap(requestMenuItemEntities -> {
                    logger.info("Checking Request Menu Item status -> {}", requestMenuItemEntities);
                    boolean allItemsCollected = requestMenuItemEntities.stream().allMatch(RequestMenuItemEntity::isFinished);
                    RequestStatus newStatus = allItemsCollected ? RequestStatus.READY_TO_COLLECT : RequestStatus.IN_PROGRESS;
                    PackingStatus packingStatus = allItemsCollected ? PackingStatus.READY_TO_COLLECT : PackingStatus.IN_PROGRESS;

                    return requestRepository.updateStatusById(requestId, newStatus)
                            .then(notifyStatusChange(requestId, newStatus, packingStatus))
                            .thenReturn(packingStatus);
                })
                .map(ExecutionResult::success);
    }

    private Mono<Void> notifyStatusChange(int requestId, RequestStatus newStatus, PackingStatus packingStatus) {
        statusChangePublisher.publishChange(requestId, newStatus, packingStatus);
        return Mono.empty();
    }
}
