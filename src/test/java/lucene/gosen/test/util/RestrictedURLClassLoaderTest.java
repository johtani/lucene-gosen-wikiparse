package lucene.gosen.test.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RestrictedURLClassLoaderTest {

    @Test
    void testConstructor() {
        URL[] urls = new URL[0];
        ClassLoader parent = Thread.currentThread().getContextClassLoader();

        assertDoesNotThrow(() -> {
            RestrictedURLClassLoader loader = new RestrictedURLClassLoader(urls, parent);
            assertNotNull(loader);
        });
    }

    @Test
    void testLoadClassWithSystemClass() throws Exception {
        URL[] urls = new URL[0];
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        RestrictedURLClassLoader loader = new RestrictedURLClassLoader(urls, parent);

        // システムクラスのロードは成功するはず
        Class<?> clazz = loader.loadClass("java.lang.String");
        assertNotNull(clazz);
        assertEquals("java.lang.String", clazz.getName());
    }

    @Test
    void testLoadClassWithNonExistentClass() {
        URL[] urls = new URL[0];
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        RestrictedURLClassLoader loader = new RestrictedURLClassLoader(urls, parent);

        // 存在しないクラスのロードは失敗するはず
        assertThrows(ClassNotFoundException.class, () -> {
            loader.loadClass("com.example.NonExistentClass");
        });
    }

    @Test
    void testLoadClassWithNullParent() throws Exception {
        URL[] urls = new URL[0];
        RestrictedURLClassLoader loader = new RestrictedURLClassLoader(urls, null);

        // nullの親クラスローダーでもシステムクラスはロードできる
        Class<?> clazz = loader.loadClass("java.lang.Object");
        assertNotNull(clazz);
    }

    @Test
    void testLoadClassWithEmptyURLs() throws Exception {
        URL[] urls = new URL[0];
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        RestrictedURLClassLoader loader = new RestrictedURLClassLoader(urls, parent);

        // 空のURLでもシステムクラスはロードできる
        Class<?> clazz = loader.loadClass("java.util.ArrayList");
        assertNotNull(clazz);
        assertEquals("java.util.ArrayList", clazz.getName());
    }

    @Test
    void testLoadClassFromFileURL(@TempDir Path tempDir) throws Exception {
        File dir = tempDir.toFile();
        URL[] urls = new URL[]{dir.toURI().toURL()};
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        RestrictedURLClassLoader loader = new RestrictedURLClassLoader(urls, parent);

        // システムクラスは常にロードできる
        Class<?> clazz = loader.loadClass("java.lang.String");
        assertNotNull(clazz);
    }

    @Test
    void testMultipleURLs(@TempDir Path tempDir) throws Exception {
        File dir1 = tempDir.resolve("lib1").toFile();
        File dir2 = tempDir.resolve("lib2").toFile();
        dir1.mkdirs();
        dir2.mkdirs();

        URL[] urls = new URL[]{dir1.toURI().toURL(), dir2.toURI().toURL()};
        ClassLoader parent = Thread.currentThread().getContextClassLoader();

        assertDoesNotThrow(() -> {
            RestrictedURLClassLoader loader = new RestrictedURLClassLoader(urls, parent);
            assertNotNull(loader);
        });
    }

    @Test
    void testGetParent() {
        URL[] urls = new URL[0];
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        RestrictedURLClassLoader loader = new RestrictedURLClassLoader(urls, parent);

        // 親クラスローダーが正しく設定されていることを確認
        assertEquals(parent, loader.getParent());
    }

    @Test
    void testLoadClassReturnsNonNullClass() throws Exception {
        URL[] urls = new URL[0];
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        RestrictedURLClassLoader loader = new RestrictedURLClassLoader(urls, parent);

        // loadClassはnullを返さないことを確認
        Class<?> clazz = loader.loadClass("java.lang.Integer");
        assertNotNull(clazz);
        // loadClass内部のnullチェックロジックが機能している
    }
}
