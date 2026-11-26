package portfolioboss;

import com.ib.client.EClient;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import portfolioboss.db.portfolio.HoldingEntity;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * The packages' rules, checked on the compiled classes by ArchUnit — a class in the wrong place, or one that uses a
 * layer above its own, fails the build with the file and the line. The layers, each using only the ones below it:
 * <ol>
 *   <li>the root — {@code Main}, {@code TwsPortfolioRunner}, {@code AppMetadata} — and {@code ui}: the app starting;</li>
 *   <li>{@code api}, the door: controllers, requests and responses, the services that only serve them;</li>
 *   <li>the flows — {@code sync}, {@code recommendations}, {@code analysis}, {@code alerts}, {@code tax}: what runs;</li>
 *   <li>{@code db}: what is stored, and only that;</li>
 *   <li>{@code calculation}: the pure rules;</li>
 *   <li>{@code model}: the values stored or sent as they are;</li>
 *   <li>the sources — {@code ib}, {@code sources} (its sockets, and a folder per source: {@code finviz},
 *   {@code finnhub}), {@code ai}, {@code exchangerates}, {@code flex}: they fetch and read;</li>
 *   <li>the base: {@code utils}, {@code exceptions}.</li>
 * </ol>
 * Inside a layer a feature folder may use another (in {@code calculation}, {@code investors} uses {@code portfolio}),
 * but never both ways. Only the app's classes are checked: a test may use any layer.
 */
class ArchitectureTest {

    /** Read once for every rule: the app's own classes, without the tests. */
    private static final JavaClasses APP_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("portfolioboss");

    private static final String[] FLOW_PACKAGES = {"portfolioboss.sync..", "portfolioboss.recommendations..",
            "portfolioboss.recommendationlists..", "portfolioboss.analysis..", "portfolioboss.alerts..",
            "portfolioboss.tax.."};
    private static final String[] SOURCE_PACKAGES = {"portfolioboss.ib..", "portfolioboss.sources..",
            "portfolioboss.ai..", "portfolioboss.exchangerates..", "portfolioboss.flex.."};

    @Test
    void eachLayerUsesOnlyTheLayersBelowIt() {
        layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("Root").definedBy("portfolioboss", "portfolioboss.ui..")
                .layer("Door").definedBy("portfolioboss.api..")
                .layer("Flows").definedBy(FLOW_PACKAGES)
                .layer("Stored data").definedBy("portfolioboss.db..")
                .layer("Calculation").definedBy("portfolioboss.calculation..")
                .layer("Shared values").definedBy("portfolioboss.model..")
                .layer("Sources").definedBy(SOURCE_PACKAGES)
                .layer("Base").definedBy("portfolioboss.utils..", "portfolioboss.exceptions..")
                .whereLayer("Root").mayNotBeAccessedByAnyLayer()
                .whereLayer("Door").mayNotBeAccessedByAnyLayer()
                .whereLayer("Flows").mayOnlyBeAccessedByLayers("Root", "Door")
                .whereLayer("Stored data").mayOnlyBeAccessedByLayers("Root", "Door", "Flows")
                .whereLayer("Calculation").mayOnlyBeAccessedByLayers("Root", "Door", "Flows", "Stored data")
                .whereLayer("Shared values").mayOnlyBeAccessedByLayers("Root", "Door", "Flows", "Stored data",
                        "Calculation")
                .whereLayer("Sources").mayOnlyBeAccessedByLayers("Root", "Door", "Flows", "Stored data",
                        "Calculation", "Shared values")
                .whereLayer("Base").mayOnlyBeAccessedByLayers("Root", "Door", "Flows", "Stored data", "Calculation",
                        "Shared values", "Sources")
                .check(APP_CLASSES);
    }

    /** Between the flows too ({@code alerts} uses {@code sync}, which uses {@code tax}), and between the sources. */
    @Test
    void theTopPackagesHaveNoCycle() {
        slices().matching("portfolioboss.(*)..").should().beFreeOfCycles().check(APP_CLASSES);
    }

    /**
     * Once for each layer with a folder per feature: {@code portfolioboss.db.tax..} is the folder {@code tax} of db; in
     * {@code sources} a folder is a source ({@code finviz}, {@code finnhub}), and no source uses another.
     */
    @ParameterizedTest
    @ValueSource(strings = {"api", "db", "calculation", "model", "sources"})
    void theFeatureFoldersOfALayerHaveNoCycle(String layer) {
        slices().matching("portfolioboss." + layer + ".(*)..").should().beFreeOfCycles().check(APP_CLASSES);
    }

    /**
     * The broker is the source of truth: a holding is created, refreshed and closed from IB's reading by the sync alone —
     * never by an endpoint. The entity's own constructor refreshes it too.
     */
    @Test
    void onlySyncCreatesAHoldingOrWritesIbsFiguresIntoIt() {
        noClasses().that().resideOutsideOfPackage("portfolioboss.sync..")
                .and().doNotBelongToAnyOf(HoldingEntity.class)
                .should().callMethodWhere(callToAMethodOf(HoldingEntity.class,
                        Set.of("refreshFromIb", "refreshSectorAndIndustryFromIb", "markClosed")))
                .orShould().callConstructorWhere(DescribedPredicate.describe("a new HoldingEntity",
                        call -> call.getTargetOwner().isEquivalentTo(HoldingEntity.class)))
                .check(APP_CLASSES);
    }

    /**
     * The flows return plain values and throw the app's own exceptions; which HTTP status each one is, is the door's
     * ({@code api.ApiErrorHandler}). The sources' clients call out over HTTP, so they may.
     */
    @Test
    void onlyTheApiAndTheSourcesKnowHttp() {
        noClasses().that().resideInAnyPackage(FLOW_PACKAGES)
                .or().resideInAnyPackage("portfolioboss.db..", "portfolioboss.calculation..", "portfolioboss.model..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..",
                        "org.springframework.http..")
                .check(APP_CLASSES);
    }

    /** Pure Java, so that each rule is tested directly, on made-up figures. */
    @Test
    void calculationAndModelUseNoSpringDatabaseOrIb() {
        noClasses().that().resideInAnyPackage("portfolioboss.calculation..", "portfolioboss.model..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..",
                        "org.hibernate..", "com.ib..")
                .check(APP_CLASSES);
    }

    /** What IB reports is read in {@code ib} into its own records, and everything else uses those. */
    @Test
    void onlyIbUsesIbsApi() {
        noClasses().that().resideOutsideOfPackage("portfolioboss.ib..")
                .should().dependOnClassesThat().resideInAPackage("com.ib..")
                .check(APP_CLASSES);
    }

    /** PortfolioBoss reads the account and never places, modifies or cancels an order (CLAUDE.md, read-only). */
    @Test
    void nothingPlacesModifiesOrCancelsAnOrder() {
        noClasses().should().callMethodWhere(callToAMethodOf(EClient.class,
                        Set.of("placeOrder", "cancelOrder", "reqGlobalCancel", "exerciseOptions")))
                .check(APP_CLASSES);
    }

    /** A call to one of these methods of {@code owner}, or of a class that extends it ({@code EClientSocket}). */
    private DescribedPredicate<JavaCall<?>> callToAMethodOf(Class<?> owner, Set<String> methodNames) {
        return DescribedPredicate.describe("a call to " + methodNames + " of " + owner.getSimpleName(),
                call -> methodNames.contains(call.getName()) && call.getTargetOwner().isAssignableTo(owner));
    }
}
