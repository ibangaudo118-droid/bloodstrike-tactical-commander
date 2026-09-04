# Blood Strike Tactical Commander

A tactical AI commander prototype for Blood Strike.

## Current MVP

- Live screen capture foundation
- Gameplay feed
- Tactical commander dashboard
- Tactical command system
- Squad status panel
- Mission objectives
- Command history
- Mobile-friendly interface

## Architecture

Current:

Blood Strike -> Screen Capture -> Commander Dashboard -> Tactical Commands

Future:

Blood Strike -> Android MediaProjection -> Live Gameplay Frames -> AI Vision -> Battlefield Analysis -> Tactical Decision Engine -> Voice Commander -> Player

## Important

The current tactical commands are demonstrations. The system does NOT yet understand what is happening inside the game.

The next phase will add real AI vision, live frame analysis, enemy detection, position analysis, squad awareness, zone/rotation analysis, tactical decision making, voice commands, match recording, and post-match coaching.

## Same-phone goal

The final Android version should watch Blood Strike gameplay, analyze it continuously, give short tactical commands, speak commands through the phone, track player decisions, and review mistakes after each match.

The native Android capture layer will use Android MediaProjection rather than requiring manual screenshots.
