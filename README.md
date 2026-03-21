# MultiWorld

Run several worlds on one Fabric server, each with its own player state: create and delete world sets, import an existing world folder, and hop between them with a command.

## What it does

- Create and delete custom world sets (overworld, nether, end)
- Teleport between worlds with command support
- Import an existing world folder into MultiWorld
- Switch to a creative-mode world with separate inventory/profile context

## Commands

- `/mw tp <world>`
- `/mw import <folder> <name>`
- `/mw create <name> [preset] [mode] [seed]`
- `/mw delete <name>`
- `/creative`

### Create syntax

- `name`: world base name
- `preset`: `normal` or `flat` (optional, default: `normal`)
- `mode`: `normal` or `creative` (optional, default: `normal`)
- `seed`: long value (optional, default: random)

Examples:

- `/mw create survival_world`
- `/mw create flat_world flat`
- `/mw create build_world flat creative`
- `/mw create seeded_world normal normal -2831322041033427943`

## Notes

- `/creative` toggles in/out of a creative-mode world.
- If no creative-mode world exists, create one first with:
  - `/mw create <name> [preset] creative [seed]`
