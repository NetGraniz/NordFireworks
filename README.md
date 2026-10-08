# NordFireworks

Limits how often a player can use firework rockets on Paper 26.2 or Folia 26.2. One Java 25 JAR supports both platforms.

A rocket launch or elytra boost starts the vanilla item cooldown indicator. The default is 40 ticks—2 seconds at 20 TPS. The cooldown belongs to the player, not a hotbar slot, stack or hand.

## Installation

Download the JAR from [Releases](https://github.com/NetGraniz/NordFireworks/releases). Stop the server, install one copy in `plugins` and start it to create `plugins/NordFireworks/config.yml`.

## Configuration

```yaml
cooldown-ticks: 40
```

Accepted values are integers from 0 to 12000. A value of 0 disables new cooldowns; cooldowns already active expire normally. An invalid reload keeps the previous value.

Run `/nordfireworks reload` after editing the file.

## Permissions

| Permission | Allows | Default |
| --- | --- | --- |
| `nordfireworks.admin` | `/nordfireworks reload` | Operators |
| `nordfireworks.bypass` | Use rockets without NordFireworks adding a new cooldown | Nobody, including operators |

Bypass does not clear an already active vanilla cooldown.

## Behavior and cost

The plugin handles rocket launches and boosts, not movement ticks. It applies a cooldown only to accepted events; events already cancelled by another plugin do not start one.

The normal client displays the built-in server cooldown without a mod. Custom item cooldown groups are respected, and a longer existing cooldown is not shortened.

There are no background jobs, database or network requests, UUID maps, player scans or chunk scans. Player work stays on the owning region.

Cooldowns count ticks, so low TPS lengthens the real-time wait. Their state follows Minecraft's player lifecycle; NordFireworks does not persist it across reconnects or server restarts.

## Limits

Dispenser and crossbow rockets are not limited. Direct entity spawning by another plugin is outside this cooldown. The plugin does not cap elytra speed, chunk loading or generation, and does not remove other lag machines.

The listener runs at MONITOR for accepted events. If another plugin cancels an event later, a cooldown may already have been applied.

## Build and tests

Use Maven 3.9+ and JDK 25:

```text
mvn -B -ntp clean verify
```

The output is `target/NordFireworks-1.0.0.jar`. Tests cover configuration and event handling. Integration helpers in `test-support` use synthetic Paper/Folia fixtures, not production worlds or credentials.

## API references

- [Folia support](https://docs.papermc.io/paper/dev/folia-support/)
- [HumanEntity cooldown API](https://jd.papermc.io/paper/26.2/org/bukkit/entity/HumanEntity.html)
- [FireworkRocketItem event hooks](https://github.com/PaperMC/Paper/blob/ver/26.2/paper-server/patches/sources/net/minecraft/world/item/FireworkRocketItem.java.patch)
