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

package com.artipie.maven.aether;

import com.artipie.asto.Key;
import com.artipie.asto.blocking.BlockingStorage;
import com.artipie.asto.fs.FileStorage;
import com.artipie.maven.Repository;
import com.artipie.maven.aether.repository.AetherRepository;
import com.artipie.maven.util.AutoCloseablePath;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.aether.repository.LocalRepository;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link AetherRepository}.
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (60 lines)
 */
public final class AetherRepositoryTest {

    /**
     * Test temporary directory.
     * By JUnit annotation contract it should not be private
     * @checkstyle VisibilityModifierCheck (3 lines)
     */
    @TempDir
    Path temp;

    /**
     * Asto.
     */
    private BlockingStorage asto;

    /**
     * Maven adapter.
     */
    private Repository repository;

    @BeforeEach
    public void before() {
        final FileStorage files = new FileStorage(this.temp.resolve("asto"));
        this.asto = new BlockingStorage(files);
        this.repository = new AetherRepository(
            new ServiceLocatorFactory(files),
            new LocalRepository(this.temp.resolve("local").toFile()),
            new AutoCloseablePath.Parent(this.temp.resolve("staging")),
            new SimpleRemoteRepositories()
        );
    }

    @Test
    public void shouldStoreArtifact() throws Exception {
        final var path = "example/artifact/1.0/artifact-1.0.jar";
        this.repository.upload(path, new ByteArrayInputStream(new byte[0]));
        MatcherAssert.assertThat(
            "should create the file in Asto",
            this.asto.exists(new Key.From(path)),
            new IsEqual<>(true)
        );
    }

    @Test
    public void shouldMatchChecksums() throws Exception {
        final var path = "example/artifact/1.0/artifact-1.0.pom";
        final var bytes = new byte[0];
        final var artifact = this.repository.upload(path, new ByteArrayInputStream(bytes));
        MatcherAssert.assertThat(
            "checksums should match",
            artifact.sha1(),
            new IsEqual<>(DigestUtils.sha1Hex(bytes))
        );
    }

    @Test
    public void shouldUpload() throws Exception {
        final var path = "org/example/artifact/1.0/artifact-1.0.jar";
        this.repository.upload(path, new ByteArrayInputStream(new byte[0]));
        MatcherAssert.assertThat(
            "should create a metadata file",
            this.asto.exists(
                new Key.From(
                    "org/example",
                    "artifact",
                    "maven-metadata.xml"
                )
            ),
            new IsEqual<>(true)
        );
    }
}
