package pl.codehouse.restaurant.orders;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.codehouse.commons.ExecutionResult;

@DisplayName("ExecutionResult Tests")
class ExecutionResultTest {

    @Test
    @DisplayName("should create non-empty value when success")
    void should_create_non_empty_value_when_success() {
        // given
        String value = "Success";

        // when
        ExecutionResult<String> result = ExecutionResult.success(value);

        // then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isFailure()).isFalse();
        assertThat(result.value()).isEqualTo(Optional.of(value));
    }

    @Test
    @DisplayName("should create empty value when failure")
    void should_create_empty_value_when_failure() {
        // given
        RuntimeException exception = new RuntimeException("Test Exception");

        // when
        ExecutionResult<String> result = ExecutionResult.failure(exception);

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isFailure()).isTrue();
        assertThat(result.value()).isEmpty();
        assertThat(result.exception()).isEqualTo(exception);
    }

    @Test
    @DisplayName("should throw exception when handle does not contain value")
    void should_throw_exception_when_handle_does_not_contain_value() {
        // given
        RuntimeException exception = new RuntimeException("Test Exception");
        ExecutionResult<String> result = ExecutionResult.failure(exception);

        Assertions.assertThatThrownBy(result::handle)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Test Exception");
    }

    @Test
    @DisplayName("should return true when successful")
    void should_return_true_when_successful() {
        // given
        String value = "Success";

        // when
        ExecutionResult<String> result = ExecutionResult.success(value);

        // then
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("should return false when failure")
    void should_return_false_when_failure() {
        // given
        RuntimeException exception = new RuntimeException("Test Exception");

        // when
        ExecutionResult<String> result = ExecutionResult.failure(exception);

        // then
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("should return true when failure occurs")
    void should_return_true_when_failure_occurs() {
        // given
        RuntimeException exception = new RuntimeException("Test Exception");

        // when
        ExecutionResult<String> result = ExecutionResult.failure(exception);

        // then
        assertThat(result.isFailure()).isTrue();
    }

    @Test
    @DisplayName("should return false when successful")
    void should_return_false_when_successful() {
        // given
        String value = "Success";

        // when
        ExecutionResult<String> result = ExecutionResult.success(value);

        // then
        assertThat(result.isFailure()).isFalse();
    }
}