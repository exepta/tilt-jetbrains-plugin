package com.tiltus.bevywizard.catalog;

/** A published Bevy release and its declared minimum Rust compiler version. */
public record BevyRelease(String version, String rustVersion) {}
