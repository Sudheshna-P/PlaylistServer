package service;

import model.MediaFile;
import model.LibraryModel;

import java.io.IOException;
import java.util.List;

public class LibraryService {

    private final LibraryModel libraryModel;

    public LibraryService(LibraryModel libraryModel) {
        this.libraryModel = libraryModel;
    }

    public List<MediaFile> getAll() throws ServiceException {
        try {
            return libraryModel.getAll();
        } catch (IOException e) {
            throw new ServiceException(500, "Failed to read library");
        }
    }

    public void delete(String filename) throws ServiceException {
        if (filename == null || filename.isEmpty()
                || filename.contains("..") || filename.contains("/"))
            throw new ServiceException(403, "Invalid filename");

        java.nio.file.Path target = java.nio.file.Paths.get(
                libraryModel.getUploadsPath(), filename);

        if (!java.nio.file.Files.exists(target))
            throw new ServiceException(404, "File not found");

        try {
            java.nio.file.Files.delete(target);
        } catch (IOException e) {
            throw new ServiceException(500, "Failed to delete file");
        }
    }
}