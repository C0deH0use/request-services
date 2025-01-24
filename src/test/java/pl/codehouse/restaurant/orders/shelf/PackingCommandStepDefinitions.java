package pl.codehouse.restaurant.orders.shelf;

import io.cucumber.java.ParameterType;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.ReturnsElementsOf;
import pl.codehouse.commons.Context;
import pl.codehouse.commons.ExecutionResult;
import pl.codehouse.restaurant.orders.request.PackingActionResult;
import pl.codehouse.restaurant.orders.request.RequestDto;
import pl.codehouse.restaurant.orders.request.RequestService;
import pl.codehouse.restaurant.orders.request.RequestStatus;
import pl.codehouse.restaurant.orders.request.UpdatePreparedMenuItemsDto;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static pl.codehouse.restaurant.orders.request.MenuItemEntityBuilder.MENU_ITEM_1_ID;
import static pl.codehouse.restaurant.orders.request.RequestDtoBuilder.aRequestDto;
import static pl.codehouse.restaurant.orders.request.RequestEntityBuilder.REQUEST_ID;
import static pl.codehouse.restaurant.orders.request.RequestMenuItemBuilder.aMenuItemsRequest;
import static pl.codehouse.restaurant.orders.request.RequestMenuItemBuilder.aRequestMenuItemOne;

public class PackingCommandStepDefinitions {
    private static final String MENU_ITEM_1_NAME = "MenuItem 1";

    private final Shelf shelf = Mockito.mock(Shelf.class);
    private final RequestService requestService = Mockito.mock(RequestService.class);
    private final ArgumentCaptor<UpdatePreparedMenuItemsDto> updatePreparedMenuItemsDtoArgumentCaptor = ArgumentCaptor.captor();
    private final PackingCommand sut = new PackingCommand(shelf, requestService);
    private Mono<ExecutionResult<PackingActionResult>> executionResult;

    private int collectedItems, totalItems = 0;

    @Given("{int} out of {int} total menu items got collected from shelf")
    public void given_TheStoreContainsXMenuItemsFromRequest(int collectedMenuItems, int totalMenuItems) {
        collectedItems = collectedMenuItems;
        totalItems = totalMenuItems;
        var requestMenuItems = IntStream.range(0, totalMenuItems)
                .mapToObj(i -> aMenuItemsRequest()
                        .withMenuId(MENU_ITEM_1_ID + i)
                        .withMenuItemName(MENU_ITEM_1_NAME + i)
                        .withQuantity(1)
                        .build()
                )
                .toList();
        RequestDto initialRequestDto = aRequestDto()
                .withMenuItems(requestMenuItems)
                .withPreparedItemsCount(0)
                .withTotalItemsCount(totalMenuItems)
                .build();
        RequestDto updatedRequestDto = aRequestDto()
                .withMenuItems(requestMenuItems)
                .withPreparedItemsCount(collectedMenuItems)
                .withTotalItemsCount(totalMenuItems)
                .withStatus(totalMenuItems == collectedMenuItems ? RequestStatus.READY_TO_COLLECT : RequestStatus.IN_PROGRESS)
                .build();
        given(requestService.findById(REQUEST_ID))
                .willReturn(Mono.just(initialRequestDto))
                .willReturn(Mono.just(updatedRequestDto));

        List<Mono<ShelfTakeResult>> responses = IntStream.range(0, totalMenuItems)
                .mapToObj(i -> mapBasedOn(i, collectedItems))
                .map(Mono::just)
                .toList();

        given(shelf.take(any())).willAnswer(new ReturnsElementsOf(responses));
        given(requestService.updateCollectedItems(any())).willReturn(Mono.just(updatedRequestDto));
    }

    @Given("request containing Menu Items of type 'immediate'")
    public void given_RequestContainingMenuItemsOfType() {
        collectedItems = 3;
        totalItems = 3;
        var requestMenuItems = List.of(
                aRequestMenuItemOne()
                        .withQuantity(3)
                        .isImmediate()
                        .build()
        );
        RequestDto initialRequestDto = aRequestDto()
                .withMenuItems(requestMenuItems)
                .withPreparedItemsCount(0)
                .withTotalItemsCount(3)
                .build();
        RequestDto updatedRequestDto = aRequestDto()
                .withMenuItems(requestMenuItems)
                .withPreparedItemsCount(3)
                .withTotalItemsCount(3)
                .withStatus(RequestStatus.READY_TO_COLLECT)
                .build();
        given(requestService.findById(REQUEST_ID))
                .willReturn(Mono.just(initialRequestDto))
                .willReturn(Mono.just(updatedRequestDto));
        given(requestService.updateCollectedItems(any())).willReturn(Mono.just(updatedRequestDto));
    }

    @When("packing request")
    public void when_packingRequest() {
        executionResult = sut.execute(new Context<>(REQUEST_ID));
    }

    @Then("request status should be set to {requestStatus}")
    public void then_RequestStatusShouldBeSetToExpectedStatus(RequestStatus expectedStatus) {
        StepVerifier.create(executionResult)
                .assertNext(result -> {
                    assertThat(result.isSuccess()).isTrue();
                    assertThat(result.value()).hasValueSatisfying(packingResult -> {
                        assertThat(packingResult.requestId()).isEqualTo(REQUEST_ID);
                        assertThat(packingResult.collectedItems()).isEqualTo(collectedItems);
                        assertThat(packingResult.totalItems()).isEqualTo(totalItems);
                        assertThat(packingResult.status()).isEqualTo(expectedStatus);
                    });
                })
                .verifyComplete();
    }

    @And("requested menu items where updated {int} times by {int}")
    public void and_RequestedMenuItemsWhereUpdatedByX(int updatedCollectedItems, int itemsTakenFromShelf) {
        then(requestService).should(times(updatedCollectedItems)).updateCollectedItems(updatePreparedMenuItemsDtoArgumentCaptor.capture());

        List<Integer> collectedMenuItemIds = IntStream.range(0, collectedItems).mapToObj(i -> MENU_ITEM_1_ID + i).toList();
        List<UpdatePreparedMenuItemsDto> capturedValues = updatePreparedMenuItemsDtoArgumentCaptor.getAllValues();
        assertThat(capturedValues)
                .hasSize(collectedItems)
                .allSatisfy(updateDto -> assertThat(updateDto).hasFieldOrPropertyWithValue("preparedQuantity", itemsTakenFromShelf))
                .extracting(UpdatePreparedMenuItemsDto::menuItemId)
                .containsAll(collectedMenuItemIds);
    }

    @And("no items where picked from shelf")
    public void and_NoItemsWhenPickedFromShelf() {
        then(shelf).should(never()).take(any());
    }

    @ParameterType("NEW|IN_PROGRESS|READY_TO_COLLECT|COLLECTED")
    public RequestStatus requestStatus(String status) {
        return RequestStatus.valueOf(status);
    }

    private ShelfTakeResult mapBasedOn(int idx, int collectedItems) {
        if (collectedItems >= 1 && idx < collectedItems) {
            return new ShelfTakeResult(PackingStatus.READY_TO_COLLECT, 1);
        }
        return new ShelfTakeResult(PackingStatus.REQUESTED_ITEMS, 0);
    }
}
