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
package com.artipie.maven.asto;

import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.blocking.BlockingStorage;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link AstoValidUpload}.
 * @since 0.5
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public class AstoValidUploadTest {

    @Test
    void returnsTrueWhenAllChecksumsAreValid() throws InterruptedException {
        final Storage storage = new InMemoryStorage();
        final BlockingStorage bsto = new BlockingStorage(storage);
        final Key artifact = new Key.From("com/test");
        final Key jar = new Key.From("com/test/1.0/my-package.jar");
        final Key war = new Key.From("com/test/1.0/my-package.war");
        final byte[] jbytes = "jar artifact".getBytes();
        final byte[] wbytes = "war artifact".getBytes();
        bsto.save(jar, jbytes);
        bsto.save(war, wbytes);
        new TestResource("maven-metadata.xml.example")
            .saveTo(storage, new Key.From(artifact, "maven-metadata.xml"));
        bsto.save(jar, jbytes);
        bsto.save(war, wbytes);
        new RepositoryChecksums(storage).generate(jar).toCompletableFuture().join();
        new RepositoryChecksums(storage).generate(war).toCompletableFuture().join();
        MatcherAssert.assertThat(
            new AstoValidUpload(storage).validate(artifact)
                .toCompletableFuture().join(),
            new IsEqual<>(true)
        );
    }

    @Test
    void returnsFalseWhenNotAllChecksumsAreValid() throws InterruptedException {
        final Storage storage = new InMemoryStorage();
        final BlockingStorage bsto = new BlockingStorage(storage);
        final Key key = new Key.From("org/example");
        final Key jar = new Key.From("org/example/1.0/my-package.jar");
        final Key war = new Key.From("org/example/1.0/my-package.war");
        final byte[] bytes = "artifact".getBytes();
        bsto.save(jar, bytes);
        bsto.save(war, "war artifact".getBytes());
        bsto.save(new Key.From(String.format("%s.sha256", war.string())), "123".getBytes());
        new TestResource("maven-metadata.xml.example")
            .saveTo(storage, new Key.From(key, "maven-metadata.xml"));
        bsto.save(jar, bytes);
        new RepositoryChecksums(storage).generate(jar).toCompletableFuture().join();
        MatcherAssert.assertThat(
            new AstoValidUpload(storage).validate(key)
                .toCompletableFuture().join(),
            new IsEqual<>(false)
        );
    }
}
