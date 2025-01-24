package pl.codehouse.restaurant.orders.request;

import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.codehouse.commons.Command;
import pl.codehouse.commons.Context;
import pl.codehouse.commons.ExecutionResult;
import pl.codehouse.restaurant.orders.exceptions.ResourceNotFoundException;
import pl.codehouse.restaurant.orders.exceptions.ResourceType;
import pl.codehouse.restaurant.orders.shelf.PackingStatus;
import pl.codehouse.restaurant.orders.shelf.ShelfKafkaProperties;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

/**
 * Command for creating a new request in the restaurant system.
 * This command handles the creation of a new request, including saving the request details,
 * associated menu items, and emitting relevant events.
 */
@Component
class CreateCommand implements Command<RequestPayload, RequestDto> {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(CreateCommand.class);
    private static final String MISSING_ORDER_COMPONENTS_ERROR_MESSAGE = "Not all request components were found";
    private final RequestRepository repository;
    private final MenuItemRepository menuItemRepository;
    private final RequestMenuItemRepository requestMenuItemRepository;
    private final KafkaTemplate<String, ShelfEventDto> kafkaTemplate;
    private final ShelfKafkaProperties shelfKafkaProperties;
    private final RequestStatusChangePublisher requestStatusChangePublisher;

    CreateCommand(
            RequestRepository repository,
            MenuItemRepository menuItemRepository,
            RequestMenuItemRepository requestMenuItemRepository,
            KafkaTemplate<String, ShelfEventDto> kafkaTemplate,
            ShelfKafkaProperties shelfKafkaProperties,
            RequestStatusChangePublisher requestStatusChangePublisher
    ) {
        this.repository = repository;
        this.menuItemRepository = menuItemRepository;
        this.requestMenuItemRepository = requestMenuItemRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.shelfKafkaProperties = shelfKafkaProperties;
        this.requestStatusChangePublisher = requestStatusChangePublisher;
    }

    @Override
    @Transactional
    public Mono<ExecutionResult<RequestDto>> execute(Context<RequestPayload> context) {
        List<RequestedMenuItemsPayload> menuItems = context.request().menuItems();
        LOGGER.info("Executing the creation of new Request for Customer: {} with Menu Items: {}", context.request().customerId(), menuItems);

        List<Integer> menuItemIds = menuItems
                .stream()
                .map(RequestedMenuItemsPayload::menuId)
                .toList();
        return menuItemRepository.findAllById(menuItemIds)
                .collectList()
                .flatMap(validateAndCreateNew(context))
                .map(ExecutionResult::success)
                .doOnError(exc -> ExecutionResult.failure(new RuntimeException("Error during RequestCreation command execution", exc)));
    }

    private Function<List<MenuItemEntity>, Mono<RequestDto>> validateAndCreateNew(Context<RequestPayload> context) {
        return selectedMenuItems -> {
            LOGGER.info("Validating customers new request menu items");
            List<RequestedMenuItemsPayload> requestedMenuItems = context.request().menuItems();
            if (selectedMenuItems.size() != requestedMenuItems.size()) {
                LOGGER.error("Following items {} out of {} requested items are unknown",
                        requestedMenuItems.size() - selectedMenuItems.size(),
                        requestedMenuItems.size());
                return Mono.error(new ResourceNotFoundException(MISSING_ORDER_COMPONENTS_ERROR_MESSAGE, ResourceType.MENU_ITEM));
            }

            int customerId = context.request().customerId();
            LOGGER.info("Creating new Request for Customer: {} with Menu Items: {}", customerId, requestedMenuItems);
            return repository.save(RequestEntity.newRequestFor(customerId))
                    .zipWhen(savedOrderEntity -> {
                        List<RequestMenuItemEntity> requestMenuItemEntities = selectedMenuItems.stream()
                                .map(createMenuItemEntity(savedOrderEntity, requestedMenuItems))
                                .toList();
                        LOGGER.info("Storing Request MenuItems: {}", requestMenuItemEntities);
                        return requestMenuItemRepository.saveAll(requestMenuItemEntities).collectList();
                    })
                    .doOnNext(this::emitNewRequestEvent)
                    .map(tuple -> RequestDto.from(tuple.getT1(), tuple.getT2(), selectedMenuItems));
        };
    }

    private void emitNewRequestEvent(Tuple2<RequestEntity, List<RequestMenuItemEntity>> tuple) {
        Message<ShelfEventDto> shelfMessage = new GenericMessage<>(
                ShelfEventDto.newRequestEvent(tuple.getT1().id()),
                shelfKafkaProperties.kafkaHeaders()
        );
        LOGGER.info("Emit event: {} for the following request: {}", shelfMessage.getPayload().eventType(), tuple.getT1());
        kafkaTemplate.send(shelfMessage);
        requestStatusChangePublisher.publishChange(tuple.getT1().id(), tuple.getT1().status(), PackingStatus.NOT_STARTED);
    }

    private static Function<MenuItemEntity, RequestMenuItemEntity> createMenuItemEntity(
            RequestEntity savedOrderEntity,
            List<RequestedMenuItemsPayload> menuItems) {
        return menuItem -> {
            int menuItemQuantity = menuItems.stream()
                    .filter(requestedMenuItem -> menuItem.id() == requestedMenuItem.menuId())
                    .findFirst()
                    .map(RequestedMenuItemsPayload::quantity)
                    .orElseThrow(() -> new ResourceNotFoundException("Unable to match Menu Item by Id: " + menuItem.id(), ResourceType.MENU_ITEM));
            return RequestMenuItemEntity.newInstance(savedOrderEntity.id(), menuItem.id(), menuItemQuantity, menuItem.immediate());
        };
    }
}
