package pl.codehouse.restaurant.orders.request;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import pl.codehouse.commons.Context;
import pl.codehouse.commons.ExecutionResult;
import pl.codehouse.restaurant.orders.exceptions.ResourceNotFoundException;
import pl.codehouse.restaurant.orders.exceptions.ResourceType;
import pl.codehouse.restaurant.orders.shelf.PackingStatus;
import pl.codehouse.restaurant.orders.shelf.ShelfKafkaProperties;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static pl.codehouse.restaurant.orders.request.MenuItemEntityBuilder.MENU_ITEM_1_ID;
import static pl.codehouse.restaurant.orders.request.MenuItemEntityBuilder.MENU_ITEM_2_ID;
import static pl.codehouse.restaurant.orders.request.MenuItemEntityBuilder.aMenuItemEntityOne;
import static pl.codehouse.restaurant.orders.request.MenuItemEntityBuilder.aMenuItemEntityTwo;
import static pl.codehouse.restaurant.orders.request.RequestMenuItemEntityBuilder.aRequestMenuItemEntityOne;
import static pl.codehouse.restaurant.orders.request.RequestMenuItemEntityBuilder.aRequestMenuItemEntityTwo;
import static pl.codehouse.restaurant.orders.request.RequestedMenuItemPayloadBuilder.aMenuItemOneRequest;
import static pl.codehouse.restaurant.orders.request.RequestedMenuItemPayloadBuilder.aMenuItemTwoRequest;

public class RequestStepDefinitions {
    private static final int REQUEST_ID = 1100;
    private static final int VALUE_REPRESENTING_NULL_ID = 0;
    private static final int CUSTOMER_ID_1 = 1001;

    private final RequestRepository repository = Mockito.mock(RequestRepository.class);

    private final MenuItemRepository menuItemRepository = Mockito.mock(MenuItemRepository.class);

    private final RequestMenuItemRepository requestMenuItemRepository = Mockito.mock(RequestMenuItemRepository.class);

    private final ShelfKafkaProperties shelfKafkaProperties = Mockito.mock(ShelfKafkaProperties.class);

    private final RequestStatusChangePublisher requestStatusChangePublisher = Mockito.mock(RequestStatusChangePublisher.class);

    private final KafkaTemplate<String, ShelfEventDto> kafkaTemplate = Mockito.mock(KafkaTemplate.class);

    private final CreateCommand command = new CreateCommand(repository, menuItemRepository, requestMenuItemRepository, kafkaTemplate, shelfKafkaProperties, requestStatusChangePublisher);

    private Context<RequestPayload> context;

    private Mono<ExecutionResult<RequestDto>> result;

    private RequestDto expectedRequest;

    private final RequestEntity requestEntity = RequestEntityBuilder.aRequestEntity().withId(REQUEST_ID).withCustomerId(CUSTOMER_ID_1).build();

    @Given("customer requests known menu items")
    public void customerRequestsKnownMenuItems() {
        RequestPayload requestPayload = new RequestPayload(List.of(
                aMenuItemOneRequest().build(),
                aMenuItemTwoRequest().build()
        ), CUSTOMER_ID_1);

        List<RequestMenuItemEntity> requestMenuItems = List.of(
                aRequestMenuItemEntityOne().build(),
                aRequestMenuItemEntityTwo().build()
        );

        context = new Context<>(requestPayload);

        given(menuItemRepository.findAllById(anyList())).willReturn(Flux.just(aMenuItemEntityOne().build(), aMenuItemEntityTwo().build()));
        given(repository.save(any(RequestEntity.class))).willReturn(Mono.just(requestEntity));
        given(requestMenuItemRepository.saveAll(anyList())).willReturn(Flux.fromIterable(requestMenuItems));

        expectedRequest = RequestDto.from(requestEntity, requestMenuItems, List.of(aMenuItemEntityOne().build(), aMenuItemEntityTwo().build()));
    }

    @Given("customer requests any of the menu items not being known to the restaurant")
    public void customerRequestsAnyOfTheMenuItemsNotBeingKnownToTheRestaurant() {
        RequestPayload requestPayload = new RequestPayload(List.of(
                aMenuItemOneRequest().build(),
                aMenuItemTwoRequest().build()
        ), CUSTOMER_ID_1);
        context = new Context<>(requestPayload);

        given(menuItemRepository.findAllById(anyList())).willReturn(Flux.just(aMenuItemEntityOne().build()));
    }

    @When("creating new request")
    public void creatingNewRequest() {
       result = command.execute(context);
    }

    @Then("new request is created")
    public void newRequestCreated() {
        StepVerifier.create(result)
                .assertNext(executionResult -> {
                    assertThat(executionResult.isSuccess()).isTrue();
                    assertThat(executionResult.value()).hasValue(expectedRequest);
                })
                .verifyComplete();
        ArgumentCaptor<RequestEntity> requestEntityArgumentCaptor = ArgumentCaptor.captor();
        ArgumentCaptor<List<RequestMenuItemEntity>> requestMenuItemEntityArgumentCaptor = ArgumentCaptor.captor();
        ArgumentCaptor<Message<?>> kafkaMessagesArgumentCaptor = ArgumentCaptor.captor();


        then(repository).should(times(1)).save(requestEntityArgumentCaptor.capture());
        then(requestMenuItemRepository).should(times(1)).saveAll(requestMenuItemEntityArgumentCaptor.capture());
        then(requestStatusChangePublisher).should(times(1)).publishChange(REQUEST_ID, RequestStatus.IN_PROGRESS, PackingStatus.NOT_STARTED);
        then(kafkaTemplate).should(times(1)).send(kafkaMessagesArgumentCaptor.capture());

        // And
        assertThat(requestEntityArgumentCaptor.getValue())
                .hasFieldOrPropertyWithValue("id", VALUE_REPRESENTING_NULL_ID)
                .hasFieldOrPropertyWithValue("customerId", requestEntity.customerId());

        assertThat(requestMenuItemEntityArgumentCaptor.getValue())
                .hasSize(2)
                .allSatisfy(requestMenuItemEntity -> assertThat(requestMenuItemEntity.requestId()).isEqualTo(REQUEST_ID))
                .extracting(RequestMenuItemEntity::menuItemId)
                .allSatisfy(menuItemId -> assertThat(List.of(MENU_ITEM_1_ID, MENU_ITEM_2_ID)).contains(menuItemId));

        List<Message<?>> actualKafkaMessages = kafkaMessagesArgumentCaptor.getAllValues();
        assertThat(actualKafkaMessages)
                .hasSize(1)
                .satisfiesExactly(message -> assertThat(message.getPayload()).isInstanceOf(ShelfEventDto.class))
                .first()
                .satisfies(message -> assertThat((ShelfEventDto) message.getPayload())
                        .hasFieldOrPropertyWithValue("requestId", requestEntity.id())
                        .hasFieldOrPropertyWithValue("eventType", EventType.NEW_REQUEST)
                )
        ;
    }

    @Then("no request is created")
    public void noRequestCreated() {
        StepVerifier.create(result)
                .expectErrorSatisfies(errorThrown -> assertThat(errorThrown)
                        .hasMessageContaining("Not all request components were found")
                        .hasFieldOrPropertyWithValue("resourceType", ResourceType.MENU_ITEM)
                        .isInstanceOf(ResourceNotFoundException.class))
                .verify();

        then(repository).should(never()).save(any());
        then(requestMenuItemRepository).should(never()).saveAll(anyList());
    }
}
