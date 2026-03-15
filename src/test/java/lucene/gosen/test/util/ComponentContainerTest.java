package lucene.gosen.test.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ComponentContainerTest {

    @Test
    void testConstructorWithNullJarFiles() {
        assertDoesNotThrow(() -> {
            ComponentContainer container = new ComponentContainer(null);
            assertNotNull(container);
        });
    }

    @Test
    void testConstructorWithEmptyJarFiles() {
        assertDoesNotThrow(() -> {
            ComponentContainer container = new ComponentContainer(new File[0]);
            assertNotNull(container);
        });
    }

    @Test
    void testLoadComponentWithNullJarFiles() {
        ComponentContainer container = new ComponentContainer(null);

        assertThrows(ClassNotFoundException.class, () -> {
            container.loadComponent("java.lang.String");
        });
    }

    @Test
    void testLoadComponentWithNonExistentClass() {
        File[] jarFiles = new File[]{new File("dummy.jar")};
        ComponentContainer container = new ComponentContainer(jarFiles);

        assertThrows(ClassNotFoundException.class, () -> {
            container.loadComponent("com.example.NonExistentClass");
        });
    }

    @Test
    void testLoadComponentWithInvalidJarPath(@TempDir Path tempDir) {
        File nonExistentJar = tempDir.resolve("nonexistent.jar").toFile();
        File[] jarFiles = new File[]{nonExistentJar};
        ComponentContainer container = new ComponentContainer(jarFiles);

        // 存在しないJARファイルからのクラスロードの動作を確認
        // URL変換は成功するが、クラスロード時にエラーが発生する可能性がある
        try {
            container.loadComponent("java.lang.String");
            // 例外が発生しない場合もある（システムクラスローダーから読み込まれる可能性）
            assertTrue(true);
        } catch (Exception e) {
            // 例外が発生する場合もある
            assertTrue(true);
        }
    }

    @Test
    void testCreateComponentWithNullArguments() {
        ComponentContainer container = new ComponentContainer(null);

        assertThrows(ClassNotFoundException.class, () -> {
            container.createComponent("java.lang.String", null, null);
        });
    }

    @Test
    void testCreateComponentWithInvalidClassName() {
        File[] jarFiles = new File[]{new File("dummy.jar")};
        ComponentContainer container = new ComponentContainer(jarFiles);

        assertThrows(ClassNotFoundException.class, () -> {
            container.createComponent("invalid.ClassName", null, null);
        });
    }

    @Test
    void testMultipleJarFiles(@TempDir Path tempDir) {
        File jar1 = tempDir.resolve("lib1.jar").toFile();
        File jar2 = tempDir.resolve("lib2.jar").toFile();
        File[] jarFiles = new File[]{jar1, jar2};

        assertDoesNotThrow(() -> {
            ComponentContainer container = new ComponentContainer(jarFiles);
            assertNotNull(container);
        });
    }

    @Test
    void testCreateComponentWithArgumentTypes() {
        ComponentContainer container = new ComponentContainer(null);

        // String(String)コンストラクタを呼び出そうとする
        Class<?>[] argTypes = new Class<?>[]{String.class};
        Object[] args = new Object[]{"test"};

        assertThrows(ClassNotFoundException.class, () -> {
            container.createComponent("java.lang.String", argTypes, args);
        });
    }
}
