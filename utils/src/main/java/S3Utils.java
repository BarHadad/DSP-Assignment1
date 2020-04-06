import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class S3Utils {

    private static final S3Client s3 = S3Client.builder().region(Region.US_EAST_1).build();

    public static String uploadFile(String fileLocalPath, String fileKey) {
        String bucketName = "dsp-public-bucket";
        uploadFile(fileLocalPath, fileKey, bucketName);
        return bucketName;
    }

    public static boolean uploadFile(String fileLocalPath, String fileKey, String bucketName) {
        File input_file = new File(fileLocalPath);
        uploadInputFile(input_file, bucketName, fileKey);
        return true;
    }

    public static boolean uploadLargeFile(String fileLocalPath, String fileKey, String bucketName) {
        multipartUpload(fileLocalPath, fileKey, bucketName);
        return true;
    }

    public static void getObjectToLocal(String fileKey, String bucket, String localFilePath){
        s3.getObject(GetObjectRequest.builder().bucket(bucket).key(localFilePath).build(),
                ResponseTransformer.toFile(Paths.get(localFilePath)));
    }

    /**
     * Upload first Input file to S3
     *
     * @param input_file
     * @param bucket
     * @param key
     */
    private static void uploadInputFile(File input_file, String bucket, String key) {
       try {
           createBucket(bucket);
       } catch (BucketAlreadyExistsException ignored) {}
        s3.putObject(PutObjectRequest.builder().acl(ObjectCannedACL.PUBLIC_READ_WRITE).bucket(bucket).key(key).build(),
                RequestBody.fromFile(input_file));
    }

    private static void createBucket(String bucket) {
        s3.createBucket(CreateBucketRequest
                .builder()
                .acl(BucketCannedACL.PUBLIC_READ_WRITE)
                .bucket(bucket)
                .createBucketConfiguration(
                        CreateBucketConfiguration.builder()
                                .build())
                .build());
    }

    private static void multipartUpload(String filePath, String keyName, String bucketName) {
        createBucket(bucketName);
        File file = new File(filePath);
        long contentLength = file.length();
        long partSize = 5 * 1024 * 1024; // Set part size to 5 MB.

        try {
            // Create a list of ETag objects. You retrieve ETags for each object part uploaded,
            // then, after each individual part has been uploaded, pass the list of ETags to
            // the request to complete the upload.
            List<CompletedPart> completedParts = new ArrayList<>();

            // Initiate the multipart upload.
            CreateMultipartUploadRequest createMultipartUploadRequest = CreateMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(keyName)
                    .acl(ObjectCannedACL.PUBLIC_READ_WRITE)
                    .build();
            CreateMultipartUploadResponse response = s3.createMultipartUpload(createMultipartUploadRequest);
            String uploadId = response.uploadId();
            System.out.println(uploadId);

            // Upload the file parts.
            long filePosition = 0;
            System.out.println("content length: " + contentLength);
            for (int i = 1; filePosition < contentLength; i++) {
                System.out.println("uploading part: " + i);
                System.out.println("file pos: " + filePosition);
                // Because the last part could be less than 5 MB, adjust the part size as needed.
                partSize = Math.min(partSize, (contentLength - filePosition));

                // Create the request to upload a part.
                UploadPartRequest uploadPartRequestI = UploadPartRequest.builder()
                        .bucket(bucketName)
                        .key(keyName)
                        .uploadId(uploadId)
                        .partNumber(i)
                        .contentLength(partSize)
                        .build();

                UploadPartResponse uploadPartResponse =
                        s3.uploadPart(uploadPartRequestI, RequestBody.fromInputStream(new FileInputStream(file), partSize));
                String etagI = uploadPartResponse.eTag();
                completedParts.add(CompletedPart.builder().partNumber(i).eTag(etagI).build());

                filePosition += partSize;
            }
            // Complete the multipart upload.
            CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder().parts(completedParts).build();
            CompleteMultipartUploadRequest completeMultipartUploadRequest = CompleteMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(keyName)
                    .uploadId(uploadId)
                    .multipartUpload(completedMultipartUpload)
                    .build();
            s3.completeMultipartUpload(completeMultipartUploadRequest);
        } catch (SdkClientException | FileNotFoundException e) {
            // Amazon S3 couldn't be contacted for a response, or the client
            // couldn't parse the response from Amazon S3.
            e.printStackTrace();
        }
    }
}