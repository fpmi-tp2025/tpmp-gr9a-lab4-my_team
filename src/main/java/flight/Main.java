package flight;

import java.util.Scanner;

public class Main {
    private static final ConsoleManager consoleManager;
    private static final ConnectionHandler connectionHandler;
    private static final AuthManager authManager;

    static {
        consoleManager = new ConsoleManager(System.out, new Scanner(System.in));
        connectionHandler = new ConnectionHandler();
        authManager = new AuthManager(consoleManager, connectionHandler);
    }

    public static void main(String[] args) {
        printWelcomeMessage();
        User auth = authManager.getAuth();
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
