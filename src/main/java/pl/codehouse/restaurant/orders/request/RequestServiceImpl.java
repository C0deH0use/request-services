package pl.codehouse.restaurant.orders.request;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.codehouse.commons.Context;
import pl.codehouse.restaurant.orders.shelf.PackingStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

/**
 * Implementation of the RequestService interface.
 * This service handles operations related to restaurant requests,
 * including updating, fetching, and listening for status changes.
 */
@Service
public class RequestServiceImpl implements RequestService {

    private static final Logger logger = LoggerFactory.getLogger(RequestServiceImpl.class);

    private static final List<RequestStatus> ACTIVE_REQUEST_STATUSES = List.of(RequestStatus.NEW, RequestStatus.IN_PROGRESS, RequestStatus.READY_TO_COLLECT);

    private final RequestRepository requestRepository;
    private final RequestMenuItemRepository requestMenuItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final UpdatePrepairedMenuItemsCommand updatePrepairedMenuItemsCommand;
    private final RequestStatusChangeListener requestStatusChangeListener;

    /**
     * Constructs a new RequestServiceImpl with the necessary dependencies.
     *
     * @param requestRepository The repository for managing request entities.
     * @param requestMenuItemRepository The repository for managing request menu item entities.
     * @param menuItemRepository The repository for managing menu item entities.
     * @param updatePrepairedMenuItemsCommand The command for updating prepared menu items.
     * @param requestStatusChangeListener The listener for request status changes.
     */
    public RequestServiceImpl(RequestRepository requestRepository,
                              RequestMenuItemRepository requestMenuItemRepository,
                              MenuItemRepository menuItemRepository,
                              UpdatePrepairedMenuItemsCommand updatePrepairedMenuItemsCommand,
                              RequestStatusChangeListener requestStatusChangeListener) {
        this.requestRepository = requestRepository;
        this.requestMenuItemRepository = requestMenuItemRepository;
        this.menuItemRepository = menuItemRepository;
        this.updatePrepairedMenuItemsCommand = updatePrepairedMenuItemsCommand;
        this.requestStatusChangeListener = requestStatusChangeListener;
    }

    /**
     * Updates the collected items for a request and returns the updated request.
     *
     * @param updateDto The DTO containing the update information.
     * @return A Mono emitting the updated RequestDto.
     */
    @Override
    public Mono<RequestDto> updateCollectedItems(UpdatePreparedMenuItemsDto updateDto) {
        return updatePrepairedMenuItemsCommand.execute(new Context<>(updateDto))
                .then(findById(updateDto.requestId()));
    }

    /**
     * Finds a request by its ID.
     *
     * @param requestId The ID of the request to find.
     * @return A Mono emitting the RequestDto for the specified ID.
     */
    @Override
    public Mono<RequestDto> findById(int requestId) {
        Mono<RequestEntity> entityMono = requestRepository.findById(requestId);
        Mono<Tuple2<List<RequestMenuItemEntity>, List<MenuItemEntity>>> tuple2Mono = requestMenuItemRepository.findByRequestId(requestId)
                .collectList()
                .flatMap(getListMonoFunction());
        return Mono.zip(entityMono, tuple2Mono)
                .map(tuple2 -> RequestDto.from(tuple2.getT1(), tuple2.getT2().getT1(), tuple2.getT2().getT2()));
    }

    /**
     * Fetches all active requests.
     *
     * @return A Flux emitting RequestDto objects for all active requests.
     */
    @Override
    public Flux<RequestDto> fetchActive() {
        return requestRepository.findByStatus(ACTIVE_REQUEST_STATUSES)
                .doOnComplete(() -> logger.info(">>.findByStatus({}) completed", ACTIVE_REQUEST_STATUSES))
                .doOnNext(r -> logger.info("Fetching components of RequestDTO for {}, status: {}", r.id(), r.status()))
                .flatMap(request -> requestMenuItemRepository.findByRequestId(request.id())
                        .collectList()
                        .flatMap(getListMonoFunction())
                        .map(tuple -> RequestDto.from(request, tuple.getT1(), tuple.getT2())));
    }

    /**
     * Listens for request status updates and emits RequestStatusDto objects.
     *
     * @return A Flux emitting RequestStatusDto objects for each status update.
     */
    public Flux<RequestStatusDto> listenOnRequestUpdates() {
        return requestStatusChangeListener.getRequestStatusChanges()
                .flatMap(notification -> {
                    int requestId = notification.requestId();
                    PackingStatus packingStatus = notification.packingStatus();
                    return findById(requestId)
                            .map(requestDto -> new RequestStatusDto(requestDto.requestId(), packingStatus, requestDto.preparedItemsCount(),
                                    requestDto.totalItemsCount()));
                });
    }

    /**
     * Creates a function to fetch menu items for a list of request menu item entities.
     *
     * @return A Function that takes a List of RequestMenuItemEntity and returns a Mono of Tuple2 containing
     *         the original list and a list of corresponding MenuItemEntity objects.
     */
    private Function<List<RequestMenuItemEntity>, Mono<? extends Tuple2<List<RequestMenuItemEntity>, List<MenuItemEntity>>>> getListMonoFunction() {
        return entities -> {
            Set<Integer> menuItemIds = entities.stream()
                    .map(RequestMenuItemEntity::menuItemId)
                    .collect(Collectors.toSet());
            return Mono.zip(Mono.just(entities), menuItemRepository.findAllById(menuItemIds).collectList());
        };
    }
}
