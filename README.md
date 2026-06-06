# One Topic to Rule 'Em All

This repository is the companion demo for the talk *"One Topic to Rule 'Em All - taming domain-event order with Kafka & Avro"* (Kotlin User Group Berlin). It shows, in running code, how splitting domain events across one-topic-per-event-type silently breaks ordering between events that belong to the same aggregate - even when every message is delivered exactly once. The fix is deliberate and minimal: one topic per aggregate, every message keyed by aggregate id, multiple Avro schemas on the same topic via `TopicRecordNameStrategy`.

**Takeaway:** Order within the aggregate → one topic per aggregate, key = aggregate id, multiple schemas via `TopicRecordNameStrategy`.

---

## Quickstart

**Prerequisites:** Docker (Compose v2), JDK 21+

### Act 1 - watch it break

```bash
# Terminal 1: full reset (tears down any existing stack, starts fresh, registers schemas)
make reset           # ~60-90 s including image pulls on first run

# Terminal 1: boot the broken topology
make run-broken

# Terminal 2 (new terminal, once the app is running):
make scenario
```

Watch Terminal 1: a 💥 rejection fires almost instantly (and scrolls away under the
flood), and when the flood finally drains, the last line is the proof:

```
💥 LEVEL-UP FOR UNKNOWN ADVENTURER <id> - the ledger has never heard of them!
...500 📜 registrations later...
📜 Bob the Brave the Fighter joined the guild (level 1)
💥 Bob the Brave finally registered - but their level-up arrived FIRST and was lost. That's the bug.
```

The hero's level-up arrives before his own registration because the registration
consumer is drowning in the backlog of 500 flood registrations (each takes 5 ms of
simulated work), while the level-up topic is empty and consumed instantly.

Also note `🤷` lines - the quest and death events have no topic in this topology;
the producer drops them with a shrug.

### Act 2 - watch it work

```bash
# Terminal 1: stop the app (Ctrl-C), then:
make run-fixed

# Terminal 2: same scenario, new hero
make scenario
```

Watch Terminal 1 for the hero's ordered lifecycle - no 💥, ever:

```
📜 Bob the Brave the Fighter joined the guild (level 1)
⬆️ Bob the Brave reached level 2
⚔️ Bob the Brave accepted quest: Slay the Rat King of Köpenick
💀 Bob the Brave has died (rocks fell)
```

And the tavern's independent consumer group gossiping:

```
🍺 Hear ye! <id-prefix>... is now level 2!
🍺 A toast! <id-prefix>... rocks fell. F.
```

---

## What just happened

**Act 1 - the race.** When events live on separate topics, Kafka's ordering guarantee
(messages with the same key land in the same partition, in order) applies *per topic*,
not across topics. The registration consumer has real work to do per message - it
simulates enrichment and a DB write - so it accumulates a backlog. The level-up topic
is nearly empty, so that consumer races ahead. By the time the hero's level-up arrives
at the ledger, his registration has not yet been processed. The ledger has never heard
of him. Red flag: `💥 LEVEL-UP FOR UNKNOWN ADVENTURER`.

**Act 2 - the fix.** All four adventurer events land on the single `guild.adventurers`
topic, keyed by `adventurerId`. Kafka guarantees that all messages with the same key
land in the same partition, in the order they were produced. The registration consumer
still does the same 5 ms of work per message - same load, same latency - but now the
level-up is behind it in the same partition queue and cannot overtake it. The `@KafkaHandler`
dispatch in `GuildLedgerListener` routes each Avro type to the right method. The
`TavernNoticeBoard` consumes the same topic under its own consumer group (`tavern-notice-board`),
demonstrating that one-topic-per-aggregate does not couple consumers to one another.

---

## The one line

In `app/src/main/resources/application-fixed.yml`:

```yaml
spring:
  kafka:
    producer:
      properties:
        # The one line this talk is about:
        value.subject.name.strategy: io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
```

Without this, the Confluent serializer defaults to `TopicNameStrategy` and expects a
single schema per topic - it cannot handle multiple Avro types on `guild.adventurers`.
`TopicRecordNameStrategy` uses `<topic>-<record FQN>` as the subject, so each schema
has its own slot in the registry and the serializer can find the right one at runtime.

---

## Repo tour

| Path | What it is |
|---|---|
| `schemas/src/main/avro/` | Avro IDL (`.avdl`) for all four events |
| `schemas/build.gradle.kts` | Gradle tasks `registerAllSchemas` / `unregisterAllSchemas` - parse `.avdl` files at execution time via `IdlReader`, talk to the registry with plain JDK `HttpClient` (no Confluent SDK) |
| `app/src/main/kotlin/org/guild/app/broken/` | Act 1: `BrokenTopologyListeners` (two `@KafkaListener`s) |
| `app/src/main/kotlin/org/guild/app/fixed/` | Act 2: `GuildLedgerListener` (`@KafkaHandler` dispatch), `TavernNoticeBoard` (second consumer group), and `GuildLedgerWhenListener` (the `when`-dispatch alternative - try profile `fixed-when`) |
| `app/src/main/kotlin/org/guild/app/ledger/` | `GuildLedgerState` - profile-agnostic in-memory state shared by both acts |
| `app/src/main/kotlin/org/guild/app/producer/` | `ScenarioRunner`, `ScenarioController` (`POST /scenario/run`), `EventRouter` |
| `app/src/main/resources/` | `application.yml` (shared config, flood-size=500, latency-ms=5), `application-broken.yml`, `application-fixed.yml` |
| `Makefile` | Full control surface - run `make help` for all targets |
| `docker-compose.yml` | Kafka (KRaft, port 9092), Schema Registry (port 8081), Kafka UI (port 8090) |

---

## Schema subject naming

The Schema Registry holds subjects under three naming strategies. This repo uses two of them:

| Strategy | Subject pattern | Example subjects in this repo |
|---|---|---|
| `TopicNameStrategy` (Act 1 default) | `<topic>-value` | `guild.adventurer_registered-value`, `guild.adventurer_leveled_up-value` |
| `TopicRecordNameStrategy` (Act 2 fix) | `<topic>-<record FQN>` | `guild.adventurers-org.guild.adventurer.AdventurerRegistered`, `guild.adventurers-org.guild.adventurer.AdventurerLeveledUp`, `guild.adventurers-org.guild.adventurer.QuestAccepted`, `guild.adventurers-org.guild.adventurer.AdventurerDied` |
| `RecordNameStrategy` | `<record FQN>` | _(not used in this demo)_ |

All six subjects are registered by `make register` (which runs `./gradlew :schemas:registerAllSchemas`).

---

## Makefile reference

```
make help          # Print all targets with descriptions
make reset         # Full rehearsal reset: tear down, start fresh, register schemas
make infra-up      # Start Docker stack and wait for Schema Registry readiness
make infra-down    # Tear down Docker stack (removes volumes)
make register      # Register all Avro schemas with Schema Registry
make unregister    # Unregister all Avro schemas from Schema Registry
make build         # Compile and assemble the project (skip tests)
make test          # Run the full test suite
make run-broken    # Boot the app with the 'broken' Spring profile
make run-fixed     # Boot the app with the 'fixed' Spring profile
make scenario      # POST /scenario/run and print JSON response (heroId)
```

---

## Links

- This repo: <https://github.com/yonatankarp/one-topic-to-rule-em-all>
- Confluent subject-naming strategy docs: <https://docs.confluent.io/platform/current/schema-registry/fundamentals/serdes-develop/index.html#subject-name-strategy>
- Slides: [`slides/one-topic-to-rule-em-all.pdf`](slides/one-topic-to-rule-em-all.pdf)
- Kafka UI (local, while stack is running): <http://localhost:8090>
