package com.mgaray.ragserver.bootstrap;

import com.mgaray.ragserver.common.JsonUtils;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.PutInputVector;
import software.amazon.awssdk.services.s3vectors.model.PutVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.QueryOutputVector;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsResponse;
import software.amazon.awssdk.services.s3vectors.model.VectorData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class S3VectorStore<T> implements IVectorStore<T> {

    private final String DOCUMENT_PAYLOAD_KEY = "json";

    private final String bucket;
    private final String ingestionManifestId;
    private final S3VectorsClient s3VectorsClient;

    public S3VectorStore(String bucket, String ingestionManifestId) {
        this.bucket = bucket;
        this.ingestionManifestId = ingestionManifestId;
        this.s3VectorsClient = S3VectorsClient.create();
    }

    @Override
    public void add(float[] vector, T t) {
        Document metadataDocument = Document.mapBuilder().putString(DOCUMENT_PAYLOAD_KEY, JsonUtils.toJson(t)).build();
        PutInputVector putInputVector = PutInputVector.builder()
                .key(UUID.randomUUID().toString())
                .data(VectorData.builder().float32(toFloatList(vector)).build())
                .metadata(metadataDocument)
                .build();
        PutVectorsRequest putVectorRequest = PutVectorsRequest.builder()
                .vectorBucketName(bucket)
                .indexName(ingestionManifestId)
                .vectors(putInputVector)
                .build();
        s3VectorsClient.putVectors(putVectorRequest);
    }

    @Override
    public List<VectorRecord<T>> get(float[] searchVector, int topK, Class<T> clazz) {

        List<VectorRecord<T>> vectorRecords = new ArrayList<>();
        QueryVectorsRequest request = QueryVectorsRequest.builder()
                .vectorBucketName(bucket)
                .indexName(ingestionManifestId)
                .queryVector(VectorData.builder().float32(toFloatList(searchVector)).build())
                .topK(topK)
                .returnDistance(true)
                .returnMetadata(true)
                .build();

        QueryVectorsResponse queryVectorsResponse = s3VectorsClient.queryVectors(request);
        for (QueryOutputVector queryOutputVector : queryVectorsResponse.vectors()) {
            double matchScore = queryOutputVector.distance();
            String tJsonString = queryOutputVector.metadata().asMap().get(DOCUMENT_PAYLOAD_KEY).asString();
            T t = JsonUtils.toObject(tJsonString, clazz);
            vectorRecords.add(new VectorRecord<>(t, matchScore));

        }
        return vectorRecords;
    }

    private static List<Float> toFloatList(float[] vector) { //todo duplicate
        List<Float> floats = new ArrayList<>(vector.length);
        for (float value : vector) {
            floats.add(value);
        }
        return floats;
    }

}