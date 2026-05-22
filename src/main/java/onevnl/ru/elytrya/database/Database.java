package onevnl.ru.elytrya.database;

import java.util.List;
import java.util.UUID;
import onevnl.ru.elytrya.models.BoostyUser;

public interface Database {
  void connect();
  void disconnect();
  void saveLink(
    UUID uuid,
    String playerName,
    String boostyName,
    String levelName
  );
  String getBoostyName(UUID uuid);
  boolean isBoostyNameLinked(String boostyName);
  List<BoostyUser> getAllUsers();
  void removeLink(UUID uuid);
  void updateLevel(UUID uuid, String levelName);
  int getActiveSubscribersCount();
  BoostyUser getUser(UUID uuid);

  //для дискорд бота
  String getDiscordUser(UUID uuid);
  void setDiscordUser(UUID uuid, String discordUser);
}
