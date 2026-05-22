package onevnl.ru.elytrya.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import onevnl.ru.elytrya.models.BoostyUser;
import org.bukkit.plugin.java.JavaPlugin;

public abstract class AbstractDatabase implements Database {

  protected final JavaPlugin plugin;
  protected Connection connection;

  protected AbstractDatabase(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public void disconnect() {
    try {
      if (connection != null && !connection.isClosed()) connection.close();
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  protected void createTable() {
    String sql =
      "CREATE TABLE IF NOT EXISTS boosty_links (uuid VARCHAR(36) PRIMARY KEY, player_name VARCHAR(16), boosty_name VARCHAR(255), level_name VARCHAR(255))";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.execute();
    } catch (SQLException e) {
      e.printStackTrace();
    }

    //если колонки discord_user нет
    try {
      String alterSql =
        "ALTER TABLE boosty_links ADD COLUMN discord_user VARCHAR(255) DEFAULT NULL";
      try (
        PreparedStatement statement = connection.prepareStatement(alterSql)
      ) {
        statement.execute();
      }
    } catch (SQLException ignored) {}
  }

  @Override
  public BoostyUser getUser(UUID uuid) {
    String sql = "SELECT * FROM boosty_links WHERE uuid = ?";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, uuid.toString());
      try (ResultSet rs = statement.executeQuery()) {
        if (rs.next()) return mapUser(rs);
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public String getBoostyName(UUID uuid) {
    BoostyUser user = getUser(uuid);
    return user != null ? user.boostyName() : null;
  }

  @Override
  public boolean isBoostyNameLinked(String boostyName) {
    String sql = "SELECT 1 FROM boosty_links WHERE LOWER(boosty_name) = ?";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, boostyName.toLowerCase());
      try (ResultSet rs = statement.executeQuery()) {
        return rs.next();
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return false;
  }

  @Override
  public List<BoostyUser> getAllUsers() {
    List<BoostyUser> list = new ArrayList<>();
    String sql = "SELECT * FROM boosty_links";
    try (
      PreparedStatement statement = connection.prepareStatement(sql);
      ResultSet rs = statement.executeQuery()
    ) {
      while (rs.next()) list.add(mapUser(rs));
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return list;
  }

  @Override
  public void removeLink(UUID uuid) {
    String sql = "DELETE FROM boosty_links WHERE uuid = ?";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, uuid.toString());
      statement.executeUpdate();
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void updateLevel(UUID uuid, String levelName) {
    String sql = "UPDATE boosty_links SET level_name = ? WHERE uuid = ?";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, levelName);
      statement.setString(2, uuid.toString());
      statement.executeUpdate();
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  @Override
  public int getActiveSubscribersCount() {
    String sql = "SELECT COUNT(*) FROM boosty_links WHERE level_name != 'none'";
    try (
      PreparedStatement statement = connection.prepareStatement(sql);
      ResultSet rs = statement.executeQuery()
    ) {
      if (rs.next()) return rs.getInt(1);
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return 0;
  }

  @Override
  public String getDiscordUser(UUID uuid) {
    String sql = "SELECT discord_user FROM boosty_links WHERE uuid = ?";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, uuid.toString());
      try (ResultSet rs = statement.executeQuery()) {
        if (rs.next()) return rs.getString("discord_user");
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public void setDiscordUser(UUID uuid, String discordUser) {
    String checkSql = "SELECT 1 FROM boosty_links WHERE uuid = ?";
    boolean exists = false;
    try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
      checkStmt.setString(1, uuid.toString());
      try (ResultSet rs = checkStmt.executeQuery()) {
        if (rs.next()) exists = true;
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }

    if (exists) {
      String sql = "UPDATE boosty_links SET discord_user = ? WHERE uuid = ?";
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, discordUser);
        statement.setString(2, uuid.toString());
        statement.executeUpdate();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    } else {
      org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(uuid);
      String name = op.getName() != null ? op.getName() : "Unknown";
      String sql =
        "INSERT INTO boosty_links (uuid, player_name, boosty_name, level_name, discord_user) VALUES (?, ?, 'none', 'none', ?)";
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, uuid.toString());
        statement.setString(2, name);
        statement.setString(3, discordUser);
        statement.executeUpdate();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
  }

  protected BoostyUser mapUser(ResultSet rs) throws SQLException {
    return new BoostyUser(
      UUID.fromString(rs.getString("uuid")),
      rs.getString("player_name"),
      rs.getString("boosty_name"),
      rs.getString("level_name")
    );
  }
}
