# Minecraft Companions Mod

A Fabric client panel for the [Minecraft Agent Harness companion controller](https://github.com/espresso95/minecraft-agent-harness). Press **K** to message agents, manage their profiles, inspect shared work and monitor reported token usage. This repository also builds the small Paper server bridge used for world rules and status name tags.

## Mac mini server, laptop client

The Mac mini runs Minecraft's server, the controller and the AI agents. The laptop runs Minecraft and this client mod. The laptop does not need Node, Docker, model credentials or a separate agent process. Both computers must be on the same reachable local network.

1. On the Mac mini, run `npm run companions` in the harness to start the server and agents without opening a Minecraft client. Open the web UI at **http://127.0.0.1:8767**, pair using the code printed in the terminal, then turn **Host settings → Access → LAN sharing** on. Note the listed Minecraft address, control URL and pairing code. The Mac mini's in-game panel can also enable sharing if you use a client there.
2. On the laptop, install **Minecraft Java 1.21.6**, **Fabric Loader 0.20.1** and **Fabric API 0.128.2+1.21.6** in the same launcher instance.
3. Download `companion-mailbox-0.1.0+1.21.6.jar` from [Releases](https://github.com/espresso95/minecraft-companions-mod/releases/latest) and put it in that instance's `mods` folder alongside Fabric API. Remove older companion JARs from that folder, then restart Minecraft.
4. Join the Mac mini's listed Minecraft address. Press **K**. The control URL is normally filled from the server address with port **8767**. Enter the host's pairing code and choose **Pair this device**.

The laptop then has the same shared agent, mailbox, Work, model, skin, difficulty and token controls. Trusted paired devices can manage all shared settings, including LAN sharing and device revocation. Only the permanent owner credential stays on the Mac mini. **Revoke devices** invalidates device pairings while keeping that owner credential valid. The pairing is saved on the laptop, separately for each server.

Use a different player name on each simultaneous Minecraft connection. The default server uses offline identities and is intended for trusted local networks. No internet port forwarding is needed. Wi-Fi client isolation or a host firewall can prevent two devices on the same Wi-Fi from reaching each other.

Only the **companion-mailbox** JAR belongs on the laptop. `companion-rules` is a Paper server plugin; the harness installs it on the Mac mini automatically. The matching Fabric API is a separate required download from [Fabric](https://fabricmc.net/use/installer/) or its [official Modrinth project](https://modrinth.com/mod/fabric-api/versions?g=1.21.6).

## Build and update

Install Java 21 and run:

```sh
./gradlew build compileGametestJava --console=plain
```

Builds are in `build/libs/`. The regular client JAR is installable; the `-sources.jar` is source code. Game tests use the matching harness's test controller/fixtures; see its `scripts/verify-companions.js` and companion guide.

GitHub Actions builds the client and server plugin on each push to `main`. A `v*` tag also publishes those JARs as a GitHub release. On the laptop, replace the old client JAR with the new release and restart Minecraft. The API remains under `/v1`; the harness pins a tested mod commit as a Git submodule, so updating the server checkout does not silently switch mod versions.

## Source structure

- `src/main`: Fabric client, screens, pairing and HTTP client. Both native and web panels render work fields supplied by the controller; agent policy and model requests belong to the harness.
- `src/server`: Paper bridge for server settings and agent status labels.
- `src/gametest`: client UI checks against harness fixtures.

MIT licensed. See [LICENSE](LICENSE).
