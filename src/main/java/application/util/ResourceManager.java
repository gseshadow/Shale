package application.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javafx.scene.image.Image;

/**
 * Centralises access to classpath resources bundled with the application.  This
 * avoids the brittle pattern of referencing files relative to the working
 * directory and makes it possible to package the application as a standalone
 * JAR.  All resources exposed here live under the {@code Local} directory on
 * the classpath.
 */
public final class ResourceManager {

    private static final String RESOURCE_ROOT = "/Local";
    private static final String BACKGROUND_INDEX = RESOURCE_ROOT + "/Backgrounds/backgrounds.list";
    private static final List<String> BACKGROUND_FILES = loadBackgroundIndex();

    /** Default background image shipped with the application. */
    public static final String DEFAULT_BACKGROUND_RESOURCE = "Backgrounds/Default.png";

    private ResourceManager() {
    }

    /**
     * Loads an {@link Image} from the packaged resources.  The provided path is
     * resolved relative to the {@code Local} directory.
     *
     * @param resourcePath resource path relative to {@code Local}
     * @return loaded {@link Image}
     * @throws IllegalArgumentException if the resource cannot be found
     */
    public static Image loadImage(String resourcePath) {
        URL resource = getResource(resourcePath);
        if (resource == null) {
            throw new IllegalArgumentException("Unable to locate resource: " + resourcePath);
        }
        return new Image(resource.toExternalForm());
    }

    /**
     * Opens the requested resource as an {@link InputStream}.
     *
     * @param resourcePath resource path relative to {@code Local}
     * @return input stream representing the resource
     * @throws IOException if the resource cannot be found
     */
    public static InputStream openResource(String resourcePath) throws IOException {
        InputStream stream = ResourceManager.class.getResourceAsStream(toClasspathLocation(resourcePath));
        if (stream == null) {
            throw new IOException("Unable to locate resource: " + resourcePath);
        }
        return stream;
    }

    /**
     * Copies a resource from the classpath to the specified destination on the
     * filesystem.
     *
     * @param resourcePath resource path relative to {@code Local}
     * @param destination absolute destination path
     * @param options copy behaviour options
     * @throws IOException if an error occurs while copying the resource
     */
    public static void copyResource(String resourcePath, Path destination, StandardCopyOption... options) throws IOException {
        Objects.requireNonNull(destination, "destination");
        try (InputStream stream = openResource(resourcePath)) {
            Path parent = destination.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (options == null || options.length == 0) {
                Files.copy(stream, destination);
            } else {
                Files.copy(stream, destination, options);
            }
        }
    }

    /**
     * Copies all bundled background images to the provided destination
     * directory.
     *
     * @param destination directory where background assets should be written
     * @throws IOException if any asset cannot be copied
     */
    public static void copyBackgroundAssets(Path destination) throws IOException {
        Objects.requireNonNull(destination, "destination");
        Files.createDirectories(destination);
        for (String background : BACKGROUND_FILES) {
            copyResource("Backgrounds/" + background, destination.resolve(background), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * @return immutable list of bundled background image file names
     */
    public static List<String> getBackgroundFiles() {
        return BACKGROUND_FILES;
    }

    private static URL getResource(String resourcePath) {
        return ResourceManager.class.getResource(toClasspathLocation(resourcePath));
    }

    private static String toClasspathLocation(String resourcePath) {
        return RESOURCE_ROOT + (resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath);
    }

    private static List<String> loadBackgroundIndex() {
        try (InputStream stream = ResourceManager.class.getResourceAsStream(BACKGROUND_INDEX)) {
            if (stream == null) {
                return Collections.emptyList();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .collect(Collectors.toUnmodifiableList());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read bundled background index", ex);
        }
    }
}
