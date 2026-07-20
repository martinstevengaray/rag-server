package com.mgaray.ragserver.server;

import com.mgaray.ragserver.awsresources.DataStore;
import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.chunker.Embedder;
import com.mgaray.ragserver.chunker.VectorStore;
import com.mgaray.ragserver.common.Models;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class WebappHandler implements JavaCoreServer.IListener {

    private final IDatastore datastore;
    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;

    public WebappHandler() {
        this(new DataStore(DataStore.Mode.ON_DISK, "/Users/turtlemccully/projects/rag-server/local/s3bucket"),
                "local-embedding-portland-city-code");
    }

    public WebappHandler(IDatastore datastore, String sourceManifestId) {
        this.datastore = datastore;
        this.vectorStore = VectorStore.load(datastore, sourceManifestId);
        this.embeddingModel = Embedder.createModel(Models.ModelType.BGE_SMALL_EN_V15_QUANTIZED);
    }

    @Override
    public String handlePost(String path, String body) {
        System.out.println("Post: " + path + ", " + body);
        float[] searchVector = embeddingModel.embed(body).content().vector();
        vectorStore.get(searchVector, 5);
        List<Models.ChunkMatch> chunkMatches = vectorStore.get(searchVector, 5);
        StringBuilder stringBuilder = new StringBuilder();
        for (Models.ChunkMatch chunkMatch : chunkMatches) {
            Models.Chunk chunk = chunkMatch.chunk();
            String chunkText = datastore.fetch(chunk.textLocation());
            stringBuilder.append("\n\n--------------------------------------------------------------\n\n");
            stringBuilder.append(chunkText);
        }
        return readResource("/confirm.html")
                .replace("{{length}}", String.valueOf(body.length()))
                .replace("{{content}}", escapeHtml(stringBuilder.toString()));
    }

    @Override
    public String handleGet(String path) {
        String resource = path.equals("/") ? "/index.html" : path;
        return readResource(resource);
    }

    //reads a page from the resources folder (classpath)
    private String readResource(String resource) {
        try (InputStream is = getClass().getResourceAsStream(resource)) {
            if (is == null) {
                return "<html><body>404 - " + resource + " not found</body></html>";
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    //escapes user text so it can't break out of the html we render it into
    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

}
