package com.steppenwolf.springaws.profile;

import com.steppenwolf.springaws.bucket.BucketName;
import com.steppenwolf.springaws.filestore.FileStore;
import org.apache.http.entity.ContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Service
public class UserProfileService {

    private final UserProfileDataAccessService userProfileDataAccessService;
    private final FileStore fileStore;

    @Autowired
    public UserProfileService(UserProfileDataAccessService userProfileDataAccessService, FileStore fileStore) {
        this.userProfileDataAccessService = userProfileDataAccessService;
        this.fileStore = fileStore;
    }

    List<UserProfile> getUserProfiles(){
        return userProfileDataAccessService.getUserProfiles();
    }


    public void uploadUserProfileImage(UUID userProfileId, MultipartFile file) {

        // Check if image is not empty
        isFileEmpty(file);

        // Check if file is an image
        isImage(file);

        // Check if the user exists in database
       UserProfile user = getUserProfileOrThrow(userProfileId);

        // Grab metadata from file if any
        Map<String, String> metadata = extractMetadata(file);

        // Store the image in s3 and update database (userProfileImageLink) with s3 image Link
        String path = String.format("%s/%s", BucketName.PROFILE_IMAGE.getBucketName(), user.getUserProfileId());
        String fileName = String.format("%s-%s", file.getOriginalFilename(), UUID.randomUUID());
        try {
            fileStore.save(path, fileName, Optional.of(metadata), file.getInputStream());
            user.setUserProfileImageLink(fileName);
        } catch (IOException e) {
           throw new IllegalStateException(e);
        }


    }

    public byte[] downloadUserProfileImage(UUID userProfileId) {

        // Check if the user exists in database
        UserProfile user = getUserProfileOrThrow(userProfileId);

        String path = String.format("%s/%s",
                BucketName.PROFILE_IMAGE.getBucketName(),
                user.getUserProfileId());
        return user.getUserProfileImageLink()
                .map(key -> fileStore.download(path, key))
                .orElse(new byte[0]);

    }

    private Map<String, String> extractMetadata(MultipartFile file) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("Content-Type", file.getContentType());
        metadata.put("Content-Length", String.valueOf(file.getSize()));
        return metadata;
    }

    private UserProfile getUserProfileOrThrow(UUID userProfileId) {
        return userProfileDataAccessService
                 .getUserProfiles()
                 .stream()
                 .filter(userProfile -> userProfile.getUserProfileId().equals(userProfileId))
                 .findFirst()
                 .orElseThrow(() -> new IllegalStateException(String.format("User profile %s not found", userProfileId)));
    }

    private void isImage(MultipartFile file) {
        if (!Arrays.asList(
                ContentType.IMAGE_JPEG.getMimeType(),
                ContentType.IMAGE_PNG.getMimeType(),
                ContentType.IMAGE_GIF.getMimeType())
                .contains(file.getContentType())){
            throw new IllegalStateException("File must be an image [" + file.getContentType() + "]" );

        }
    }

    private void isFileEmpty(MultipartFile file) {
        if (file.isEmpty()){
            throw new IllegalStateException("Cannot upload empty file [ " + file.getSize() + "]" );
        }
    }


}
