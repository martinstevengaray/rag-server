package com.mgaray.ragserver.sourcereader;

import com.mgaray.ragserver.Models;
import com.mgaray.ragserver.awsresources.DataFetcher;

public class SourceTransformerAndValidatorRunner {

    public static void main(String[] args) {
        DataFetcher dataFetcher = new DataFetcher(DataFetcher.Mode.ON_DISK, "/Users/turtlemccully/projects/rag-server/local/s3bucket");
        SourceTransformer sourceTransformer = new SourceTransformer(dataFetcher);
        SourceValidator sourceValidator = new SourceValidator();
        Models.SourceManifest sourceManifest = null;

        String inputPortland = "../rag-content-corpus-download/src/portland_city_code/downloads-clean";
        sourceManifest = sourceTransformer.sourceFolderForPortland("portland-city-code", inputPortland);
        //System.out.println(JsonUtils.toJsonPretty(sourceManifest));
        System.out.println("errors: " + sourceValidator.validate(sourceManifest));

        String inputOregon = "../rag-content-corpus-download/src/oregon-state-code/downloads-clean";
        sourceManifest = sourceTransformer.sourceFolderForOregon("oregon-state-code", inputOregon);
        //System.out.println(JsonUtils.toJsonPretty(source));
        System.out.println("errors: " + sourceValidator.validate(sourceManifest));

        String inputWebc = "../rag-content-corpus-download/src/web-catholic-bible/downloads-clean";
        sourceManifest = sourceTransformer.sourceFolderForNabAndWebc("web-catholic-bible", inputWebc);
        //System.out.println(JsonUtils.toJsonPretty(source));
        System.out.println("errors: " + sourceValidator.validate(sourceManifest));

        String inputNab = "../rag-content-corpus-download/src/new-american-bible/downloads-clean";
        sourceManifest = sourceTransformer.sourceFolderForNabAndWebc("new-american-bible", inputNab);
        //System.out.println(JsonUtils.toJsonPretty(source));
        System.out.println("errors: " + sourceValidator.validate(sourceManifest));
    }

}
