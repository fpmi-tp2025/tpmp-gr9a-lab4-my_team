import flight.AdminStrategy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class DataFormatTest {
    private final Predicate<String> predicate;

    {
        AdminStrategy strategy = new AdminStrategy(null, null);
        try {
            Method dateValidator= strategy.getClass().getDeclaredMethod("dateValidatorNotBack");
            dateValidator.setAccessible(true);
            predicate = (Predicate<String>) dateValidator.invoke(strategy);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @ParameterizedTest
    @MethodSource("params")
    public void dataFormatTest(String str, boolean res){
        Assertions.assertEquals(res, predicate.test(str));
    }

    static Stream<Arguments> params() {
        return Stream.of(
                Arguments.of("2020-01-01", true),
                Arguments.of("2020-02-30", false),
                Arguments.of("2020-13-01", false),
                Arguments.of("2020-1-11", false),
                Arguments.of(null, false)
        );
    }
}
