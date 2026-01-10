package org.gradle.accessors.dm;

import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.plugin.use.PluginDependency;
import org.gradle.api.artifacts.ExternalModuleDependencyBundle;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.provider.Provider;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.internal.catalog.AbstractExternalDependencyFactory;
import org.gradle.api.internal.catalog.DefaultVersionCatalog;
import java.util.Map;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParser;
import javax.inject.Inject;

/**
 * A catalog of dependencies accessible via the `libs` extension.
 */
@NonNullApi
public class LibrariesForLibs extends AbstractExternalDependencyFactory {

    private final AbstractExternalDependencyFactory owner = this;
    private final AndroidxLibraryAccessors laccForAndroidxLibraryAccessors = new AndroidxLibraryAccessors(owner);
    private final AniyomiLibraryAccessors laccForAniyomiLibraryAccessors = new AniyomiLibraryAccessors(owner);
    private final CoroutinesLibraryAccessors laccForCoroutinesLibraryAccessors = new CoroutinesLibraryAccessors(owner);
    private final GradleLibraryAccessors laccForGradleLibraryAccessors = new GradleLibraryAccessors(owner);
    private final InjektLibraryAccessors laccForInjektLibraryAccessors = new InjektLibraryAccessors(owner);
    private final KotlinLibraryAccessors laccForKotlinLibraryAccessors = new KotlinLibraryAccessors(owner);
    private final VersionAccessors vaccForVersionAccessors = new VersionAccessors(providers, config);
    private final BundleAccessors baccForBundleAccessors = new BundleAccessors(objects, providers, config, attributesFactory, capabilityNotationParser);
    private final PluginAccessors paccForPluginAccessors = new PluginAccessors(providers, config);

    @Inject
    public LibrariesForLibs(DefaultVersionCatalog config, ProviderFactory providers, ObjectFactory objects, ImmutableAttributesFactory attributesFactory, CapabilityNotationParser capabilityNotationParser) {
        super(config, providers, objects, attributesFactory, capabilityNotationParser);
    }

        /**
         * Creates a dependency provider for jsoup (org.jsoup:jsoup)
     * with version '1.18.1'.
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getJsoup() {
            return create("jsoup");
    }

        /**
         * Creates a dependency provider for jsunpacker (dev.datlag.jsunpacker:jsunpacker)
     * with version '1.0.1'.
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getJsunpacker() {
            return create("jsunpacker");
    }

        /**
         * Creates a dependency provider for okhttp (com.squareup.okhttp3:okhttp)
     * with versionRef 'okhttp.version'.
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getOkhttp() {
            return create("okhttp");
    }

        /**
         * Creates a dependency provider for quickjs (app.cash.quickjs:quickjs-android)
     * with version '0.9.2'.
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getQuickjs() {
            return create("quickjs");
    }

    /**
     * Returns the group of libraries at androidx
     */
    public AndroidxLibraryAccessors getAndroidx() {
        return laccForAndroidxLibraryAccessors;
    }

    /**
     * Returns the group of libraries at aniyomi
     */
    public AniyomiLibraryAccessors getAniyomi() {
        return laccForAniyomiLibraryAccessors;
    }

    /**
     * Returns the group of libraries at coroutines
     */
    public CoroutinesLibraryAccessors getCoroutines() {
        return laccForCoroutinesLibraryAccessors;
    }

    /**
     * Returns the group of libraries at gradle
     */
    public GradleLibraryAccessors getGradle() {
        return laccForGradleLibraryAccessors;
    }

    /**
     * Returns the group of libraries at injekt
     */
    public InjektLibraryAccessors getInjekt() {
        return laccForInjektLibraryAccessors;
    }

    /**
     * Returns the group of libraries at kotlin
     */
    public KotlinLibraryAccessors getKotlin() {
        return laccForKotlinLibraryAccessors;
    }

    /**
     * Returns the group of versions at versions
     */
    public VersionAccessors getVersions() {
        return vaccForVersionAccessors;
    }

    /**
     * Returns the group of bundles at bundles
     */
    public BundleAccessors getBundles() {
        return baccForBundleAccessors;
    }

    /**
     * Returns the group of plugins at plugins
     */
    public PluginAccessors getPlugins() {
        return paccForPluginAccessors;
    }

    public static class AndroidxLibraryAccessors extends SubDependencyFactory {

        public AndroidxLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

            /**
             * Creates a dependency provider for annotation (androidx.annotation:annotation)
         * with version '1.7.1'.
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getAnnotation() {
                return create("androidx.annotation");
        }

            /**
             * Creates a dependency provider for preference (androidx.preference:preference)
         * with version '1.2.1'.
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getPreference() {
                return create("androidx.preference");
        }

    }

    public static class AniyomiLibraryAccessors extends SubDependencyFactory {

        public AniyomiLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

            /**
             * Creates a dependency provider for lib (com.github.komikku-app:aniyomi-extensions-lib)
         * with version 'F078000101'.
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getLib() {
                return create("aniyomi.lib");
        }

    }

    public static class CoroutinesLibraryAccessors extends SubDependencyFactory {

        public CoroutinesLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

            /**
             * Creates a dependency provider for android (org.jetbrains.kotlinx:kotlinx-coroutines-android)
         * with versionRef 'coroutines.version'.
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getAndroid() {
                return create("coroutines.android");
        }

            /**
             * Creates a dependency provider for core (org.jetbrains.kotlinx:kotlinx-coroutines-core)
         * with versionRef 'coroutines.version'.
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getCore() {
                return create("coroutines.core");
        }

    }

    public static class GradleLibraryAccessors extends SubDependencyFactory {

        public GradleLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

            /**
             * Creates a dependency provider for agp (com.android.tools.build:gradle)
         * with versionRef 'agp.version'.
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getAgp() {
                return create("gradle.agp");
        }

            /**
             * Creates a dependency provider for kotlin (org.jetbrains.kotlin:kotlin-gradle-plugin)
         * with versionRef 'kotlin.version'.
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getKotlin() {
                return create("gradle.kotlin");
        }

            /**
             * Creates a dependency provider for kotlinter (org.jmailen.gradle:kotlinter-gradle)
         * with version '3.15.0'.
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getKotlinter() {
                return create("gradle.kotlinter");
        }

            /**
             * Creates a dependency provider for serialization (org.jetbrains.kotlin:kotlin-serialization)
         * with versionRef 'kotlin.version'.
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getSerialization() {
                return create("gradle.serialization");
        }

    }

    public static class InjektLibraryAccessors extends SubDependencyFactory {

        public InjektLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

            /**
             * Creates a dependency provider for core (com.github.mihonapp:injekt)
         * with version '91edab2317'.
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getCore() {
                return create("injekt.core");
        }

    }

    public static class KotlinLibraryAccessors extends SubDependencyFactory {
        private final KotlinStdlibLibraryAccessors laccForKotlinStdlibLibraryAccessors = new KotlinStdlibLibraryAccessors(owner);

        public KotlinLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

            /**
             * Creates a dependency provider for json (org.jetbrains.kotlinx:kotlinx-serialization-json)
         * with versionRef 'serialization.version'.
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getJson() {
                return create("kotlin.json");
        }

            /**
             * Creates a dependency provider for protobuf (org.jetbrains.kotlinx:kotlinx-serialization-protobuf)
         * with versionRef 'serialization.version'.
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getProtobuf() {
                return create("kotlin.protobuf");
        }

            /**
             * Creates a dependency provider for reflect (org.jetbrains.kotlin:kotlin-reflect)
         * with versionRef 'kotlin.version'.
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getReflect() {
                return create("kotlin.reflect");
        }

        /**
         * Returns the group of libraries at kotlin.stdlib
         */
        public KotlinStdlibLibraryAccessors getStdlib() {
            return laccForKotlinStdlibLibraryAccessors;
        }

    }

    public static class KotlinStdlibLibraryAccessors extends SubDependencyFactory implements DependencyNotationSupplier {

        public KotlinStdlibLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

            /**
             * Creates a dependency provider for stdlib (org.jetbrains.kotlin:kotlin-stdlib)
         * with versionRef 'kotlin.version'.
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> asProvider() {
                return create("kotlin.stdlib");
        }

            /**
             * Creates a dependency provider for jdk7 (org.jetbrains.kotlin:kotlin-stdlib-jdk7)
         * with versionRef 'kotlin.version'.
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getJdk7() {
                return create("kotlin.stdlib.jdk7");
        }

            /**
             * Creates a dependency provider for jdk8 (org.jetbrains.kotlin:kotlin-stdlib-jdk8)
         * with versionRef 'kotlin.version'.
             * This dependency was declared in catalog libs.versions.toml
             */
            public Provider<MinimalExternalModuleDependency> getJdk8() {
                return create("kotlin.stdlib.jdk8");
        }

    }

    public static class VersionAccessors extends VersionFactory  {

        private final AgpVersionAccessors vaccForAgpVersionAccessors = new AgpVersionAccessors(providers, config);
        private final CoroutinesVersionAccessors vaccForCoroutinesVersionAccessors = new CoroutinesVersionAccessors(providers, config);
        private final KotlinVersionAccessors vaccForKotlinVersionAccessors = new KotlinVersionAccessors(providers, config);
        private final OkhttpVersionAccessors vaccForOkhttpVersionAccessors = new OkhttpVersionAccessors(providers, config);
        private final SerializationVersionAccessors vaccForSerializationVersionAccessors = new SerializationVersionAccessors(providers, config);
        public VersionAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

        /**
         * Returns the group of versions at versions.agp
         */
        public AgpVersionAccessors getAgp() {
            return vaccForAgpVersionAccessors;
        }

        /**
         * Returns the group of versions at versions.coroutines
         */
        public CoroutinesVersionAccessors getCoroutines() {
            return vaccForCoroutinesVersionAccessors;
        }

        /**
         * Returns the group of versions at versions.kotlin
         */
        public KotlinVersionAccessors getKotlin() {
            return vaccForKotlinVersionAccessors;
        }

        /**
         * Returns the group of versions at versions.okhttp
         */
        public OkhttpVersionAccessors getOkhttp() {
            return vaccForOkhttpVersionAccessors;
        }

        /**
         * Returns the group of versions at versions.serialization
         */
        public SerializationVersionAccessors getSerialization() {
            return vaccForSerializationVersionAccessors;
        }

    }

    public static class AgpVersionAccessors extends VersionFactory  {

        public AgpVersionAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

            /**
             * Returns the version associated to this alias: agp.version (8.2.2)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getVersion() { return getVersion("agp.version"); }

    }

    public static class CoroutinesVersionAccessors extends VersionFactory  {

        public CoroutinesVersionAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

            /**
             * Returns the version associated to this alias: coroutines.version (1.8.1)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getVersion() { return getVersion("coroutines.version"); }

    }

    public static class KotlinVersionAccessors extends VersionFactory  {

        public KotlinVersionAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

            /**
             * Returns the version associated to this alias: kotlin.version (2.0.0)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getVersion() { return getVersion("kotlin.version"); }

    }

    public static class OkhttpVersionAccessors extends VersionFactory  {

        public OkhttpVersionAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

            /**
             * Returns the version associated to this alias: okhttp.version (5.0.0-alpha.16)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getVersion() { return getVersion("okhttp.version"); }

    }

    public static class SerializationVersionAccessors extends VersionFactory  {

        public SerializationVersionAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

            /**
             * Returns the version associated to this alias: serialization.version (1.7.1)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getVersion() { return getVersion("serialization.version"); }

    }

    public static class BundleAccessors extends BundleFactory {

        public BundleAccessors(ObjectFactory objects, ProviderFactory providers, DefaultVersionCatalog config, ImmutableAttributesFactory attributesFactory, CapabilityNotationParser capabilityNotationParser) { super(objects, providers, config, attributesFactory, capabilityNotationParser); }

            /**
             * Creates a dependency bundle provider for common which is an aggregate for the following dependencies:
             * <ul>
             *    <li>org.jsoup:jsoup</li>
             *    <li>app.cash.quickjs:quickjs-android</li>
             *    <li>dev.datlag.jsunpacker:jsunpacker</li>
             * </ul>
             * This bundle was declared in catalog libs.versions.toml
             */
            public Provider<ExternalModuleDependencyBundle> getCommon() {
                return createBundle("common");
            }

            /**
             * Creates a dependency bundle provider for provided which is an aggregate for the following dependencies:
             * <ul>
             *    <li>org.jetbrains.kotlin:kotlin-stdlib</li>
             *    <li>org.jetbrains.kotlin:kotlin-stdlib-jdk8</li>
             *    <li>org.jetbrains.kotlin:kotlin-stdlib-jdk7</li>
             *    <li>org.jetbrains.kotlin:kotlin-reflect</li>
             *    <li>org.jetbrains.kotlinx:kotlinx-coroutines-core</li>
             *    <li>org.jetbrains.kotlinx:kotlinx-coroutines-android</li>
             *    <li>com.github.mihonapp:injekt</li>
             *    <li>com.squareup.okhttp3:okhttp</li>
             *    <li>com.github.komikku-app:aniyomi-extensions-lib</li>
             *    <li>androidx.preference:preference</li>
             *    <li>androidx.annotation:annotation</li>
             *    <li>org.jetbrains.kotlinx:kotlinx-serialization-protobuf</li>
             *    <li>org.jetbrains.kotlinx:kotlinx-serialization-json</li>
             * </ul>
             * This bundle was declared in catalog libs.versions.toml
             */
            public Provider<ExternalModuleDependencyBundle> getProvided() {
                return createBundle("provided");
            }

    }

    public static class PluginAccessors extends PluginFactory {

        public PluginAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

    }

}
