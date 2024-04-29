/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installertools;

import java.util.function.Supplier;

public enum Tasks {
    MCP_DATA(McpData::new),
    CREATE_DIR(CreateDirectory::new),
    CREATE_PARENTS(CreateParents::new),
    SRG_TO_MCP(SrgMcpRenamer::new),
    EXTRACT_INHERITANCE(ExtractInheritance::new),
    CHAIN_MAPPING(ChainMappings::new),
    MERGE_MAPPING(MergeMappings::new),
    DOWNLOAD_MOJMAPS(DownloadMojmaps::new),
    EXTRACT_FILES(ExtractFiles::new),
    BUNDLER_EXTRACT(BundlerExtract::new),
    MAPPINGS_CSV(MappingsCsv::new)
    ;

    private Supplier<? extends Task> supplier;

    private Tasks(Supplier<? extends Task> supplier) {
        this.supplier = supplier;
    }

    @SuppressWarnings("unchecked")
    public <T extends Task> T get() {
        return (T)supplier.get();
    }
}
