package pl.codehouse.restaurant.request;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import pl.codehouse.restaurant.Context;
import pl.codehouse.restaurant.ExecutionResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping(value = "/request", consumes = {MediaType.APPLICATION_JSON_VALUE}, produces = {MediaType.APPLICATION_JSON_VALUE})
class RequestResource {

    private final MenuItemRepository menuItemRepository;
    private final RequestService requestService;
    private final CreateCommand createCommand;

    RequestResource(MenuItemRepository menuItemRepository, RequestService requestService, CreateCommand createCommand, ObjectMapper objectMapper) {
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
    @ResponseStatus(HttpStatus.CREATED)
    Mono<RequestDto> fetchRequest(@PathVariable int requestId) {
        return requestService.findById(requestId);
    }

    @GetMapping
    @RequestMapping(consumes = {MediaType.TEXT_EVENT_STREAM_VALUE}, produces = {MediaType.TEXT_EVENT_STREAM_VALUE})
    Flux<ServerSentEvent<RequestDto>> fetchActiveRequests() {
        return requestService.fetchActive()
                .map(dto -> ServerSentEvent.<RequestDto>builder()
                        .id(String.valueOf(dto.requestId()))
                        .event("request-status-events")
                        .data(dto)
                        .build());
    }

    @GetMapping("/menu-items")
    Mono<List<MenuItem>> fetchAvailableMenuItems() {
        return menuItemRepository.findAll()
                .map(MenuItem::from)
                .collectList();
    }
}
