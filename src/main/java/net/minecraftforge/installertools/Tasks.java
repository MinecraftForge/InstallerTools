/*
 * InstallerTools
 * Copyright (c) 2019-2021.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
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
