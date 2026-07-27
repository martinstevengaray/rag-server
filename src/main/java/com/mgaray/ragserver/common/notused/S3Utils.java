package com.mgaray.ragserver.common.notused;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.ArrayList;
import java.util.List;

public class S3Utils {

    // The S3 client is thread-safe and expensive to create, so share a single lazily-initialized instance.
    private static volatile S3Client s3Client;

    private static S3Client client() {
        S3Client result = s3Client;
        if (result == null) {
            synchronized (S3Utils.class) {
                result = s3Client;
                if (result == null) {
                    result = S3Client.create();
                    s3Client = result;
                }
            }
        }
        return result;
    }

    // Returns null when the object does not exist, mirroring FileUtils.readBytes.
    public static byte[] readBytes(String bucket, String key) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            ResponseBytes<GetObjectResponse> response = client().getObjectAsBytes(request);
            return response.asByteArray();
        } catch (NoSuchKeyException e) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read s3://" + bucket + "/" + key, e);
        }
    }

    public static void writeBytes(String bucket, String key, byte[] bytes) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            client().putObject(request, RequestBody.fromBytes(bytes));
        } catch (Exception e) {
            throw new RuntimeException("Failed to write s3://" + bucket + "/" + key, e);
        }
    }

    // Lists the immediate children of keyPrefix, mirroring FileUtils.listFolder: one level deep, each entry a
    // full "bucket/key" path, with no trailing slash on the prefixes standing in for subfolders.
    public static List<String> list(String bucket, String keyPrefix) {
        try {
            String prefix = keyPrefix.isEmpty() || keyPrefix.endsWith("/") ? keyPrefix : keyPrefix + "/";
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .delimiter("/")          // stop at one level, exposing subfolders as common prefixes
                    .build();
            List<String> children = new ArrayList<>();
            for (ListObjectsV2Response response : client().listObjectsV2Paginator(request)) {
                for (CommonPrefix commonPrefix : response.commonPrefixes()) {
                    String subfolder = commonPrefix.prefix();   // always ends with the delimiter
                    children.add(bucket + "/" + subfolder.substring(0, subfolder.length() - 1));
                }
                for (S3Object s3Object : response.contents()) {
                    if (!s3Object.key().equals(prefix)) {       // skip the folder placeholder object, if any
                        children.add(bucket + "/" + s3Object.key());
                    }
                }
            }
            return children;
        } catch (Exception e) {
            throw new RuntimeException("Failed to list s3://" + bucket + "/" + keyPrefix, e);
        }
    }

    public static boolean exists(String bucket, String key) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            client().headObject(request);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            throw new RuntimeException("Failed to check existence of s3://" + bucket + "/" + key, e);
        }
    }

}
