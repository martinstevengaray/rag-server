package com.mgaray.ragserver.sourcereader;

import com.mgaray.ragserver.Models;
import com.mgaray.ragserver.awsresources.DataFetcher;
import com.mgaray.ragserver.common.JsonUtils;

public class SourceReaderTest {

    public static void main(String[] args) {
        DataFetcher dataFetcher = new DataFetcher(DataFetcher.Mode.IN_MEMORY, "/Users/turtlemccully/projects/rag-server/local/s3bucket");
        SourceReader sourceReader = new SourceReader(dataFetcher);
        SourceValidator sourceValidator = new SourceValidator();
        Models.SourceManifest sourceManifest = null;

        String inputPortland = "../rag-content-corpus-download/src/portland_city_code/downloads-clean";
        sourceManifest = sourceReader.sourceFolderForPortland("portland-city-code", inputPortland);
        System.out.println(JsonUtils.toJsonPretty(sourceManifest));
        System.out.println("errors: " + sourceValidator.validate(sourceManifest));
//if(true) return;
        String inputOregon = "../rag-content-corpus-download/src/oregon-state-code/downloads-clean";
        sourceManifest = sourceReader.sourceFolderForOregon("oregon-state-code", inputOregon);
        //System.out.println(JsonUtils.toJsonPretty(source));
        System.out.println("errors: " + sourceValidator.validate(sourceManifest));

        String inputWebc = "../rag-content-corpus-download/src/web-catholic-bible/downloads-clean";
        sourceManifest = sourceReader.sourceFolderForNabAndWebc("web-catholic-bible", inputWebc);
        //System.out.println(JsonUtils.toJsonPretty(source));
        System.out.println("errors: " + sourceValidator.validate(sourceManifest));

        String inputNab = "../rag-content-corpus-download/src/new-american-bible/downloads-clean";
        sourceManifest = sourceReader.sourceFolderForNabAndWebc("new-american-bible", inputNab);
        //System.out.println(JsonUtils.toJsonPretty(source));
        System.out.println("errors: " + sourceValidator.validate(sourceManifest));
    }

}
