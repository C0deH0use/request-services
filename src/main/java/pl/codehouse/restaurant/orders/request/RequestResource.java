package pl.codehouse.restaurant.orders.request;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.codehouse.commons.Context;
import pl.codehouse.commons.ExecutionResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

@RestController
@RequestMapping(value = "/request", consumes = {MediaType.APPLICATION_JSON_VALUE}, produces = {MediaType.APPLICATION_JSON_VALUE})
class RequestResource {
    private static final Logger logger = LoggerFactory.getLogger(RequestResource.class);
    private final MenuItemRepository menuItemRepository;
    private final RequestService requestService;
    private final CreateCommand createCommand;

    RequestResource(MenuItemRepository menuItemRepository, RequestService requestService, CreateCommand createCommand) {
        this.menuItemRepository = menuItemRepository;
        this.requestService = requestService;
        this.createCommand = createCommand;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Mono<RequestDto> createRequest(@RequestBody RequestPayload request) {
        return createCommand.execute(new Context<>(request))
                .map(ExecutionResult::handle);
    }

    @GetMapping("/{requestId}")
    @ResponseStatus(HttpStatus.OK)
    Mono<RequestDto> fetchRequest(@PathVariable int requestId) {
        return requestService.findById(requestId);
    }

    @GetMapping("/menu-items")
    Mono<List<MenuItem>> fetchAvailableMenuItems() {
        return menuItemRepository.findAll()
                .map(MenuItem::from)
                .collectList();
    }

    @GetMapping(value = "/notification/status", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<RequestStatusDto>> getStatusUpdates() {
        return requestService.listenOnRequestUpdates()
                .doOnSubscribe(subscription -> logger.info("Client subscribed to notifications"))
                .doOnCancel(() -> logger.info("Client unsubscribed from notifications"))
                .doFinally(signalType -> {
                    if (signalType == SignalType.CANCEL) {
                        logger.info("Closing SSE connection");
                    }
                })
                .map(statusDto -> ServerSentEvent.<RequestStatusDto>builder()
                        .id(String.valueOf(statusDto.requestId()))
                        .event("request-status-update")
                        .data(statusDto)
                        .build());
    }
}
