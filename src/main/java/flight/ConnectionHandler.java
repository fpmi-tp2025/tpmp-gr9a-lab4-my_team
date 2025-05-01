package flight;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

@NoArgsConstructor
public class ConnectionHandler {
    private static final String PROPERTIES_NAME = "application.properties";
    private static final Properties properties = new Properties();
    private static final HikariConfig config = new HikariConfig();
    private static final HikariDataSource dataSource;

    static {
        try {
            properties.load(ConnectionHandler.class.getClassLoader().getResourceAsStream(PROPERTIES_NAME));
            config.setJdbcUrl(properties.getProperty("db.url"));
            config.setMaximumPoolSize(Integer.parseInt(properties.getProperty("db.pool_size")));
            config.setConnectionTimeout(Long.parseLong(properties.getProperty("db.time_out")));
            dataSource = new HikariDataSource(config);
        } catch (IOException e) {
            throw new RuntimeException("Can't load properties", e);
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
