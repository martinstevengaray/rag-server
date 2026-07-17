package com.mgaray.ragserver.sourcereader;

import com.mgaray.ragserver.Models;

public class SourceReaderTest {

    public static void main(String[] args) {
        SourceReader sourceReader = new SourceReader();
        SourceValidator sourceValidator = new SourceValidator();
        Models.SourceManifest sourceManifest = null;

        String inputPortland = "../rag-content-corpus-download/src/portland_city_code/downloads-clean";
        sourceManifest = sourceReader.sourceFolderForPortland("portland-city-code", inputPortland);
        //System.out.println(JsonUtils.toJsonPretty(source));
        System.out.println(sourceValidator.validate(sourceManifest));

        String inputOregon = "../rag-content-corpus-download/src/oregon-state-code/downloads-clean";
        sourceManifest = sourceReader.sourceFolderForOregon("oregon-state-code", inputOregon);
        //System.out.println(JsonUtils.toJsonPretty(source));
        System.out.println(sourceValidator.validate(sourceManifest));

    }

}
