package com.java.dev.jim.personal.importer;

import com.java.dev.jim.personal.domain.BillSource;
import com.java.dev.jim.personal.domain.ParsedTransaction;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface BillParser {
    BillSource source();
    boolean supports(Path file);
    List<ParsedTransaction> parse(Path file) throws IOException;
}
