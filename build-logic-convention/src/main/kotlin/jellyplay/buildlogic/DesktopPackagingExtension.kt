package jellyplay.buildlogic

import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

/**
 * The version/release-channel vocabulary the desktop shell's packaging
 * config (and the About screen, through desktop-build.properties) reads.
 * Relocated verbatim from :apps:desktop's build script when the packaging
 * mechanics became the [DesktopPackagingPlugin] convention plugin — the
 * grammar rules and their failure modes are unchanged (see the plugin's
 * KDoc for the relocated history).
 *
 * Managed type (abstract class + Gradle-instantiated properties); every
 * field is a [Property] (a kind of [Provider], so consumers just `.get()`
 * it), set once by the plugin at apply time.
 */
abstract class DesktopPackagingExtension {

    /**
     * The jpackage/packageVersion grammar: strictly numeric x.y.z. Defaults
     * to "0.1.0" on dev machines (no -PjellyplayVersion); a non-conforming
     * explicit value fails CONFIGURATION (the plugin throws eagerly), not
     * packaging execution — a packaged 0.1.0 that believed it was 2.0.0 is
     * the failure mode this guard exists for.
     */
    abstract val packageVersion: Property<String>

    /**
     * The display version (About screen / release tags): -PjellyplayVersionName
     * when the release lane passes one (e.g. 0.11.0-alpha.1), else
     * [packageVersion].
     */
    abstract val displayVersion: Property<String>

    /**
     * "release" only when -PjellyplayVersion was EXPLICITLY passed (a CI
     * release lane produced this build); "dev" otherwise — including the
     * 0.1.0 fallback. The desktop auto-update decision reads it (see
     * docs/adr/desktop-auto-update.md): a dev build is up-to-date BY
     * CONSTRUCTION because no feed entry can offer an update to a build
     * that was never part of a release channel.
     */
    abstract val releaseChannel: Property<String>

    /**
     * [packageVersion] shifted 0.x.y -> 1.x.y, for the macOS BUNDLE version
     * only: Apple's jpackage rejects app-versions whose first segment is
     * zero (plist CFBundleVersion rule), so the pre-1.0 line could never
     * package a dmg as-is. msi/deb keep the real numeric triple; the About
     * screen and release tags carry [displayVersion], so this stays cosmetic.
     */
    abstract val macOsBundleVersion: Property<String>
}
