package com.mgaray.ragserver.chunker;

import com.mgaray.ragserver.ModelRecords;
import com.mgaray.ragserver.awsresources.BucketResource;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;

public class Embedder {


    private final BucketResource bucketResource = new BucketResource();

    public void mutateEmbedding(ModelRecords.Chunk chunk) {
        String chunkOfText = bucketResource.fetch(chunk.textSourceUrl());

        EmbeddingModel model = null; /* todo onfigured model */;

        Embedding embedding = model.embed(chunkOfText).content();

        float[] vector = embedding.vector();

//        chunk.embedding()


    }
}
