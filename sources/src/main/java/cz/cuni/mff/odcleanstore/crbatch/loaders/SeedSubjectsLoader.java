package cz.cuni.mff.odcleanstore.crbatch.loaders;

import java.util.Locale;
import java.util.NoSuchElementException;

import org.openrdf.OpenRDFException;
import org.openrdf.model.Value;
import org.openrdf.query.BindingSet;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.cuni.mff.odcleanstore.crbatch.DataSource;
import cz.cuni.mff.odcleanstore.crbatch.config.SparqlRestriction;
import cz.cuni.mff.odcleanstore.crbatch.config.SparqlRestrictionImpl;
import cz.cuni.mff.odcleanstore.crbatch.exceptions.CRBatchErrorCodes;
import cz.cuni.mff.odcleanstore.crbatch.exceptions.CRBatchException;
import cz.cuni.mff.odcleanstore.crbatch.exceptions.CRBatchQueryException;
import cz.cuni.mff.odcleanstore.crbatch.util.CRBatchUtils;

/**
 * Loads subjects of triples to be processed.
 * If seed resource restriction is given, only subjects matching this restriction will be returned.
 * In the current implementation, the collection uses an open cursor in the database.
 * @author Jan Michelfeit
 */
public class SeedSubjectsLoader extends RepositoryLoaderBase {
    private static final Logger LOG = LoggerFactory.getLogger(SeedSubjectsLoader.class);
    
    /**
     * Collection of subjects of relevant triples.
     */
    private final class UriCollectionImpl implements UriCollection {
        private TupleQueryResult subjectsResultSet;
        private RepositoryConnection connection;
        private String next = null;

        /**
         * Creates a new instance.
         * @param query query that retrieves subjects from the database; the query must return
         *        the subjects as the first variable in the results
         * @param repository an initialized RDF repository
         * @throws CRBatchException error
         */
        /*package*/UriCollectionImpl(String query, Repository repository) throws CRBatchException {
            try {
                this.connection = repository.getConnection();
                this.subjectsResultSet = connection.prepareTupleQuery(QueryLanguage.SPARQL, query).evaluate();
            } catch (OpenRDFException e) {
                close();
                throw new CRBatchQueryException(CRBatchErrorCodes.QUERY_TRIPLE_SUBJECTS, query, dataSource.getName(), e);
            }

            next = getNextResult();
        }
        
        private String getNextResult() throws CRBatchException {
            try {
                String subjectVar = subjectsResultSet.getBindingNames().get(0);
                while (subjectsResultSet.hasNext()) {
                    BindingSet bindings = subjectsResultSet.next();
                    
                    Value subject = bindings.getValue(subjectVar);
                    String uri = CRBatchUtils.getNodeURI(subject);
                    if (uri != null) {
                        return uri;
                    }
                }
                close();
                return null;
            } catch (OpenRDFException e) {
                close();
                throw new CRBatchException(CRBatchErrorCodes.TRIPLE_SUBJECT_ITERATION,
                        "Database error while iterating over triple subjects.", e);
            }
        }
        
        /**
         * Returns {@code true} if the collection has more elements.
         * @return {@code true} if the collection has more elements
         */
        @Override
        public boolean hasNext() {
            return next != null;
        }

        /**
         * Returns an element from the collection and removes it from the collection.
         * @return the removed element
         * @throws CRBatchException error
         */
        @Override
        public String next() throws CRBatchException {
            if (subjectsResultSet == null) {
                throw new IllegalStateException("The collection is empty");
            }
            if (next == null) {
                throw new NoSuchElementException();
            }
            String result = next;
            next = getNextResult();
            return result;
        }

        @Override
        public void close() {
            if (subjectsResultSet != null) {
                try {
                    subjectsResultSet.close();
                } catch (QueryEvaluationException e) {
                    // ignore
                }
                subjectsResultSet = null;
            }
            if (connection != null) {
                try {
                    connection.close();
                } catch (RepositoryException e) {
                    // ignore
                }
                connection = null;
            }
        }

        /**
         * Does nothing.
         */
        @Override
        public void add(String node) {
            // do nothing
        }
    }

    private static final String SUBJECT_VARIABLE = VAR_PREFIX + "s";
    
    /**
     * SPARQL query that gets all distinct subjects of triples to be processed.
     * The result contains a single variable {@value #SUBJECT_VARIABLE}. 
     * 
     * Must be formatted with arguments:
     * (1) namespace prefixes declaration
     * (2) named graph restriction pattern
     * (3) named graph restriction variable
     * (4) seed resource restriction pattern
     * (5) seed resource restriction variable
     */
    private static final String SUBJECTS_QUERY = "%1$s"
            + "\n SELECT DISTINCT (?%5$s AS ?" + SUBJECT_VARIABLE + ")"
            + "\n WHERE {"
            + "\n   %2$s"
            + "\n   GRAPH ?%3$s {"
            + "\n     ?%5$s ?" + VAR_PREFIX + "p ?" + VAR_PREFIX + "o."
            + "\n     %4$s"
            + "\n   }"
            + "\n }";
    
    /**
     * An empty seed restriction.
     */
    private static final SparqlRestriction EMPTY_SEED_RESTRICTION = new SparqlRestrictionImpl("", VAR_PREFIX + "seed");
    
    /**
     * An empty source named graphs restriction. Must have variable different from {@link #EMPTY_SEED_RESTRICTION}.
     */
    private static final SparqlRestriction EMPTY_GRAPH_RESTRICTION = new SparqlRestrictionImpl("", VAR_PREFIX + "graph");

    /**
     * Creates a new instance.
     * @param dataSource an initialized data source  
     */
    public SeedSubjectsLoader(DataSource dataSource) {
        super(dataSource);
    }

    /**
     * Returns all subjects of triples in payload graphs matching the given named graph constraint pattern.
     * The collection should be closed after it is no longer needed.
     * The current implementation returns distinct values.
     * @param seedResourceRestriction SPARQL restriction on URI resources which are initially loaded and processed
     *      or null to iterate all subjects
     * @return collection of subjects of relevant triples
     * @throws CRBatchException query error or when seed resource restriction variable and named graph restriction variable are
     *         the same
     */
    public UriCollection getTripleSubjectsCollection(SparqlRestriction seedResourceRestriction) throws CRBatchException {
        long startTime = System.currentTimeMillis();

        SparqlRestriction graphRestriction = dataSource.getNamedGraphRestriction() != null 
                ? dataSource.getNamedGraphRestriction()
                : EMPTY_GRAPH_RESTRICTION;
        SparqlRestriction seedRestriction = seedResourceRestriction != null
                ? seedResourceRestriction
                : EMPTY_SEED_RESTRICTION;
                
                
        if (graphRestriction.getVar().equals(seedRestriction.getVar())) {
            throw new CRBatchException(
                    CRBatchErrorCodes.SEED_AND_SOURCE_VARIABLE_CONFLICT,
                    "Source named graph restriction and seed resource restrictions need to use different"
                            + " variables in SPARQL patterns, both using ?" + seedRestriction.getVar());
        }
        
        String query = String.format(Locale.ROOT, SUBJECTS_QUERY,
                getPrefixDecl(),
                graphRestriction.getPattern(),
                graphRestriction.getVar(),
                seedRestriction.getPattern(),
                seedRestriction.getVar());
        UriCollection result = new UriCollectionImpl(query, dataSource.getRepository());
        LOG.debug("CR-batch: Triple subjects collection initialized in {} ms", System.currentTimeMillis() - startTime);
        return result;
    }
}
