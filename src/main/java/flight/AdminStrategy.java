package flight;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class AdminStrategy implements UserStrategy {
    private final Map<String, Supplier<Boolean>> commands;
    private final ConnectionHandler connectionHandler;
    private final ConsoleManager consoleManager;

    public AdminStrategy(ConsoleManager consoleManager, ConnectionHandler connectionHandler) {
        this.consoleManager = consoleManager;
        this.connectionHandler = connectionHandler;

        this.commands = new LinkedHashMap<>();
        commands.put("/helicopter_flight_hours_resource", () -> {
            printHelicopterFlightHoursAndResource();
            return false;
        });
        commands.put("/helicopter_flights_period", () -> {
            printHelicopterFlightsForPeriod();
            return false;
        });
        commands.put("/special_flights_summary", () -> {
            printSpecialFlightsSummary();
            return false;
        });
        commands.put("/regular_flights_summary", () -> {
            printRegularFlightsSummary();
            return false;
        });
        commands.put("/helicopter_max_flights_info", () -> {
            printHelicopterWithMaxFlightsInfo();
            return false;
        });
        commands.put("/crew_max_earnings_flights", () -> {
            printCrewWithMaxEarningsFlights();
            return false;
        });
        commands.put("/crew_member_flights_info", () -> {
            printCrewOrMemberFlightsInfo();
            return false;
        });
        commands.put("/add_flight", () -> {
            addFlightWithResourceCheck();
            return false;
        });
        commands.put("/update_flight_info", () -> {
            updateFlightInfo();
            return false;
        });
        commands.put("/delete_flight", () -> {
            deleteFlight();
            return false;
        });
        commands.put("/calculate_crew_earnings_period", () -> {
            calculateCrewEarningsForPeriod();
            return false;
        });
        commands.put("/pilot_earnings_period", () -> {
            printPilotEarningsForPeriod();
            return false;
        });
        commands.put("/pilot_earnings_specific_flights", () -> {
            printPilotEarningsForSpecificFlights();
            return false;
        });
        commands.put("/help", () -> {
            printHelp();
            return false;
        });
        commands.put("/out", () -> true);
    }

    private void printHelp() {
        final StringBuilder helpMessage = new StringBuilder("Available commands:\n");
        for (String command : commands.keySet()) {
            helpMessage.append(" - ").append(command).append("\n");
        }
        consoleManager.printMessage(helpMessage.toString());
    }

    @Override
    public void apply(User user) {
        Supplier<Boolean> func;
        do {
            String input = consoleManager.getInput(
                    String.class,
                    "Введите команду:",
                    "Неизвестная команда",
                    commands::containsKey
            );
            if ("/back".equalsIgnoreCase(input)) {
                consoleManager.printMessage("Returning to previous menu or exiting.");
                break;
            }
            func = commands.get(input);
        } while (func != null && !func.get());
    }

    private Predicate<String> notBack() {
        return s -> s != null && !"/back".equalsIgnoreCase(s.trim());
    }

    private Predicate<String> dateValidatorNotBack() {
        return s -> {
            if(s == null) return false;
            if ("/back".equalsIgnoreCase(s)) return true;
            Pattern pattern = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
            if (!pattern.matcher(s).matches()) {
                return false;
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            sdf.setLenient(false);

            try {
                sdf.parse(s);
                return true;
            } catch (ParseException e) {
                return false;
            }
        };
    }

    private Predicate<String> flightCodeValidatorNotBack() {
        return s -> {
            if ("/back".equalsIgnoreCase(s)) return true;
            return "usual".equalsIgnoreCase(s) || "special".equalsIgnoreCase(s);
        };
    }


    private void printHelicopterFlightHoursAndResource() {
        consoleManager.printMessage("Информация по налету и ресурсу вертолетов:");
        final String sql = """
                SELECT
                   h.seria_num as seria,
                   h.hours_before_repair as flight_limit,
                   COALESCE(SUM(f.flight_hours), 0.0) as hours_after_repair
                FROM helicopter h
                LEFT JOIN flight f ON h.id = f.helicopter_id AND f.date >= h.repair_date
                GROUP BY h.id, h.seria_num, h.hours_before_repair, h.repair_date
                ORDER BY h.seria_num;
                """;
        try (Connection connection = connectionHandler.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            ResultSet resultSet = statement.executeQuery();
            if (!resultSet.isBeforeFirst()) {
                consoleManager.printMessage("Данные по вертолетам не найдены.");
                return;
            }
            consoleManager.printMessage("Серийный номер | Ресурс (часы) | Налетано после ремонта (часы) | Остаток ресурса (часы)");
            while (resultSet.next()) {
                String seriaNum = resultSet.getString("seria");
                double flightLimit = resultSet.getDouble("flight_limit");
                double hoursAfterRepair = resultSet.getDouble("hours_after_repair");
                double remainingHours = flightLimit - hoursAfterRepair;
                consoleManager.printMessage("%s | %.2f | %.2f | %.2f".formatted(seriaNum, flightLimit, hoursAfterRepair, remainingHours));
            }
            consoleManager.printMessage("");
        } catch (SQLException e) {
            consoleManager.printMessage("Ошибка при доступе к базе данных: " + e.getMessage());
        }
    }


    private void printHelicopterFlightsForPeriod() {
        consoleManager.printMessage("Вывод списка рейсов вертолета за период.");
        String helicopterIdStr = consoleManager.getInput(String.class, "Введите ID вертолета (или /back для отмены):", "Неверный ID.", notBack());
        if ("/back".equalsIgnoreCase(helicopterIdStr)) return;
        int helicopterId;
        try {
            helicopterId = Integer.parseInt(helicopterIdStr);
        } catch (NumberFormatException e) {
            consoleManager.printMessage("Некорректный ID вертолета.");
            return;
        }


        String startDateStr = consoleManager.getInput(String.class, "Введите начальную дату периода (YYYY-MM-DD или /back):", "Неверный формат даты.", dateValidatorNotBack());
        if ("/back".equalsIgnoreCase(startDateStr)) return;

        String endDateStr = consoleManager.getInput(String.class, "Введите конечную дату периода (YYYY-MM-DD или /back):", "Неверный формат даты.", dateValidatorNotBack());
        if ("/back".equalsIgnoreCase(endDateStr)) return;

        final String sql = """
                SELECT
                   f.id as flight_id,
                   f.date,
                   f.code,
                   f.goods_weight,
                   f.passangers,
                   f.flight_hours,
                   f.price
                FROM flight f
                WHERE f.helicopter_id = ? AND f.date BETWEEN ? AND ?
                ORDER BY f.date;
                """;
        try (Connection connection = connectionHandler.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, helicopterId);
            statement.setString(2, startDateStr);
            statement.setString(3, endDateStr);
            ResultSet resultSet = statement.executeQuery();

            if (!resultSet.isBeforeFirst()) {
                consoleManager.printMessage("Рейсы для вертолета с ID " + helicopterId + " за указанный период не найдены.");
                return;
            }
            consoleManager.printMessage("Рейсы вертолета ID " + helicopterId + " с " + startDateStr + " по " + endDateStr + ":");
            consoleManager.printMessage("ID Рейса | Дата | Тип | Груз (кг) | Пассажиры | Часы налета | Стоимость");
            double totalGoods = 0;
            int totalPassengers = 0;
            while (resultSet.next()) {
                int flightId = resultSet.getInt("flight_id");
                String flightDate = resultSet.getString("date");
                String code = resultSet.getString("code");
                double goodsWeight = resultSet.getDouble("goods_weight");
                int passengers = resultSet.getInt("passangers");
                double flightHours = resultSet.getDouble("flight_hours");
                double price = resultSet.getDouble("price");

                consoleManager.printMessage("%d | %s | %s | %.2f | %d | %.2f | %.2f".formatted(
                        flightId, flightDate, code, goodsWeight, passengers, flightHours, price
                ));
                totalGoods += goodsWeight;
                totalPassengers += passengers;
            }
            consoleManager.printMessage("----------------------------------------------------");
            consoleManager.printMessage("Итого за период: Общая масса грузов = %.2f кг, Общее количество пассажиров = %d".formatted(totalGoods, totalPassengers));
            consoleManager.printMessage("");
        } catch (SQLException e) {
            consoleManager.printMessage("Ошибка при доступе к базе данных: " + e.getMessage());
        }
    }

    private void printSpecialFlightsSummary() {
        consoleManager.printMessage("Сводка по спецрейсам:");
        final String sql = """
                SELECT
                   COUNT(f.id) as total_flights,
                   COALESCE(SUM(f.goods_weight), 0.0) as total_goods_weight,
                   COALESCE(SUM(f.price), 0.0) as total_money_earned
                FROM flight f
                WHERE f.code = 'special';
                """;
        try (Connection connection = connectionHandler.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                int totalFlights = resultSet.getInt("total_flights");
                double totalGoodsWeight = resultSet.getDouble("total_goods_weight");
                double totalMoneyEarned = resultSet.getDouble("total_money_earned");

                if (totalFlights == 0) {
                    consoleManager.printMessage("Спецрейсы не выполнялись.");
                } else {
                    consoleManager.printMessage("Общее количество спецрейсов: " + totalFlights);
                    consoleManager.printMessage("Общая масса перевезенных грузов (спецрейсы): %.2f кг".formatted(totalGoodsWeight));
                    consoleManager.printMessage("Общая сумма заработанных денег (спецрейсы): %.2f".formatted(totalMoneyEarned));
                }
            }
            consoleManager.printMessage("");
        } catch (SQLException e) {
            consoleManager.printMessage("Ошибка при доступе к базе данных: " + e.getMessage());
        }
    }

    // выводит по всем вертолетам, выполнявшим обычные рейсы, общее количество рейсов, общую массу перевезенных грузов, общую сумму заработанных денег.

    private void printRegularFlightsSummary() {
        consoleManager.printMessage("Сводка по обычным рейсам:");
        final String sql = """
                SELECT
                   COUNT(f.id) as total_flights,
                   COALESCE(SUM(f.goods_weight), 0.0) as total_goods_weight,
                   COALESCE(SUM(f.price), 0.0) as total_money_earned
                FROM flight f
                WHERE f.code = 'usual';
                """;
        try (Connection connection = connectionHandler.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                int totalFlights = resultSet.getInt("total_flights");
                double totalGoodsWeight = resultSet.getDouble("total_goods_weight");
                double totalMoneyEarned = resultSet.getDouble("total_money_earned");

                if (totalFlights == 0) {
                    consoleManager.printMessage("Обычные рейсы не выполнялись.");
                } else {
                    consoleManager.printMessage("Общее количество обычных рейсов: " + totalFlights);
                    consoleManager.printMessage("Общая масса перевезенных грузов (обычные рейсы): %.2f кг".formatted(totalGoodsWeight));
                    consoleManager.printMessage("Общая сумма заработанных денег (обычные рейсы): %.2f".formatted(totalMoneyEarned));
                }
            }
            consoleManager.printMessage("");
        } catch (SQLException e) {
            consoleManager.printMessage("Ошибка при доступе к базе данных: " + e.getMessage());
        }
    }

    private void printHelicopterWithMaxFlightsInfo() {
        consoleManager.printMessage("Информация по вертолету с максимальным количеством рейсов:");
        final String findMaxFlightsHelicopterSql = """
                   SELECT helicopter_id, COUNT(id) as flight_count
                   FROM flight
                   GROUP BY helicopter_id
                   ORDER BY flight_count DESC
                   LIMIT 1;
                """;
        int helicopterIdWithMaxFlights = -1;
        long maxFlights = 0;

        try (Connection connection = connectionHandler.getConnection();
             PreparedStatement statement = connection.prepareStatement(findMaxFlightsHelicopterSql)) {
            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                helicopterIdWithMaxFlights = rs.getInt("helicopter_id");
                maxFlights = rs.getLong("flight_count");
            } else {
                consoleManager.printMessage("Нет данных о рейсах для определения вертолета.");
                return;
            }
        } catch (SQLException e) {
            consoleManager.printMessage("Ошибка при поиске вертолета с макс. рейсами: " + e.getMessage());
            return;
        }

        consoleManager.printMessage("Вертолет с ID " + helicopterIdWithMaxFlights + " выполнил максимальное количество рейсов: " + maxFlights);
        final String helicopterInfoSql = """
                   SELECT
                       h.seria_num,
                       h.mark,
                       (SELECT SUM(f.price) FROM flight f WHERE f.helicopter_id = h.id) as total_earned_money
                   FROM helicopter h
                   WHERE h.id = ?;
                """;

        final String crewInfoSql = """
                   SELECT
                       p.tabel_num,
                       p.last_name,
                       p.position
                   FROM pilot p
                   WHERE p.helicopter_id = ?;
                """;

        try (Connection connection = connectionHandler.getConnection();
             PreparedStatement helicopterStmt = connection.prepareStatement(helicopterInfoSql);
             PreparedStatement crewStmt = connection.prepareStatement(crewInfoSql)) {

            helicopterStmt.setInt(1, helicopterIdWithMaxFlights);
            ResultSet helicopterRs = helicopterStmt.executeQuery();
            if (helicopterRs.next()) {
                consoleManager.printMessage("Серийный номер: " + helicopterRs.getString("seria_num"));
                consoleManager.printMessage("Марка: " + helicopterRs.getString("mark"));
                consoleManager.printMessage("Общая сумма заработанных денег этим вертолетом: %.2f".formatted(helicopterRs.getDouble("total_earned_money")));
            }

            consoleManager.printMessage("\nСведения об экипаже вертолета ID " + helicopterIdWithMaxFlights + ":");
            crewStmt.setInt(1, helicopterIdWithMaxFlights);
            ResultSet crewRs = crewStmt.executeQuery();
            if (!crewRs.isBeforeFirst()) {
                consoleManager.printMessage("Данные об экипаже не найдены.");
            } else {
                consoleManager.printMessage("Табельный номер | Фамилия | Должность");
                while (crewRs.next()) {
                    consoleManager.printMessage("%s | %s | %s".formatted(
                            crewRs.getString("tabel_num"),
                            crewRs.getString("last_name"),
                            crewRs.getString("position")
                    ));
                }
            }
            consoleManager.printMessage("");

        } catch (SQLException e) {
            consoleManager.printMessage("Ошибка при получении информации о вертолете/экипаже: " + e.getMessage());
        }
    }

    private void printCrewWithMaxEarningsFlights() {
        consoleManager.printMessage("Информация по экипажу (вертолету) с максимальным заработком:");

        final String findMaxEarningHelicopterSql = """
                  SELECT f.helicopter_id, SUM(f.price) as total_earnings
                  FROM flight f
                  GROUP BY f.helicopter_id
                  ORDER BY total_earnings DESC
                  LIMIT 1;
                """;
        int helicopterIdWithMaxEarnings = -1;
        double maxEarnings = 0;

        try (Connection connection = connectionHandler.getConnection();
             PreparedStatement statement = connection.prepareStatement(findMaxEarningHelicopterSql)) {
            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                helicopterIdWithMaxEarnings = rs.getInt("helicopter_id");
                maxEarnings = rs.getDouble("total_earnings");
            } else {
                consoleManager.printMessage("Нет данных о рейсах для определения самого доходного экипажа (вертолета).");
                return;
            }
        } catch (SQLException e) {
            consoleManager.printMessage("Ошибка при поиске самого доходного экипажа (вертолета): " + e.getMessage());
            return;
        }

        consoleManager.printMessage("Экипаж вертолета с ID " + helicopterIdWithMaxEarnings + " заработал максимальную сумму: %.2f".formatted(maxEarnings));
        consoleManager.printMessage("\nСведения о рейсах этого экипажа (вертолета):");

        final String flightsInfoSql = """
                  SELECT
                      f.id as flight_id,
                      f.date,
                      f.code,
                      f.goods_weight,
                      f.passangers,
                      f.flight_hours,
                      f.price
                  FROM flight f
                  WHERE f.helicopter_id = ?
                  ORDER BY f.date;
                """;
        try (Connection connection = connectionHandler.getConnection();
             PreparedStatement statement = connection.prepareStatement(flightsInfoSql)) {
            statement.setInt(1, helicopterIdWithMaxEarnings);
            ResultSet resultSet = statement.executeQuery();

            if (!resultSet.isBeforeFirst()) {
                consoleManager.printMessage("Рейсы для данного экипажа (вертолета) не найдены.");
            } else {
                consoleManager.printMessage("ID Рейса | Дата | Тип | Груз (кг) | Пассажиры | Часы налета | Стоимость");
                while (resultSet.next()) {
                    consoleManager.printMessage("%d | %s | %s | %.2f | %d | %.2f | %.2f".formatted(
                            resultSet.getInt("flight_id"),
                            resultSet.getString("date"),
                            resultSet.getString("code"),
                            resultSet.getDouble("goods_weight"),
                            resultSet.getInt("passangers"),
                            resultSet.getDouble("flight_hours"),
                            resultSet.getDouble("price")
                    ));
                }
            }
            consoleManager.printMessage("");
        } catch (SQLException e) {
            consoleManager.printMessage("Ошибка при получении списка рейсов: " + e.getMessage());
        }
    }

    private void printCrewOrMemberFlightsInfo() {
        consoleManager.printMessage("Поиск рейсов по экипажу (вертолету) или члену экипажа (пилоту).");
        String searchType = consoleManager.getInput(String.class, "Искать по ID вертолета (введите 'H') или ID пилота (введите 'P')? (/back для отмены):", "Неверный выбор.", s -> "H".equalsIgnoreCase(s) || "P".equalsIgnoreCase(s) || "/back".equalsIgnoreCase(s));
        if ("/back".equalsIgnoreCase(searchType)) return;

        if ("H".equalsIgnoreCase(searchType)) {
            String helicopterIdStr = consoleManager.getInput(String.class, "Введите ID вертолета (экипажа) (/back для отмены):", "Неверный ID.", notBack());
            if ("/back".equalsIgnoreCase(helicopterIdStr)) return;
            int helicopterId;
            try {
                helicopterId = Integer.parseInt(helicopterIdStr);
            } catch (NumberFormatException e) {
                consoleManager.printMessage("Некорректный ID вертолета.");
                return;
            }


            consoleManager.printMessage("Рейсы экипажа вертолета ID " + helicopterId + ":");
            final String sql = """
                       SELECT f.id, f.date, f.code, f.goods_weight, f.passangers, f.flight_hours, f.price
                       FROM flight f
                       WHERE f.helicopter_id = ?
                       ORDER BY f.date;
                    """;
            try (Connection connection = connectionHandler.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, helicopterId);
                ResultSet resultSet = statement.executeQuery();
                if (!resultSet.isBeforeFirst()) {
                    consoleManager.printMessage("Рейсы не найдены.");
                } else {
                    consoleManager.printMessage("ID Рейса | Дата | Тип | Груз (кг) | Пассажиры | Часы налета | Стоимость");
                    while (resultSet.next()) {
                        consoleManager.printMessage("%d | %s | %s | %.2f | %d | %.2f | %.2f".formatted(
                                resultSet.getInt("id"), resultSet.getString("date"), resultSet.getString("code"),
                                resultSet.getDouble("goods_weight"), resultSet.getInt("passangers"),
                                resultSet.getDouble("flight_hours"), resultSet.getDouble("price")
                        ));
                    }
                }
                consoleManager.printMessage("");
            } catch (SQLException e) {
                consoleManager.printMessage("Ошибка при доступе к базе данных: " + e.getMessage());
            }
        } else if ("P".equalsIgnoreCase(searchType)) {
            String pilotIdStr = consoleManager.getInput(String.class, "Введите ID пилота (/back для отмены):", "Неверный ID.", notBack());
            if ("/back".equalsIgnoreCase(pilotIdStr)) return;
            int pilotId;
            try {
                pilotId = Integer.parseInt(pilotIdStr);
            } catch (NumberFormatException e) {
                consoleManager.printMessage("Некорректный ID пилота.");
                return;
            }

            consoleManager.printMessage("Рейсы, выполненные на вертолете, к которому приписан пилот ID " + pilotId + ":");
            final String sql = """
                       SELECT f.id, f.date, f.code, f.goods_weight, f.passangers, f.flight_hours, f.price, h.seria_num as helicopter_seria
                       FROM flight f
                       JOIN helicopter h ON f.helicopter_id = h.id
                       JOIN pilot p ON h.id = p.helicopter_id
                       WHERE p.id = ?
                       ORDER BY f.date;
                    """;
            try (Connection connection = connectionHandler.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, pilotId);
                ResultSet resultSet = statement.executeQuery();
                if (!resultSet.isBeforeFirst()) {
                    consoleManager.printMessage("Рейсы не найдены для данного пилота (или пилот не приписан к вертолету с рейсами).");
                } else {
                    consoleManager.printMessage("ID Рейса | Дата | Тип | Груз (кг) | Пассажиры | Часы налета | Стоимость | Вертолет (серия)");
                    while (resultSet.next()) {
                        consoleManager.printMessage("%d | %s | %s | %.2f | %d | %.2f | %.2f | %s".formatted(
                                resultSet.getInt("id"), resultSet.getString("date"), resultSet.getString("code"),
                                resultSet.getDouble("goods_weight"), resultSet.getInt("passangers"),
                                resultSet.getDouble("flight_hours"), resultSet.getDouble("price"),
                                resultSet.getString("helicopter_seria")
                        ));
                    }
                }
                consoleManager.printMessage("");
            } catch (SQLException e) {
                consoleManager.printMessage("Ошибка при доступе к базе данных: " + e.getMessage());
            }
        }
    }

    private void addFlightWithResourceCheck() {
        consoleManager.printMessage("Добавление нового рейса:");

        String dateStr = consoleManager.getInput(String.class, "Дата рейса (YYYY-MM-DD или /back):", "Неверный формат.", dateValidatorNotBack());
        if ("/back".equalsIgnoreCase(dateStr)) return;

        String helicopterIdStr = consoleManager.getInput(String.class, "ID вертолета (или /back):", "Неверный ID.", notBack());
        if ("/back".equalsIgnoreCase(helicopterIdStr)) return;
        int helicopterId;
        try {
            helicopterId = Integer.parseInt(helicopterIdStr);
        } catch (NumberFormatException e) {
            consoleManager.printMessage("Некорректный ID вертолета.");
            return;
        }

        String code = consoleManager.getInput(String.class, "Тип рейса (usual/special или /back):", "Неверный тип.", flightCodeValidatorNotBack());
        if ("/back".equalsIgnoreCase(code)) return;

        String goodsWeightStr = consoleManager.getInput(String.class, "Вес груза (кг) (или /back):", "Неверное значение.", notBack());
        if ("/back".equalsIgnoreCase(goodsWeightStr)) return;
        double goodsWeight;
        try {
            goodsWeight = Double.parseDouble(goodsWeightStr);
            if (goodsWeight < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            consoleManager.printMessage("Некорректный вес груза.");
            return;
        }


        String passengersStr = consoleManager.getInput(String.class, "Количество пассажиров (или /back):", "Неверное значение.", notBack());
        if ("/back".equalsIgnoreCase(passengersStr)) return;
        int passengers;
        try {
            passengers = Integer.parseInt(passengersStr);
            if (passengers < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            consoleManager.printMessage("Некорректное количество пассажиров.");
            return;
        }

        String flightHoursStr = consoleManager.getInput(String.class, "Продолжительность полета (часы) (или /back):", "Неверное значение.", notBack());
        if ("/back".equalsIgnoreCase(flightHoursStr)) return;
        double flightHours;
        try {
            flightHours = Double.parseDouble(flightHoursStr);
            if (flightHours <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            consoleManager.printMessage("Некорректная продолжительность полета.");
            return;
        }

        String priceStr = consoleManager.getInput(String.class, "Стоимость рейса (или /back):", "Неверное значение.", notBack());
        if ("/back".equalsIgnoreCase(priceStr)) return;
        double price;
        try {
            price = Double.parseDouble(priceStr);
            if (price < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            consoleManager.printMessage("Некорректная стоимость.");
            return;
        }

        final String checkResourceSql = """
                   SELECT h.hours_before_repair, h.repair_date, COALESCE(SUM(f.flight_hours), 0.0) as flown_after_repair
                   FROM helicopter h
                   LEFT JOIN flight f ON f.helicopter_id = h.id AND f.date >= h.repair_date
                   WHERE h.id = ?
                   GROUP BY h.id, h.hours_before_repair, h.repair_date;
                """;
        final String insertFlightSql = "INSERT INTO flight (date, helicopter_id, code, goods_weight, passangers, flight_hours, price) VALUES (?, ?, ?, ?, ?, ?, ?);";

        try (Connection connection = connectionHandler.getConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement checkStmt = connection.prepareStatement(checkResourceSql)) {
                checkStmt.setInt(1, helicopterId);
                ResultSet rs = checkStmt.executeQuery();

                if (!rs.next()) {
                    consoleManager.printMessage("Вертолет с ID " + helicopterId + " не найден.");
                    connection.rollback();
                    return;
                }

                double hoursBeforeRepair = rs.getDouble("hours_before_repair");
                double flownAfterRepair = rs.getDouble("flown_after_repair");

                if (flownAfterRepair + flightHours > hoursBeforeRepair) {
                    consoleManager.printMessage("Ошибка: Добавление этого рейса превысит ресурс летного времени вертолета.");
                    consoleManager.printMessage("Ресурс: " + hoursBeforeRepair + ", налетано после ремонта: " + flownAfterRepair + ", планируется: " + flightHours);
                    consoleManager.printMessage("Остаток ресурса: " + (hoursBeforeRepair - flownAfterRepair));
                    connection.rollback();
                    return;
                }
            }

            try (PreparedStatement insertStmt = connection.prepareStatement(insertFlightSql)) {
                insertStmt.setString(1, dateStr);
                insertStmt.setInt(2, helicopterId);
                insertStmt.setString(3, code);
                insertStmt.setDouble(4, goodsWeight);
                insertStmt.setInt(5, passengers);
                insertStmt.setDouble(6, flightHours);
                insertStmt.setDouble(7, price);

                int affectedRows = insertStmt.executeUpdate();
                if (affectedRows > 0) {
                    connection.commit();
                    consoleManager.printMessage("Рейс успешно добавлен.");
                } else {
                    connection.rollback();
                    consoleManager.printMessage("Не удалось добавить рейс.");
                }
            } catch (SQLException e) {
                connection.rollback();
                consoleManager.printMessage("Ошибка при добавлении рейса в БД: " + e.getMessage());
            } finally {
                connection.setAutoCommit(true);
            }

        } catch (SQLException e) {
            consoleManager.printMessage("Ошибка соединения с БД: " + e.getMessage());
        }
        consoleManager.printMessage("");
    }

    private void updateFlightInfo() {
        consoleManager.printMessage("Обновление информации о рейсе:");

        String flightIdStr = consoleManager.getInput(String.class, "Введите ID рейса для обновления (или /back):", "Неверный ID.", notBack());
        if ("/back".equalsIgnoreCase(flightIdStr)) return;
        int flightId;
        try {
            flightId = Integer.parseInt(flightIdStr);
        } catch (NumberFormatException e) {
            consoleManager.printMessage("Некорректный ID рейса.");
            return;
        }

        consoleManager.printMessage("Введите новые данные (оставьте пустым, если не хотите менять, или /back для отмены поля):");

        String dateStr = consoleManager.getInput(String.class, "Новая дата рейса (YYYY-MM-DD или /back):", "Неверный формат.", s -> s.isEmpty() || "/back".equalsIgnoreCase(s) || dateValidatorNotBack().test(s));
        if ("/back".equalsIgnoreCase(dateStr)) return;

        String helicopterIdStr = consoleManager.getInput(String.class, "Новый ID вертолета (или /back):", "Неверный ID.", s -> s.isEmpty() || "/back".equalsIgnoreCase(s) || notBack().test(s));
        if ("/back".equalsIgnoreCase(helicopterIdStr)) return;


        String code = consoleManager.getInput(String.class, "Новый тип рейса (usual/special или /back):", "Неверный тип.", s -> s.isEmpty() || "/back".equalsIgnoreCase(s) || flightCodeValidatorNotBack().test(s));
        if ("/back".equalsIgnoreCase(code)) return;

        String goodsWeightStr = consoleManager.getInput(String.class, "Новый вес груза (кг) (или /back):", "Неверное значение.", s -> s.isEmpty() || "/back".equalsIgnoreCase(s) || notBack().test(s));
        if ("/back".equalsIgnoreCase(goodsWeightStr)) return;


        String passengersStr = consoleManager.getInput(String.class, "Новое количество пассажиров (или /back):", "Неверное значение.", s -> s.isEmpty() || "/back".equalsIgnoreCase(s) || notBack().test(s));
        if ("/back".equalsIgnoreCase(passengersStr)) return;


        String flightHoursStr = consoleManager.getInput(String.class, "Новая продолжительность полета (часы) (или /back):", "Неверное значение.", s -> s.isEmpty() || "/back".equalsIgnoreCase(s) || notBack().test(s));
        if ("/back".equalsIgnoreCase(flightHoursStr)) return;


        String priceStr = consoleManager.getInput(String.class, "Новая стоимость рейса (или /back):", "Неверное значение.", s -> s.isEmpty() || "/back".equalsIgnoreCase(s) || notBack().test(s));
        if ("/back".equalsIgnoreCase(priceStr)) return;


        StringBuilder sqlBuilder = new StringBuilder("UPDATE flight SET ");
        boolean firstField = true;

        if (!dateStr.isEmpty()) {
            sqlBuilder.append("date = ?");
            firstField = false;
        }
        if (!helicopterIdStr.isEmpty()) {
            if (!firstField) sqlBuilder.append(", ");
            sqlBuilder.append("helicopter_id = ?");
            firstField = false;
        }
        if (!code.isEmpty()) {
            if (!firstField) sqlBuilder.append(", ");
            sqlBuilder.append("code = ?");
            firstField = false;
        }
        if (!goodsWeightStr.isEmpty()) {
            if (!firstField) sqlBuilder.append(", ");
            sqlBuilder.append("goods_weight = ?");
            firstField = false;
        }
        if (!passengersStr.isEmpty()) {
            if (!firstField) sqlBuilder.append(", ");
            sqlBuilder.append("passangers = ?");
            firstField = false;
        }
        if (!flightHoursStr.isEmpty()) {
            if (!firstField) sqlBuilder.append(", ");
            sqlBuilder.append("flight_hours = ?");
        }
        if (!priceStr.isEmpty()) {
            if (!firstField) sqlBuilder.append(", ");
            sqlBuilder.append("price = ?");
        }


        if (firstField) {
            consoleManager.printMessage("Нет данных для обновления.");
            return;
        }

        sqlBuilder.append(" WHERE id = ?;");

        try (Connection connection = connectionHandler.getConnection();
             PreparedStatement statement = connection.prepareStatement(sqlBuilder.toString())) {
            int paramIndex = 1;
            if (!dateStr.isEmpty()) statement.setString(paramIndex++, dateStr);
            if (!helicopterIdStr.isEmpty()) statement.setInt(paramIndex++, Integer.parseInt(helicopterIdStr));
            if (!code.isEmpty()) statement.setString(paramIndex++, code);
            if (!goodsWeightStr.isEmpty()) statement.setDouble(paramIndex++, Double.parseDouble(goodsWeightStr));
            if (!passengersStr.isEmpty()) statement.setInt(paramIndex++, Integer.parseInt(passengersStr));
            if (!flightHoursStr.isEmpty()) statement.setDouble(paramIndex++, Double.parseDouble(flightHoursStr));
            if (!priceStr.isEmpty()) statement.setDouble(paramIndex++, Double.parseDouble(priceStr));

            statement.setInt(paramIndex, flightId);
            int affectedRows = statement.executeUpdate();
            if (affectedRows > 0) {
                consoleManager.printMessage("Информация о рейсе ID " + flightId + " успешно обновлена.");
            } else {
                consoleManager.printMessage("Рейс с ID " + flightId + " не найден или данные не изменены.");
            }
        } catch (SQLException e) {
            consoleManager.printMessage("Ошибка при обновлении информации о рейсе: " + e.getMessage());
        } catch (NumberFormatException e) {
            consoleManager.printMessage("Ошибка в формате введенных числовых данных.");
        }
        consoleManager.printMessage("");
    }


    private void deleteFlight() {
        consoleManager.printMessage("Удаление рейса:");
        String flightIdStr = consoleManager.getInput(String.class, "Введите ID рейса для удаления (или /back):", "Неверный ID.", notBack());
        if ("/back".equalsIgnoreCase(flightIdStr)) return;
        int flightId;
        try {
            flightId = Integer.parseInt(flightIdStr);
        } catch (NumberFormatException e) {
            consoleManager.printMessage("Некорректный ID рейса.");
            return;
        }


        String confirmation = consoleManager.getInput(String.class,
                "Вы уверены, что хотите удалить рейс с ID " + flightId + "? (yes/no или /back):",
                "Неверный ввод. Введите 'yes' или 'no'.",
                s -> "yes".equalsIgnoreCase(s) || "no".equalsIgnoreCase(s) || "/back".equalsIgnoreCase(s)
        );

        if ("/back".equalsIgnoreCase(confirmation) || "no".equalsIgnoreCase(confirmation)) {
            consoleManager.printMessage("Удаление отменено.");
            return;
        }

        if ("yes".equalsIgnoreCase(confirmation)) {
            final String sql = "DELETE FROM flight WHERE id = ?;";
            try (Connection connection = connectionHandler.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, flightId);
                int affectedRows = statement.executeUpdate();
                if (affectedRows > 0) {
                    consoleManager.printMessage("Рейс с ID " + flightId + " успешно удален.");
                } else {
                    consoleManager.printMessage("Рейс с ID " + flightId + " не найден.");
                }
            } catch (SQLException e) {
                consoleManager.printMessage("Ошибка при удалении рейса: " + e.getMessage());
            }
        }
        consoleManager.printMessage("");
    }

    private void calculateCrewEarningsForPeriod() {
        consoleManager.printMessage("Расчет заработка экипажей (вертолетов) за период.");

        String startDateStr = consoleManager.getInput(String.class, "Начальная дата периода (YYYY-MM-DD или /back):", "Неверный формат.", dateValidatorNotBack());
        if ("/back".equalsIgnoreCase(startDateStr)) return;

        String endDateStr = consoleManager.getInput(String.class, "Конечная дата периода (YYYY-MM-DD или /back):", "Неверный формат.", dateValidatorNotBack());
        if ("/back".equalsIgnoreCase(endDateStr)) return;


        final String calculateSql = """
                  SELECT helicopter_id, SUM(price) as earnings
                  FROM flight
                  WHERE date BETWEEN ? AND ?
                  GROUP BY helicopter_id;
                """;
        try (Connection connection = connectionHandler.getConnection()) {
            try (PreparedStatement calcStmt = connection.prepareStatement(calculateSql)) {

                calcStmt.setString(1, startDateStr);
                calcStmt.setString(2, endDateStr);
                ResultSet rs = calcStmt.executeQuery();

                if (!rs.isBeforeFirst()) {
                    consoleManager.printMessage("Нет данных о рейсах за указанный период для расчета.");
                    connection.rollback();
                    return;
                }
                consoleManager.printMessage("ID вертолета|Заработок отряда");
                int count = 0;
                while (rs.next()) {
                    int helicopterId = rs.getInt("helicopter_id");
                    double earnings = rs.getDouble("earnings");
                    consoleManager.printMessage(helicopterId + "|" + earnings);
                    count++;
                }
                consoleManager.printMessage("Расчеты по " + count + " экипажам (вертолетам) за период с " + startDateStr + " по " + endDateStr + " сохранены.");

            } catch (SQLException e) {
                connection.rollback();
                consoleManager.printMessage("Ошибка при расчете или сохранении заработка: " + e.getMessage());
            }
        } catch (SQLException e) {
            consoleManager.printMessage("Ошибка соединения с БД: " + e.getMessage());
        }
        consoleManager.printMessage("");
    }

    private void printPilotEarningsForPeriod() {
        consoleManager.printMessage("Расчет заработка указанного летчика за период.");

        String pilotIdStr = consoleManager.getInput(String.class, "Введите ID летчика (или /back):", "Неверный ID.", notBack());
        if ("/back".equalsIgnoreCase(pilotIdStr)) return;
        int pilotId;
        try {
            pilotId = Integer.parseInt(pilotIdStr);
        } catch (NumberFormatException e) {
            consoleManager.printMessage("Некорректный ID летчика.");
            return;
        }

        String startDateStr = consoleManager.getInput(String.class, "Начальная дата периода (YYYY-MM-DD или /back):", "Неверный формат.", dateValidatorNotBack());
        if ("/back".equalsIgnoreCase(startDateStr)) return;

        String endDateStr = consoleManager.getInput(String.class, "Конечная дата периода (YYYY-MM-DD или /back):", "Неверный формат.", dateValidatorNotBack());
        if ("/back".equalsIgnoreCase(endDateStr)) return;

        final String sql = """
                  SELECT SUM(f.price) as total_helicopter_earnings
                  FROM flight f
                  JOIN pilot p ON f.helicopter_id = p.helicopter_id
                  WHERE p.id = ? AND f.date BETWEEN ? AND ?;
                """;
        try (Connection connection = connectionHandler.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, pilotId);
            statement.setString(2, startDateStr);
            statement.setString(3, endDateStr);
            ResultSet rs = statement.executeQuery();

            if (rs.next()) {
                double totalEarnings = rs.getDouble("total_helicopter_earnings");
                if (rs.wasNull()) {
                    consoleManager.printMessage("Для пилота ID " + pilotId + " за период с " + startDateStr + " по " + endDateStr + " не найдено рейсов вертолета, к которому он приписан, или нет данных о заработке.");
                } else {
                    consoleManager.printMessage("Общая сумма, заработанная вертолетом пилота ID " + pilotId +
                            " за период с " + startDateStr + " по " + endDateStr + ": %.2f".formatted(totalEarnings));
                    consoleManager.printMessage("(Это сумма рейсов вертолета. Система не хранит индивидуальные начисления пилотам.)");
                }
            } else {
                consoleManager.printMessage("Нет данных для пилота ID " + pilotId + " за указанный период.");
            }
            consoleManager.printMessage("");
        } catch (SQLException e) {
            consoleManager.printMessage("Ошибка при расчете заработка летчика: " + e.getMessage());
        }
    }

    private void printPilotEarningsForSpecificFlights() {
        consoleManager.printMessage("Расчет заработка летчика за указанные рейсы/тип рейсов за период.");

        String pilotIdStr = consoleManager.getInput(String.class, "Введите ID летчика (или /back):", "Неверный ID.", notBack());
        if ("/back".equalsIgnoreCase(pilotIdStr)) return;
        int pilotId;
        try {
            pilotId = Integer.parseInt(pilotIdStr);
        } catch (NumberFormatException e) {
            consoleManager.printMessage("Некорректный ID летчика.");
            return;
        }

        String startDateStr = consoleManager.getInput(String.class, "Начальная дата периода (YYYY-MM-DD или /back):", "Неверный формат.", dateValidatorNotBack());
        if ("/back".equalsIgnoreCase(startDateStr)) return;

        String endDateStr = consoleManager.getInput(String.class, "Конечная дата периода (YYYY-MM-DD или /back):", "Неверный формат.", dateValidatorNotBack());
        if ("/back".equalsIgnoreCase(endDateStr)) return;

        String flightTypeChoice = consoleManager.getInput(String.class,
                "Указать конкретные ID рейсов (IDs) или тип рейса (type)? (или /back):",
                "Неверный выбор.", s -> "ids".equalsIgnoreCase(s) || "type".equalsIgnoreCase(s) || "/back".equalsIgnoreCase(s));
        if ("/back".equalsIgnoreCase(flightTypeChoice)) return;

        StringBuilder sqlBuilder = new StringBuilder(
                "SELECT SUM(f.price) as total_earnings " +
                        "FROM flight f " +
                        "JOIN pilot p ON f.helicopter_id = p.helicopter_id " +
                        "WHERE p.id = ? AND f.date BETWEEN ? AND ? "
        );

        try (Connection connection = connectionHandler.getConnection()) {
            if ("ids".equalsIgnoreCase(flightTypeChoice)) {
                String flightIdsStr = consoleManager.getInput(String.class, "Введите ID рейсов через запятую (например, 1,2,3 или /back):", "Неверный ввод.", notBack());
                if ("/back".equalsIgnoreCase(flightIdsStr)) return;

                sqlBuilder.append("AND f.id IN (");
                String[] ids = flightIdsStr.split(",");
                for (int i = 0; i < ids.length; i++) {
                    sqlBuilder.append("?");
                    if (i < ids.length - 1) {
                        sqlBuilder.append(",");
                    }
                }
                sqlBuilder.append(");");

                try (PreparedStatement statement = connection.prepareStatement(sqlBuilder.toString())) {
                    statement.setInt(1, pilotId);
                    statement.setString(2, startDateStr);
                    statement.setString(3, endDateStr);
                    int paramIdx = 4;
                    for (String id : ids) {
                        try {
                            statement.setInt(paramIdx++, Integer.parseInt(id.trim()));
                        } catch (NumberFormatException e) {
                            consoleManager.printMessage("Некорректный ID рейса в списке: " + id);
                            return;
                        }
                    }
                    ResultSet rs = statement.executeQuery();
                    if (rs.next()) {
                        double totalEarnings = rs.getDouble("total_earnings");
                        if (rs.wasNull()) {
                            consoleManager.printMessage("Для пилота ID " + pilotId + " по указанным рейсам (" + flightIdsStr + ") за период не найдено данных о заработке.");
                        } else {
                            consoleManager.printMessage("Общая сумма, заработанная вертолетом пилота ID " + pilotId +
                                    " за рейсы (" + flightIdsStr + ") в период: %.2f".formatted(totalEarnings));
                        }
                    } else {
                        consoleManager.printMessage("Нет данных.");
                    }
                }
            } else if ("type".equalsIgnoreCase(flightTypeChoice)) {
                String flightCode = consoleManager.getInput(String.class, "Введите тип рейса (usual/special или /back):", "Неверный тип.", flightCodeValidatorNotBack());
                if ("/back".equalsIgnoreCase(flightCode)) return;

                sqlBuilder.append("AND f.code = ?;");
                try (PreparedStatement statement = connection.prepareStatement(sqlBuilder.toString())) {
                    statement.setInt(1, pilotId);
                    statement.setString(2, startDateStr);
                    statement.setString(3, endDateStr);
                    statement.setString(4, flightCode);
                    ResultSet rs = statement.executeQuery();
                    if (rs.next()) {
                        double totalEarnings = rs.getDouble("total_earnings");
                        if (rs.wasNull()) {
                            consoleManager.printMessage("Для пилота ID " + pilotId + " по рейсам типа '" + flightCode + "' за период не найдено данных о заработке.");
                        } else {
                            consoleManager.printMessage("Общая сумма, заработанная вертолетом пилота ID " + pilotId +
                                    " за рейсы типа '" + flightCode + "' в период: %.2f".formatted(totalEarnings));
                        }
                    } else {
                        consoleManager.printMessage("Нет данных.");
                    }
                }
            }
            consoleManager.printMessage("(Это сумма рейсов вертолета. Система не хранит индивидуальные начисления пилотам.)");
            consoleManager.printMessage("");
        } catch (SQLException e) {
            consoleManager.printMessage("Ошибка при расчете заработка летчика: " + e.getMessage());
        }
    }
}