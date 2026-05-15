# skyblock

JPA entities + static locator for the SkyBlock game-data set that sibling services
(`simplified-data`, `simplified-bot`, `simplified-server`) consume. Extracted from
the `minecraft-api` module so every downstream caller can depend on the data layer
without dragging in Hypixel / Mojang / SBS clients.

## Packages

- `dev.sbs.skyblockdata.model` - 43 JPA entities (Item, Pet, Rarity columns, etc.)
- `dev.sbs.skyblockdata.common` - `Rarity`, `GameStage`, `GameMode`, `Profile`
- `dev.sbs.skyblockdata.date` - `SkyBlockDate`, `Season`, `Election`, `SpecialElection`
- `dev.sbs.skyblockdata.crafting` - `CraftingRecipe`, `CraftingTable`
- Top level - `SkyBlockData` (static locator), `SkyBlockFactory`, `SkinTexture`

## Usage

```java
// at startup, once
SkyBlockData.connect(GsonSettings.defaults());

// anywhere thereafter
Repository<Item> items = SkyBlockData.getRepository(Item.class);
Item diamond = items.findFirst(Item::getId, "DIAMOND").orElseThrow();
```

`GsonSettings.defaults()` auto-discovers this module's `GsonContributor` via
ServiceLoader, which registers the `SkyBlockDate.RealTime.Adapter`,
`SkyBlockDate.SkyBlockTime.Adapter`, and `JpaExclusionStrategy` needed for
Hibernate to round-trip the Gson-stored columns.

## Building

```bash
./gradlew build
```

JSON resources that drive the embedded H2 database live under
`src/main/resources/skyblock/` and are loaded by `SkyBlockFactory` via its
`Source.json("skyblock")` registration.
