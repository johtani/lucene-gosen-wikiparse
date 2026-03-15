package lucene.gosen.wikipedia;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class WikipediaModelTest {

    @Test
    void testConstructor() {
        WikipediaModel model = new WikipediaModel();
        assertNotNull(model);
    }

    @Test
    void testSetAndGetId() {
        WikipediaModel model = new WikipediaModel();
        model.setId("12345");
        assertEquals("12345", model.getId());
    }

    @Test
    void testSetAndGetTitle() {
        WikipediaModel model = new WikipediaModel();
        model.setTitle("Test Title");
        assertEquals("Test Title", model.getTitle());
    }

    @Test
    void testSetAndGetTitleAnnotation() {
        WikipediaModel model = new WikipediaModel();
        model.setTitleAnnotation("曖昧さ回避");
        assertEquals("曖昧さ回避", model.getTitleAnnotation());
    }

    @Test
    void testSetAndGetText() {
        WikipediaModel model = new WikipediaModel();
        model.setText("This is test text.");
        assertEquals("This is test text.", model.getText());
    }

    @Test
    void testSetAndGetLastModified() {
        WikipediaModel model = new WikipediaModel();
        Date date = new Date();
        model.setLastModified(date);
        assertEquals(date, model.getLastModified());
    }

    @Test
    void testSetNullValues() {
        WikipediaModel model = new WikipediaModel();

        model.setId(null);
        assertNull(model.getId());

        model.setTitle(null);
        assertNull(model.getTitle());

        model.setTitleAnnotation(null);
        assertNull(model.getTitleAnnotation());

        model.setText(null);
        assertNull(model.getText());

        model.setLastModified(null);
        assertNull(model.getLastModified());
    }

    @Test
    void testSetEmptyStrings() {
        WikipediaModel model = new WikipediaModel();

        model.setId("");
        assertEquals("", model.getId());

        model.setTitle("");
        assertEquals("", model.getTitle());

        model.setTitleAnnotation("");
        assertEquals("", model.getTitleAnnotation());

        model.setText("");
        assertEquals("", model.getText());
    }

    @Test
    void testToString() {
        WikipediaModel model = new WikipediaModel();
        model.setId("123");
        model.setTitle("Title");
        model.setTitleAnnotation("Annotation");
        Date date = new Date();
        model.setLastModified(date);

        String result = model.toString();
        assertNotNull(result);
        assertTrue(result.contains("123"));
        assertTrue(result.contains("Title"));
        assertTrue(result.contains("Annotation"));
    }

    @Test
    void testToStringWithNullValues() {
        WikipediaModel model = new WikipediaModel();
        String result = model.toString();

        assertNotNull(result);
        assertTrue(result.contains("null"));
    }

    @Test
    void testSetLongText() {
        WikipediaModel model = new WikipediaModel();
        String longText = "a".repeat(10000);
        model.setText(longText);
        assertEquals(longText, model.getText());
        assertEquals(10000, model.getText().length());
    }

    @Test
    void testSetJapaneseText() {
        WikipediaModel model = new WikipediaModel();
        String japaneseText = "これは日本語のテストです。";
        model.setText(japaneseText);
        assertEquals(japaneseText, model.getText());
    }

    @Test
    void testMultipleSetCalls() {
        WikipediaModel model = new WikipediaModel();

        model.setId("1");
        model.setId("2");
        assertEquals("2", model.getId());

        model.setTitle("First");
        model.setTitle("Second");
        assertEquals("Second", model.getTitle());
    }

    @Test
    void testAllFieldsSet() {
        WikipediaModel model = new WikipediaModel();
        Date date = new Date();

        model.setId("123");
        model.setTitle("Test Title");
        model.setTitleAnnotation("Test Annotation");
        model.setText("Test Text");
        model.setLastModified(date);

        assertEquals("123", model.getId());
        assertEquals("Test Title", model.getTitle());
        assertEquals("Test Annotation", model.getTitleAnnotation());
        assertEquals("Test Text", model.getText());
        assertEquals(date, model.getLastModified());
    }
}
