package onevnl.ru.elytrya.database;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class MySQL extends AbstractDatabase {

  public MySQL(JavaPlugin plugin) {
    super(plugin);
  }

  @Override
  public void connect() {
    try {
      FileConfiguration config = plugin.getConfig();
      String host = config.getString("database.mysql.host");
      int port = config.getInt("database.mysql.port");
      String database = config.getString("database.mysql.database");
      String username = config.getString("database.mysql.username");
      String password = config.getString("database.mysql.password");
      String url =
        "jdbc:mysql://" +
        host +
        ":" +
        port +
        "/" +
        database +
        "?autoReconnect=true&useSSL=false";
      connection = DriverManager.getConnection(url, username, password);
      createTable();
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void saveLink(
    UUID uuid,
    String playerName,
    String boostyName,
    String levelName
  ) {
    String sql =
      "INSERT INTO boosty_links (uuid, player_name, boosty_name, level_name) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE player_name = ?, boosty_name = ?, level_name = ?";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, uuid.toString());
      statement.setString(2, playerName);
      statement.setString(3, boostyName);
      statement.setString(4, levelName);
      statement.setString(5, playerName);
      statement.setString(6, boostyName);
      statement.setString(7, levelName);
      statement.executeUpdate();
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }
}
