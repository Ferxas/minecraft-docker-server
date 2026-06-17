# ArcadeGames — configuración manual requerida
# Documentación: https://www.spigotmc.org/resources/arcade-minigames.49093/

## Lo que debes hacer tú

1. **Crear un mundo** para cada minijuego arcade (o reutilizar uno con varias arenas).
   ```
   /mv create ArcadeLobby normal
   /mv create ArcadeGame1 normal
   ```

2. **Entrar al mundo** y usar los comandos de setup del plugin (como OP).
   - Consulta `/start help` o la wiki del plugin para crear arenas.
   - Cada minijuego necesita su propia arena configurada.

3. **Multiverse** — acceso para jugadores:
   ```
   lp group default permission set multiverse.access.ArcadeLobby true
   lp group default permission set multiverse.teleport.self.w.ArcadeLobby true
   ```

4. **Probar**: `/start` desde el menú. Si no hay arenas, no teletransportará a nadie.

## Comandos jugadores

| Comando | Acción |
|---------|--------|
| `/start` | Unirse a partida disponible |
| `/rejoin` | Volver si te desconectaste |
| `/leave` | Salir de la partida |
