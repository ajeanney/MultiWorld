# MultiWorld

Plusieurs mondes sur un seul serveur Fabric, chacun avec son propre état de joueur : créez et supprimez des ensembles de mondes, importez un dossier de monde existant et passez de l'un à l'autre avec une commande.

## Ce qu'il fait

- Créer et supprimer des ensembles de mondes personnalisés (overworld, nether, end)
- Se téléporter entre les mondes avec support des commandes
- Importer un dossier de monde existant dans MultiWorld
- Basculer vers un monde en mode créatif avec un inventaire et un profil séparés

## Commandes

- `/mw tp <monde>`
- `/mw import <dossier> <nom>`
- `/mw create <nom> [preset] [mode] [seed]`
- `/mw delete <nom>`
- `/creative`

### Syntaxe de create

- `name` : le nom de base du monde
- `preset` : `normal` ou `flat` (optionnel, par défaut : `normal`)
- `mode` : `normal` ou `creative` (optionnel, par défaut : `normal`)
- `seed` : valeur long (optionnel, par défaut : aléatoire)

Exemples :

- `/mw create survival_world`
- `/mw create flat_world flat`
- `/mw create build_world flat creative`
- `/mw create seeded_world normal normal -2831322041033427943`

## Remarques

- `/creative` bascule vers un monde en mode créatif, et inversement.
- Si aucun monde créatif n'existe, créez-en un d'abord avec :
  - `/mw create <nom> [preset] creative [seed]`
