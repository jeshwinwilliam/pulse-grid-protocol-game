# Pulse Grid Protocol

Pulse Grid Protocol is a small original Java arcade game inspired by real-time systems work. Instead of fighting enemies directly, you race across a failing network and stabilize collapsing nodes with timed pulse bursts.

## Why this game is different

- The core loop is based on grid stabilization and chain reactions, not jumping or shooting.
- Your score comes from recovering multiple unstable nodes in one pulse to build a combo chain.
- Standing near a failing node repairs it slowly, so movement and pulse timing both matter.
- The threat ramps up over time as more nodes destabilize and decay faster.

## Controls

- `W A S D` or arrow keys to move
- `Space` to release a stabilization pulse
- `Enter` to restart after win or loss

## Goal

Reach `4000` points before grid integrity drops to zero.

## Run locally

```bash
javac -d out src/com/jeshwin/pulsegrid/*.java
java -cp out com.jeshwin.pulsegrid.PulseGridGame
```

## Idea behind the theme

The project takes inspiration from distributed systems, signal recovery, and event-driven software, which makes it a nice fit for a software engineering portfolio instead of feeling like a generic sample game.
