package com.tiltus.bevywizard.catalog;

import java.util.List;

/** A community asset together with optional crates.io release metadata. */
public record CrateAsset(String name, String title, String description, String category,
                         List<String> bevyTags, String url, String version,
                         long downloads, long recentDownloads, String repository, String updatedAt,
                         IdentitySource identitySource) {
    /** How the package name was matched to the Bevy Assets entry. */
    public enum IdentitySource {
        UNVERIFIED("Unverified"),
        CRATES_IO_LINK("crates.io link"),
        REPOSITORY_MATCH("Matching repository"),
        USER_PROVIDED("Entered by user");

        private final String label;
        IdentitySource(String label) { this.label = label; }
        public String label() { return label; }
    }

    /** Creates an asset whose catalog title has not yet been verified as a Cargo package. */
    public CrateAsset(String name, String title, String description, String category,
                      List<String> bevyTags, String url, String version,
                      long downloads, long recentDownloads, String repository, String updatedAt) {
        this(name, title, description, category, bevyTags, url, version,
                downloads, recentDownloads, repository, updatedAt, IdentitySource.UNVERIFIED);
    }

    public boolean hasVerifiedIdentity() { return identitySource != null && identitySource != IdentitySource.UNVERIFIED; }

    /** Uses the canonical package name returned by crates.io. */
    public CrateAsset withIdentity(String canonicalName, IdentitySource source) {
        return new CrateAsset(canonicalName, title, description, category, bevyTags, url,
                version, downloads, recentDownloads, repository, updatedAt, source);
    }

    /** Returns a copy with resolved crates.io metadata. */
    public CrateAsset withMetadata(String newVersion, long total, long recent, String repo, String updated) {
        return new CrateAsset(name, title, description, category, bevyTags, url,
                newVersion, total, recent, repo, updated, identitySource);
    }
}
