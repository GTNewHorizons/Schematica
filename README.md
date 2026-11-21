## Welcome to Schematica GTNH!

Changes from Original:
- Store Coordinates & rotation of schematics per world/server. No more re-entering coordinates for large builds!
- Fix heavy lag when having lotr armor stands/weapon racks in loaded schematic
- Updated Chinese translation

### Schematic compatibility

- Schematica reads `.schematic` and `.schemplus` files automatically. New saves use classic block ID encoding up to 4095 and support IDs up to 24 bits when needed. Older readers may truncate wide IDs or extended metadata.
- Schematic downloads require matching download protocol versions on the client and server.
