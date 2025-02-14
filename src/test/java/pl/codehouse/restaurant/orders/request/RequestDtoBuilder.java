package pl.codehouse.restaurant.orders.request;

import java.util.ArrayList;
import java.util.List;

import static pl.codehouse.restaurant.orders.request.RequestEntityBuilder.CUSTOMER_ID;
import static pl.codehouse.restaurant.orders.request.RequestEntityBuilder.REQUEST_ID;
import static pl.codehouse.restaurant.orders.request.RequestMenuItemBuilder.aRequestMenuItemOne;
import static pl.codehouse.restaurant.orders.request.RequestMenuItemBuilder.aRequestMenuItemTwo;

public class RequestDtoBuilder {
    private int requestId = REQUEST_ID;
    private int customerId = CUSTOMER_ID;
    private List<RequestMenuItem> menuItems = new ArrayList<>();
    private int preparedItemsCount = 0;
    private int totalItemsCount = 1;
    private RequestStatus status = RequestStatus.NEW;

    private RequestDtoBuilder() {
    }

    public static RequestDtoBuilder aRequestDto() {
        return new RequestDtoBuilder();
    }

    public RequestDtoBuilder withRequestId(int requestId) {
        this.requestId = requestId;
        return this;
    }

    public RequestDtoBuilder withCustomerId(int customerId) {
        this.customerId = customerId;
        return this;
    }

    public RequestDtoBuilder withMenuItems(List<RequestMenuItem> menuItems) {
        this.menuItems = new ArrayList<>(menuItems);
        return this;
    }

    public RequestDtoBuilder addMenuItem(RequestMenuItem menuItem) {
        this.menuItems.add(menuItem);
        return this;
    }

    public RequestDtoBuilder addMenuItemOne() {
        addMenuItem(aRequestMenuItemOne().build());
        return this;
    }

    public RequestDtoBuilder addMenuItemTwo() {
        addMenuItem(aRequestMenuItemTwo().build());
        return this;
    }

    public RequestDtoBuilder withPreparedItemsCount(int preparedItemsCount) {
        this.preparedItemsCount = preparedItemsCount;
        return this;
    }

    public RequestDtoBuilder withTotalItemsCount(int totalItemsCount) {
        this.totalItemsCount = totalItemsCount;
        return this;
    }

    public RequestDtoBuilder withStatus(RequestStatus status) {
        this.status = status;
        return this;
    }

    public RequestDto build() {
        return new RequestDto(requestId, customerId, menuItems, preparedItemsCount, totalItemsCount, status);
    }
}
