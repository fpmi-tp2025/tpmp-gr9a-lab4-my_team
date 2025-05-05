package flight;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class AdminStrategy implements UserStrategy {
    private final Map<String, Supplier<Boolean>> commands;
    private final ConnectionHandler connectionHandler;
    private final ConsoleManager consoleManager;

    public AdminStrategy(ConsoleManager consoleManager, ConnectionHandler connectionHandler) {
        this.consoleManager = consoleManager;
        this.connectionHandler = connectionHandler;

        this.commands = Map.of(
                "/flight_limits", () -> {
                    printHelicoptersFlightLimit();
                    return false;
                },
                "/help", () -> {
                    final String message = """
                            Available commands:
                             - /flight_limits - check limits
                             - /help - get available commands
                             - /out - log out
                            """;
                    consoleManager.printMessage(message);
                    return false;
                },
                "/out", () -> true
        );
    }

    @Override
    public void apply(User user) {
        Supplier<Boolean> func = null;
        do {
            String input = consoleManager.getInput(
                    String.class,
                    "Input command:",
                    "Unknown command",
                    commands::containsKey
            );
            func = commands.get(input);
        }
        while (func.get() == false);
    }

    private void printHelicoptersFlightLimit() {
        final String sql = """
                select
                    h.seria_num as seria,
                    h.hours_before_repair as flight_limit,
                    COALESCE(
                        (select sum(f.flight_hours)
                         from flight f
                         where f.helicopter_id = h.id and f.date > h.repair_date),
                    0.0
                    ) as hours
                from helicopter h
                """;

        try (Connection connection = connectionHandler.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            ResultSet resultSet = statement.executeQuery();
            if (resultSet.isBeforeFirst()) {
                consoleManager.printMessage("seria_num|limit|flied|difference");
            } else {
                consoleManager.printMessage("Helicopter not found\n");
            }

            while(resultSet.next()){
                String seriaNum = resultSet.getString("seria");
                Double limit = resultSet.getDouble("flight_limit");
                Double hours = resultSet.getDouble("hours");
                Double dif = limit - hours;
                consoleManager.printMessage("%s|%.2f|%.2f|%.2f".formatted(seriaNum, limit, hours, dif));
            }
            consoleManager.printMessage("");
        } catch (SQLException e) {
            throw new RuntimeException("Error with db");
        }
    }

}
