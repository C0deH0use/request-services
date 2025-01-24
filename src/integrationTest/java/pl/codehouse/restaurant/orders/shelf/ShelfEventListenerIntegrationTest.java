package pl.codehouse.restaurant.orders.shelf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.assertj.core.api.Assertions;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import pl.codehouse.commons.Context;
import pl.codehouse.commons.ExecutionResult;
import pl.codehouse.restaurant.orders.TestcontainersConfiguration;
import pl.codehouse.restaurant.orders.request.PackingActionResult;
import pl.codehouse.restaurant.orders.request.RequestStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static pl.codehouse.restaurant.orders.request.MenuItemEntityBuilder.MENU_ITEM_1_ID;
import static pl.codehouse.restaurant.orders.request.MenuItemEntityBuilder.MENU_ITEM_1_NAME;
import static pl.codehouse.restaurant.orders.request.MenuItemEntityBuilder.MENU_ITEM_2_ID;
import static pl.codehouse.restaurant.orders.request.MenuItemEntityBuilder.MENU_ITEM_2_NAME;
import static pl.codehouse.restaurant.orders.shelf.ShelfBuilder.aShelf;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.kafka.shelf.topic.topic-name=test_shelf_topic"
        }
)
@ExtendWith(SpringExtension.class)
@Import(TestcontainersConfiguration.class)
class ShelfEventListenerIntegrationTest {
    private static final String SHELF_TOPIC_NAME = "test_shelf_topic";
    private static final LocalDateTime UPDATED_AT = LocalDateTime.parse("2024-10-03T10:15:30");

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PackingCommand packingCommand;

    @Captor
    private ArgumentCaptor<Context<Integer>> contextCaptor;

    private Producer<Integer, String> producerServiceTest;

    @BeforeEach
    void setUp(
            @Autowired Flyway flyway,
            @Autowired R2dbcEntityTemplate entityTemplate,
            @Autowired KafkaContainer kafkaContainer
    ) {
        flyway.clean();
        flyway.migrate();
        Flux.just(
                        aShelf()
                                .newShelfEntity()
                                .withMenuId(MENU_ITEM_1_ID)
                                .withName(MENU_ITEM_1_NAME)
                                .withUpdatedAt(UPDATED_AT)
                                .build(),
                        aShelf()
                                .aShelfWithAvailableMenuItems()
                                .withMenuId(MENU_ITEM_2_ID)
                                .withName(MENU_ITEM_2_NAME)
                                .withUpdatedAt(UPDATED_AT)
                                .build()
                )
                .flatMap(o -> {
                    System.out.println("Inserting object: " + o.toString());
                    return entityTemplate.insert(o);
                })
                .collectList()
                .block();

        Map<String, Object> testProducerProps = KafkaTestUtils.producerProps(kafkaContainer.getBootstrapServers());
        producerServiceTest = new DefaultKafkaProducerFactory<Integer, String>(testProducerProps).createProducer();
    }

    @Test
    @DisplayName("Should execute PackingCommand when ShelfEventDto is received")
    void shouldExecutePackingCommandWhenShelfEventDtoIsReceived() {
        // given
        int requestId = 123;
        Map<String, Object> shelfEventDto = Map.of(
                "eventType", "NEW_REQUEST",
                "requestId", requestId,
                "menuItemId", -1,
                "quantity", 2
        );
        String recordPayload = createPayload(shelfEventDto);
        RecordHeaders recordHeaders = new RecordHeaders();
        recordHeaders.add("__TypeId__", "pl.codehouse.restaurant.orders.request.ShelfEventDto".getBytes(StandardCharsets.UTF_8));
        var producerRecord = new ProducerRecord<Integer, String>(SHELF_TOPIC_NAME, null, null, requestId, recordPayload, recordHeaders);

        var expectedResult = new PackingActionResult(requestId, 1, 1, RequestStatus.IN_PROGRESS);
        given(packingCommand.execute(any())).willReturn(Mono.just(ExecutionResult.success(expectedResult)));

        // when
        producerServiceTest.send(producerRecord);

        // then
        Awaitility.given()
                .atMost(Duration.ofSeconds(2))
                .then()
                .pollDelay(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    then(packingCommand).should(times(1)).execute(contextCaptor.capture());
                    Context<Integer> capturedContext = contextCaptor.getValue();
                    assertThat(capturedContext.request()).isEqualTo(requestId);
                });
    }

    private String createPayload(Map<String, Object> shelfEventDto) {
        try {
            return objectMapper.writeValueAsString(shelfEventDto);
        } catch (JsonProcessingException e) {
            return Assertions.fail("Failed to create payload", e);
        }
    }
}