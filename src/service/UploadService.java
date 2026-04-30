package service;

import model.UploadModel;

import java.util.List;

public class UploadService {

    private final UploadModel uploadModel;

    public UploadService(UploadModel uploadModel) {
        this.uploadModel = uploadModel;
    }

    public void parse(byte[] body, String boundary,
                      List<String> saved, List<String> errors) {
        uploadModel.parse(body, boundary, saved, errors);
    }
}