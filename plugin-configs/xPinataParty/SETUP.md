# Piñata Party — mapa de eventos

## Configuración actual

- **Auto-spawn desactivado** — no aparece sola en ningún mapa.
- **Ubicación reservada:** `EventWorld,0,100,0` (placeholder).
- **NO** está en SakuraSpawn.

## Cuando quieras usarlo

1. Crea el mundo de eventos:
   ```
   /mv create EventWorld normal
   /mv modify set spawn true EventWorld
   ```

2. Edita `plugins/xPinataParty/config.yml`:
   - `spawn-location: "EventWorld,X,Y,Z"` con coordenadas reales.

3. Para iniciar manualmente (comando admin del plugin — revisa `/pinata help` o wiki Spigot 59318).

4. Opcional: activar `auto-pinata.enabled: true` solo cuando el mapa esté listo.

5. Permisos Multiverse para jugadores en eventos:
   ```
   lp group default permission set multiverse.access.EventWorld true
   ```
