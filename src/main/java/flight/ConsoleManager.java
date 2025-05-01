package flight;

import lombok.AllArgsConstructor;

import java.io.PrintStream;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Function;
import java.util.function.Predicate;

@AllArgsConstructor
public class ConsoleManager {
    private static final Map<Class<?>, Function<String, ?>> functions;
    private PrintStream OUT;
    private Scanner SCANNER;

    static {
        functions = Map.of(
                String.class, s -> s,
                Integer.class, Integer::parseInt,
                Double.class, Double::parseDouble
        );
    }

    public <T> T getInput(Class<T> clazz, String message, String errMessage, Predicate<T> predicate) {
        Function<String, T> parseFunction = (Function<String, T>) functions.get(clazz);
        if(parseFunction == null) {
            throw new RuntimeException("Unsupported class: " + clazz.getName());
        }

        while(true){
            OUT.println(message);
            String next = SCANNER.nextLine();
            try {
                T apply = parseFunction.apply(next);
                if(predicate.test(apply)){
                    return apply;
                }
            }
            catch(Exception ignored){}
            OUT.println(errMessage);
            OUT.println();
        }
    }
}
