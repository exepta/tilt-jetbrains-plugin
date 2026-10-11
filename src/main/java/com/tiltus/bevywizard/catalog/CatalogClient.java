package com.tiltus.bevywizard.catalog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Retrieves Bevy releases, community assets, crate metadata, and README content. */
public final class CatalogClient {
    /** The complete rendered README and the URL used to resolve relative links. */
    public record ReadmePage(String html, String sourceUrl) {}
    private static final Set<String> BEVY_CRATES = Set.of(("bevy bevy_a11y bevy_animation bevy_anti_alias bevy_app bevy_asset bevy_audio " +
            "bevy_camera bevy_camera_controller bevy_clipboard bevy_color bevy_core_pipeline bevy_derive " +
            "bevy_dev_tools bevy_diagnostic bevy_ecs bevy_feathers bevy_gilrs bevy_gizmos bevy_gizmos_render " +
            "bevy_gltf bevy_image bevy_input bevy_input_focus bevy_internal bevy_light bevy_log bevy_macro_utils " +
            "bevy_material bevy_math bevy_mesh bevy_pbr bevy_picking bevy_platform bevy_post_process bevy_ptr " +
            "bevy_reflect bevy_remote bevy_render bevy_scene bevy_shader bevy_solari bevy_sprite bevy_sprite_render " +
            "bevy_state bevy_tasks bevy_text bevy_time bevy_transform bevy_ui bevy_ui_render bevy_ui_widgets " +
            "bevy_utils bevy_window bevy_winit bevy_world_serialization").split(" "));
    private static final String AGENT = "BevyProjectWizard/0.1 (https://github.com/exepta/tilt-ui)";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(7))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private CatalogClient() {}

    /** Fetches published Bevy version numbers from crates.io. */
    public static List<String> bevyVersions() throws IOException {
        return bevyReleases().stream().map(BevyRelease::version).toList();
    }

    /** Fetches Bevy releases and their minimum Rust versions. */
    public static List<BevyRelease> bevyReleases() throws IOException {
        return parseBevyReleases(get("https://crates.io/api/v1/crates/bevy/versions"));
    }

    /** Lists non-yanked TiltUI versions published for both runtime and build tooling. */
    public static List<String> tiltUiVersions() throws IOException {
        List<String> runtime = parseTiltUiVersions(get("https://crates.io/api/v1/crates/tilt-ui/versions"));
        Set<String> build = new LinkedHashSet<>(parseTiltUiVersions(
                get("https://crates.io/api/v1/crates/tilt-ui-build/versions")));
        return runtime.stream().filter(build::contains).toList();
    }

    /** Keeps only stable and numbered RC releases from registry metadata. */
    public static List<String> parseTiltUiVersions(String json) {
        List<String> versions = new ArrayList<>();
        for (JsonElement entry : JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("versions")) {
            JsonObject item = entry.getAsJsonObject();
            String number = string(item, "num");
            if (number != null && number.matches("\\d+\\.\\d+\\.\\d+(?:-rc\\.\\d+)?")
                    && !item.get("yanked").getAsBoolean()) versions.add(number);
        }
        return versions;
    }

    /** Extracts stable and release-candidate version numbers. */
    public static List<String> parseBevyVersions(String json) {
        return parseBevyReleases(json).stream().map(BevyRelease::version).toList();
    }

    /** Parses Bevy release metadata returned by crates.io. */
    public static List<BevyRelease> parseBevyReleases(String json) {
        List<BevyRelease> versions = new ArrayList<>();
        for (JsonElement entry : JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("versions")) {
            JsonObject item = entry.getAsJsonObject();
            String number = item.get("num").getAsString();
            if (number.matches("\\d+\\.\\d+\\.\\d+(?:-rc\\.\\d+)?") && !item.get("yanked").getAsBoolean())
                versions.add(new BevyRelease(number, string(item, "rust_version")));
        }
        return versions;
    }

    /** Returns whether a version is a Bevy release candidate. */
    public static boolean isReleaseCandidate(String version) { return version.matches("\\d+\\.\\d+\\.\\d+-rc\\.\\d+"); }

    /** Downloads the community asset catalog from bevy.org. */
    public static List<CrateAsset> loadAssets() throws IOException {
        return parseAssets(get("https://bevy.org/assets/"));
    }

    /** Parses asset cards and Bevy tags from the catalog page. */
    public static List<CrateAsset> parseAssets(String html) {
        Document page = Jsoup.parse(html, "https://bevy.org/assets/");
        List<CrateAsset> result = new ArrayList<>();
        for (Element section : page.select("h3.asset-subsection")) {
            String category = section.ownText().trim();
            Element grid = section.nextElementSibling();
            if (grid == null || !grid.hasClass("item-grid")) continue;
            for (Element card : grid.select("a.asset-card")) {
                String name = card.parent() == null ? "" : card.parent().id();
                String title = card.select(".asset-card__title").text();
                String description = card.select(".asset-card__description").text();
                String url = card.absUrl("href");
                if (name.isBlank() || title.isBlank() || url.isBlank()) continue;
                // The Bevy site uses slug IDs. Crate names must come from crates.io links or metadata.
                String linkedName = cratesIoName(url);
                String crateName = linkedName == null ? title.trim().replace(' ', '_') : linkedName;
                List<String> tags = card.select(".asset-card__bevy-versions span.asset-card__tag")
                        .stream().map(Element::text).toList();
                result.add(new CrateAsset(crateName, title, description, category, tags, url,
                        null, -1, -1, null, null));
            }
        }
        return result;
    }

    /** Matches a catalog entry to a real crates.io package before it can be selected. */
    public static Optional<CrateAsset> identify(CrateAsset asset) throws IOException {
        if (asset.hasVerifiedIdentity()) return Optional.of(asset);
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(asset.name());
        String repoName = githubRepositoryName(asset.url());
        if (repoName != null) candidates.add(repoName);
        IOException failure = null;
        for (String candidate : candidates) {
            if (!candidate.matches("[A-Za-z][A-Za-z0-9_-]*")) continue;
            try {
                CrateAsset found = crateMetadata(asset, candidate, CrateAsset.IdentitySource.UNVERIFIED);
                Optional<CrateAsset> verified = matchIdentity(asset, found);
                if (verified.isPresent()) return verified;
            } catch (PlaceholderCrate ignored) {
                // A reserved registry name is not an installable suggestion for this asset.
            } catch (IOException error) {
                if (error.getMessage() == null || !error.getMessage().startsWith("HTTP 404 ")) failure = error;
            }
        }
        if (failure != null) throw failure;
        return Optional.empty();
    }

    /** Accepts a published package only when the catalog link names it or points to its repository. */
    static Optional<CrateAsset> matchIdentity(CrateAsset asset, CrateAsset metadata) {
        String linkedName = cratesIoName(asset.url());
        CrateAsset.IdentitySource source = linkedName != null && equivalentName(linkedName, metadata.name())
                ? CrateAsset.IdentitySource.CRATES_IO_LINK
                : matchingGithubRepository(asset.url(), metadata.repository())
                ? CrateAsset.IdentitySource.REPOSITORY_MATCH : null;
        return source == null ? Optional.empty() : Optional.of(metadata.withIdentity(metadata.name(), source));
    }

    /** Verifies a package name explicitly entered by the user against crates.io. */
    public static CrateAsset identifyUserName(CrateAsset asset, String name) throws IOException {
        String candidate = name == null ? "" : name.trim();
        if (!candidate.matches("[A-Za-z][A-Za-z0-9_-]*"))
            throw new IOException("Enter a valid crates.io package name.");
        return crateMetadata(asset, candidate, CrateAsset.IdentitySource.USER_PROVIDED);
    }

    /** Searches published package names for a correction choice. */
    public static List<String> suggestCrateNames(CrateAsset asset) throws IOException {
        String canonicalRepository = canonicalGithubUrl(asset.url());
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        queries.add(asset.title());
        String repositoryName = githubRepositoryName(asset.url());
        if (repositoryName != null) queries.add(repositoryName);
        List<CrateSuggestion> suggestions = new ArrayList<>();
        IOException failure = null;
        boolean searched = false;
        int firstOrder = 0;
        for (String query : queries) {
            if (query.isBlank()) continue;
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            try {
                suggestions.addAll(searchSuggestions(asset,
                        canonicalRepository,
                        get("https://crates.io/api/v1/crates?q=" + encoded + "&per_page=100"), firstOrder));
                searched = true;
            } catch (IOException error) { failure = error; }
            firstOrder += 100;
        }
        if (!searched && failure != null) throw failure;
        return rankedSuggestions(suggestions);
    }

    /** Puts packages from the linked repository first, then keeps crates.io search order. */
    static List<String> parseCrateSuggestions(CrateAsset asset, String json) {
        return parseCrateSuggestions(asset, asset.url(), json);
    }

    static List<String> parseCrateSuggestions(CrateAsset asset, String canonicalRepository, String json) {
        return rankedSuggestions(searchSuggestions(asset, canonicalRepository, json, 0));
    }

    private record CrateSuggestion(String name, boolean sameRepository, int nameMatch, int order) {}

    private static List<CrateSuggestion> searchSuggestions(CrateAsset asset, String canonicalRepository,
                                                            String json, int firstOrder) {
        List<CrateSuggestion> suggestions = new ArrayList<>();
        JsonObject data = JsonParser.parseString(json).getAsJsonObject();
        if (!data.has("crates") || !data.get("crates").isJsonArray()) return suggestions;
        int order = firstOrder;
        for (JsonElement entry : data.getAsJsonArray("crates")) {
            JsonObject item = entry.getAsJsonObject();
            String name = string(item, "id");
            if (name != null && name.matches("[A-Za-z][A-Za-z0-9_-]*") && !isPlaceholder(item))
                suggestions.add(new CrateSuggestion(name,
                        matchingGithubRepository(canonicalRepository, string(item, "repository")),
                        nameMatch(asset.name(), name), order));
            order++;
        }
        return suggestions;
    }

    private static List<String> rankedSuggestions(List<CrateSuggestion> suggestions) {
        boolean repositoryMatches = suggestions.stream().anyMatch(CrateSuggestion::sameRepository);
        suggestions.removeIf(item -> repositoryMatches ? !item.sameRepository() : item.nameMatch() == 0);
        suggestions.sort(Comparator.comparingInt(CrateSuggestion::nameMatch).reversed()
                .thenComparingInt(CrateSuggestion::order));
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (CrateSuggestion suggestion : suggestions) names.add(suggestion.name());
        return names.stream().limit(8).toList();
    }

    private static int nameMatch(String titleName, String packageName) {
        String base = titleName.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        String name = packageName.toLowerCase(Locale.ROOT).replace('-', '_');
        if (name.matches(java.util.regex.Pattern.quote(base) + "[23]d")) return 3;
        if (name.equals(base)) return 2;
        return name.startsWith(base + "_") || name.startsWith(base) ? 1 : 0;
    }

    private static boolean isPlaceholder(JsonObject data) {
        String description = string(data, "description");
        return description != null && description.toLowerCase(Locale.ROOT).contains("reserved for");
    }

    private static final class PlaceholderCrate extends IOException {
        private PlaceholderCrate(String name) { super(name + " is a reserved crate name."); }
    }

    /** Reads the canonical package name and metadata returned by crates.io. */
    private static CrateAsset crateMetadata(CrateAsset asset, String candidate,
                                             CrateAsset.IdentitySource source) throws IOException {
        JsonObject data = json("https://crates.io/api/v1/crates/" + candidate).getAsJsonObject("crate");
        if (data == null) throw new IOException("crates.io returned no crate metadata for " + candidate + ".");
        if (isPlaceholder(data)) throw new PlaceholderCrate(candidate);
        String canonical = string(data, "id");
        if (canonical == null || !canonical.matches("[A-Za-z][A-Za-z0-9_-]*"))
            throw new IOException("crates.io returned an invalid package name for " + candidate + ".");
        return asset.withIdentity(canonical, source).withMetadata(string(data, "max_stable_version"),
                number(data, "downloads"), number(data, "recent_downloads"),
                string(data, "repository"), string(data, "updated_at"));
    }

    /** Extracts a package name only from an actual crates.io crate URL. */
    static String cratesIoName(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equals(uri.getScheme()) || !"crates.io".equalsIgnoreCase(uri.getHost())) return null;
            String[] path = uri.getPath().split("/");
            return path.length == 3 && path[1].equals("crates") && path[2].matches("[A-Za-z][A-Za-z0-9_-]*")
                    ? path[2] : null;
        } catch (Exception ignored) { return null; }
    }

    /** Compares the GitHub repository behind a catalog card and a crates.io entry. */
    static boolean matchingGithubRepository(String catalogUrl, String crateRepository) {
        String catalog = githubRepositoryNameAndOwner(catalogUrl);
        return catalog != null && catalog.equals(githubRepositoryNameAndOwner(crateRepository));
    }

    private static String githubRepositoryName(String url) {
        String path = githubRepositoryNameAndOwner(url);
        return path == null ? null : path.substring(path.indexOf('/') + 1);
    }

    private static String githubRepositoryNameAndOwner(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equals(uri.getScheme()) || !"github.com".equalsIgnoreCase(uri.getHost())) return null;
            String[] path = uri.getPath().split("/");
            if (path.length < 3 || path[1].isBlank() || path[2].isBlank()) return null;
            return (path[1] + "/" + path[2].replaceFirst("\\.git$", "")).toLowerCase(java.util.Locale.ROOT);
        } catch (Exception ignored) { return null; }
    }

    /** Follows a GitHub rename when matching a catalog link to registry repositories. */
    private static String canonicalGithubUrl(String url) {
        if (githubRepositoryNameAndOwner(url) == null) return url;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                    .header("User-Agent", AGENT).method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<Void> response = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
            String canonical = response.uri().toString();
            return response.statusCode() >= 200 && response.statusCode() < 300
                    && githubRepositoryNameAndOwner(canonical) != null ? canonical : url;
        } catch (Exception ignored) { return url; }
    }

    private static boolean equivalentName(String first, String second) {
        return first.replace('-', '_').equalsIgnoreCase(second.replace('-', '_'));
    }

    /** Adds verified crate release, download, and repository metadata. */
    public static Optional<CrateAsset> enrich(CrateAsset asset) {
        try {
            Optional<CrateAsset> verified = identify(asset);
            if (verified.isEmpty()) return Optional.empty();
            CrateAsset result = verified.get();
            if (result.name().equals("lightyear") && BevyCompatibility.supports("0.19.1", result.bevyTags())) {
                String preferred = preferredLightyearRelease(get("https://crates.io/api/v1/crates/lightyear/versions"));
                if (preferred != null) result = result.withMetadata(preferred, result.downloads(),
                        result.recentDownloads(), result.repository(), result.updatedAt());
            }
            return Optional.of(result);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    /** Finds a stable crate release compatible with the selected Bevy version. */
    public static Optional<CrateAsset> compatibleRelease(CrateAsset asset, String bevyVersion) throws IOException {
        if (!asset.hasVerifiedIdentity()) throw new IOException("The crates.io package name is not verified.");
        if (!BevyCompatibility.supports(bevyVersion, asset.bevyTags())) return Optional.empty();
        if (asset.version() != null && allowedRelease(asset.name(), asset.version(), bevyVersion)
                && releaseSupports(asset.name(), asset.version(), bevyVersion)) return Optional.of(asset);
        JsonObject data = json("https://crates.io/api/v1/crates/" + asset.name() + "/versions");
        int checked = 0;
        for (JsonElement entry : data.getAsJsonArray("versions")) {
            JsonObject release = entry.getAsJsonObject();
            String candidate = string(release, "num");
            if (candidate == null || !candidate.matches("\\d+\\.\\d+\\.\\d+") || release.get("yanked").getAsBoolean()) continue;
            if (candidate.equals(asset.version())) continue;
            if (!allowedRelease(asset.name(), candidate, bevyVersion)) continue;
            if (++checked > 20) break;
            if (releaseSupports(asset.name(), candidate, bevyVersion))
                return Optional.of(asset.withMetadata(candidate, asset.downloads(), asset.recentDownloads(), asset.repository(), asset.updatedAt()));
        }
        return Optional.empty();
    }

    /** Applies the selected Lightyear release policy. */
    private static boolean allowedRelease(String crate, String candidate, String bevyVersion) {
        return !crate.equals("lightyear") || !bevyVersion.matches("0\\.19\\.\\d+") || candidate.matches("0\\.28\\.\\d+");
    }

    /** Chooses the latest non-yanked Lightyear 0.28 release. */
    public static String preferredLightyearRelease(String json) {
        String latest = null;
        for (JsonElement entry : JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("versions")) {
            JsonObject release = entry.getAsJsonObject();
            String number = string(release, "num");
            if (number != null && number.matches("0\\.28\\.\\d+") && !release.get("yanked").getAsBoolean()
                    && (latest == null || BevyCompatibility.compare(number, latest) > 0)) latest = number;
        }
        return latest;
    }

    /** Checks direct Bevy dependencies in a crates.io response. */
    public static boolean dependenciesSupport(String json, String bevyVersion) {
        for (JsonElement entry : JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("dependencies")) {
            JsonObject dependency = entry.getAsJsonObject();
            String name = string(dependency, "crate_id");
            String kind = string(dependency, "kind");
            if (name == null || !BEVY_CRATES.contains(name) || "dev".equals(kind)) continue;
            if (!BevyCompatibility.matches(bevyVersion, string(dependency, "req"))) return false;
        }
        return true;
    }

    /** Checks a published crate release against Bevy dependencies. */
    private static boolean releaseSupports(String name, String version, String bevyVersion) throws IOException {
        return dependenciesSupport(get("https://crates.io/api/v1/crates/" + name + "/" + version + "/dependencies"), bevyVersion);
    }

    /** Builds the crates.io README endpoint for an asset. */
    public static String readmeUrl(CrateAsset asset) {
        if (asset.version() == null) return asset.url();
        return "https://crates.io/api/v1/crates/" + asset.name() + "/" + asset.version() + "/readme";
    }

    /** Loads the complete README HTML and its final source URL. */
    public static ReadmePage readmePage(CrateAsset asset) throws IOException {
        if (asset.version() == null) throw new IOException("No crates.io release is available.");
        HttpResponse<String> response = request(readmeUrl(asset));
        String location = readmeLocation(response.body());
        if (location != null) response = request(location);
        else if (response.headers().firstValue("Content-Type").orElse("").contains("application/json"))
            throw new IOException("crates.io did not provide a readable README URL.");
        return new ReadmePage(response.body(), response.uri().toString());
    }

    /** Extracts and validates a crates.io static README URL. */
    public static String readmeLocation(String body) {
        if (!body.stripLeading().startsWith("{")) return null;
        try {
            String url = string(JsonParser.parseString(body).getAsJsonObject(), "url");
            if (url == null) return null;
            URI uri = URI.create(url);
            return uri.getScheme().equals("https") && uri.getHost().equals("static.crates.io")
                    && uri.getPath().startsWith("/readmes/") ? url : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Downloads a Markdown README from the repository. */
    public static String rawGithubReadme(CrateAsset asset) throws IOException {
        String base = githubRawBase(asset);
        if (base == null) throw new IOException("No GitHub repository is available.");
        IOException last = null;
        for (String file : List.of("README.md", "Readme.md", "readme.md")) {
            try { return get(base + file); }
            catch (IOException e) { last = e; }
        }
        throw last == null ? new IOException("GitHub README.md is unavailable.") : last;
    }

    /** Builds the repository base URL for relative README assets. */
    public static String githubRawBase(CrateAsset asset) {
        String repository = asset.repository() != null ? asset.repository() : asset.url();
        if (repository == null || !repository.startsWith("https://github.com/")) return null;
        String[] parts = repository.substring("https://github.com/".length()).split("/");
        if (parts.length < 2) return null;
        return "https://raw.githubusercontent.com/" + parts[0] + "/" + parts[1].replaceFirst("\\.git$", "") + "/HEAD/";
    }

    /** Builds the GitHub README API URL. */
    public static String githubReadmeApi(CrateAsset asset) {
        String repository = asset.repository() != null ? asset.repository() : asset.url();
        if (repository == null) return null;
        String path = repository.replaceFirst("^https?://github\\.com/", "").replaceFirst("\\.git$", "");
        if (path.equals(repository)) return null;
        String[] parts = path.split("/");
        if (parts.length < 2) return null;
        return "https://api.github.com/repos/" + parts[0] + "/" + parts[1] + "/readme";
    }

    /** Loads README metadata from the GitHub API. */
    public static JsonObject githubReadme(CrateAsset asset) throws IOException {
        String url = githubReadmeApi(asset);
        if (url == null) throw new IOException("No GitHub repository is available.");
        return json(url);
    }

    /** Reads an optional string from a JSON object. */
    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }
    /** Reads an optional numeric value from a JSON object. */
    private static long number(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsLong() : -1;
    }
    /** Downloads and parses a JSON object. */
    private static JsonObject json(String url) throws IOException { return JsonParser.parseString(get(url)).getAsJsonObject(); }
    /** Downloads the response body for a URL. */
    private static String get(String url) throws IOException { return request(url).body(); }
    /** Sends an HTTP request and verifies a successful response. */
    private static HttpResponse<String> request(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("User-Agent", AGENT).header("Accept", "application/json, text/html;q=0.9").GET().build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IOException("HTTP " + response.statusCode() + " for " + url);
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }
    }
}
