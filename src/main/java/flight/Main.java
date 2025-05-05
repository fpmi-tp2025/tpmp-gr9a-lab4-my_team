package flight;

import java.util.Scanner;

public class Main {
    private static final ConsoleManager consoleManager;
    private static final ConnectionHandler connectionHandler;
    private static final AuthManager authManager;
    private static final UserStrategy adminStrategy;
    private static final UserStrategy pilotStrategy;

    static {
        connectionHandler = new ConnectionHandler();
        consoleManager = new ConsoleManager(System.out, new Scanner(System.in));
        authManager = new AuthManager(consoleManager, connectionHandler);
        adminStrategy = new AdminStrategy(consoleManager, connectionHandler);
        pilotStrategy = new PilotStrategy(consoleManager, connectionHandler);
    }

    public static void main(String[] args) {
        printWelcomeMessage();
        User auth = authManager.getAuth();
        UserStrategy curStrategy = (auth.role() == UserRole.PILOT) ? pilotStrategy : adminStrategy;
        curStrategy.apply(auth);
    }

    private static void printWelcomeMessage() {
        final String welcome = """
                   / ___/ / /      ()  /  ____\\  / /  /_   __/
                  / /__  / /     /--/ /  /__    / /__   / /
                 /  __/ / /___  /  / /  /__/ | / /-/ / / /
                / /    /_____/ /__/ /_______/ /_/ /_/ /_/
                """;

        consoleManager.printMessage(welcome);
    }
}
