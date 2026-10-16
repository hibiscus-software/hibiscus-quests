/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.api;

/**
 * Constants describing the Hibiscus Quests public API.
 *
 * <p>The API follows <a href="https://semver.org/">semantic versioning</a>, independently of the Hibiscus Quests mod
 * version:
 * <ul>
 *   <li>a <b>patch</b> bump fixes documentation or behaviour without changing any type or method;</li>
 *   <li>a <b>minor</b> bump adds types, methods (as default methods on interfaces) or enum constants in a backwards
 *       compatible way, so mods built against an older minor version keep working;</li>
 *   <li>a <b>major</b> bump changes or removes a type or method.</li>
 * </ul>
 * Before 1.0.0 the API may still change between minor versions. From 1.0.0 it only grows.
 */
public final class HibiscusQuestsApi {
    /** The Hibiscus Quests mod id, which is also the namespace of the built-in task and reward types. */
    public static final String MOD_ID = "hibiscusquests";

    /** The full API version, {@code MAJOR.MINOR.PATCH}. This is also the version of the published API jar. */
    public static final String API_VERSION = "1.0.0";

    /** The major part of {@link #API_VERSION}. */
    public static final int API_VERSION_MAJOR = 1;

    /** The minor part of {@link #API_VERSION}. */
    public static final int API_VERSION_MINOR = 0;

    /** The patch part of {@link #API_VERSION}. */
    public static final int API_VERSION_PATCH = 0;

    private HibiscusQuestsApi() {}
}
