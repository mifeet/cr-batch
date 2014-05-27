package cz.cuni.mff.odcleanstore.fusiontool.loaders;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import cz.cuni.mff.odcleanstore.core.ODCSUtils;
import cz.cuni.mff.odcleanstore.fusiontool.config.ConfigParameters;
import cz.cuni.mff.odcleanstore.fusiontool.config.EnumDataSourceType;
import cz.cuni.mff.odcleanstore.fusiontool.config.SparqlRestriction;
import cz.cuni.mff.odcleanstore.fusiontool.config.SparqlRestrictionImpl;
import cz.cuni.mff.odcleanstore.fusiontool.loaders.data.AllTriplesLoader;
import cz.cuni.mff.odcleanstore.fusiontool.loaders.data.AllTriplesRepositoryLoader;
import cz.cuni.mff.odcleanstore.fusiontool.source.DataSource;
import cz.cuni.mff.odcleanstore.fusiontool.source.DataSourceImpl;
import org.junit.Test;
import org.mockito.Mockito;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.rio.RDFHandler;
import org.openrdf.rio.helpers.StatementCollector;
import org.openrdf.sail.memory.MemoryStore;

import java.util.*;

import static cz.cuni.mff.odcleanstore.fusiontool.testutil.ContextAwareStatementIsEqual.contextAwareStatementIsEqual;
import static cz.cuni.mff.odcleanstore.fusiontool.testutil.ODCSFTTestUtils.createHttpStatement;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class AllTriplesRepositoryLoaderTest {
    public static final SparqlRestrictionImpl EMPTY_SPARQL_RESTRICTION = new SparqlRestrictionImpl("", "338ae1bdf9_x");

    @Test
    public void loadsAllTriplesWhenNumberOfStatementsIsNotDivisibleByMaxResultSize() throws Exception {
        // Arrange
        Collection<Statement> statements = ImmutableList.of(
                createHttpStatement("s1", "p", "o", "g1"),
                createHttpStatement("s2", "p", "o", "g2"),
                createHttpStatement("s3", "p", "o", "g3"),
                createHttpStatement("s4", "p", "o", "g4"),
                createHttpStatement("s5", "p", "o", "g5")
        );
        DataSource dataSource = createDataSource(statements, 2);

        // Act
        Collection<Statement> result = new HashSet<Statement>();
        AllTriplesLoader loader = new AllTriplesRepositoryLoader(dataSource);
        loader.loadAllTriples(new StatementCollector(result));
        loader.close();

        // Assert
        assertThat(result.size(), equalTo(statements.size()));
        dataSource.getRepository().shutDown();
    }

    @Test
    public void loadsAllTriplesWhenNumberOfStatementsIsDivisibleByMaxResultSize() throws Exception {
        // Arrange
        Collection<Statement> statements = ImmutableList.of(
                createHttpStatement("s1", "p", "o", "g1"),
                createHttpStatement("s2", "p", "o", "g2"),
                createHttpStatement("s3", "p", "o", "g3"),
                createHttpStatement("s4", "p", "o", "g4")
        );
        DataSource dataSource = createDataSource(statements, 2);

        // Act
        Collection<Statement> result = new HashSet<Statement>();
        AllTriplesRepositoryLoader loader = new AllTriplesRepositoryLoader(dataSource);
        loader.loadAllTriples(new StatementCollector(result));
        loader.close();

        // Assert
        assertThat(result.size(), equalTo(statements.size()));
        dataSource.getRepository().shutDown();
    }

    @Test
    public void returnsEmptyResultWhenNoMatchingTriplesExist() throws Exception {
        // Arrange
        Collection<Statement> statements = ImmutableList.of();
        DataSource dataSource = createDataSource(statements, 2);

        // Act
        Collection<Statement> result = new HashSet<Statement>();
        AllTriplesRepositoryLoader loader = new AllTriplesRepositoryLoader(dataSource);
        loader.loadAllTriples(new StatementCollector(result));
        loader.close();

        // Assert
        assertThat(result.size(), equalTo(0));
        dataSource.getRepository().shutDown();
    }

    @Test
    public void limitsResultsWhenNamedGraphRestrictionGiven() throws Exception {
        // Arrange
        Statement statement1 = createHttpStatement("s1", "p", "o", "g1");
        Statement statement2 = createHttpStatement("s2", "p", "o", "g2");
        List<Statement> statements = ImmutableList.of(statement1, statement2);

        SparqlRestriction namedGraphRestriction = new SparqlRestrictionImpl(
                "FILTER(?gg = <" + statement1.getContext().stringValue() + ">)",
                "gg");
        DataSource dataSource = createDataSource(statements, namedGraphRestriction, new HashMap<String, String>(), 100, "test");

        // Act
        Collection<Statement> result = new HashSet<Statement>();
        AllTriplesRepositoryLoader loader = new AllTriplesRepositoryLoader(dataSource);
        loader.loadAllTriples(new StatementCollector(result));
        loader.close();

        // Assert
        assertThat(result.size(), equalTo(1));
        assertThat(result.iterator().next(), contextAwareStatementIsEqual(statement1));
        dataSource.getRepository().shutDown();
    }

    @Test
    public void callsStartRDFAndEndRDFOnGivenHandler() throws Exception {
        // Arrange
        RDFHandler rdfHandler = Mockito.mock(RDFHandler.class);
        Collection<Statement> statements = ImmutableList.of(
                createHttpStatement("s1", "p", "o", "g1")
        );
        DataSource dataSource = createDataSource(statements, 2);

        // Act
        AllTriplesRepositoryLoader loader = new AllTriplesRepositoryLoader(dataSource);
        loader.loadAllTriples((rdfHandler));
        loader.close();

        // Assert
        Mockito.verify(rdfHandler).startRDF();
        Mockito.verify(rdfHandler).endRDF();
    }

    @Test
    public void usesPrefixesWhenPrefixesGiven() throws Exception {
        // Arrange
        Statement statement1 = createHttpStatement("s1", "p", "o", "example1.com/g1");
        Statement statement2 = createHttpStatement("s2", "p", "o", "example2.com/g2");
        List<Statement> statements = ImmutableList.of(statement1, statement2);

        SparqlRestriction namedGraphRestriction = new SparqlRestrictionImpl("FILTER(?gg = ex1:g1)", "gg");
        HashMap<String, String> prefixes = new HashMap<String, String>();
        prefixes.put("ex1", "http://example1.com/");
        prefixes.put("ex2", "http://example2.com/");
        DataSource dataSource = createDataSource(statements, namedGraphRestriction, prefixes, 100, "test");

        // Act
        Collection<Statement> result = new HashSet<Statement>();
        AllTriplesRepositoryLoader loader = new AllTriplesRepositoryLoader(dataSource);
        loader.loadAllTriples(new StatementCollector(result));
        loader.close();

        // Assert
        assertThat(result.size(), equalTo(1));
        assertThat(result.iterator().next(), contextAwareStatementIsEqual(statement1));
        dataSource.getRepository().shutDown();
    }

    @Test
    public void returnsNonNullDefaultContext() throws Exception {
        // Arrange
        Collection<Statement> statements = ImmutableList.of();
        DataSource dataSource = createDataSource(statements, 2);
        assertThat(dataSource.getParams().get(ConfigParameters.DATA_SOURCE_FILE_BASE_URI), nullValue());

        // Act
        AllTriplesLoader loader = new AllTriplesRepositoryLoader(dataSource);
        URI defaultContext = loader.getDefaultContext();
        loader.close();

        // Assert
        assertTrue(ODCSUtils.isValidIRI(defaultContext.stringValue()));
    }

    private DataSource createDataSource(Collection<Statement> statements, int maxSparqlResultRows) throws RepositoryException {
        return createDataSource(
                statements,
                EMPTY_SPARQL_RESTRICTION,
                new HashMap<String, String>(),
                maxSparqlResultRows,
                "test");
    }

    private DataSource createDataSource(Collection<Statement> statements,
            SparqlRestriction namedGraphRestriction,
            Map<String, String> prefixes,
            int maxSparqlResultRows,
            String name)
            throws RepositoryException {
        Repository repository = new SailRepository(new MemoryStore());
        repository.initialize();
        RepositoryConnection connection = repository.getConnection();
        connection.add(statements);
        connection.close();
        Map<String, String> params = ImmutableMap.of(ConfigParameters.DATA_SOURCE_SPARQL_RESULT_MAX_ROWS, Integer.toString(maxSparqlResultRows));
        return new DataSourceImpl(repository, prefixes, name, EnumDataSourceType.SPARQL, params, namedGraphRestriction);
    }
}