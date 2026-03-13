package lucene.gosen.wikipedia;

import java.util.Date;


public class WikipediaModel {

    String title;
    String titleAnnotation;
    String id;
    String text;
    int textCount;
    Date lastModified;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitleAnnotation() {
        return titleAnnotation;
    }

    public void setTitleAnnotation(String titleAnnotation) {
        this.titleAnnotation = titleAnnotation;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
    }

    public String toString() {
        return this.id + "," + this.title + "," + this.titleAnnotation + "," + this.lastModified;
    }
}