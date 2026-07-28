package com.mgaray.ragserver.storage.vector;

import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.ingest.Embedder;
import com.mgaray.ragserver.util.JsonUtils;
import com.mgaray.ragserver.Models.EmbeddingSpec;
import com.mgaray.ragserver.Models.VectorMatch;
import com.mgaray.ragserver.Models.IVectorRecord;
import com.mgaray.ragserver.Models.S3VectorStoreManifest;
import dev.langchain4j.model.embedding.EmbeddingModel;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.ConflictException;
import software.amazon.awssdk.services.s3vectors.model.CreateIndexRequest;
import software.amazon.awssdk.services.s3vectors.model.DataType;
import software.amazon.awssdk.services.s3vectors.model.DistanceMetric;
import software.amazon.awssdk.services.s3vectors.model.GetOutputVector;
import software.amazon.awssdk.services.s3vectors.model.GetVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.GetVectorsResponse;
import software.amazon.awssdk.services.s3vectors.model.PutInputVector;
import software.amazon.awssdk.services.s3vectors.model.PutVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.QueryOutputVector;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsResponse;
import software.amazon.awssdk.services.s3vectors.model.VectorData;
import com.mgaray.ragserver.Models.VectorStoreSpec;

import java.util.ArrayList;
import java.util.List;

public class S3VectorStore<T extends IVectorRecord> implements IVectorStore<T> {

    private static final String DOCUMENT_PAYLOAD_KEY = "json";

    private final String bucket;
    private final String ingestionManifestId;
    private final Class<T> clazz;
    private final S3VectorsClient s3VectorsClient;

    public S3VectorStore(String bucket, String ingestionManifestId, Class<T> clazz) {
        this.bucket = bucket;
        this.ingestionManifestId = ingestionManifestId;
        this.clazz = clazz;
        this.s3VectorsClient = S3VectorsClient.create();
    }

    @Override
    public void add(float[] vector, T t) {
        Document metadataDocument = Document.mapBuilder().putString(DOCUMENT_PAYLOAD_KEY, JsonUtils.toJson(t)).build();
        PutInputVector putInputVector = PutInputVector.builder()
                .key(t.id())
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
    public List<VectorMatch<T>> get(float[] searchVector, int topK) {
        List<VectorMatch<T>> vectorMatches = new ArrayList<>();
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
            vectorMatches.add(new VectorMatch<>(t, matchScore));

        }
        return vectorMatches;
    }

    public T get(String id) {
        GetVectorsRequest getVectorsRequest = GetVectorsRequest.builder()
                .vectorBucketName(bucket)
                .indexName(ingestionManifestId)
                .keys(id)
                .returnData(false)       // the payload lives in the metadata, skip the vector itself
                .returnMetadata(true)
                .build();
        GetVectorsResponse response = s3VectorsClient.getVectors(getVectorsRequest);
        for (GetOutputVector getOutputVector : response.vectors()) {
            String tJsonString = getOutputVector.metadata().asMap().get(DOCUMENT_PAYLOAD_KEY).asString();
            return JsonUtils.toObject(tJsonString, clazz);
        }
        return null;
    }

    @Override
    public void initialize(EmbeddingSpec embeddingSpec) { //ensure vector store index is created
        final EmbeddingModel embeddingModel = Embedder.createEmbeddingModel(embeddingSpec, null);
        int dimension = embeddingModel.dimension();
        try {
            s3VectorsClient.createIndex(CreateIndexRequest.builder()
                    .vectorBucketName(bucket)
                    .indexName(ingestionManifestId)
                    .dataType(DataType.FLOAT32)
                    .dimension(dimension)
                    .distanceMetric(DistanceMetric.COSINE)
                    .build());
        } catch (ConflictException alreadyExists) {
            // index already exists, do nothing
        }
    }

    @Override
    public boolean resultsExist(IDatastore datastore, VectorStoreSpec vectorStoreSpec) {
        String s3VectorStoreManifestLocation = vectorStoreSpec.s3VectorStoreManifestLocation();
        return datastore.exists(s3VectorStoreManifestLocation);
    }

    @Override
    public void writeResults(IDatastore datastore, VectorStoreSpec vectorStoreSpec) {
        String s3VectorStoreManifestLocation = vectorStoreSpec.s3VectorStoreManifestLocation();
        S3VectorStoreManifest s3VectorStoreManifest = new S3VectorStoreManifest(bucket, ingestionManifestId);
        datastore.writeObject(s3VectorStoreManifestLocation, s3VectorStoreManifest);
    }

    @Override
    public boolean exists(T t) {
        GetVectorsRequest getVectorsRequest = GetVectorsRequest.builder()
                .vectorBucketName(bucket)
                .indexName(ingestionManifestId)
                .keys(t.id())
                .returnData(false)       // we only care about presence, skip the vector payload
                .returnMetadata(false)
                .build();
        GetVectorsResponse response = s3VectorsClient.getVectors(getVectorsRequest);
        return !response.vectors().isEmpty();
    }
    
    private static List<Float> toFloatList(float[] vector) {
        List<Float> floats = new ArrayList<>(vector.length);
        for (float value : vector) {
            floats.add(value);
        }
        return floats;
    }

}