/*
 * MIT License
 *
 * Copyright (c) 2020 Artipie
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.artipie.maven;

import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.cache.StorageCache;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.http.client.jetty.JettyClientSlices;
import com.artipie.http.slice.LoggingSlice;
import com.artipie.maven.http.MavenProxySlice;
import com.artipie.vertx.VertxSliceServer;
import com.jcabi.log.Logger;
import io.vertx.reactivex.core.Vertx;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.cactoos.list.ListOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.AllOf;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.StringContains;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;

/**
 * Integration test for {@link com.artipie.maven.http.MavenProxySlice}.
 *
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 * @since 0.11
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
@EnabledOnOs({OS.LINUX, OS.MAC})
final class MavenProxyIT {

    /**
     * Vertx instance.
     */
    private static final Vertx VERTX = Vertx.vertx();

    /**
     * Temporary directory for all tests.
     * @checkstyle VisibilityModifierCheck (3 lines)
     */
    @TempDir
    Path tmp;

    /**
     * Vertx slice server instance.
     */
    private VertxSliceServer server;

    /**
     * Container.
     */
    private GenericContainer<?> cntn;

    /**
     * Storage.
     */
    private Storage storage;

    /**
     * Vertx slice server port.
     */
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        final JettyClientSlices slices = new JettyClientSlices();
        slices.start();
        this.storage = new InMemoryStorage();
        this.server = new VertxSliceServer(
            MavenProxyIT.VERTX,
            new LoggingSlice(
                new MavenProxySlice(
                    slices,
                    URI.create("https://repo.maven.apache.org/maven2"),
                    new StorageCache(this.storage)
            ))
        );
        this.port = this.server.start();
        Testcontainers.exposeHostPorts(this.port);
        this.cntn = new GenericContainer<>("centos:centos8")
            .withCommand("tail", "-f", "/dev/null")
            .withWorkingDirectory("/home/")
            .withFileSystemBind(this.tmp.toString(), "/home");
        this.cntn.start();
        this.exec("yum", "-y", "install", "maven");
    }

    @AfterEach
    void tearDown() {
        this.server.close();
        this.cntn.stop();
    }

    @AfterAll
    static void close() {
        MavenProxyIT.VERTX.close();
    }

    @Test
    void shouldGetArtifactFromCentralAndSaveInCache() throws Exception {
        this.settings();
        final String artifact = "-Dartifact=args4j:args4j:2.32:jar";
        MatcherAssert.assertThat(
            "Artifact wasn't downloaded",
            this.exec(
                "mvn", "-s", "/home/settings.xml", "dependency:get", artifact
            ).replaceAll("\n", ""),
            new AllOf<>(
                Arrays.asList(
                    new StringContains("BUILD SUCCESS"),
                    new StringContains(
                        String.format(
                            // @checkstyle LineLengthCheck (1 line)
                            "Downloaded from my-repo: http://host.testcontainers.internal:%s/args4j/args4j/2.32/args4j-2.32.jar (154 kB",
                            this.port
                        )
                    )
                )
            )
        );
        MatcherAssert.assertThat(
            "Artifact wasn't in storage",
            this.storage.exists(new Key.From("args4j", "args4j", "2.32", "args4j-2.32.jar"))
                .toCompletableFuture().join(),
            new IsEqual<>(true)
        );
    }

    private String exec(final String... command) throws Exception {
        Logger.debug(this, "Command:\n%s", String.join(" ", command));
        return this.cntn.execInContainer(command).getStdout();
    }

    private void settings() throws IOException {
        final Path setting = this.tmp.resolve("settings.xml");
        setting.toFile().createNewFile();
        Files.write(
            setting,
            new ListOf<String>(
                "<settings>",
                "    <profiles>",
                "        <profile>",
                "            <id>artipie</id>",
                "            <repositories>",
                "                <repository>",
                "                    <id>my-repo</id>",
                String.format("<url>http://host.testcontainers.internal:%d/</url>", this.port),
                "                </repository>",
                "            </repositories>",
                "        </profile>",
                "    </profiles>",
                "    <activeProfiles>",
                "        <activeProfile>artipie</activeProfile>",
                "    </activeProfiles>",
                "</settings>"
            )
        );
    }
}
