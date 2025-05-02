package flight;

import lombok.AllArgsConstructor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@AllArgsConstructor
public class AuthManager {
    private final ConsoleManager consoleManager;
    private final ConnectionHandler connectionHandler;

    public User getAuth() {
        while (true) {
            String login = getLogin();
            String password = getPassword(login);
            if(password != null) {
                return getUser(login, password);
            }
        }
    }

    private String getLogin() {
        while(true) {
            String loginInput = consoleManager.getInput(
                    String.class,
                    "Input login:",
                    "Unknown login!",
                    s -> loginExists(s) || s.equals("/end") || s.equals("/help")
            );

            if(loginInput.equals("/end")){
                System.exit(0);
            }

            if(loginInput.equals("/help")){
                final String help = """
                           Available commands:
                            - /end - end program
                            - /help - check commands
                           """ ;

                consoleManager.printMessage(help);
                continue;
            }

            return loginInput;
        }
    }

    private String getPassword(String login) {
        while(true) {
            String passwordInput = consoleManager.getInput(
                    String.class, "Input password:",
                    "Wrong password",
                    s -> correctPassword(login, s) || s.equals("/back") || s.equals("/end") || s.equals("/help")
            );

            switch (passwordInput) {
                case "/end" -> System.exit(0);
                case "/back" -> {
                    return null;
                }
                case "/help" -> {
                    final String help = """
                            Available commands:
                             - /back - back to login
                             - /end - end program
                             - /help - check available commands
                            """;

                    consoleManager.printMessage(help);
                    continue;
                }
            }

            return passwordInput;
        }
    }

    private boolean loginExists(String login) {
        final String sql = "select id from auth where login = ?;";

        try (Connection connection = connectionHandler.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, login);
            ResultSet resultSet = statement.executeQuery();
            return resultSet.next();
        } catch (SQLException ex) {
            throw new RuntimeException("Error with db");
        }
    }

    private boolean correctPassword(String login, String password) {
        final String sql = "select id from auth where login = ? and password = ?;";

        try (Connection connection = connectionHandler.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, login);
            statement.setString(2, password);
            ResultSet resultSet = statement.executeQuery();
            return resultSet.next();
        } catch (SQLException ex) {
            throw new RuntimeException("Error with db");
        }
    }

    private User getUser(String login, String password) {
        final String sql = """
                select a.role, a.pilot_id, p.helicopter_id
                from auth a 
                left join pilot p on p.id = a.pilot_id
                where a.login = ? and a.password = ?;
                """;
        try (Connection connection = connectionHandler.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, login);
            statement.setString(2, password);
            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                String role = rs.getString(1);
                int pilotId = rs.getInt(2);
                int helicopterId = rs.getInt(3);
                consoleManager.printMessage("Successful sign in!");
                return new User(UserRole.valueOf(role.toUpperCase()), pilotId, helicopterId);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error with db");
        }
        return null;
    }
}