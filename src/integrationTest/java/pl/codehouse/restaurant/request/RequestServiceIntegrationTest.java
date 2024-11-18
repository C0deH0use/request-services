package pl.codehouse.restaurant.request;

import org.flywaydb.core.Flyway;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import pl.codehouse.restaurant.TestcontainersConfiguration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.codehouse.restaurant.request.MenuItemEntityBuilder.MENU_ITEM_2_ID;
import static pl.codehouse.restaurant.request.MenuItemEntityBuilder.MENU_ITEM_3_ID;
import static pl.codehouse.restaurant.request.MenuItemEntityBuilder.MENU_ITEM_4_ID;
import static pl.codehouse.restaurant.request.MenuItemEntityBuilder.aMenuItemEntityFour;
import static pl.codehouse.restaurant.request.MenuItemEntityBuilder.aMenuItemEntityOne;
import static pl.codehouse.restaurant.request.MenuItemEntityBuilder.aMenuItemEntityThree;
import static pl.codehouse.restaurant.request.MenuItemEntityBuilder.aMenuItemEntityTwo;
import static pl.codehouse.restaurant.request.RequestEntityBuilder.CUSTOMER_ID;
import static pl.codehouse.restaurant.request.RequestEntityBuilder.REQUEST_ID;
import static pl.codehouse.restaurant.request.RequestEntityBuilder.aRequestEntity;
import static pl.codehouse.restaurant.request.RequestMenuItemBuilder.aRequestMenuItemOne;
import static pl.codehouse.restaurant.request.RequestMenuItemBuilder.aRequestMenuItemTwo;
import static pl.codehouse.restaurant.request.RequestMenuItemEntityBuilder.REQUEST_MENU_ITEM_1_ID;
import static pl.codehouse.restaurant.request.RequestMenuItemEntityBuilder.aRequestMenuItemEntityOne;
import static pl.codehouse.restaurant.request.RequestMenuItemEntityBuilder.aRequestMenuItemEntityTwo;
import static pl.codehouse.restaurant.request.RequestMenuItemEntityBuilder.aRequestMenuItems;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureWebTestClient
@ExtendWith(SpringExtension.class)
@Import(TestcontainersConfiguration.class)
class RequestServiceIntegrationTest {
    private static final int REQUEST_2_ID = REQUEST_ID + 10;
    private static final int REQUEST_3_ID = REQUEST_ID + 15;
    private static final int REQUEST_4_ID = REQUEST_ID + 25;
    @Autowired
    private Flyway flyway;

    @Autowired
    private R2dbcEntityTemplate entityTemplate;

    @Autowired
    private RequestService sut;

    @BeforeEach
    void setUp() {
        flyway.clean();
        flyway.migrate();
        getInitRequests()
                .doOnNext(o -> System.out.println("Inserting object: " + o.toString()))
                .flatMap(entityTemplate::insert)
                .collectList()
                .block();
        getInitRequestMenuItems()
                .doOnNext(o -> System.out.println("Inserting object: " + o.toString()))
                .flatMap(entityTemplate::insert)
                .collectList()
                .block();
    }

    @Test
    @DisplayName("should fetch stored request")
    void shouldFetchStoredRequest() {
        // when
        Mono<RequestDto> response = sut.findById(REQUEST_ID);

        // then
        StepVerifier.create(response)
                .assertNext(result -> {
                    assertThat(result)
                            .hasFieldOrPropertyWithValue("customerId", CUSTOMER_ID)
                            .hasFieldOrPropertyWithValue("preparedItemsCount", 0)
                            .hasFieldOrPropertyWithValue("totalItemsCount", 3);
                    assertThat(result.menuItems())
                            .hasSize(2)
                            .contains(
                                    aRequestMenuItemOne().build(),
                                    aRequestMenuItemTwo().withQuantity(2).build());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("should fetch requests which have active statuses")
    void shouldFetchRequestsWithActiveStatuses() {
        // when
        LinkedList<Integer> expectedRequestIds = new LinkedList<Integer>(List.of(REQUEST_ID, REQUEST_2_ID, REQUEST_3_ID));
        Flux<RequestDto> response = sut.fetchActive();

        // then
        AtomicInteger totalItemsCount = new AtomicInteger(0);
        StepVerifier.create(response)
                .assertNext(requestDto -> assertExpectedRequestIds(requestDto, expectedRequestIds, totalItemsCount))
                .assertNext(requestDto -> assertExpectedRequestIds(requestDto, expectedRequestIds, totalItemsCount))
                .assertNext(requestDto -> assertExpectedRequestIds(requestDto, expectedRequestIds, totalItemsCount))
                .expectComplete()
                .verify(Duration.ofSeconds(30));

        assertThat(totalItemsCount.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("should update request with packed values for a given Request Menu Item")
    void shouldUpdateRequestWithPackedValuesForAGivenMenuItem() {
        // given
        UpdatePreparedMenuItemsDto updateDto = new UpdatePreparedMenuItemsDto(REQUEST_ID, MENU_ITEM_2_ID, 2);

        // when
        Mono<RequestDto> response = sut.updateCollectedItems(updateDto);

        // then
        StepVerifier.create(response)
                .assertNext(result -> {
                    assertThat(result)
                            .hasFieldOrPropertyWithValue("preparedItemsCount", 2)
                            .hasFieldOrPropertyWithValue("totalItemsCount", 3);
                    assertThat(result.menuItems())
                            .hasSize(2)
                            .contains(
                                    aRequestMenuItemOne().build(),
                                    aRequestMenuItemTwo()
                                            .withQuantity(2)
                                            .withPrepared(2)
                                            .build());
                })
                .verifyComplete();
    }

    private static void assertExpectedRequestIds(RequestDto requestDto, LinkedList<Integer> expectedRecords, AtomicInteger totalItemsCount) {
        assertThat(requestDto.requestId()).isIn(expectedRecords);
        expectedRecords.removeIf(r -> r.equals(requestDto.requestId()));
        totalItemsCount.getAndIncrement();
    }

    private static @NotNull Flux<Record> getInitRequests() {
        return Flux.just(
                // request entity One
                aRequestEntity().build(),
                aRequestEntity(REQUEST_2_ID)
                        .withStatus(RequestStatus.READY_TO_COLLECT)
                        .build(),
                aRequestEntity(REQUEST_3_ID)
                        .build(),
                aRequestEntity(REQUEST_4_ID)
                        .withStatus(RequestStatus.COLLECTED)
                        .build()
        );
    }

    private static @NotNull Flux<Record> getInitRequestMenuItems() {
        return Flux.just(
                // Menu item entities
                aMenuItemEntityOne().build(),
                aMenuItemEntityTwo().build(),
                aMenuItemEntityThree().build(),
                aMenuItemEntityFour().build(),

                // Request 1: Menu Items - One & Two -> NEW
                aRequestMenuItemEntityOne().build(),
                aRequestMenuItemEntityTwo().withQuantity(2).build(),


                // Request 2: Menu Items - 3 & 4 -> READY TO COLLECT
                aRequestMenuItems()
                        .withId(REQUEST_MENU_ITEM_1_ID + 10)
                        .withRequestId(REQUEST_2_ID)
                        .withMenuId(MENU_ITEM_3_ID)
                        .withPrepared(2)
                        .withQuantity(2)
                        .build(),

                aRequestMenuItems()
                        .withId(REQUEST_MENU_ITEM_1_ID + 11)
                        .withRequestId(REQUEST_2_ID)
                        .withMenuId(MENU_ITEM_4_ID)
                        .withPrepared(2)
                        .withQuantity(2)
                        .build(),

                // Request 3: Menu Items - 1 & 2 -> IN_PROGRESS
                aRequestMenuItemEntityOne()
                        .withId(REQUEST_MENU_ITEM_1_ID + 20)
                        .withRequestId(REQUEST_3_ID)
                        .build(),
                aRequestMenuItemEntityTwo()
                        .withId(REQUEST_MENU_ITEM_1_ID + 21)
                        .withRequestId(REQUEST_3_ID)
                        .withQuantity(2)
                        .withPrepared(2)
                        .build(),

                // Request 4: Menu Items - 1 -> READY TO COLLECT
                aRequestMenuItemEntityOne()
                        .withId(REQUEST_MENU_ITEM_1_ID + 31)
                        .withRequestId(REQUEST_4_ID)
                        .withPrepared(1)
                        .build()
        );
    }

}
