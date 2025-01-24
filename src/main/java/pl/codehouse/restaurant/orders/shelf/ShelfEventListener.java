package pl.codehouse.restaurant.orders.shelf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pl.codehouse.commons.Context;
import pl.codehouse.commons.ExecutionResult;
import pl.codehouse.restaurant.orders.request.ShelfEventDto;

@Component
class ShelfEventListener {

    private static final Logger logger = LoggerFactory.getLogger(ShelfEventListener.class);

    private final PackingCommand packingCommand;

    ShelfEventListener(PackingCommand packingCommand) {
        this.packingCommand = packingCommand;
    }

    @KafkaListener(topics = "${app.kafka.shelf.topic.topic-name}", groupId = "${spring.kafka.consumer.group-id}")
    public void listen(ShelfEventDto event) {
        logger.info("Received shelf event: {}", event);
        // Add your event processing logic here
        packingCommand.execute(new Context<Integer>(event.requestId()))
                .map(ExecutionResult::handle)
                .doOnSuccess(result -> logger.info("Packing command for the following request:{} finished with the following:{}", event.requestId(), result))
                .doOnError(error -> logger.error("Error while processing packing command for request: {}. Error:{}",
                        event.requestId(),
                        error.getMessage(),
                        error)
                )
                .subscribe();
    }
}
