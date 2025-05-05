package flight;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.function.Function;

public class PilotStrategy implements UserStrategy {
    private final Map<String, Function<User, Boolean>> commands;
    private final ConnectionHandler connectionHandler;
    private final ConsoleManager consoleManager;

    public PilotStrategy(ConsoleManager consoleManager, ConnectionHandler connectionHandler) {
        this.consoleManager = consoleManager;
        this.connectionHandler = connectionHandler;

        this.commands = Map.of(
                "/flights_info", user -> {
                    printFlightsInfo(user.helicopterId());
                    return false;
                },
                "/flight_limit", user -> {
                    printFlightLimitInfo(user.helicopterId());
                    return false;
                },
                "/flight_statistic", user -> {
                    printFlightStatistics(user.helicopterId());
                    return false;
                },
                "/help", user -> {
                    final String message = """
                            Available commands:
                             - /flights_info - get information about completed flights
                             - /flight_limit - get information about limit and hours
                             - /flight_statistic - get information about all time passengers count and goods sum
                             - /help - get available commands
                             - /out - log out
                            """;
                    consoleManager.printMessage(message);
                    return false;
                },
                "/out", user -> true
        );
    }

    @Override
    public void apply(User user) {
        Function<User, Boolean> func = null;
        do {
            String input = consoleManager.getInput(
                    String.class,
                    "Input command",
                    "Unknown command",
                    commands::containsKey
            );
            func = commands.get(input);
        }
        while (func.apply(user) == false);
    }

    private void printFlightsInfo(int helicopterId) {
        final String sql = """
                select date, code, goods_weight, passangers, flight_hours, price
                from flight
                where helicopter_id = ?;
                """;

        try (Connection connection = connectionHandler.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, helicopterId);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.isBeforeFirst()) {
                consoleManager.printMessage("date|code|goods_weight|passengers|flight_hours|price");
            } else {
                consoleManager.printMessage("Data not found");
            }
            while (resultSet.next()) {
                String date = resultSet.getString("date");
                String code = resultSet.getString("code");
                Double goodsWeight = resultSet.getDouble("goods_weight");
                Integer passengers = resultSet.getInt("passangers");
                Double flightHours = resultSet.getDouble("flight_hours");
                Double price = resultSet.getDouble("price");

                consoleManager.printMessage("%s|%s|%.2f|%d|%.2f|%.2f".formatted(date, code, goodsWeight, passengers, flightHours, price));
            }
            consoleManager.printMessage("");
        } catch (SQLException e) {
            throw new RuntimeException("Error with db");
        }
    }

    private void printFlightLimitInfo(int helicopterId) {
        final String sql = """
                select h.hours_before_repair as flight_limit, sum(f.flight_hours) as hours
                from helicopter h
                left join flight f on h.id = f.helicopter_id and f.date > h.repair_date
                where h.id = ?;
                """;

        try (Connection connection = connectionHandler.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, helicopterId);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.isBeforeFirst()) {
                consoleManager.printMessage("limit|flied|difference");
            } else {
                consoleManager.printMessage("Helicopter not found\n");
            }

            resultSet.next();
            Double limit = resultSet.getDouble("flight_limit");
            Double hours = resultSet.getDouble("hours");
            Double dif = limit - hours;
            consoleManager.printMessage("%.2f|%.2f|%.2f\n".formatted(limit, hours, dif));
        } catch (SQLException e) {
            throw new RuntimeException("Error with db");
        }
    }

    private void printFlightStatistics(int helicopterId) {
        final String sql = """
                select sum(f.passangers) as passengers, sum(f.goods_weight) as weight
                from flight f
                where f.helicopter_id = ?;
                """;

        try (Connection connection = connectionHandler.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, helicopterId);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.isBeforeFirst()) {
                consoleManager.printMessage("passengers|goods_weight");
            } else {
                consoleManager.printMessage("Helicopter not found\n");
            }

            resultSet.next();
            Integer passengers = resultSet.getInt("passengers");
            Double weight = resultSet.getDouble("weight");
            consoleManager.printMessage("%d|%.2f\n".formatted(passengers, weight));
        } catch (SQLException e) {
            throw new RuntimeException("Error with db");
        }
    }
}
