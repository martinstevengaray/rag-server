package com.mgaray.ragserver;

import com.mgaray.ragserver.sourcereader.SourceReader;
import com.mgaray.ragserver.sourcereader.SourceValidator;

public class SourceReaderTest {

    public static void main(String[] args) {
        SourceReader sourceReader = new SourceReader();
        SourceValidator sourceValidator = new SourceValidator();
        Models.SourceRecords sourceRecords = null;

        String inputPortland = "../rag-content-corpus-download/src/portland_city_code/downloads-clean";
        sourceRecords = sourceReader.sourceFolderForPortland(inputPortland);
        //System.out.println(JsonUtils.toJsonPretty(source));
        System.out.println(sourceValidator.validate(sourceRecords));

        String inputOregon = "../rag-content-corpus-download/src/oregon-state-code/downloads-clean";
        sourceRecords = sourceReader.sourceFolderForOregon(inputOregon);
        //System.out.println(JsonUtils.toJsonPretty(source));
        System.out.println(sourceValidator.validate(sourceRecords));

    }

}
