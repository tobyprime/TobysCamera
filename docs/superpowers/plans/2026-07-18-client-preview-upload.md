# Client Preview Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upload and persist a client-generated one-map preview before all full media tiles.

**Architecture:** The common protocol carries preview chunks, sessions enforce that they complete first, Fabric uploads the preview derived from encoded output, and Folia stores/uses the supplied bytes.

**Tech Stack:** Java 21, JUnit 5, Fabric API, Paper/Folia, SQLite.

---

### Task 1: Protocol and session contract

**Files:** `common/.../protocol/{Packets,PacketCodec,CameraPacket,PacketType}.java`, `common/.../upload/{UploadSession,VideoUploadSession}.java`, matching tests.

- [ ] Write tests that round-trip photo/video preview chunks and reject media chunks or finish before preview completion.
- [ ] Add chunk packet types and contiguous preview storage to both sessions.
- [ ] Run focused common tests.

### Task 2: Fabric preview generation and upload order

**Files:** `fabric/.../camera/{MapTileEncoder,PhotoUploadController}.java`, `fabric/.../video/VideoUploadController.java`, matching tests.

- [ ] Write tests that observe preview chunks before normal photo/video tile chunks.
- [ ] Downsample encoded palette tiles client-side and send preview chunks before normal data.
- [ ] Run focused Fabric tests.

### Task 3: Folia validation and persistent storage

**Files:** upload coordinators, router, records, repositories, SQLite implementations, map services, matching tests.

- [ ] Write tests for coordinator rejection of out-of-order uploads and repository persistence of preview bytes.
- [ ] Route preview packets, append them to sessions, require them at finish, and add SQLite `preview` BLOB columns with read APIs.
- [ ] Make bags and restart restore consume persisted preview bytes, with tile-derived fallback only for legacy records.
- [ ] Run focused Folia tests.

### Task 4: Integration verification

- [ ] Run `./gradlew.bat :common:test :fabric:test :folia:test`.
- [ ] Run `./gradlew.bat build`.
- [ ] Inspect the final diff for no server-side sampling on newly uploaded bags.
