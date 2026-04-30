package model;

public class MediaFile {

    private final String name;
    private final String type;

    public MediaFile(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String getName() { return name; }
    public String getType() { return type; }

}