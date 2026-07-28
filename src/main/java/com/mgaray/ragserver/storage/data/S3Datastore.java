package com.mgaray.ragserver.storage.data;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class S3Datastore implements IDatastore {

    private final String bucket;
    private final S3Client s3Client;

    public S3Datastore(String bucket) {
        this.bucket = bucket;
        this.s3Client = S3Client.create();
    }

    @Override
    public byte[] read(String storageLocation) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(storageLocation)
                    .build();
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
            return response.asByteArray();
        } catch (NoSuchKeyException e) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read s3://" + bucket + "/" + storageLocation, e);
        }
    }

    @Override
    public void write(String storageLocation, byte[] bytes)  {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(storageLocation)
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(bytes));
        } catch (Exception e) {
            throw new RuntimeException("Failed to write s3://" + bucket + "/" + storageLocation, e);
        }
    }

    @Override
    public boolean exists(String storageLocation) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(storageLocation)
                    .build();
            s3Client.headObject(request);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            throw new RuntimeException("Failed to check existence of s3://" + bucket + "/" + storageLocation, e);
        }
    }

}
