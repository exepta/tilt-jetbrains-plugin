package com.tiltus.bevywizard.catalog;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CatalogClientIdentityTest {
    @Test void publishedTiltUiVersionsExcludeYankedAndUnrelatedPrereleases() {
        String json = """
                {"versions":[
                  {"num":"0.2.0-beta.1","yanked":false},
                  {"num":"0.1.0","yanked":false},
                  {"num":"0.1.0-rc.2","yanked":false},
                  {"num":"0.1.0-rc.1","yanked":true}
                ]}
                """;
        assertEquals(List.of("0.1.0", "0.1.0-rc.2"), CatalogClient.parseTiltUiVersions(json));
    }

    @Test void catalogTitleIsOnlyAnUnverifiedCandidate() {
        String html = "<h3 class='asset-subsection'>2D</h3><div class='item-grid'>"
                + "<div id='camera'><a class='asset-card' href='https://github.com/example/real-package'>"
                + "<div class='asset-card__title'>Pretty Plugin</div></a></div></div>";
        CrateAsset asset = CatalogClient.parseAssets(html).getFirst();
        assertEquals("Pretty_Plugin", asset.name());
        assertFalse(asset.hasVerifiedIdentity());
    }

    @Test void publishedPackageMustMatchCatalogSource() {
        CrateAsset asset = new CrateAsset("Pretty_Plugin", "Pretty Plugin", "", "2D", List.of("^0.19"),
                "https://github.com/example/real-package/tree/main/crates/plugin", null, -1, -1, null, null);
        CrateAsset matching = asset.withIdentity("real_package", CrateAsset.IdentitySource.UNVERIFIED)
                .withMetadata("1.2.3", 10, 2, "https://github.com/example/real-package.git", null);
        CrateAsset verified = CatalogClient.matchIdentity(asset, matching).orElseThrow();
        assertEquals("real_package", verified.name());
        assertEquals(CrateAsset.IdentitySource.REPOSITORY_MATCH, verified.identitySource());
        assertTrue(verified.hasVerifiedIdentity());

        CrateAsset unrelated = matching.withMetadata("1.2.3", 10, 2,
                "https://github.com/other/real-package", null);
        assertTrue(CatalogClient.matchIdentity(asset, unrelated).isEmpty());
    }

    @Test void directCratesIoLinkMustNamePublishedPackage() {
        CrateAsset asset = new CrateAsset("my-crate", "My Crate", "", "2D", List.of("^0.19"),
                "https://crates.io/crates/my-crate", null, -1, -1, null, null);
        CrateAsset matching = asset.withIdentity("my_crate", CrateAsset.IdentitySource.UNVERIFIED);
        assertEquals(CrateAsset.IdentitySource.CRATES_IO_LINK,
                CatalogClient.matchIdentity(asset, matching).orElseThrow().identitySource());
        assertTrue(CatalogClient.matchIdentity(asset,
                asset.withIdentity("another_crate", CrateAsset.IdentitySource.UNVERIFIED)).isEmpty());
    }

    @Test void crateNameSuggestionsPrioritizeTheCatalogRepository() {
        CrateAsset asset = new CrateAsset("Pretty_Plugin", "Pretty Plugin", "", "2D", List.of("^0.19"),
                "https://github.com/example/real-package", null, -1, -1, null, null);
        String search = """
                {"crates":[
                  {"id":"other_plugin","repository":"https://github.com/other/plugin"},
                  {"id":"pretty-plugin","repository":"https://github.com/other/pretty-plugin"},
                  {"id":"real_package","repository":"https://github.com/example/real-package.git"},
                  {"id":"bad name","repository":"https://github.com/example/real-package"}
                ]}
                """;
        assertEquals(List.of("real_package"),
                CatalogClient.parseCrateSuggestions(asset, search));
        assertEquals(List.of(), CatalogClient.parseCrateSuggestions(asset, "{\"crates\":[]}"));
        assertEquals(List.of(), CatalogClient.parseCrateSuggestions(asset, "{}"));
    }

    @Test void avianSuggestionsUseTheRenamedRepositoryAndIgnoreReservedNames() {
        CrateAsset asset = new CrateAsset("avian", "avian", "Physics", "Physics", List.of("^0.19"),
                "https://github.com/Jondolf/avian", null, -1, -1, null, null);
        String search = """
                {"crates":[
                  {"id":"tuna","repository":"https://github.com/someone/tuna"},
                  {"id":"avian","repository":"https://github.com/avianphysics/avian",
                   "description":"Reserved for the Avian physics engine project. Please use avian2d or avian3d instead!"},
                  {"id":"avian_derive","repository":"https://github.com/avianphysics/avian"},
                  {"id":"avian3d","repository":"https://github.com/avianphysics/avian"},
                  {"id":"avian2d","repository":"https://github.com/avianphysics/avian"},
                  {"id":"avian_fdm","repository":"https://github.com/other/avian_fdm"}
                ]}
                """;
        assertEquals(List.of("avian3d", "avian2d", "avian_derive"), CatalogClient.parseCrateSuggestions(asset,
                "https://github.com/avianphysics/avian", search));
    }
}
