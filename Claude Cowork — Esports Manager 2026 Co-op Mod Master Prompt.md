# ROLE — LEAD MULTIPLAYER / REVERSE-ENGINEERING ENGINEER

You are the lead engineer responsible for designing and implementing a REAL, PLAYABLE multiplayer/co-op mod for:

**Game:** Esports Manager 2026  
**Target game version:** 1.1.3.1  
**Target platform:** Windows / Steam  
**Engine:** Unity / IL2CPP  
**Known community modding stack:** EM2026 ModKit + BepInEx 6.0  
**Network environment:** Radmin VPN / LAN-style direct IP connection  
**Target players:** 2–4 human managers

Project codename:

# ESM26 CO-OP

The objective is NOT to create a fake multiplayer overlay.

The objective is to transform the single-player career into a Football Manager-style shared multiplayer career in which 2–4 human players inhabit ONE authoritative game world.

Each human manager can control a different esports organization.

All players must see the same:

- date/time
- tournaments
- fixtures
- results
- standings
- players
- teams
- contracts
- transfers
- finances where globally relevant
- player development
- injuries/conditions
- morale
- staff changes
- rankings
- tournament progression
- match results
- generated players
- retirements
- AI decisions
- world events

There must NEVER be four independent simulations pretending to be multiplayer.

There must be ONE WORLD.

The host owns and simulates that world.

Clients interact with it.

---

# 0. CRITICAL WORKING RULE

Do not start by blindly writing networking code.

FIRST reverse-engineer the actual game version installed in this project/environment.

I want implementation based on the REAL classes, methods, save structures and game systems.

Do NOT invent classes such as:

`TransferManager`
`CareerController`
`MatchEngine`

unless those classes actually exist.

Whenever this prompt uses conceptual names such as "TransferService", treat them as descriptions of a system, NOT assumptions about the game's actual class names.

Search the real assemblies / IL2CPP metadata / generated interop assemblies / ModKit APIs and locate the actual implementation.

Document every important discovery.

---

# 1. PRE-FLIGHT / ENVIRONMENT INSPECTION

Before implementing anything, inspect the available project and game installation.

Find:

- Esports Manager 2026 installation
- EsportsManager.exe
- GameAssembly.dll
- global-metadata.dat
- BepInEx
- BepInEx plugins
- BepInEx config
- EM2026 ModKit
- generated IL2CPP interop assemblies
- Assembly-CSharp or equivalents
- existing mods
- config files
- save files
- logs

Determine exactly which modding environment is already installed.

Verify target version:

**1.1.3.1**

Do not silently develop against another version.

Create:

`docs/ENVIRONMENT.md`

Record:

- detected game version
- Unity version if detectable
- BepInEx version
- ModKit version
- important DLLs
- important namespaces
- save location
- log location
- plugin directory

---

# 2. REVERSE ENGINEERING PHASE

Before implementing multiplayer, map the game's architecture.

Create:

`docs/GAME_ARCHITECTURE.md`

Find the real classes/methods responsible for at least:

## World / Career

- current career
- current date
- advancing time
- daily tick
- weekly tick
- calendar
- save/load
- database
- generated entities
- game state initialization

## Teams

Find representation for:

- team ID
- team name
- roster
- staff
- finances
- reputation/ranking
- contracts
- organization data
- controlled team

Determine how the game identifies "the player's team."

This is CRITICAL because vanilla assumes one human-controlled organization.

We need to support multiple human organizations.

---

# 3. HUMAN MANAGER CONTEXT

One of the hardest problems is likely the game's assumption:

`one save = one player team`

We need a multiplayer abstraction.

Conceptually create:

`HumanManagerContext`

Each connection must have:

- NetworkPlayerId
- DisplayName
- AssignedTeamId
- ConnectionState
- ReadyState
- CurrentScreen/context if necessary
- Permissions
- MatchParticipationState

Example:

Host:
Manager 1 → NAVI

Client:
Manager 2 → Vitality

Client:
Manager 3 → G2

Client:
Manager 4 → Spirit

But do NOT permanently rewrite the global player-team field whenever a client performs an action.

Investigate how UI and gameplay queries determine:

"my team"

and implement the safest contextual override.

When processing an action from Client 2, relevant gameplay logic should execute using Client 2's assigned team context.

When processing Client 3, use Client 3's team.

Host remains authoritative.

Document the implementation in:

`docs/MULTI_MANAGER_CONTEXT.md`

---

# 4. NETWORK ARCHITECTURE

Use:

# HOST-AUTHORITATIVE CLIENT/SERVER

The host runs the canonical simulation.

Clients DO NOT independently decide authoritative game outcomes.

Host responsibilities:

- canonical save
- world simulation
- date progression
- RNG-sensitive authoritative operations
- AI
- tournament simulation
- match simulation
- transfers
- contract resolution
- finances
- player development
- rankings
- world events

Clients:

- display replicated state
- submit commands
- control their assigned organization
- receive authoritative results

Basic topology:

CLIENT 1 ─┐
CLIENT 2 ─┼──> HOST ───> AUTHORITATIVE WORLD
CLIENT 3 ─┘

Host may also be Manager 1.

---

# 5. RADMIN VPN CONNECTION MODEL

No dedicated server is required.

Use direct IP networking over Radmin VPN.

Default configurable port:

`27026`

Host flow:

Multiplayer
→ Host Game
→ select/load career
→ port
→ lobby
→ wait for players

Client flow:

Multiplayer
→ Join Game
→ Host Radmin IP
→ Port
→ Connect

Example:

`26.x.x.x:27026`

Do not depend on Steam matchmaking.

Radmin already creates the virtual LAN.

Networking should therefore work over normal IPv4 TCP/UDP sockets.

Prefer reliability and correctness over extreme optimization.

Management-game commands should normally use reliable ordered delivery.

---

# 6. PROTOCOL

Create a versioned protocol.

Every packet/message should conceptually contain:

- protocol version
- session ID
- sender
- sequence number
- message type
- payload
- optional checksum
- timestamp/tick where useful

Implement protocol compatibility checking.

Example:

`ProtocolVersion = 1`

Handshake must verify:

- game version
- mod version
- protocol version
- database compatibility
- important gameplay mods
- save/session identity

Reject incompatible clients with a readable reason.

Never allow mismatched clients to silently join.

---

# 7. LOBBY

Create a native-feeling multiplayer lobby.

Required:

- host indicator
- connected players
- ping
- player names
- selected organization
- ready status
- connection status

Maximum:

**4 players**

Minimum:

**2 players**

Team selection rules:

- one human manager per organization
- two humans cannot accidentally select the same organization
- host can lock selections
- duplicate selection must be rejected server-side

Buttons:

HOST GAME  
JOIN GAME  
READY  
START  
LEAVE

Host starts only when all required players are ready.

---

# 8. SAVE ARCHITECTURE

There is ONE canonical career save.

Host owns it.

Clients should NOT maintain independent authoritative career saves.

Create multiplayer metadata associated with the save.

Example conceptual structure:

`MultiplayerSessionMetadata`

- SessionId
- SaveFingerprint
- HostManager
- HumanManagers[]
- TeamAssignments
- ProtocolVersion
- ModVersion
- LastAuthoritativeTick
- RNG/session metadata if required

If possible, avoid destructive changes to the vanilla save format.

Prefer sidecar metadata if safer.

Example:

`CareerName.emdb`
`CareerName.esmcoop`

Investigate the real save extension and architecture before deciding.

---

# 9. INITIAL WORLD SYNCHRONIZATION

When a client joins, it must receive the host's authoritative world.

Do NOT assume simply copying the save file is sufficient while the game is running.

Implement a safe snapshot system.

Possible process:

Client connects

→ handshake

→ compatibility validation

→ host creates stable snapshot

→ snapshot metadata sent

→ world state transferred

→ client reconstructs/applies state

→ state hash checked

→ client enters lobby/game

Use:

- chunking
- compression if useful
- transfer progress
- checksum
- retry
- timeout

Never leave the client in a half-loaded career.

---

# 10. COMMAND MODEL

Clients must not directly mutate authoritative state.

Use commands.

Examples:

`SubmitTransferOffer`
`WithdrawTransferOffer`
`OfferContract`
`RenewContract`
`ReleasePlayer`
`HireStaff`
`FireStaff`
`ChangeLineup`
`ChangeTraining`
`ChangeStrategy`
`RegisterTournamentRoster`
`AcceptInvitation`
`DeclineInvitation`
`AdvanceReady`
`SubmitMatchDecision`

Flow:

CLIENT
→ COMMAND
→ HOST VALIDATES
→ HOST EXECUTES REAL GAME LOGIC
→ HOST PRODUCES STATE CHANGE
→ STATE CHANGE BROADCAST
→ ALL CLIENTS APPLY

Do not duplicate game rules unnecessarily.

Whenever possible, call the game's REAL logic on the host.

---

# 11. TRANSFERS — EXTREMELY IMPORTANT

Transfers must behave like Football Manager multiplayer.

Example:

Manager A wants Player X.

Manager B also wants Player X.

Both may submit offers.

The system must NOT use:

"first network packet wins"

unless vanilla game logic genuinely resolves it that way.

Both offers must exist in the authoritative world.

The target player/club evaluates offers using the game's normal logic.

Possible outcome:

Player X chooses Manager B.

Manager A receives rejection/lost-target information.

Everyone sees the resulting transfer.

Synchronize:

- transfer listing
- interest
- bids
- counteroffers
- negotiation
- club acceptance
- player negotiations
- salary
- signing bonus
- contract length
- clauses if present
- rejection
- acceptance
- competing bids
- transfer completion
- roster movement
- budgets

Concurrency matters.

If two humans negotiate with the same player simultaneously, both negotiations must remain valid independent processes until the game resolves them.

Create authoritative transaction IDs.

Example:

`TransferNegotiationId`

Never identify a negotiation only by UI screen.

---

# 12. CONTRACTS

Synchronize every relevant contract field.

Examples:

- salary
- expiration
- start date
- duration
- clauses
- bonuses
- role
- contract status
- renewal
- termination

Use actual game fields discovered during reverse engineering.

If a player signs for another human-controlled organization while I am negotiating with him, my UI must immediately reflect the authoritative outcome.

No ghost negotiations.

---

# 13. FINANCES

Each manager controls their own organization's finances.

Host maintains canonical values.

Synchronize all relevant financial state:

- cash
- transfer budget
- salary/wage budget
- operating budget
- marketing budget
- sponsorship income
- tournament winnings
- salaries
- transfer expenditure
- transfer income
- other vanilla financial categories

Never trust a client-supplied resulting balance.

Client sends:

"attempt this purchase/signing"

Host checks whether it is legal and affordable.

Host computes resulting finances.

---

# 14. ROSTERS

Synchronize:

- active roster
- academy
- reserves if present
- staff
- player roles
- lineup
- substitutions
- registrations
- bench
- captain/leader equivalents
- transfers
- released players

Use stable entity IDs.

NEVER synchronize players by display name.

Two players may have identical names.

---

# 15. TRAINING

Synchronize all persistent training decisions.

Examples depending on actual game implementation:

- training schedules
- training focus
- map training
- player development
- coaches
- intensity
- fatigue consequences
- attribute development

Host calculates outcomes.

Clients select actions.

---

# 16. TOURNAMENTS

Tournament state must be completely shared.

Synchronize:

- tournament ID
- participants
- invitations
- qualification
- seeding
- brackets
- groups
- standings
- fixtures
- match results
- elimination state
- prize money
- registration
- roster locks
- tournament progression

All clients must see exactly the same bracket.

If one client has:

Team A vs Team B

another client must NEVER see:

Team A vs Team C.

---

# 17. HUMAN VS HUMAN MATCHES

This is essential.

If Manager A's team plays Manager B's team, BOTH players participate in the same authoritative match.

Do not simulate two copies.

There must be ONE:

- map veto
- lineup state
- match simulation
- score
- event timeline
- statistics
- result

Both players receive the same authoritative match data.

---

# 18. MAP VETO / PRE-MATCH DECISIONS

Investigate the actual pre-match flow.

If both human managers need to make choices:

Manager A submits choice.
Manager B submits choice.

Host resolves them in the proper order.

Implement a server-side match room/state machine.

Conceptual states:

WAITING
PRE_MATCH
VETO
LINEUP_LOCK
READY
LIVE
FINISHED
COMMITTED

Prevent illegal double actions.

Example:

If Manager A has already banned Mirage, replayed/duplicate packets must not ban another map accidentally.

Commands need idempotency protection.

---

# 19. LIVE MATCH SYNCHRONIZATION

Version 1.1.3.1 contains a richer live match/replay system.

Investigate the REAL simulation architecture before choosing synchronization strategy.

Priority:

Do NOT run four independent match simulations and hope they remain deterministic.

Preferred model:

# Host simulates the match.

Clients receive authoritative match events/state.

Determine whether the match engine can be replicated through:

A. deterministic seed + synchronized commands

or

B. host-generated event/state stream.

Choose whichever is actually reliable after inspecting the game.

Correctness is more important than bandwidth.

Synchronize enough information for clients to see:

- current map
- score
- round number
- alive/dead state
- kills
- deaths
- assists
- bomb events
- round winner
- economy where applicable
- player positions if required for the 3D viewer
- grenades
- match clock
- statistics
- overtime
- final result

---

# 20. LIVE MATCH SPECTATING

If Manager A plays an AI team, other connected managers should be able to continue normal management when possible.

Optionally allow:

WATCH MATCH

A spectator receives the authoritative match stream but has no control.

For human-vs-human matches, both participating managers receive the match automatically.

---

# 21. TIME ADVANCEMENT — FOOTBALL MANAGER STYLE

This is one of the most important systems.

A client must NEVER independently advance the global career date.

Implement synchronized continuation.

Each manager has:

READY / CONTINUE

Example:

Manager 1: Ready  
Manager 2: Ready  
Manager 3: Not Ready  
Manager 4: Ready

World does NOT advance past a blocking point.

When Manager 3 presses Continue:

HOST advances authoritative simulation.

Then all clients receive the new state.

However, do not naïvely require all four players for every tiny internal tick if this makes gameplay unusable.

Study the game's actual time model and implement synchronization at meaningful progression barriers.

---

# 22. BLOCKING EVENTS

A manager may have a decision that prevents progression.

Examples:

- contract decision
- tournament registration
- roster registration
- match lineup
- map veto
- important inbox decision
- transfer response
- match start

Create:

`ProgressionBlocker`

Host knows which manager is blocking progression and why.

UI example:

WAITING FOR PLAYERS

Mehmet — Ready  
Ahmet — Ready  
Can — Selecting tournament roster  
Emre — Ready

Do not leak private negotiation details unnecessarily.

Display an appropriate generic reason where needed.

---

# 23. DIFFERENT MATCHES ON THE SAME DATE

Important edge case.

Suppose:

Manager A has a match at 18:00.

Manager B has a different match at 18:00.

Manager C has no match.

Manager D has no match.

The multiplayer system must support this correctly.

Investigate whether the vanilla engine simulates fixtures sequentially internally.

The host remains authoritative.

Each relevant manager must receive the correct match interface/context.

Do not create conflicting global "current match" assumptions.

If the vanilla UI/engine cannot support simultaneous live human match contexts, implement a safe scheduling/serialization layer while preserving the same game-world time/results semantics as closely as possible.

Document the compromise if technically necessary.

---

# 24. AI TEAMS

AI teams remain controlled by vanilla game logic.

Do NOT replace the entire AI.

Only human-controlled teams require multiplayer command routing.

Host executes all AI simulation.

This includes:

- AI transfers
- AI contracts
- AI tournaments
- AI matches
- AI roster decisions
- other world simulation

Then results replicate to clients.

---

# 25. RNG

Randomness can destroy synchronization.

Identify all important RNG sources.

Examples:

- match simulation
- player generation
- development
- transfer decisions
- AI choices
- injuries/conditions
- tournament draws

Do not rely on every machine independently producing the same random sequence unless proven deterministic.

Host should normally own authoritative RNG outcomes.

Clients receive results.

---

# 26. ENTITY IDENTIFICATION

Create a stable network identity system.

Entities may include:

- Player
- Staff
- Team
- Tournament
- Match
- Contract
- Transfer negotiation
- Sponsor
- Generated player

Prefer existing persistent database IDs.

If the game lacks suitable IDs, create a mapping layer.

Never use array index as long-term network identity.

Never use display names.

---

# 27. STATE REPLICATION

Do NOT continuously transmit the entire save.

Use:

Initial Snapshot
+
Incremental State Updates

Conceptually:

WorldSnapshot

then:

PlayerChanged
TeamChanged
ContractChanged
TransferChanged
TournamentChanged
FixtureChanged
MatchChanged
FinanceChanged
DateChanged

Batch updates when useful.

---

# 28. STATE HASHING / DESYNC DETECTION

Implement state verification.

Periodically calculate hashes for important authoritative state.

At minimum consider:

- date
- teams
- rosters
- contracts
- transfers
- tournaments
- fixtures
- results

Host sends expected hash.

Client compares.

If mismatch:

DESYNC DETECTED

Attempt safe resynchronization.

Do NOT allow corruption to continue silently.

---

# 29. RESYNCHRONIZATION

Implement:

`RequestResync`

Possible strategy:

1. Pause client's command submission.
2. Host creates consistent snapshot.
3. Host sends snapshot.
4. Client applies it.
5. Client calculates hash.
6. Host verifies.
7. Client resumes.

Avoid requiring everyone to restart the game for a minor desync.

---

# 30. DISCONNECTION

If a client disconnects:

Do NOT corrupt the career.

Mark:

DISCONNECTED

Host keeps authoritative world.

Allow reconnect.

Reconnect process:

- authenticate session
- identify manager
- validate versions
- send current snapshot/delta
- restore assigned organization
- resume

Configurable policy:

`PauseOnHumanDisconnect = true`

Default TRUE.

Host can choose:

WAIT FOR PLAYER

or

CONTINUE WITH PLAYER ABSENT

Do NOT permanently give the team to AI unless explicitly chosen.

---

# 31. HOST DISCONNECT

Host is authoritative.

Therefore if host exits unexpectedly:

- clients must be notified
- session stops safely
- no client should continue creating divergent authoritative saves

Initial version does NOT require host migration.

Display:

`Host disconnected. Multiplayer session ended.`

Later host migration may be investigated separately.

---

# 32. SAVE SAFETY

Multiplayer must never destroy a user's career.

Before major operations:

- validate state
- use safe save operations
- consider backups

Create configurable automatic multiplayer backups.

Example:

`Backups/ESMCoop/`

Keep several rotating backups.

Never overwrite the only good save after detecting a desync or failed state application.

---

# 33. AUTOSAVE

Only host performs authoritative autosave.

Clients must not race to write the same career.

Autosave metadata should record multiplayer session information.

---

# 34. UI INTEGRATION

Do not make this feel like an external debug utility if avoidable.

Integrate multiplayer UI into the game.

At minimum provide:

## Main Multiplayer Panel

HOST GAME  
JOIN GAME  
SETTINGS

## Lobby

Players  
Teams  
Ready state  
Ping  
Version status

## In-game status

Connected players  
Ready status  
Current blockers  
Ping  
Connection state

## Notifications

Examples:

`Ahmet submitted a transfer offer.`

`Can is ready.`

`Waiting for Emre.`

`Connection lost.`

`Resynchronizing...`

Do not reveal private offer amounts to opponents unless vanilla game mechanics would expose them.

---

# 35. INFORMATION PRIVACY

Multiplayer synchronization does NOT mean every client should be shown every private management value.

The host may need full authoritative state internally.

But UI visibility must respect normal game rules.

Examples of potentially private information:

- exact transfer offer
- salary offer
- scouting knowledge
- private finances
- tactical setup before a match

Synchronize data required for correctness but preserve appropriate UI visibility.

---

# 36. SECURITY / TRUST MODEL

This is a friends-only Radmin VPN mod, not an anti-cheat esports platform.

Nevertheless:

NEVER trust arbitrary client state mutations.

Validate commands server-side.

Examples:

Client must NOT be able to send:

`Money = 999999999`

Instead:

`SubmitTransferOffer(PlayerId, Amount)`

Host validates:

- sender owns that team
- player exists
- transfer is legal
- budget permits it
- game state permits negotiation

Then vanilla logic executes.

---

# 37. DUPLICATE / OUT-OF-ORDER PACKETS

Network code must tolerate:

- duplicate packets
- delayed packets
- reconnect
- timeouts
- partial messages

Use:

- sequence IDs
- command IDs
- acknowledgements where necessary
- idempotent command handling

A repeated packet must not cause:

two transfers  
two contracts  
two purchases  
two match decisions.

---

# 38. NETWORK THREADING

Never mutate Unity game objects from an unsafe network thread.

Network receive loop:

receive
→ deserialize
→ queue

Unity/main thread:

dequeue
→ validate
→ apply

Create a safe dispatcher.

Avoid random IL2CPP crashes caused by cross-thread Unity access.

---

# 39. SERIALIZATION

Use a maintainable serialization format.

For high-frequency messages, a compact binary protocol is acceptable.

For debugging/configuration, JSON may be useful.

But do not blindly serialize arbitrary IL2CPP objects.

Create explicit DTOs/network messages.

Example:

`TransferOfferCommandDto`

not:

serialize entire `DataPlayer`.

This protects compatibility and reduces payload size.

---

# 40. MOD COMPATIBILITY

At session start calculate a compatibility fingerprint.

At least detect:

- game version
- ESM26 Coop version
- protocol version
- ModKit version
- database/save fingerprint
- gameplay-affecting mods where detectable

Cosmetic mods may be allowed.

Gameplay/data-changing mods should produce warnings or rejection unless proven compatible.

Create a config:

`StrictModCompatibility`

Default:

TRUE.

---

# 41. VERSION TARGETING

Target:

# Esports Manager 2026 v1.1.3.1

Do not claim compatibility with future game updates automatically.

Implement explicit version checking.

If game update changes methods/classes:

show:

`Unsupported game version`

instead of crashing the save.

---

# 42. PATCHING STRATEGY

Use Harmony/BepInEx/ModKit mechanisms appropriate for the actual environment.

Patch as little as possible.

Prefer:

- postfix/prefix around stable boundaries
- command interception
- state observation
- contextual human-team overrides

Avoid rewriting entire vanilla systems unnecessarily.

Every patch must be documented:

Class  
Method  
Reason  
Patch type  
Expected side effects

Create:

`docs/PATCH_MAP.md`

---

# 43. LOGGING

Create detailed logs.

Example:

`BepInEx/LogOutput.log`

and optionally:

`BepInEx/ESMCoop/coop.log`

Categories:

NET
LOBBY
SYNC
SAVE
TRANSFER
MATCH
TOURNAMENT
TIME
ERROR
DESYNC

Example:

`[SYNC] Applied TeamChanged TeamId=15 Revision=381`

Do NOT spam thousands of useless lines every frame.

---

# 44. DEBUG OVERLAY

Development builds should provide an optional debug overlay.

Show:

- Host/Client
- Session ID
- Ping
- Current revision
- Queue size
- Last packet
- State hash
- Desync status
- connected players

Configurable hotkey.

Disable/hide for normal users.

---

# 45. PROJECT STRUCTURE

Create a clean architecture.

Suggested conceptual structure:

ESM26Coop/
    Core/
    Game/
    Networking/
        Transport/
        Protocol/
        Messages/
        Serialization/
    Session/
    Lobby/
    Managers/
    Sync/
        Snapshot/
        Replication/
        Hashing/
    Gameplay/
        Time/
        Transfers/
        Contracts/
        Teams/
        Training/
        Tournaments/
        Matches/
        Finances/
    Patches/
    UI/
    Config/
    Logging/
    Diagnostics/

Adapt names to the actual solution.

Do not create one 10,000-line plugin class.

---

# 46. IMPLEMENTATION PHASES

Do NOT attempt everything simultaneously.

Implement in this order.

## PHASE 1 — Reverse engineering

Deliver:

- environment report
- important classes
- save architecture
- time progression
- team ownership
- transfer system
- tournament system
- match system

No fake assumptions.

## PHASE 2 — Networking foundation

Implement:

- host
- join
- Radmin IP
- handshake
- lobby
- 4 connections
- disconnect
- ping
- protocol

Success criterion:

4 game instances can enter the same lobby.

## PHASE 3 — Shared career

Implement:

- host save
- initial snapshot
- team assignment
- client reconstruction
- world hash

Success:

all machines see the same career.

## PHASE 4 — Multi-manager context

Each human controls a different organization.

Success:

Client A changing its lineup cannot modify Client B's team.

## PHASE 5 — Time synchronization

Implement:

- ready/continue
- blockers
- host progression

Success:

all clients remain on identical date/world revision.

## PHASE 6 — Transfers/contracts

Implement simultaneous negotiations.

Test:

Human A and Human B both bid for the same player.

Correct vanilla resolution must propagate to everyone.

## PHASE 7 — Tournaments

Synchronize:

fixtures
brackets
registrations
standings
results

## PHASE 8 — Matches

First:

human vs AI.

Then:

human vs human.

Finally:

spectating.

## PHASE 9 — Recovery

Implement:

- reconnect
- resync
- backups
- desync detection

## PHASE 10 — Polish

Implement:

- native UI
- notifications
- installer
- documentation
- diagnostics

---

# 47. TEST MATRIX

Create automated tests where possible and manual integration scenarios.

Test:

### TEST A
2 players.

Different teams.

Advance one week.

Hashes must remain identical.

### TEST B
4 players.

All different teams.

Perform simultaneous management actions.

No cross-team contamination.

### TEST C
Two humans bid for same player.

Both negotiations exist.

One ultimately wins according to game logic.

Correct budgets/contracts/rosters update everywhere.

### TEST D
Human vs human tournament match.

Both enter same match.

One authoritative result.

Both see identical:

score
rounds
stats
tournament consequences.

### TEST E
Client disconnects.

Reconnects.

Receives latest state.

No duplicate commands.

### TEST F
Client intentionally corrupts local replicated state.

Hash detects mismatch.

Resync restores canonical state.

### TEST G
Host saves and exits.

Later host reloads.

Same manager-team assignments restored.

### TEST H
4 players have different actions pending.

Career does not progress past required blockers.

### TEST I
Two human-controlled teams have matches at the same in-game time.

System handles the game's internal limitations without generating divergent world state.

---

# 48. PERFORMANCE

This is a management game over virtual LAN.

Correctness > tiny bandwidth savings.

Nevertheless:

Do not send full world every frame.

Use:

- dirty state tracking
- revisions
- batching
- deltas
- compression for snapshots

Target normal management traffic:

very low bandwidth.

Live match traffic may be higher.

---

# 49. ACCEPTANCE CRITERIA

I will consider the mod successful only if this scenario works:

Four computers connect through Radmin VPN.

Host loads one career.

Player 1 controls Team A.

Player 2 controls Team B.

Player 3 controls Team C.

Player 4 controls Team D.

All four see the same world.

Player 1 signs a player.

Everyone's database reflects it.

Player 2 changes their lineup.

Only Player 2's team changes.

Player 3 and Player 4 bid for the same player.

Both offers exist and the authoritative game resolves the player's choice.

Player 1 and Player 2 later meet in a tournament.

Both enter the same match.

There is ONE match simulation.

Both see the same rounds, statistics and final result.

Tournament bracket updates identically for everyone.

All players continue.

The world advances.

Host saves.

Everyone exits.

Later they reconnect.

The same multiplayer career continues.

THAT is the target.

Anything substantially less is not "finished multiplayer."

---

# 50. DO NOT CHEAT THE IMPLEMENTATION

Do NOT solve this by:

- periodically copying save files between PCs
- running separate careers
- simply synchronizing final match scores
- manually editing databases after actions
- giving every client full authority
- replacing all vanilla simulation
- making transfers first-packet-wins
- creating fake UI without functional multiplayer
- ignoring reconnect/desync
- claiming something works without testing it

We want actual multiplayer architecture.

---

# 51. WHEN THE GAME MAKES SOMETHING DIFFICULT

Do not abandon the feature immediately.

Investigate alternatives in this order:

1. Public/internal game method
2. ModKit API
3. Harmony patch
4. IL2CPP interop
5. Controlled reflection/interception
6. State reconstruction
7. Carefully designed replacement subsystem

Document why each fallback was necessary.

If something is genuinely impossible because of a hard engine limitation, provide:

- exact technical reason
- class/method involved
- evidence
- closest viable alternative

Do NOT merely say:

"the game doesn't support multiplayer."

The entire point of this project is adding it.

---

# 52. IMPORTANT RESEARCH CLUES

Community mods for this game already demonstrate access to:

- players
- staff
- teams
- tournaments
- contracts
- finances
- transfer-related state
- player attributes
- team budgets
- database entities

Study existing installed/open-source mod code if available.

Do not copy proprietary code.

Use it to understand legitimate ModKit patterns and actual game APIs.

Also inspect how existing mods handle IL2CPP objects and Harmony patches.

---

# 53. FIRST TASK — DO THIS NOW

Do NOT immediately attempt the entire mod.

Your first response/work cycle must be:

### STEP 1
Inspect every relevant project/game/mod file available to you.

### STEP 2
Confirm:

- actual game version
- Unity version
- BepInEx environment
- ModKit environment
- IL2CPP environment

### STEP 3
Reverse-engineer and identify the real implementations of:

1. player-controlled team
2. world/career state
3. current date
4. continue/time advancement
5. save/load
6. DataPlayer/player entities
7. teams
8. contracts
9. transfer negotiations
10. tournaments
11. fixtures
12. live match simulation
13. match results
14. finances
15. AI simulation

### STEP 4
Create:

`docs/REVERSE_ENGINEERING_REPORT.md`

For every subsystem record:

REAL CLASS  
REAL METHOD  
IMPORTANT FIELDS  
CALL FLOW  
PATCH POINT  
MULTIPLAYER RISK

### STEP 5
Create:

`docs/MULTIPLAYER_ARCHITECTURE.md`

with the concrete architecture based on the discovered game internals.

### STEP 6
Create the project skeleton.

### STEP 7
Implement only the first vertical slice:

HOST  
JOIN  
RADMIN DIRECT IP  
HANDSHAKE  
LOBBY  
2–4 PLAYERS  
TEAM SELECTION  
READY  
PING  
DISCONNECT

### STEP 8
Build the DLL and give exact installation instructions.

### STEP 9
Test/log failures and fix them.

Do not stop at an architectural essay if the environment contains enough information to start implementation.

Actually create/edit the project files.

Compile frequently.

Inspect compiler errors yourself.

Fix them yourself.

Do not ask me to manually write code that you can create.

Do not ask me to perform reverse engineering that you can perform from the available files.

Work autonomously.

---

# FINAL DESIGN PRINCIPLE

At all times remember:

# ONE HOST.
# ONE SAVE.
# ONE WORLD.
# ONE AUTHORITATIVE SIMULATION.
# UP TO FOUR HUMAN MANAGERS.

Radmin VPN is only the transport path between machines.

The host is the authority.

Every important action must ultimately become part of the same authoritative Esports Manager 2026 career.

Build this as a real multiplayer conversion, not as a collection of loosely synchronized single-player games.