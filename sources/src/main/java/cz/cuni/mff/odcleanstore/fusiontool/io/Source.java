/**
 * 
 */
package cz.cuni.mff.odcleanstore.fusiontool.io;

import java.util.Map;

import org.openrdf.repository.Repository;

import cz.cuni.mff.odcleanstore.fusiontool.config.EnumDataSourceType;

/**
 * Settings for an RDF data source.
 * @author Jan Michelfeit
 */
public interface Source {
    /**
     * Returns repository representing this data source.
     * @return repository representing the data source
     */
    Repository getRepository();

    /**
     * Map of namespace prefixes that can be used (e.g. in SPARQL expressions or aggregation settings).
     * Key is the prefix, value the expanded URI.
     * @return map of namespace prefixes
     */
    Map<String, String> getPrefixes();

    /**
     * Returns a human-readable label.
     * @return label
     */
    String getName();
    
    /**
     * Returns type of the data source.
     * @return type of the data source
     */
    EnumDataSourceType getType();
}
