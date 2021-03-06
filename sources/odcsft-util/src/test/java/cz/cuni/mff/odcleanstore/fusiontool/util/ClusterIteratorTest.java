package cz.cuni.mff.odcleanstore.fusiontool.util;

import com.google.common.collect.ImmutableList;
import cz.cuni.mff.odcleanstore.fusiontool.testutil.LDFusionToolTestUtils;
import org.junit.Test;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;

import java.util.*;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;

public class ClusterIteratorTest {
    @Test
         public void iteratesOverCorrectClusters() throws Exception {
        // Arrange
        List<String> elements = ImmutableList.of("a", "b", "a", "c", "a", "b");

        // Act
        ClusterIterator<String> clusterIterator = new ClusterIterator<String>(elements, String.CASE_INSENSITIVE_ORDER);

        // Assert
        List<List<String>> actualClusters = collectClusters(clusterIterator);
        assertThat(actualClusters, containsInAnyOrder(
                (List<String>) ImmutableList.of("a", "a", "a"),
                ImmutableList.of("b", "b"),
                ImmutableList.of("c")));
    }

    @Test
    public void iteratesOverCorrectClustersWithPartialMatch() throws Exception {
        // Arrange
        List<Statement> elements = ImmutableList.of(
                LDFusionToolTestUtils.createHttpStatement("s3", "p", "o1"),
                LDFusionToolTestUtils.createHttpStatement("s1", "p", "o2"),
                LDFusionToolTestUtils.createHttpStatement("s2", "p", "o3"),
                LDFusionToolTestUtils.createHttpStatement("s1", "p", "o4"),
                LDFusionToolTestUtils.createHttpStatement("s2", "p", "o5"),
                LDFusionToolTestUtils.createHttpStatement("s1", "p", "o6"));
        Comparator<Statement> comparator = new Comparator<Statement>() {
            @Override
            public int compare(Statement o1, Statement o2) {
                return o1.getSubject().stringValue().compareTo(o2.getSubject().stringValue());
            }
        };

        // Act
        ClusterIterator<Statement> clusterIterator = new ClusterIterator<Statement>(elements, comparator);

        // Assert
        Map<URI, List<Statement>> expectedClusters = new HashMap<>();
        expectedClusters.put(LDFusionToolTestUtils.createHttpUri("s1"), ImmutableList.of(
                LDFusionToolTestUtils.createHttpStatement("s1", "p", "o2"),
                LDFusionToolTestUtils.createHttpStatement("s1", "p", "o4"),
                LDFusionToolTestUtils.createHttpStatement("s1", "p", "o6")));
        expectedClusters.put(LDFusionToolTestUtils.createHttpUri("s2"), ImmutableList.of(LDFusionToolTestUtils.createHttpStatement("s2", "p", "o3"), LDFusionToolTestUtils.createHttpStatement("s2", "p", "o5")));
        expectedClusters.put(LDFusionToolTestUtils.createHttpUri("s3"), ImmutableList.of(LDFusionToolTestUtils.createHttpStatement("s3", "p", "o1")));

        for (int i = 0; i < 3; i++) {
            List<Statement> actualCluster = clusterIterator.next();
            List<Statement> expectedCluster = expectedClusters.get(actualCluster.get(0).getSubject());
            assertThat(actualCluster, containsInAnyOrder(expectedCluster.toArray()));
        }
    }

    @Test
    public void iteratesOverEmptyCollection() throws Exception {
        // Arrange
        List<String> elements = Collections.emptyList();

        // Act
        ClusterIterator<String> clusterIterator = new ClusterIterator<String>(elements, String.CASE_INSENSITIVE_ORDER);

        // Assert
        assertThat(clusterIterator.hasNext(), is(false));
    }

    private <T> List<List<T>> collectClusters(ClusterIterator<T> clusterIterator) {
        List<List<T>> result = new ArrayList<>();
        while (clusterIterator.hasNext()) {
            result.add(clusterIterator.next());
        }
        return result;
    }
}