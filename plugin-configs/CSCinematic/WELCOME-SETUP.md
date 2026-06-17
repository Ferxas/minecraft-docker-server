# Cinemática welcome — OBLIGATORIO crear in-game una vez

La carpeta `cinematics/` está vacía hasta que la grabes. Sin esto, el menú y el Skript no harán nada visible.

## Pasos (OP en SakuraSpawn)

```
/cinematic create welcome
/cinematic edit welcome
```

Vuela a cada punto y añade posiciones:

```
/cinematic pos add 2.0 3.0
/cinematic text 1 &6&lConlospibes
/cinematic text 2 &d&lSakura Spawn
/cinematic text 3 &a&l¡A jugar!
/cinematic save
/cinematic load welcome
```

Probar: `/cinematic play welcome`

**No uses `/cs`** — FAWE intercepta ese comando.

## Automático

El Skript `welcome-cinematic.sk` reproduce la intro **en cada join** si estás en SakuraSpawn (espera 5 s).

El menú (`/menu` → Utilidades → Intro) usa consola: `cinematic play welcome <jugador>`.
